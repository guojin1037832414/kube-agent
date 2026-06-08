package com.atlas.observability;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replay timeline mapping contract tests.
 */
class AgentReplayTimelineServiceTest {

    @Test
    void traceTimeline_shouldMapAuditOutcomesToReplayPhasesKindsAndStatuses() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentReplayTimelineService service = new AgentReplayTimelineService(recorder);

        recorder.record(event("aud_prepared", AgentAuditOutcome.PREPARED));
        recorder.record(event("aud_success", AgentAuditOutcome.SUCCESS));
        recorder.record(event("aud_business_failure", AgentAuditOutcome.BUSINESS_FAILURE));
        recorder.record(event("aud_blocked", AgentAuditOutcome.BLOCKED));
        recorder.record(event("aud_error", AgentAuditOutcome.ERROR));

        AgentReplayTimelineResponse response = service.traceTimeline("trc_replay_mapping", 10);

        assertThat(response.schemaVersion()).isEqualTo("agent-replay-timeline.v1");
        assertThat(response.order()).isEqualTo("oldest-first");
        assertThat(response.steps()).extracting(AgentReplayTimelineStep::auditId)
            .containsExactly("aud_prepared", "aud_success", "aud_business_failure", "aud_blocked", "aud_error");
        assertThat(response.steps()).extracting(AgentReplayTimelineStep::phase)
            .containsExactly("PRE_EXECUTION", "FINAL", "FINAL", "FINAL", "FINAL");
        assertThat(response.steps()).extracting(AgentReplayTimelineStep::recordPhase)
            .containsExactly("PRE_EXECUTION", "FINAL", "FINAL", "FINAL", "FINAL");
        assertThat(response.steps()).extracting(AgentReplayTimelineStep::kind)
            .containsExactly("TOOL_PREPARED", "TOOL_RESULT", "TOOL_BUSINESS_FAILURE", "TOOL_BLOCKED", "TOOL_ERROR");
        assertThat(response.steps()).extracting(AgentReplayTimelineStep::status)
            .containsExactly("prepared", "success", "business_failure", "blocked", "error");
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawEndpoints", false);
    }

    private AgentAuditEvent event(String auditId, AgentAuditOutcome outcome) {
        boolean success = outcome == AgentAuditOutcome.SUCCESS;
        boolean executed = outcome == AgentAuditOutcome.SUCCESS
            || outcome == AgentAuditOutcome.BUSINESS_FAILURE
            || outcome == AgentAuditOutcome.ERROR;
        return new AgentAuditEvent(
            auditId,
            Instant.parse("2026-06-09T00:00:00Z"),
            "trc_replay_mapping",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.GRAPH_TOOL_CALL,
            "GET",
            List.of("/api/org-sensitive/pod?token=secret-token-value"),
            AtlasToolMapping.OperationType.READ,
            false,
            outcome,
            executed,
            success,
            "reason token=secret-token-value",
            Map.of("count", 0, "keys", List.of())
        );
    }
}
