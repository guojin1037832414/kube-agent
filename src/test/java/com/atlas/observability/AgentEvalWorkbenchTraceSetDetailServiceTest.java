package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench trace-set detail contract tests.
 */
class AgentEvalWorkbenchTraceSetDetailServiceTest {

    @Test
    void detail_shouldBuildTraceSetReadModelWithoutPromotionAuthority() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchTraceSetDetailService service =
            new AgentEvalWorkbenchTraceSetDetailService(traceSetCatalogService);

        Optional<AgentEvalWorkbenchTraceSetDetailResponse> response = service.detail("phase1-core-golden");

        assertThat(response).isPresent();
        AgentEvalWorkbenchTraceSetDetailResponse detail = response.get();
        assertThat(detail.schemaVersion()).isEqualTo("agent-eval-workbench-trace-set-detail.v1");
        assertThat(detail.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(detail.suiteId()).isEqualTo("release-gate-strict");
        assertThat(detail.status()).isEqualTo("NEEDS_REDACTED_EVIDENCE");
        assertThat(detail.curatedTraceCount()).isZero();
        assertThat(detail.readyForCiBlocking()).isFalse();
        assertThat(detail.traceSetView().promotionWorkflowPath())
            .isEqualTo("/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow");
        assertThat(detail.gate().schemaVersion()).isEqualTo("agent-eval-trace-set-gate.v1");
        assertThat(detail.gate().emptyInput()).isTrue();
        assertThat(detail.promotionChecklist())
            .contains(
                "discover-redacted-candidate-traces",
                "review-catalog-patch-proposal",
                "merge-catalog-change-through-git-review"
            );
        assertThat(detail.nextActions())
            .containsExactly(
                "open-candidate-discovery",
                "run-promotion-workflow",
                "review-catalog-patch-proposal"
            );
        assertThat(detail.endpointTemplates())
            .containsEntry("overview", "/api/agent/observability/eval/workbench/overview")
            .containsEntry("promotionWorkflow",
                "/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow")
            .containsEntry("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        assertThat(detail.detailPolicy())
            .containsEntry("detailOnly", true)
            .containsEntry("candidateDiscoveryExecuted", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(detail.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(detail.toString())
            .contains("phase1-core-golden", "agent-eval-suite-gate.v1")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void detail_shouldRejectUnknownTraceSet() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchTraceSetDetailService service =
            new AgentEvalWorkbenchTraceSetDetailService(traceSetCatalogService);

        assertThat(service.detail("missing-trace-set")).isEmpty();
    }
}
