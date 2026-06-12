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
 * Trace-set promotion workflow contract tests.
 *
 * <p>中文说明：本测试保护“候选发现 -> curation review -> patch proposal”这条只读工作流的学习契约，
 * 让后续重构不会把 promotion workflow 误改成目录写入接口。</p>
 *
 * <p>安全边界：这些测试只验证 read model 与 review-only artifact，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，不修改 trace set catalog，也不授予 release authority。</p>
 */
class AgentEvalTraceSetPromotionWorkflowServiceTest {

    @Test
    void workflow_shouldComposeDiscoveryReviewAndPatchProposalWithoutMutatingCatalog() {
        // 中文说明：这条用例验证 workflow 只合成证据，不会偷偷写目录。
        // 安全边界：readyForGitReview 只是 Git review 信号，不是 runtime catalog write。
        String traceId = "trc_66666666666666666666666666666666";
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recordReadEvidence(recorder, traceId);
        AgentEvalTraceSetCatalogService catalogService = catalogService(recorder);
        AgentEvalTraceSetPromotionWorkflowService service = workflowService(recorder, catalogService);

        AgentEvalTraceSetPromotionWorkflowArtifact workflow = service.workflow(
            "phase1-core-golden",
            new AgentEvalTraceSetPromotionWorkflowRequest(50, null, null, null, 5)
        ).orElseThrow();

        assertThat(workflow.schemaVersion()).isEqualTo("agent-eval-trace-set-promotion-workflow.v1");
        assertThat(workflow.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(workflow.workflowVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(workflow.readyForGitReview()).isTrue();
        assertThat(workflow.catalogMutated()).isFalse();
        assertThat(workflow.selectedCandidateTraceIds()).containsExactly(traceId);
        assertThat(workflow.candidateDiscovery().candidateTraceIds()).contains(traceId);
        assertThat(workflow.catalogPatchProposal().readyForGitReview()).isTrue();
        assertThat(workflow.catalogPatchProposal().jsonPatch()).hasSize(1);
        assertThat(workflow.workflowPolicy())
            .containsEntry("workflowOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("requiresCiGateBundleRegeneration", true)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(catalogService.findDefinition("phase1-core-golden").orElseThrow().traceIds()).isEmpty();
        assertThat(workflow.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.toString())
            .contains(traceId, "catalog-patch-proposal", "gate-bundle")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void workflow_shouldReturnNoRecommendedCandidatesWithoutRuntimeMutation() {
        // 中文说明：候选为空时，工作流应当返回可解释的拒绝结果，而不是伪造补丁。
        // 安全边界：空候选只能导致 fail-closed，不能退化成自动晋升。
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalTraceSetCatalogService catalogService = catalogService(recorder);
        AgentEvalTraceSetPromotionWorkflowService service = workflowService(recorder, catalogService);

        AgentEvalTraceSetPromotionWorkflowArtifact workflow = service.workflow(
            "phase1-core-golden",
            new AgentEvalTraceSetPromotionWorkflowRequest(50, null, null, null, 1000)
        ).orElseThrow();

        assertThat(workflow.workflowVerdict()).isEqualTo("NO_RECOMMENDED_CANDIDATES");
        assertThat(workflow.readyForGitReview()).isFalse();
        assertThat(workflow.selectedCandidateTraceIds()).isEmpty();
        assertThat(workflow.catalogPatchProposal().readyForGitReview()).isFalse();
        assertThat(workflow.catalogPatchProposal().catalogMutated()).isFalse();
        assertThat(workflow.workflowPolicy())
            .containsEntry("maxSelectedCandidates", AgentEvalTraceSetPromotionWorkflowService.MAX_RECOMMENDED_CANDIDATES)
            .containsEntry("requiresCiGateBundleRegeneration", false)
            .containsEntry("catalogMutationAllowed", false);
    }

    @Test
    void workflow_shouldRejectUnknownTraceSet() {
        // 中文说明：未知 traceSetId 只能被拒绝，不能靠默认值拼出伪造工作流。
        // 安全边界：不存在的目录项不会变成可写 catalog 记录。
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalTraceSetCatalogService catalogService = catalogService(recorder);
        AgentEvalTraceSetPromotionWorkflowService service = workflowService(recorder, catalogService);

        assertThat(service.workflow("missing-trace-set", null)).isEmpty();
    }

    private AgentEvalTraceSetPromotionWorkflowService workflowService(InMemoryAgentAuditRecorder recorder,
                                                                      AgentEvalTraceSetCatalogService catalogService) {
        AgentEvalTraceSetCandidateDiscoveryService discoveryService =
            new AgentEvalTraceSetCandidateDiscoveryService(recorder, catalogService);
        return new AgentEvalTraceSetPromotionWorkflowService(discoveryService, catalogService);
    }

    private AgentEvalTraceSetCatalogService catalogService(InMemoryAgentAuditRecorder recorder) {
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        return new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
    }

    private void recordReadEvidence(InMemoryAgentAuditRecorder recorder, String traceId) {
        recorder.record(new AgentAuditEvent(
            "aud_promotion_workflow",
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
    }
}
