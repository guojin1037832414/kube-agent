package com.atlas.observability;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trace-set candidate discovery contract tests.
 */
class AgentEvalTraceSetCandidateDiscoveryServiceTest {

    @Test
    void discover_shouldRecommendTraceCandidatesPerTraceSetWithoutRawEvidence() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recordReadSuccess(recorder, "trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        recordProtectedRead(recorder, "trc_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        recordHighRiskPrewrite(recorder, "trc_cccccccccccccccccccccccccccccccc");
        recordBlockedSafety(recorder, "trc_dddddddddddddddddddddddddddddddd");
        recorder.record(event(
            "aud_invalid",
            "trc_not_w3c",
            AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.READ,
            false,
            true,
            true,
            false
        ));
        AgentEvalTraceSetCandidateDiscoveryService service = service(recorder);

        AgentEvalTraceSetCandidateDiscoveryResponse golden = service.discover("phase1-core-golden", 50).orElseThrow();
        AgentEvalTraceSetCandidateDiscoveryResponse redaction = service.discover("phase1-redaction-regression", 50).orElseThrow();
        AgentEvalTraceSetCandidateDiscoveryResponse highRisk = service.discover("phase1-high-risk-prewrite", 50).orElseThrow();
        AgentEvalTraceSetCandidateDiscoveryResponse redTeam = service.discover("phase1-red-team-safety", 50).orElseThrow();

        assertThat(golden.schemaVersion()).isEqualTo("agent-eval-trace-set-candidates.v1");
        assertThat(golden.auditQueryBackend()).isEqualTo("in-memory-ring-buffer");
        assertThat(golden.candidateTraceIds()).contains("trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(redaction.candidateTraceIds()).contains("trc_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(highRisk.candidateTraceIds()).contains("trc_cccccccccccccccccccccccccccccccc");
        assertThat(redTeam.candidateTraceIds()).contains("trc_dddddddddddddddddddddddddddddddd");
        assertThat(golden.candidates()).allSatisfy(candidate -> assertThat(candidate.traceId()).startsWith("trc_"));
        assertThat(golden.candidates()).extracting(AgentEvalTraceSetCandidate::traceId)
            .doesNotContain("trc_not_w3c");
        assertThat(highRisk.candidates()).filteredOn(AgentEvalTraceSetCandidate::recommendedForCurationReview)
            .allSatisfy(candidate -> {
                assertThat(candidate.highRiskEvents()).isPositive();
                assertThat(candidate.preExecutionEvents()).isPositive();
                assertThat(candidate.finalEvents()).isPositive();
                assertThat(candidate.evidenceTags()).contains("high-risk", "pre-execution", "final");
            });
        assertThat(redaction.candidates()).filteredOn(AgentEvalTraceSetCandidate::recommendedForCurationReview)
            .allSatisfy(candidate -> assertThat(candidate.protectedParameterEvidence()).isTrue());
        assertThat(golden.discoveryPolicy())
            .containsEntry("sourceRedactedOnly", true)
            .containsEntry("requiresCurationReview", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(golden.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(golden.toString() + redaction + highRisk + redTeam)
            .contains("trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "trc_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void discover_shouldBoundRecentAuditScanAndRejectUnknownTraceSet() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recordReadSuccess(recorder, "trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        AgentEvalTraceSetCandidateDiscoveryService service = service(recorder);

        AgentEvalTraceSetCandidateDiscoveryResponse response = service.discover("phase1-core-golden", 10_000).orElseThrow();

        assertThat(response.maxEvents()).isEqualTo(AgentEvalTraceSetCandidateDiscoveryService.MAX_EVENTS);
        assertThat(service.discover("missing-trace-set", 50)).isEmpty();
    }

    private AgentEvalTraceSetCandidateDiscoveryService service(InMemoryAgentAuditRecorder recorder) {
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        return new AgentEvalTraceSetCandidateDiscoveryService(recorder, traceSetCatalogService);
    }

    private void recordReadSuccess(InMemoryAgentAuditRecorder recorder, String traceId) {
        recorder.record(event("aud_read_" + traceId.substring(4, 8), traceId, AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.READ, false, true, true, false));
    }

    private void recordProtectedRead(InMemoryAgentAuditRecorder recorder, String traceId) {
        recorder.record(event("aud_redaction_" + traceId.substring(4, 8), traceId, AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.SENSITIVE_READ, true, true, true, false));
    }

    private void recordHighRiskPrewrite(InMemoryAgentAuditRecorder recorder, String traceId) {
        String auditId = "aud_high_" + traceId.substring(4, 8);
        recorder.record(event(auditId, traceId, AgentAuditOutcome.PREPARED,
            AtlasToolMapping.OperationType.CREATE, true, false, false, true));
        recorder.record(event(auditId, traceId, AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.CREATE, true, true, true, true));
    }

    private void recordBlockedSafety(InMemoryAgentAuditRecorder recorder, String traceId) {
        recorder.record(event("aud_blocked_" + traceId.substring(4, 8), traceId, AgentAuditOutcome.BLOCKED,
            AtlasToolMapping.OperationType.READ, false, false, false, false));
    }

    private AgentAuditEvent event(String auditId,
                                  String traceId,
                                  AgentAuditOutcome outcome,
                                  AtlasToolMapping.OperationType operationType,
                                  boolean protectedParameter,
                                  boolean executed,
                                  boolean success,
                                  boolean requiresConfirmation) {
        return new AgentAuditEvent(
            auditId,
            Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-sensitive/pod?token=secret-token-value"),
            operationType,
            requiresConfirmation,
            outcome,
            executed,
            success,
            "reason token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", protectedParameter ? "token" : "namespace",
                "protected", protectedParameter,
                "type", "string",
                "present", true
            )))
        );
    }
}
