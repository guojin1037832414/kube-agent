package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.26 审计遥测投影契约测试。
 */
class AgentAuditTelemetryProjectorTest {

    @Test
    void project_shouldExposeStableAndExperimentalAttributesWithoutRawSensitiveEvidence() {
        AgentAuditEvent event = new AgentAuditEvent(
            "aud_0123456789abcdef0123456789abcdef",
            Instant.parse("2026-06-09T00:00:00Z"),
            "trc_0123456789abcdef0123456789abcdef",
            "conv-secret",
            "user-secret",
            "org-secret",
            "pod_query",
            "pod_query_tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-secret/pod?token=secret-token"),
            AtlasToolMapping.OperationType.SENSITIVE_READ,
            true,
            AgentAuditOutcome.BUSINESS_FAILURE,
            true,
            false,
            "business failed because token=secret-token",
            Map.of(
                "count", 2,
                "truncated", false,
                "keys", List.of(Map.of(
                    "name", "token",
                    "protected", true,
                    "type", "string",
                    "present", true
                ))
            )
        );

        AgentAuditTelemetryProjection projection = AgentAuditTelemetryProjector.project(event);
        String projectionText = projection.toDiagnosticMap().toString();

        assertThat(projection.schemaVersion()).isEqualTo("agent-audit-telemetry.v1");
        assertThat(projection.eventName()).isEqualTo("atlas.agent.audit");
        assertThat(projection.spanName()).isEqualTo("agent.tool pod_query_tool");
        assertThat(projection.spanKind()).isEqualTo("INTERNAL");
        assertThat(projection.spanStatus()).isEqualTo("OK");
        assertThat(projection.stableAttributes())
            .containsEntry("atlas.agent.audit.id", "aud_0123456789abcdef0123456789abcdef")
            .containsEntry("atlas.agent.trace.id", "trc_0123456789abcdef0123456789abcdef")
            .containsEntry("atlas.agent.intent.id", "pod_query")
            .containsEntry("atlas.agent.tool.name", "pod_query_tool")
            .containsEntry("atlas.agent.operation.type", "SENSITIVE_READ")
            .containsEntry("atlas.agent.audit.outcome", "BUSINESS_FAILURE")
            .containsEntry("atlas.agent.tool.executed", true)
            .containsEntry("atlas.agent.tool.success", false)
            .containsEntry("atlas.agent.reason.present", true)
            .containsEntry("atlas.agent.reason.length", "business failed because token=secret-token".length())
            .containsEntry("atlas.agent.parameters.count", 2)
            .containsEntry("atlas.agent.privacy.raw_principal_included", false)
            .containsEntry("atlas.agent.privacy.raw_reason_included", false)
            .containsEntry("atlas.agent.privacy.raw_parameter_values_included", false);
        assertThat(projection.experimentalOtelAttributes())
            .containsEntry("gen_ai.operation.name", "tool_call")
            .containsEntry("gen_ai.tool.name", "pod_query_tool")
            .containsEntry("gen_ai.tool.call.id", "aud_0123456789abcdef0123456789abcdef")
            .containsEntry("error.type", "tool_business_failure");
        assertThat(projectionText)
            .doesNotContain("conv-secret", "user-secret", "org-secret", "secret-token", "/api/org-secret");
    }

    @Test
    void project_shouldMarkExecutionErrorsAsErrorStatus() {
        AgentAuditEvent event = new AgentAuditEvent(
            "aud_error",
            Instant.EPOCH,
            "trc_error",
            "conv",
            "user",
            "org",
            "intent",
            "tool",
            SafeToolExecutionSource.GRAPH_TOOL_CALL,
            "POST",
            List.of("/api/test"),
            AtlasToolMapping.OperationType.ACTION,
            true,
            AgentAuditOutcome.ERROR,
            true,
            false,
            "boom",
            Map.of("count", 0, "keys", List.of())
        );

        AgentAuditTelemetryProjection projection = AgentAuditTelemetryProjector.project(event);

        assertThat(projection.spanStatus()).isEqualTo("ERROR");
        assertThat(projection.experimentalOtelAttributes()).containsEntry("error.type", "tool_execution_error");
    }
}
