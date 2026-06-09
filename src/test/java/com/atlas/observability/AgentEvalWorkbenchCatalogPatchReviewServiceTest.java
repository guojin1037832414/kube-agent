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
 * Eval workbench catalog patch review contract tests.
 */
class AgentEvalWorkbenchCatalogPatchReviewServiceTest {

    @Test
    void review_shouldBuildGitReviewModelWithoutApplyingCatalogPatch() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_patch_review",
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
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService);

        Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> response = service.review(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.get();
        assertThat(review.schemaVersion()).isEqualTo("agent-eval-workbench-catalog-patch-review.v1");
        assertThat(review.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(review.proposalVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(review.readyForGitReview()).isTrue();
        assertThat(review.candidateTraceIds()).containsExactly(traceId);
        assertThat(review.addedTraceIds()).containsExactly(traceId);
        assertThat(review.proposedTraceIds()).containsExactly(traceId);
        assertThat(review.patchOperations()).hasSize(1);
        assertThat(review.patchOperations().get(0))
            .containsEntry("op", "replace")
            .containsEntry("path", "/0/traceIds")
            .containsEntry("valueKind", "trace-id-list")
            .containsEntry("valueCount", 1)
            .containsEntry("applied", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.traceDelta())
            .containsEntry("originalTraceSetTraceCount", 0)
            .containsEntry("candidateTraceCount", 1)
            .containsEntry("addedTraceCount", 1)
            .containsEntry("proposedTraceSetTraceCount", 1)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.candidateGateSummary())
            .containsEntry("reviewVerdict", "READY_FOR_CATALOG_REVIEW")
            .containsEntry("gateVerdict", "PASS")
            .containsEntry("pass", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(review.reviewChecklist())
            .contains(
                "confirm-redacted-trace-anchors-only",
                "confirm-no-runtime-catalog-write",
                "submit-human-git-review",
                "regenerate-gate-bundle-after-merge"
            );
        assertThat(review.nextActions())
            .containsExactly(
                "copy-json-patch-into-git-review",
                "merge-reviewed-catalog-change",
                "regenerate-trace-set-gate-bundle"
            );
        assertThat(review.endpointTemplates())
            .containsEntry("workbenchCatalogPatchReview",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
            .containsEntry("rawCatalogPatchProposal",
                "/api/agent/observability/eval/trace-sets/phase1-core-golden/catalog-patch-proposal");
        assertThat(review.workbenchPolicy())
            .containsEntry("catalogPatchReviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("patchApplied", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.toString())
            .contains(traceId, "agent-eval-workbench-catalog-patch-review.v1")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(review))
            .contains(traceId, "agent-eval-workbench-catalog-patch-review.v1")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"replay\"");
    }

    @Test
    void review_shouldRejectUnknownTraceSet() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService);

        assertThat(service.review("missing-trace-set", null)).isEmpty();
    }
}
