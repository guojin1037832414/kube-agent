package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench overview contract tests.
 */
class AgentEvalWorkbenchOverviewServiceTest {

    @Test
    void overview_shouldBuildFrontendReadModelWithoutExecutionAuthority() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchOverviewService service = new AgentEvalWorkbenchOverviewService(
            new AgentEvalWorkbenchCapabilitiesService(),
            traceSetCatalogService
        );

        AgentEvalWorkbenchOverviewResponse overview = service.overview();

        assertThat(overview.schemaVersion()).isEqualTo("agent-eval-workbench-overview.v1");
        assertThat(overview.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(overview.capabilityCount()).isEqualTo(10);
        assertThat(overview.traceSetCount()).isEqualTo(4);
        assertThat(overview.traceSetNeedsEvidenceCount()).isEqualTo(4);
        assertThat(overview.traceSetReadyCount()).isZero();
        assertThat(overview.gateVerdict()).isEqualTo("FAIL");
        assertThat(overview.releaseEligible()).isFalse();
        assertThat(overview.recommendedWorkflow()).startsWith(
            "workbench-overview",
            "trace-set-catalog",
            "workbench-trace-set-detail"
        );
        assertThat(overview.nextActions())
            .contains(
                "discover-redacted-candidates",
                "promote-candidates-through-git-review",
                "regenerate-gate-bundle-after-curation"
            );
        assertThat(overview.traceSets()).extracting(AgentEvalWorkbenchTraceSetView::id)
            .containsExactly(
                "phase1-core-golden",
                "phase1-redaction-regression",
                "phase1-high-risk-prewrite",
                "phase1-red-team-safety"
            );
        assertThat(overview.traceSets()).allSatisfy(traceSet -> {
            assertThat(traceSet.status()).isEqualTo("NEEDS_REDACTED_EVIDENCE");
            assertThat(traceSet.readyForCiBlocking()).isFalse();
            assertThat(traceSet.candidateDiscoveryPath()).contains("/candidates");
            assertThat(traceSet.promotionWorkflowPath()).contains("/promotion-workflow");
            assertThat(traceSet.workflowStages()).contains("human-git-review", "gate-bundle");
            assertThat(traceSet.policy())
                .containsEntry("runtimeCatalogWrite", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false)
                .containsEntry("requiresGitReview", true);
            assertThat(traceSet.privacy())
                .containsEntry("redactedOnly", true)
                .containsEntry("containsRawKubeManagerEndpoints", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
        });
        assertThat(overview.workbenchPolicy())
            .containsEntry("frontendTarget", "vue-kube-manager eval workbench")
            .containsEntry("overviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.toString())
            .contains("workbench-overview", "agent-eval-trace-set-gate-bundle.v1")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }
}
