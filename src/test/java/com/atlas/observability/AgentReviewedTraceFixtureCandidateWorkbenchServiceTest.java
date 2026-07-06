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
 * reviewed fixture candidate 工作台预检测试。
 *
 * <p>中文说明：这些测试保护 M5.85-44 的组合读模型契约。工作台可以自动从 redacted audit 中选择第一个
 * 推荐候选并调用 candidate preview，但不能接受 caller traceId、不能创建 fixture、不能写 catalog，
 * 也不能把 raw audit/replay/eval report 暴露给前端。</p>
 */
class AgentReviewedTraceFixtureCandidateWorkbenchServiceTest {

    @Test
    void workbench_shouldAutoSelectRecommendedCandidateAndBuildPreview() throws Exception {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_e1111111111111111111111111111111";
        recorder.record(event(
            "aud_fixture_candidate_workbench",
            traceId,
            AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.READ,
            false,
            true,
            true
        ));
        AgentReviewedTraceFixtureCandidateWorkbenchService service = service(recorder);

        AgentReviewedTraceFixtureCandidateWorkbenchResponse response = service
            .workbench("phase1-core-golden", 50)
            .orElseThrow();

        assertThat(response.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-candidate-workbench.v1");
        assertThat(response.workbenchStatus()).isEqualTo("READY_FOR_HUMAN_FIXTURE_REVIEW");
        assertThat(response.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(response.selectedCandidateTraceId()).isEqualTo(traceId);
        assertThat(response.candidateSelected()).isTrue();
        assertThat(response.readyForHumanGitReview()).isTrue();
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.recommendedCandidateCount()).isEqualTo(1);
        assertThat(response.candidateDiscoverySummary())
            .containsEntry("selectedCandidateTraceId", traceId)
            .containsEntry("autoSelectedFirstRecommendedCandidate", true)
            .containsEntry("rawAuditEmbedded", false);
        assertThat(response.candidateDiscovery().candidateTraceIds()).containsExactly(traceId);
        assertThat(response.candidatePreview().candidateStatus()).isEqualTo("READY_FOR_HUMAN_FIXTURE_REVIEW");
        assertThat(response.candidatePreview().candidateFixtureDraft())
            .containsEntry("traceId", traceId)
            .containsEntry("requiresHumanGitReviewBeforeCommit", true)
            .containsEntry("readyForManifestQualityGateNow", false);
        assertThat(response.workbenchPolicy())
            .containsEntry("requestTraceIdsAccepted", false)
            .containsEntry("autoSelectsFirstRecommendedTraceId", true)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("fixtureUploadAccepted", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(response.safety())
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("ciBlockingEnabled", false);
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(response.nextActions().get(0)).isEqualTo("open-human-git-review-for-selected-candidate");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .contains("candidatePreview", "candidateDiscoverySummary", "READY_FOR_HUMAN_FIXTURE_REVIEW")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"steps\"", "\"fixtureRows\"");
    }

    @Test
    void workbench_shouldFailClosedWhenNoRecommendedCandidateExists() throws Exception {
        AgentReviewedTraceFixtureCandidateWorkbenchService service = service(new InMemoryAgentAuditRecorder());

        AgentReviewedTraceFixtureCandidateWorkbenchResponse response = service
            .workbench("phase1-core-golden", 10_000)
            .orElseThrow();

        assertThat(response.workbenchStatus()).isEqualTo("NO_RECOMMENDED_CANDIDATE_FROM_REDACTED_AUDIT");
        assertThat(response.maxEvents()).isEqualTo(AgentEvalTraceSetCandidateDiscoveryService.MAX_EVENTS);
        assertThat(response.selectedCandidateTraceId()).isBlank();
        assertThat(response.candidateSelected()).isFalse();
        assertThat(response.readyForHumanGitReview()).isFalse();
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.recommendedCandidateCount()).isZero();
        assertThat(response.blockingReasons())
            .contains(
                "no-recommended-redacted-trace-candidate",
                "candidate-trace-id-missing-or-invalid",
                "redacted-replay-timeline-missing",
                "deterministic-eval-not-passing"
            );
        assertThat(response.candidatePreview().candidateStatus()).isEqualTo("TRACE_ID_REQUIRED_FOR_FIXTURE_CANDIDATE");
        assertThat(response.nextActions())
            .contains("capture-real-redacted-audit-evidence", "rerun-candidate-discovery");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .doesNotContain("secret-token-value", "\"reports\"", "\"steps\"", "\"fixtureRows\"");
    }

    @Test
    void workbench_shouldRejectUnknownTraceSet() {
        AgentReviewedTraceFixtureCandidateWorkbenchService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.workbench("missing-trace-set", 50)).isEmpty();
    }

    private static AgentReviewedTraceFixtureCandidateWorkbenchService service(InMemoryAgentAuditRecorder recorder) {
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(recorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalTraceSetCandidateDiscoveryService discoveryService =
            new AgentEvalTraceSetCandidateDiscoveryService(recorder, traceSetCatalogService);
        AgentReviewedTraceFixtureCandidateService candidateService =
            new AgentReviewedTraceFixtureCandidateService(traceSetCatalogService, evalReportService);
        return new AgentReviewedTraceFixtureCandidateWorkbenchService(discoveryService, candidateService);
    }

    private static AgentAuditEvent event(String auditId,
                                         String traceId,
                                         AgentAuditOutcome outcome,
                                         AtlasToolMapping.OperationType operationType,
                                         boolean requiresConfirmation,
                                         boolean executed,
                                         boolean success) {
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
            "ok token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "namespace",
                "protected", false,
                "type", "string",
                "present", true
            )))
        );
    }
}
