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
 * Append-only JSONL durable audit sink.
 *
 * <p>It intentionally stores redacted evidence only. Raw user, organization,
 * conversation, reason text, endpoint strings, and parameter values stay out of
 * the durable record. The stable telemetry projection is the storage contract.</p>
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
