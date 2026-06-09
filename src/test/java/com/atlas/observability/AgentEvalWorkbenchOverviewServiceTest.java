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
        assertThat(overview.capabilityCount()).isEqualTo(16);
        assertThat(overview.traceSetCount()).isEqualTo(7);
        assertThat(overview.traceSetNeedsEvidenceCount()).isEqualTo(7);
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
                "inspect-reviewed-trace-evidence-readiness",
                "discover-redacted-candidates",
                "promote-candidates-through-git-review",
                "regenerate-gate-bundle-after-curation"
            );
        assertThat(overview.traceSets()).extracting(AgentEvalWorkbenchTraceSetView::id)
            .containsExactly(
                "phase1-core-golden",
                "phase1-redaction-regression",
                "phase1-high-risk-prewrite",
                "phase1-red-team-safety",
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(overview.traceSets()).allSatisfy(traceSet -> {
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
        assertThat(overview.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("phase1-"))
            .allSatisfy(traceSet -> assertThat(traceSet.status()).isEqualTo("NEEDS_REDACTED_EVIDENCE"));
        assertThat(overview.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("memory-rag-"))
            .hasSize(3)
            .allSatisfy(traceSet -> {
                assertThat(traceSet.suiteId()).isEqualTo("memory-rag-release-gate");
                assertThat(traceSet.gateVerdict()).isEqualTo("SUITE_RUNTIME_DISABLED");
                assertThat(traceSet.status()).isEqualTo("SUITE_RUNTIME_DISABLED_CATALOG_ONLY");
                assertThat(traceSet.nextAction()).isEqualTo("keep-catalog-only-until-reviewed-runtime-promotion");
                assertThat(traceSet.policy())
                    .containsEntry("runtimeCatalogWrite", false)
                    .containsEntry("suiteRuntimeDisabled", true)
                    .containsEntry("runtimeExecutionAllowed", false)
                    .containsEntry("retrievalRuntimeAllowed", false)
                    .containsEntry("traceSetGateRuntimeDisabled", true)
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
        assertThat(overview.capabilities().capabilities())
            .anySatisfy(capability -> assertThat(capability.id())
                .isEqualTo("memory-rag-eval-suite-binding-contract"));
        assertThat(overview.toString())
            .contains("workbench-overview", "agent-eval-trace-set-gate-bundle.v1")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }
}
