package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eval 证据晋升工作流的只读编排产物。
 *
 * <p>中文说明：这个 record 把 promotion workflow 的结果打包成一个前端可直接渲染的教学视图：
 * 发现了哪些候选、哪些 traceId 被推荐、补丁建议是什么、为什么现在可以进入 Git review。</p>
 *
 * <p>安全边界：本 artifact 只生成 read model，不写目录、不执行 runtime mutation、
 * 不调用 Tool/MCP/LLM/RAG/kube-manager。{@code readyForGitReview=true} 只是“可进入人工 Git review”，
 * 不是目录写权限，也不是 release authority。</p>
 */
public record AgentEvalTraceSetPromotionWorkflowArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String workflowVerdict,
    boolean readyForGitReview,
    boolean catalogMutated,
    int selectedCandidateTraceCount,
    List<String> selectedCandidateTraceIds,
    AgentEvalTraceSetCandidateDiscoveryResponse candidateDiscovery,
    AgentEvalTraceSetCatalogPatchProposalArtifact catalogPatchProposal,
    Map<String, Object> workflowPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-promotion-workflow.v1";

    public static AgentEvalTraceSetPromotionWorkflowArtifact from(
        AgentEvalTraceSetCandidateDiscoveryResponse discovery,
        List<String> selectedCandidateTraceIds,
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
        int maxSelectedCandidates) {
        // 中文说明：把 discovery 与 patch proposal 合成单个教学型 artifact，方便前端一次展示整条证据链。
        // 安全边界：这里不改 trace set 目录，只投影审阅结果。
        List<String> selected = selectedCandidateTraceIds != null
            ? List.copyOf(selectedCandidateTraceIds)
            : List.of();
        boolean ready = proposal != null && proposal.readyForGitReview();
        return new AgentEvalTraceSetPromotionWorkflowArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            proposal != null ? proposal.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            discovery != null ? discovery.traceSetId() : "",
            discovery != null ? discovery.traceSetTitle() : "",
            discovery != null ? discovery.suiteId() : "",
            verdict(discovery, proposal, ready),
            ready,
            false,
            selected.size(),
            selected,
            discovery,
            proposal,
            workflowPolicy(discovery, proposal, selected, maxSelectedCandidates, ready),
            privacyProof(discovery, proposal)
        );
    }

    private static String verdict(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                  AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                  boolean ready) {
        // 中文说明：verdict 只描述审阅状态，不描述发布权力。
        // 安全边界：即使走到 READY_FOR_GIT_REVIEW，也仍然必须经过人审和 Git diff。
        if (ready) {
            return "READY_FOR_GIT_REVIEW";
        }
        if (discovery == null) {
            return "REJECT_TRACE_SET_NOT_FOUND";
        }
        if (discovery.candidateTraceIds().isEmpty()) {
            return "NO_RECOMMENDED_CANDIDATES";
        }
        return proposal != null ? proposal.proposalVerdict() : "REJECT_PATCH_PROPOSAL_UNAVAILABLE";
    }

    private static Map<String, Object> workflowPolicy(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                      AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                      List<String> selected,
                                                      int maxSelectedCandidates,
                                                      boolean ready) {
        // 中文说明：workflowPolicy 让前端和学习者明确这条链路的能力边界。
        // 安全边界：policy 里显式写出 false，避免按钮或布尔值被误读为授权。
        Map<String, Object> policy = new LinkedHashMap<>();
        String traceSetId = discovery != null ? discovery.traceSetId() : "";
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("artifactOnly", true);
        policy.put("workflowOnly", true);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("candidateDiscoveryEmbedded", discovery != null);
        policy.put("catalogPatchProposalEmbedded", proposal != null);
        policy.put("selectedRecommendedOnly", true);
        policy.put("maxSelectedCandidates", Math.max(0, maxSelectedCandidates));
        policy.put("selectedCandidateTraceCount", selected.size());
        policy.put("requiresHumanReview", true);
        policy.put("requiresGitReview", true);
        policy.put("requiresCiGateBundleRegeneration", ready);
        policy.put("releaseBlockingAfterMergeOnly", true);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        if (!traceSetId.isBlank()) {
            policy.put("candidateEndpoint", "/api/agent/observability/eval/trace-sets/" + traceSetId + "/candidates");
            policy.put("curationReviewEndpoint", "/api/agent/observability/eval/trace-sets/" + traceSetId + "/curation-review");
            policy.put("catalogPatchProposalEndpoint",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/catalog-patch-proposal");
            policy.put("gateBundleEndpoint", "/api/agent/observability/eval/trace-sets/gate-bundle");
        }
        if (proposal != null) {
            policy.put("proposalVerdict", proposal.proposalVerdict());
            policy.put("readyForGitReview", proposal.readyForGitReview());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                    AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        // 中文说明：privacyProof 证明这条链路只保留脱敏证据，不把 raw principal / org / conversation
        // / endpoint / reason / parameter values 泄露给前端或审阅者。
        // 安全边界：只要任一上游混入原始敏感字段，这份 proof 就必须 fail-closed。
        Map<String, Object> discoveryProof = discovery != null ? discovery.privacy() : Map.of();
        Map<String, Object> proposalProof = proposal != null ? proposal.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(discoveryProof, "containsRawPrincipal")
            || truthy(proposalProof, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(discoveryProof, "containsRawOrganization")
            || truthy(proposalProof, "containsRawOrganization");
        boolean containsRawConversation = truthy(discoveryProof, "containsRawConversation")
            || truthy(proposalProof, "containsRawConversation");
        boolean containsRawEndpoints = truthy(discoveryProof, "containsRawEndpoints")
            || truthy(proposalProof, "containsRawEndpoints");
        boolean containsRawReason = truthy(discoveryProof, "containsRawReason")
            || truthy(proposalProof, "containsRawReason");
        boolean containsRawParameterValues = truthy(discoveryProof, "containsRawParameterValues")
            || truthy(proposalProof, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(discoveryProof.get("redactedOnly"))
            && Boolean.TRUE.equals(proposalProof.get("redactedOnly"))
            && !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawEndpoints
            || containsRawReason
            || containsRawParameterValues));
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawConversation", containsRawConversation);
        proof.put("containsRawEndpoints", containsRawEndpoints);
        proof.put("containsRawReason", containsRawReason);
        proof.put("containsRawParameterValues", containsRawParameterValues);
        proof.put("deterministic", Boolean.TRUE.equals(discoveryProof.get("deterministic"))
            && Boolean.TRUE.equals(proposalProof.get("deterministic")));
        proof.put("llmUsed", truthy(discoveryProof, "llmUsed") || truthy(proposalProof, "llmUsed"));
        proof.put("externalCalls", truthy(discoveryProof, "externalCalls") || truthy(proposalProof, "externalCalls"));
        proof.put("toolExecution", truthy(discoveryProof, "toolExecution") || truthy(proposalProof, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(discoveryProof, "kubeManagerCalls") || truthy(proposalProof, "kubeManagerCalls"));
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
