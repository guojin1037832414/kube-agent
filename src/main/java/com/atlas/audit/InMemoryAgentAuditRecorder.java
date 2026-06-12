package com.atlas.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带可选持久化出口的内存 Agent 审计记录器。
 *
 * <p>中文说明：内存 ring buffer 是给测试、管理员快照和前端诊断使用的快速视图；
 * durable sink 是追加式证据通道，后续可以替换为数据库、Kafka 或 SIEM，而不改变
 * SafeToolExecutor 的审计语义。</p>
 *
 * <p>安全边界：本类不执行 Tool、不调用 MCP、不访问 kube-manager，也不参与 prompt 构造。
 * 查询接口只返回 redacted read model，不返回原始 principal、reason、endpoint 或参数值。
 * durable 写入失败不会篡改 Tool 结果，但 durabilityStatus 会把风险暴露给 admin-only 观测面；
 * 对高风险写操作，prewriteHighRisk 仍由执行链 fail-closed 处理。</p>
 */
@Service
@Primary
public class InMemoryAgentAuditRecorder implements AgentAuditRecorder, AgentAuditSnapshotProvider, AgentAuditQueryService {

    private static final String SNAPSHOT_SCHEMA_VERSION = "agent-audit-snapshot.v1";
    private static final int MAX_RECENT_EVENTS = 200;

    private final Object monitor = new Object();
    private final ArrayDeque<AgentAuditEvent> recentEvents = new ArrayDeque<>();
    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong blockedEvents = new AtomicLong();
    private final AtomicLong errorEvents = new AtomicLong();
    private final AgentAuditTelemetryPublisher telemetryPublisher;
    private final AgentAuditDurableSink durableSink;
    private final JsonlAgentAuditQueryService jsonlQueryService;

    public InMemoryAgentAuditRecorder() {
        this(null, AgentAuditDurableSink.noop(), (JsonlAgentAuditQueryService) null);
    }

    @Autowired
    public InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher,
                                      AgentAuditDurableSink durableSink,
                                      ObjectProvider<JsonlAgentAuditQueryService> jsonlQueryServiceProvider) {
        this(
            telemetryPublisher,
            durableSink,
            jsonlQueryServiceProvider != null ? jsonlQueryServiceProvider.getIfAvailable() : null
        );
    }

    public InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher,
                                      AgentAuditDurableSink durableSink) {
        this(telemetryPublisher, durableSink, (JsonlAgentAuditQueryService) null);
    }

    InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher,
                               AgentAuditDurableSink durableSink,
                               JsonlAgentAuditQueryService jsonlQueryService) {
        this.telemetryPublisher = telemetryPublisher;
        this.durableSink = durableSink != null ? durableSink : AgentAuditDurableSink.noop();
        this.jsonlQueryService = jsonlQueryService;
    }

    public InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher) {
        this(telemetryPublisher, AgentAuditDurableSink.noop());
    }

    @Override
    public void record(AgentAuditEvent event) {
        if (event == null) {
            return;
        }
        totalEvents.incrementAndGet();
        if (event.outcome() == AgentAuditOutcome.BLOCKED) {
            blockedEvents.incrementAndGet();
        }
        if (event.outcome() == AgentAuditOutcome.ERROR) {
            errorEvents.incrementAndGet();
        }
        synchronized (monitor) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeLast();
            }
        }
        appendDurable(event);
        publishTelemetry(event);
    }

    /**
     * 返回内存中最近审计事件的副本。
     *
     * <p>中文说明：这是进程内诊断材料，不是 durable 合规存储；调用方不能修改内部队列。</p>
     */
    public List<AgentAuditEvent> recentEvents() {
        synchronized (monitor) {
            return List.copyOf(recentEvents);
        }
    }

    /**
     * 生成管理员快照。
     *
     * <p>安全边界：快照只面向 Observability admin-only 入口，且会再次把参数名、reason
     * 和遥测投影压缩成脱敏摘要，避免诊断视图泄露原始运行时输入。</p>
     */
    @Override
    public Map<String, Object> snapshot() {
        List<AgentAuditEvent> events;
        synchronized (monitor) {
            events = new ArrayList<>(recentEvents);
        }
        return Map.of(
            "schemaVersion", SNAPSHOT_SCHEMA_VERSION,
            "generatedAt", Instant.now(Clock.systemUTC()),
            "replayCapabilities", replayCapabilities(),
            "durability", durabilityStatus().toDiagnosticMap(),
            "totalEvents", totalEvents.get(),
            "blockedEvents", blockedEvents.get(),
            "errorEvents", errorEvents.get(),
            "recentEvents", events.stream()
                .map(this::diagnosticSummary)
                .toList()
        );
    }

    /**
     * 暴露 durable audit 当前状态，供前端和评测判断证据链是否可靠。
     */
    public AgentAuditDurabilityStatus durabilityStatus() {
        return durableSink.status();
    }

    @Override
    public AgentAuditDurableReceipt prewriteHighRisk(AgentAuditEvent event) {
        return durableSink.prewriteHighRisk(event);
    }

    /**
     * 按 auditId 查询脱敏事件。
     *
     * <p>中文说明：优先查询 JSONL durable read model；未启用时退回内存 ring buffer，
     * 但两种路径都必须保持 redacted-only。</p>
     */
    @Override
    public AgentAuditQueryResponse findByAuditId(String auditId) {
        if (jsonlQueryAvailable()) {
            return jsonlQueryService.findByAuditId(auditId);
        }
        String normalizedAuditId = safeText(auditId);
        List<AgentAuditQueryEvent> matches = recentEvents().stream()
            .filter(event -> normalizedAuditId.equals(event.auditId()))
            .map(AgentAuditQueryEvent::from)
            .toList();
        return AgentAuditQueryResponse.of(
            "auditId",
            normalizedAuditId,
            1,
            false,
            indexMetadata(),
            matches
        );
    }

    @Override
    public AgentAuditQueryResponse findByTraceId(String traceId, int maxResults) {
        if (jsonlQueryAvailable()) {
            return jsonlQueryService.findByTraceId(traceId, maxResults);
        }
        String normalizedTraceId = safeText(traceId);
        int boundedMaxResults = Math.max(1, Math.min(maxResults, MAX_RECENT_EVENTS));
        List<AgentAuditQueryEvent> matches = recentEvents().stream()
            .filter(event -> normalizedTraceId.equals(event.traceId()))
            .limit(boundedMaxResults + 1L)
            .map(AgentAuditQueryEvent::from)
            .toList();
        boolean truncated = matches.size() > boundedMaxResults;
        List<AgentAuditQueryEvent> visibleMatches = truncated
            ? matches.subList(0, boundedMaxResults)
            : matches;
        return AgentAuditQueryResponse.of(
            "traceId",
            normalizedTraceId,
            boundedMaxResults,
            truncated,
            indexMetadata(),
            visibleMatches
        );
    }

    @Override
    public AgentAuditQueryResponse recentEvents(int maxResults) {
        if (jsonlQueryAvailable()) {
            return jsonlQueryService.recentEvents(maxResults);
        }
        int boundedMaxResults = Math.max(1, Math.min(maxResults, MAX_RECENT_EVENTS));
        List<AgentAuditQueryEvent> matches = recentEvents().stream()
            .limit(boundedMaxResults + 1L)
            .map(AgentAuditQueryEvent::from)
            .toList();
        boolean truncated = matches.size() > boundedMaxResults;
        List<AgentAuditQueryEvent> visibleMatches = truncated
            ? matches.subList(0, boundedMaxResults)
            : matches;
        return AgentAuditQueryResponse.of(
            "recent",
            "newest-first",
            boundedMaxResults,
            truncated,
            indexMetadata(),
            visibleMatches
        );
    }

    @Override
    public Map<String, Object> indexMetadata() {
        if (jsonlQueryAvailable()) {
            return jsonlQueryService.indexMetadata();
        }
        AgentAuditDurabilityStatus durabilityStatus = durabilityStatus();
        return Map.of(
            "schemaVersion", "agent-audit-index.v1",
            "backend", "in-memory-ring-buffer",
            "lookupFields", List.of("auditId", "traceId", "recent"),
            "maxRecentEvents", MAX_RECENT_EVENTS,
            "durableRetention", durabilityStatus.durableRetention(),
            "durableStorageType", durabilityStatus.storageType(),
            "containsRawPrincipal", false,
            "containsRawReason", false,
            "containsRawParameterValues", false,
            "containsRawEndpoints", false
        );
    }

    private boolean jsonlQueryAvailable() {
        return jsonlQueryService != null && jsonlQueryService.available();
    }

    private Map<String, Object> replayCapabilities() {
        return Map.of(
            "maxRecentEvents", MAX_RECENT_EVENTS,
            "supportsTraceLookup", true,
            "supportsRecentEventTimeline", true,
            "containsRawPrincipal", false,
            "containsRawReason", false,
            "containsRawParameterValues", false,
            "durableRetention", durabilityStatus().durableRetention()
        );
    }

    /**
     * 构建不会泄露原始输入的诊断摘要。
     *
     * <p>中文说明：这里把 event 中可能较敏感的字段进一步压缩为前端可显示的只读证据，
     * 不恢复原始参数值，也不输出完整 reason 文本。</p>
     */
    private Map<String, Object> diagnosticSummary(AgentAuditEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("auditId", event.auditId());
        summary.put("occurredAt", event.occurredAt());
        summary.put("traceId", event.traceId());
        summary.put("intentId", event.intentId());
        summary.put("toolName", event.toolName());
        summary.put("source", event.source());
        summary.put("httpMethod", event.httpMethod());
        summary.put("operationType", event.operationType());
        summary.put("requiresConfirmation", event.requiresConfirmation());
        summary.put("outcome", event.outcome());
        summary.put("executed", event.executed());
        summary.put("success", event.success());
        summary.put("parameterSummary", diagnosticParameterSummary(event.parameterSummary()));
        summary.put("reasonSummary", reasonSummary(event.reason()));
        summary.put("telemetry", AgentAuditTelemetryProjector.project(event).toDiagnosticMap());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> diagnosticParameterSummary(Map<String, Object> parameterSummary) {
        if (parameterSummary == null || parameterSummary.isEmpty()) {
            return Map.of("count", 0, "keys", List.of());
        }
        Object rawKeys = parameterSummary.get("keys");
        List<Map<String, Object>> keys = rawKeys instanceof List<?>
            ? ((List<?>) rawKeys).stream()
                .filter(Map.class::isInstance)
                .map(item -> diagnosticParameterKey((Map<String, Object>) item))
                .toList()
            : List.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", parameterSummary.getOrDefault("count", keys.size()));
        summary.put("truncated", parameterSummary.getOrDefault("truncated", false));
        summary.put("keys", keys);
        return summary;
    }

    private Map<String, Object> diagnosticParameterKey(Map<String, Object> item) {
        Map<String, Object> key = new LinkedHashMap<>();
        boolean protectedField = Boolean.TRUE.equals(item.get("protected"));
        String rawName = item.get("name") != null ? item.get("name").toString() : "";
        key.put("name", safeDiagnosticName(rawName, protectedField));
        key.put("protected", protectedField);
        key.put("type", item.getOrDefault("type", ""));
        key.put("present", item.getOrDefault("present", false));
        return key;
    }

    private String safeDiagnosticName(String name, boolean protectedField) {
        if (protectedField) {
            return "<protected>";
        }
        if (name == null || name.isBlank()) {
            return "";
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("secret")
            || lowerName.contains("password")
            || lowerName.contains("credential")
            || lowerName.contains("authorization")
            || lowerName.contains("apikey")
            || lowerName.contains("api_key")
            || lowerName.contains("token")) {
            return "<redacted>";
        }
        return name.length() <= 80 ? name : name.substring(0, 80) + "...";
    }

    private Map<String, Object> reasonSummary(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of("present", false, "length", 0);
        }
        return Map.of(
            "present", true,
            "length", reason.length()
        );
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    /**
     * 追加 durable audit 证据。
     *
     * <p>安全边界：最终审计写入失败只影响诊断状态，不反向改写 Tool 结果；高风险执行前证据
     * 由 {@link #prewriteHighRisk(AgentAuditEvent)} 和 SafeToolExecutor 的 fail-closed 策略控制。</p>
     */
    private void appendDurable(AgentAuditEvent event) {
        try {
            durableSink.append(event);
        } catch (RuntimeException ignored) {
            // Durable failures are exposed through durabilityStatus().
        }
    }

    /**
     * 发布审计遥测投影。
     *
     * <p>中文说明：遥测是可观测材料，不是业务控制流；失败时不能改变 Tool 执行结果。</p>
     */
    private void publishTelemetry(AgentAuditEvent event) {
        if (telemetryPublisher == null) {
            return;
        }
        try {
            telemetryPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Telemetry publication is diagnostic and must not rewrite the Tool result.
        }
    }
}
