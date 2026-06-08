package com.atlas.audit;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存 Agent 审计记录器。
 *
 * <p>M5.25 的目标是先把审计事件语义打通到执行边界。该实现只保留最近事件用于诊断和测试，
 * 不替代后续数据库/安全日志/事件流持久化。</p>
 */
@Service
public class InMemoryAgentAuditRecorder implements AgentAuditRecorder, AgentAuditSnapshotProvider {

    private static final String SNAPSHOT_SCHEMA_VERSION = "agent-audit-snapshot.v1";
    private static final int MAX_RECENT_EVENTS = 200;

    private final Object monitor = new Object();
    private final ArrayDeque<AgentAuditEvent> recentEvents = new ArrayDeque<>();
    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong blockedEvents = new AtomicLong();
    private final AtomicLong errorEvents = new AtomicLong();

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
    }

    public List<AgentAuditEvent> recentEvents() {
        synchronized (monitor) {
            return List.copyOf(recentEvents);
        }
    }

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
            "totalEvents", totalEvents.get(),
            "blockedEvents", blockedEvents.get(),
            "errorEvents", errorEvents.get(),
            "recentEvents", events.stream()
                .map(this::diagnosticSummary)
                .toList()
        );
    }

    private Map<String, Object> replayCapabilities() {
        return Map.of(
            "maxRecentEvents", MAX_RECENT_EVENTS,
            "supportsTraceLookup", true,
            "supportsRecentEventTimeline", true,
            "containsRawPrincipal", false,
            "containsRawReason", false,
            "containsRawParameterValues", false,
            "durableRetention", false
        );
    }

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
            || lowerName.contains("api_key")) {
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
}
