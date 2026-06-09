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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench promotion workflow result contract tests.
 */
class AgentEvalWorkbenchPromotionWorkflowServiceTest {

    @Test
    void workflow_shouldBuildUiResultWithoutCatalogWriteAuthority() {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_88888888888888888888888888888888";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_workflow",
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
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService =
            new AgentEvalTraceSetCandidateDiscoveryService(auditRecorder, traceSetCatalogService);
        AgentEvalTraceSetPromotionWorkflowService rawWorkflowService =
            new AgentEvalTraceSetPromotionWorkflowService(candidateDiscoveryService, traceSetCatalogService);
        AgentEvalWorkbenchPromotionWorkflowService service =
            new AgentEvalWorkbenchPromotionWorkflowService(traceSetCatalogService, rawWorkflowService);

        Optional<AgentEvalWorkbenchPromotionWorkflowResponse> response = service.workflow(
            "phase1-core-golden",
            new AgentEvalTraceSetPromotionWorkflowRequest(50, null, null, null, 5)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchPromotionWorkflowResponse workflow = response.get();
        assertThat(workflow.schemaVersion()).isEqualTo("agent-eval-workbench-promotion-workflow.v1");
        assertThat(workflow.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(workflow.workflowVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(workflow.readyForGitReview()).isTrue();
        assertThat(workflow.selectedCandidateTraceIds()).containsExactly(traceId);
        assertThat(workflow.traceSetView().status()).isEqualTo("NEEDS_REDACTED_EVIDENCE");
        assertThat(workflow.workflow().schemaVersion()).isEqualTo("agent-eval-trace-set-promotion-workflow.v1");
        assertThat(workflow.uiSteps()).hasSize(4);
        assertThat(workflow.uiSteps()).extracting(step -> step.get("id"))
            .containsExactly(
                "candidate-discovery",
                "curation-review",
                "catalog-patch-proposal",
                "gate-bundle-regeneration"
            );
        assertThat(workflow.patchSummary())
            .containsEntry("readyForGitReview", true)
            .containsEntry("addedTraceCount", 1)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresGitReview", true);
        assertThat(workflow.candidateGateSummary())
            .containsEntry("reviewVerdict", "READY_FOR_CATALOG_REVIEW")
            .containsEntry("gateVerdict", "PASS")
            .containsEntry("pass", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(workflow.nextActions())
            .containsExactly(
                "open-catalog-patch-proposal",
                "create-human-git-review",
                "regenerate-gate-bundle-after-merge"
            );
        assertThat(workflow.endpointTemplates())
            .containsEntry("workbenchPromotionWorkflow",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/promotion-workflow")
            .containsEntry("workbenchGateBundleSummary",
                "/api/agent/observability/eval/workbench/gate-bundle-summary")
            .containsEntry("rawPromotionWorkflow",
                "/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow");
        assertThat(workflow.workbenchPolicy())
            .containsEntry("workbenchWrapperOnly", true)
            .containsEntry("candidateDiscoveryExecuted", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.toString())
            .contains(traceId, "agent-eval-workbench-promotion-workflow.v1")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void workflow_shouldRejectUnknownTraceSet() {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService =
            new AgentEvalTraceSetCandidateDiscoveryService(auditRecorder, traceSetCatalogService);
        AgentEvalTraceSetPromotionWorkflowService rawWorkflowService =
            new AgentEvalTraceSetPromotionWorkflowService(candidateDiscoveryService, traceSetCatalogService);
        AgentEvalWorkbenchPromotionWorkflowService service =
            new AgentEvalWorkbenchPromotionWorkflowService(traceSetCatalogService, rawWorkflowService);

        assertThat(service.workflow("missing-trace-set", null)).isEmpty();
    }
}
