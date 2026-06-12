package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 追加式 JSONL durable audit 写入器。
 *
 * <p>中文说明：Phase 1 先用简单、可检查、易迁移的 JSONL 保存审计证据。每一行都是一条
 * redacted durable audit record，后续可以被 replay、eval、前端审计页或外部 SIEM 消费。</p>
 *
 * <p>安全边界：本类只存脱敏证据，不保存 raw principal、raw reason、raw endpoints、
 * raw parameter values 或真实 kube-manager 请求体；持久记录只包含 reasonSummary、
 * parameterSummary、apiEndpointCount 和 telemetry projection。它不执行 Tool、不调用 MCP、
 * 不访问 kube-manager，也不把 durable audit 写入结果变成 prompt 或 release authority。</p>
 */
@Component
public class JsonlAgentAuditDurableSink implements AgentAuditDurableSink {

    private static final Logger log = LoggerFactory.getLogger(JsonlAgentAuditDurableSink.class);
    private static final String STORAGE_TYPE = "jsonl";

    private final AgentAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final Path path;
    private final Object writeMonitor = new Object();
    private final AtomicLong acceptedRecords = new AtomicLong();
    private final AtomicLong failedRecords = new AtomicLong();
    private volatile String lastError = "";
    private volatile boolean ready;

    public JsonlAgentAuditDurableSink(AgentAuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties != null ? properties : new AgentAuditProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.path = resolvePath(this.properties.getDurable().getPath());
        this.ready = preparePath();
    }

    @Override
    public void append(AgentAuditEvent event) {
        if (!properties.getDurable().isEnabled() || event == null) {
            return;
        }
        appendDurableRecord(event, "FINAL");
    }

    @Override
    public AgentAuditDurableReceipt prewriteHighRisk(AgentAuditEvent event) {
        if (!properties.getDurable().isEnabled() || event == null) {
            return AgentAuditDurableReceipt.rejected(
                "AGENT_AUDIT_DURABLE_DISABLED",
                status()
            );
        }
        try {
            appendDurableRecord(event, "PRE_EXECUTION");
            return AgentAuditDurableReceipt.accepted(
                event.auditId(),
                STORAGE_TYPE,
                status()
            );
        } catch (RuntimeException ex) {
            return AgentAuditDurableReceipt.rejected(
                "AGENT_AUDIT_DURABLE_PREWRITE_FAILED",
                status()
            );
        }
    }

    /**
     * 追加一条 JSONL 证据记录。
     *
     * <p>中文说明：recordPhase 用于区分 PRE_EXECUTION 和 FINAL，帮助 eval 检查高风险写操作
     * 是否先有 durable prewrite 证据。文件写入是追加式，不在这里做删除、导出或轮转。</p>
     */
    private void appendDurableRecord(AgentAuditEvent event, String recordPhase) {
        try {
            if (!ready && !preparePath()) {
                throw new IllegalStateException("durable audit path is not writable");
            }
            synchronized (writeMonitor) {
                Files.writeString(
                    path,
                    objectMapper.writeValueAsString(toDurableRecord(event, recordPhase)) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            }
            acceptedRecords.incrementAndGet();
            ready = true;
            lastError = "";
        } catch (IOException | RuntimeException ex) {
            failedRecords.incrementAndGet();
            ready = false;
            lastError = ex.getClass().getSimpleName() + ": " + safeText(ex.getMessage());
            log.warn("[AgentAudit] durable JSONL append failed: path={}, auditId={}", path, event.auditId(), ex);
            throw new IllegalStateException("AGENT_AUDIT_DURABLE_APPEND_FAILED", ex);
        }
    }

    @Override
    public AgentAuditDurabilityStatus status() {
        if (properties.getDurable().isEnabled() && !ready) {
            ready = preparePath();
        }
        return new AgentAuditDurabilityStatus(
            properties.getDurable().isEnabled(),
            !properties.getDurable().isEnabled() || ready,
            properties.getDurable().isEnabled(),
            properties.getDurable().isFailClosedForHighRisk(),
            properties.getDurable().isEnabled() ? STORAGE_TYPE : "none",
            properties.getDurable().isEnabled() ? path.toString() : "",
            acceptedRecords.get(),
            failedRecords.get(),
            lastError
        );
    }

    /**
     * 把运行时审计事件投影为可长期保存的脱敏记录。
     *
     * <p>安全边界：这里是 durable audit 的最后一道隐私防线，任何原始身份、原因文本、
     * endpoint 字符串或参数值都不能进入 JSONL。</p>
     */
    private Map<String, Object> toDurableRecord(AgentAuditEvent event, String recordPhase) {
        AgentAuditTelemetryProjection projection = AgentAuditTelemetryProjector.project(event);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", "agent-audit-durable.v1");
        record.put("recordPhase", safeText(recordPhase));
        record.put("recordedAt", Instant.now(Clock.systemUTC()).toString());
        record.put("auditId", event.auditId());
        record.put("traceId", event.traceId());
        record.put("eventTime", event.occurredAt() != null ? event.occurredAt().toString() : "");
        record.put("intentId", safeText(event.intentId()));
        record.put("toolName", safeText(event.toolName()));
        record.put("source", event.source() != null ? event.source().name() : "");
        record.put("httpMethod", safeText(event.httpMethod()));
        record.put("operationType", event.operationType() != null
            ? event.operationType().name()
            : AtlasToolMapping.OperationType.UNKNOWN.name());
        record.put("requiresConfirmation", event.requiresConfirmation());
        record.put("outcome", event.outcome() != null ? event.outcome().name() : AgentAuditOutcome.BLOCKED.name());
        record.put("executed", event.executed());
        record.put("success", event.success());
        record.put("reasonSummary", Map.of(
            "present", event.reason() != null && !event.reason().isBlank(),
            "length", event.reason() != null ? event.reason().length() : 0
        ));
        record.put("parameterSummary", redactedParameterSummary(event.parameterSummary()));
        record.put("apiEndpointCount", event.apiEndpoints() != null ? event.apiEndpoints().size() : 0);
        record.put("telemetry", projection.toDiagnosticMap());
        record.put("privacy", Map.of(
            "containsRawPrincipal", false,
            "containsRawReason", false,
            "containsRawParameterValues", false,
            "containsRawEndpoints", false
        ));
        return record;
    }

    @SuppressWarnings("unchecked")
    /**
     * 对参数摘要做二次脱敏。
     *
     * <p>中文说明：即使上游已经标记 protected 字段，这里仍根据名称兜底清洗 token/password
     * 等敏感参数名，确保 durable 证据只说明“有这个类别的参数”，不泄露具体值。</p>
     */
    private Map<String, Object> redactedParameterSummary(Map<String, Object> parameterSummary) {
        if (parameterSummary == null || parameterSummary.isEmpty()) {
            return Map.of("count", 0, "keys", java.util.List.of());
        }
        Object keys = parameterSummary.get("keys");
        java.util.List<Map<String, Object>> redactedKeys = keys instanceof java.util.List<?>
            ? ((java.util.List<?>) keys).stream()
                .filter(Map.class::isInstance)
                .map(item -> redactedParameterKey((Map<String, Object>) item))
                .toList()
            : java.util.List.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", parameterSummary.getOrDefault("count", redactedKeys.size()));
        summary.put("truncated", parameterSummary.getOrDefault("truncated", false));
        summary.put("keys", redactedKeys);
        return summary;
    }

    private Map<String, Object> redactedParameterKey(Map<String, Object> item) {
        boolean protectedField = Boolean.TRUE.equals(item.get("protected"));
        String name = safeText(item.get("name"));
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("name", protectedField ? "<protected>" : safeParameterName(name));
        key.put("protected", protectedField);
        key.put("type", item.getOrDefault("type", ""));
        key.put("present", item.getOrDefault("present", false));
        return key;
    }

    private String safeParameterName(String name) {
        if (name.isBlank()) {
            return "";
        }
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
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

    private boolean preparePath() {
        if (!properties.getDurable().isEnabled()) {
            lastError = "";
            return true;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            synchronized (writeMonitor) {
                Files.writeString(
                    path,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            }
            lastError = "";
            return true;
        } catch (IOException | RuntimeException ex) {
            lastError = ex.getClass().getSimpleName() + ": " + safeText(ex.getMessage());
            log.warn("[AgentAudit] durable JSONL path is not writable: path={}", path, ex);
            return false;
        }
    }

    private Path resolvePath(String configuredPath) {
        String value = configuredPath != null && !configuredPath.isBlank()
            ? configuredPath
            : "target/agent-audit/agent-audit.jsonl";
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String safeText(Object value) {
        return value != null ? value.toString() : "";
    }

    @SuppressWarnings("unused")
    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
