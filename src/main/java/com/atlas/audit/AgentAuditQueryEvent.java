package com.atlas.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Redacted audit event view for admin query APIs.
 *
 * <p>This DTO is intentionally narrower than {@link AgentAuditEvent}. It is
 * safe for observability replay because it does not expose raw principal,
 * organization, conversation, endpoint strings, reason text, or parameter
 * values.</p>
 */
public record AgentAuditQueryEvent(
    String auditId,
    Instant occurredAt,
    String traceId,
    String intentId,
    String toolName,
    String source,
    String httpMethod,
    String operationType,
    boolean requiresConfirmation,
    String outcome,
    boolean executed,
    boolean success,
    int apiEndpointCount,
    Map<String, Object> reasonSummary,
    Map<String, Object> parameterSummary,
    Map<String, Object> telemetry
) {

    public static AgentAuditQueryEvent from(AgentAuditEvent event) {
        AgentAuditEvent safeEvent = event != null
            ? event
            : new AgentAuditEvent(
                "", null, "", "", "", "", "", "", null, "",
                List.of(), null, false, AgentAuditOutcome.BLOCKED, false, false, "", Map.of());
        return new AgentAuditQueryEvent(
            safeText(safeEvent.auditId()),
            safeEvent.occurredAt(),
            safeText(safeEvent.traceId()),
            safeText(safeEvent.intentId()),
            safeText(safeEvent.toolName()),
            safeEvent.source() != null ? safeEvent.source().name() : "",
            safeText(safeEvent.httpMethod()),
            safeEvent.operationType() != null ? safeEvent.operationType().name() : "UNKNOWN",
            safeEvent.requiresConfirmation(),
            safeEvent.outcome() != null ? safeEvent.outcome().name() : AgentAuditOutcome.BLOCKED.name(),
            safeEvent.executed(),
            safeEvent.success(),
            safeEvent.apiEndpoints() != null ? safeEvent.apiEndpoints().size() : 0,
            reasonSummary(safeEvent.reason()),
            parameterSummary(safeEvent.parameterSummary()),
            AgentAuditTelemetryProjector.project(safeEvent).toDiagnosticMap()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameterSummary(Map<String, Object> parameterSummary) {
        if (parameterSummary == null || parameterSummary.isEmpty()) {
            return Map.of("count", 0, "keys", List.of());
        }
        Object rawKeys = parameterSummary.get("keys");
        List<Map<String, Object>> keys = rawKeys instanceof List<?>
            ? ((List<?>) rawKeys).stream()
                .filter(Map.class::isInstance)
                .map(item -> parameterKey((Map<String, Object>) item))
                .toList()
            : List.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", parameterSummary.getOrDefault("count", keys.size()));
        summary.put("truncated", parameterSummary.getOrDefault("truncated", false));
        summary.put("keys", keys);
        return summary;
    }

    private static Map<String, Object> parameterKey(Map<String, Object> item) {
        boolean protectedField = Boolean.TRUE.equals(item.get("protected"));
        String rawName = safeText(item.get("name"));
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("name", safeName(rawName, protectedField));
        key.put("protected", protectedField);
        key.put("type", item.getOrDefault("type", ""));
        key.put("present", item.getOrDefault("present", false));
        return key;
    }

    private static String safeName(String name, boolean protectedField) {
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

    private static Map<String, Object> reasonSummary(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of("present", false, "length", 0);
        }
        return Map.of("present", true, "length", reason.length());
    }

    private static String safeText(Object value) {
        return value != null ? value.toString() : "";
    }
}
