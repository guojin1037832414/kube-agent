package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 审计事件到遥测/回放属性的投影器。
 *
 * <p>稳定属性使用 `atlas.agent.*` 命名空间，作为本项目自己的长期契约；`experimentalOtelAttributes`
 * 只承载当前可映射的 OTel / GenAI 候选字段，后续规范变动时可以迁移而不破坏审计存储和前端回放。</p>
 */
public final class AgentAuditTelemetryProjector {

    public static final String SCHEMA_VERSION = "agent-audit-telemetry.v1";
    public static final String EVENT_NAME = "atlas.agent.audit";
    private static final String INTERNAL_NAMESPACE = "atlas.agent";

    private AgentAuditTelemetryProjector() {
    }

    public static AgentAuditTelemetryProjection project(AgentAuditEvent event) {
        AgentAuditEvent safeEvent = event != null ? event : emptyEvent();
        Map<String, Object> stableAttributes = stableAttributes(safeEvent);
        return new AgentAuditTelemetryProjection(
            SCHEMA_VERSION,
            EVENT_NAME,
            spanName(safeEvent),
            "INTERNAL",
            spanStatus(safeEvent),
            stableAttributes,
            experimentalOtelAttributes(safeEvent)
        );
    }

    private static Map<String, Object> stableAttributes(AgentAuditEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(INTERNAL_NAMESPACE + ".audit.id", safeText(event.auditId()));
        attributes.put(INTERNAL_NAMESPACE + ".trace.id", safeText(event.traceId()));
        attributes.put(INTERNAL_NAMESPACE + ".event.time", event.occurredAt() != null ? event.occurredAt().toString() : "");
        attributes.put(INTERNAL_NAMESPACE + ".intent.id", safeText(event.intentId()));
        attributes.put(INTERNAL_NAMESPACE + ".tool.name", safeText(event.toolName()));
        attributes.put(INTERNAL_NAMESPACE + ".execution.source", event.source() != null ? event.source().name() : "");
        attributes.put(INTERNAL_NAMESPACE + ".http.method", safeText(event.httpMethod()));
        attributes.put(INTERNAL_NAMESPACE + ".operation.type", event.operationType() != null ? event.operationType().name() : "UNKNOWN");
        attributes.put(INTERNAL_NAMESPACE + ".requires_confirmation", event.requiresConfirmation());
        attributes.put(INTERNAL_NAMESPACE + ".audit.outcome", event.outcome() != null ? event.outcome().name() : AgentAuditOutcome.BLOCKED.name());
        attributes.put(INTERNAL_NAMESPACE + ".tool.executed", event.executed());
        attributes.put(INTERNAL_NAMESPACE + ".tool.success", event.success());
        attributes.put(INTERNAL_NAMESPACE + ".reason.present", event.reason() != null && !event.reason().isBlank());
        attributes.put(INTERNAL_NAMESPACE + ".reason.length", event.reason() != null ? event.reason().length() : 0);
        attributes.put(INTERNAL_NAMESPACE + ".parameters.count", parameterCount(event.parameterSummary()));
        attributes.put(INTERNAL_NAMESPACE + ".parameters.truncated", Boolean.TRUE.equals(value(event.parameterSummary(), "truncated")));
        attributes.put(INTERNAL_NAMESPACE + ".api_endpoint.count", event.apiEndpoints() != null ? event.apiEndpoints().size() : 0);
        attributes.put(INTERNAL_NAMESPACE + ".privacy.raw_principal_included", false);
        attributes.put(INTERNAL_NAMESPACE + ".privacy.raw_reason_included", false);
        attributes.put(INTERNAL_NAMESPACE + ".privacy.raw_parameter_values_included", false);
        return attributes;
    }

    private static Map<String, Object> experimentalOtelAttributes(AgentAuditEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("gen_ai.operation.name", "tool_call");
        attributes.put("gen_ai.tool.name", safeText(event.toolName()));
        attributes.put("gen_ai.tool.call.id", safeText(event.auditId()));
        attributes.put("otel.status_code", spanStatus(event));
        attributes.put("http.request.method", safeText(event.httpMethod()));
        attributes.put("error.type", errorType(event));
        return attributes;
    }

    private static String spanName(AgentAuditEvent event) {
        String toolName = safeText(event.toolName());
        if (!toolName.isBlank()) {
            return "agent.tool " + toolName;
        }
        String intentId = safeText(event.intentId());
        return intentId.isBlank() ? "agent.tool unknown" : "agent.tool " + intentId;
    }

    private static String spanStatus(AgentAuditEvent event) {
        if (event.outcome() == AgentAuditOutcome.ERROR) {
            return "ERROR";
        }
        return "OK";
    }

    private static String errorType(AgentAuditEvent event) {
        if (event.outcome() == AgentAuditOutcome.ERROR) {
            return "tool_execution_error";
        }
        if (event.outcome() == AgentAuditOutcome.BLOCKED) {
            return "agent_execution_blocked";
        }
        if (event.outcome() == AgentAuditOutcome.BUSINESS_FAILURE) {
            return "tool_business_failure";
        }
        if (event.outcome() == AgentAuditOutcome.PREPARED) {
            return "";
        }
        return "";
    }

    private static int parameterCount(Map<String, Object> parameterSummary) {
        Object count = value(parameterSummary, "count");
        if (count instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    private static Object value(Map<String, Object> map, String key) {
        return map != null ? map.get(key) : null;
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }

    private static AgentAuditEvent emptyEvent() {
        return new AgentAuditEvent(
            "",
            null,
            "",
            "",
            "",
            "",
            "",
            "",
            null,
            "",
            java.util.List.of(),
            AtlasToolMapping.OperationType.UNKNOWN,
            false,
            AgentAuditOutcome.BLOCKED,
            false,
            false,
            "",
            Map.of("count", 0, "keys", java.util.List.of())
        );
    }
}
