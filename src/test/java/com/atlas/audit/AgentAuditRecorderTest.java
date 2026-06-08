package com.atlas.audit;

import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.25 Agent 审计事件模型测试。
 */
class AgentAuditRecorderTest {

    @Test
    void auditEventFactory_shouldSummarizeParametersWithoutSecretValues() {
        AgentAuditEvent event = AgentAuditEventFactory.fromExecution(
            new SafeToolExecutionRequest(
                "pod_query",
                Map.of(
                    "namespace", "default",
                    "token", "secret-token-value",
                    "auditReceipt", Map.of("receiptId", "fake")
                ),
                "user-A",
                "token-A",
                "100002",
                "conv-A",
                "trc_audit_factory",
                null,
                SafeToolExecutionSource.GRAPH_TOOL_CALL
            ),
            null,
            "trc_audit_factory",
            "100002",
            AgentAuditOutcome.BLOCKED,
            false,
            false,
            "blocked"
        );

        assertThat(event.auditId()).matches("aud_[0-9a-f]{32}");
        assertThat(event.traceId()).isEqualTo("trc_audit_factory");
        assertThat(event.parameterSummary().toString()).contains("namespace", "token", "auditReceipt");
        assertThat(event.parameterSummary().toString()).contains("protected=true");
        assertThat(event.parameterSummary().toString()).doesNotContain("secret-token-value").doesNotContain("fake");
    }

    @Test
    void inMemoryRecorder_shouldKeepCountersAndRecentEvents() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();

        recorder.record(event("aud_success", AgentAuditOutcome.SUCCESS));
        recorder.record(event("aud_blocked", AgentAuditOutcome.BLOCKED));
        recorder.record(event("aud_error", AgentAuditOutcome.ERROR));

        assertThat(recorder.recentEvents()).extracting(AgentAuditEvent::auditId)
            .containsExactly("aud_error", "aud_blocked", "aud_success");
        assertThat(recorder.snapshot()).containsEntry("totalEvents", 3L)
            .containsEntry("blockedEvents", 1L)
            .containsEntry("errorEvents", 1L);
        assertThat(recorder.snapshot()).containsKeys("schemaVersion", "generatedAt", "replayCapabilities", "durability");
    }

    @Test
    void snapshot_shouldExposeOnlyDiagnosticSummaries() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recorder.record(new AgentAuditEvent(
            "aud_sensitive",
            java.time.Instant.EPOCH,
            "trc_sensitive",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "POST",
            java.util.List.of("/api/sensitive"),
            null,
            true,
            AgentAuditOutcome.BLOCKED,
            false,
            false,
            "blocked because token=secret-token-value",
            Map.of("count", 2, "keys", java.util.List.of(Map.of(
                "name", "namespace",
                "protected", false,
                "type", "string",
                "present", true
            ), Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));

        String snapshotText = recorder.snapshot().toString();

        assertThat(recorder.recentEvents()).extracting(AgentAuditEvent::conversationId)
            .containsExactly("conv-sensitive");
        assertThat(snapshotText)
            .contains("aud_sensitive", "trc_sensitive", "reasonSummary", "parameterSummary", "telemetry", "namespace", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "token", "secret-token-value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_shouldDescribeReplayCapabilitiesWithoutRawEvidenceLeakage() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();

        Map<String, Object> snapshot = recorder.snapshot();
        Map<String, Object> capabilities = (Map<String, Object>) snapshot.get("replayCapabilities");

        assertThat(snapshot.get("schemaVersion")).isEqualTo("agent-audit-snapshot.v1");
        assertThat(snapshot.get("generatedAt")).isNotNull();
        assertThat(capabilities)
            .containsEntry("supportsTraceLookup", true)
            .containsEntry("supportsRecentEventTimeline", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawReason", false)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("durableRetention", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_shouldExposeDurableRetentionStatusWhenSinkIsEnabled() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder(
            null,
            new StaticDurableSink(new AgentAuditDurabilityStatus(
                true,
                true,
                true,
                true,
                "jsonl",
                "target/agent-audit/agent-audit.jsonl",
                1,
                0,
                ""
            ))
        );

        Map<String, Object> snapshot = recorder.snapshot();
        Map<String, Object> capabilities = (Map<String, Object>) snapshot.get("replayCapabilities");
        Map<String, Object> durability = (Map<String, Object>) snapshot.get("durability");

        assertThat(capabilities).containsEntry("durableRetention", true);
        assertThat(durability)
            .containsEntry("enabled", true)
            .containsEntry("ready", true)
            .containsEntry("durableRetention", true)
            .containsEntry("failClosedForHighRisk", true)
            .containsEntry("storageType", "jsonl");
    }

    private AgentAuditEvent event(String auditId, AgentAuditOutcome outcome) {
        return new AgentAuditEvent(
            auditId,
            java.time.Instant.EPOCH,
            "trc_test",
            "conv",
            "user",
            "org",
            "intent",
            "tool",
            SafeToolExecutionSource.GRAPH_TOOL_CALL,
            "GET",
            java.util.List.of("/api/test"),
            null,
            false,
            outcome,
            outcome == AgentAuditOutcome.SUCCESS,
            outcome == AgentAuditOutcome.SUCCESS,
            outcome.name(),
            Map.of("count", 0, "keys", java.util.List.of())
        );
    }

    private static final class StaticDurableSink implements AgentAuditDurableSink {
        private final AgentAuditDurabilityStatus status;

        private StaticDurableSink(AgentAuditDurabilityStatus status) {
            this.status = status;
        }

        @Override
        public void append(AgentAuditEvent event) {
        }

        @Override
        public AgentAuditDurabilityStatus status() {
            return status;
        }
    }
}
