package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向前端的 trace-set promotion workflow 包装读模型。
 *
 * <p>中文说明：raw workflow artifact 仍然是事实源，这个 response 只负责把候选发现、
 * curation review、patch proposal 和 gate bundle 的教学信息拼成前端可渲染的工作台视图。</p>
 *
 * <p>安全边界：这里是 read model，不是 runtime catalog write。uiSteps、patchSummary、
 * candidateGateSummary 和 nextActions 都只是教学/审阅导航，不是执行权限，不是 release authority。</p>
 */
public record AgentEvalWorkbenchPromotionWorkflowResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String workflowVerdict,
    boolean readyForGitReview,
    int selectedCandidateTraceCount,
    List<String> selectedCandidateTraceIds,
    AgentEvalWorkbenchTraceSetView traceSetView,
    AgentEvalTraceSetPromotionWorkflowArtifact workflow,
    List<Map<String, Object>> uiSteps,
    Map<String, Object> patchSummary,
    Map<String, Object> candidateGateSummary,
    List<String> nextActions,
    Map<String, Object> endpointTemplates,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-promotion-workflow.v1";

    public static AgentEvalWorkbenchPromotionWorkflowResponse from(
        AgentEvalTraceSetDefinition definition,
        AgentEvalTraceSetGateArtifact traceSetGate,
        AgentEvalTraceSetPromotionWorkflowArtifact workflow) {
        // 中文说明：先把 definition/gate/workflow 拼成统一工作台视图，方便前端按步骤理解审阅链路。
        // 安全边界：这个工厂只做投影，不调用任何运行时执行器，也不写 trace set catalog。
        AgentEvalWorkbenchTraceSetView view = AgentEvalWorkbenchTraceSetView.from(definition, traceSetGate);
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal = workflow != null
            ? workflow.catalogPatchProposal()
            : null;
        return new AgentEvalWorkbenchPromotionWorkflowResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            workflow != null ? workflow.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            workflow != null ? workflow.traceSetId() : safeText(definition != null ? definition.id() : ""),
            workflow != null ? workflow.traceSetTitle() : safeText(definition != null ? definition.title() : ""),
            workflow != null ? workflow.suiteId() : safeText(definition != null ? definition.suiteId() : ""),
            workflow != null ? workflow.workflowVerdict() : "REJECT_WORKFLOW_UNAVAILABLE",
            workflow != null && workflow.readyForGitReview(),
            workflow != null ? workflow.selectedCandidateTraceCount() : 0,
            workflow != null ? List.copyOf(workflow.selectedCandidateTraceIds()) : List.of(),
            view,
            workflow,
            buildUiSteps(workflow, proposal),
            buildPatchSummary(proposal),
            buildCandidateGateSummary(proposal),
            buildNextActions(workflow, proposal),
            buildEndpointTemplates(view.id()),
            buildWorkbenchPolicy(workflow, proposal, view),
            privacyProof(workflow, view)
        );
    }

    private static List<Map<String, Object>> buildUiSteps(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                          AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        // 中文说明：uiSteps 是教学型步骤，不是自动执行编排；前端据此展示下一步该看什么。
        // 安全边界：steps 只描述 read-only 证据，不授予目录写权限。
        AgentEvalTraceSetCandidateDiscoveryResponse discovery = workflow != null ? workflow.candidateDiscovery() : null;
        AgentEvalTraceSetCurationReviewArtifact review = proposal != null ? proposal.curationReview() : null;
        return List.of(
            step(
                "candidate-discovery",
                discovery != null && discovery.candidateTraceCount() > 0 ? "CANDIDATES_FOUND" : "NO_RECOMMENDED_CANDIDATES",
                "review-redacted-candidate-traces",
                Map.of(
                    "candidateTraceCount", discovery != null ? discovery.candidateTraceCount() : 0,
                    "selectedCandidateTraceCount", workflow != null ? workflow.selectedCandidateTraceCount() : 0,
                    "auditQueryTruncated", discovery != null && discovery.auditQueryTruncated()
                )
            ),
            step(
                "curation-review",
                review != null && review.readyForCatalogReview() ? "READY_FOR_CATALOG_REVIEW" : "REVIEW_NOT_READY",
                "inspect-deterministic-candidate-gate",
                Map.of(
                    "candidateTraceCount", review != null ? review.candidateTraceCount() : 0,
                    "reviewVerdict", review != null ? review.reviewVerdict() : "REVIEW_UNAVAILABLE",
                    "readyForCatalogReview", review != null && review.readyForCatalogReview()
                )
            ),
            step(
                "catalog-patch-proposal",
                proposal != null && proposal.readyForGitReview() ? "READY_FOR_GIT_REVIEW" : "PATCH_NOT_READY",
                "open-git-reviewed-catalog-patch",
                Map.of(
                    "proposalVerdict", proposal != null ? proposal.proposalVerdict() : "PROPOSAL_UNAVAILABLE",
                    "addedTraceCount", proposal != null ? proposal.addedTraceCount() : 0,
                    "jsonPatchOperationCount", proposal != null ? proposal.jsonPatch().size() : 0
                )
            ),
            step(
                "gate-bundle-regeneration",
                proposal != null && proposal.readyForGitReview() ? "WAITING_FOR_GIT_MERGE" : "BLOCKED_UNTIL_PATCH_READY",
                "regenerate-gate-bundle-after-reviewed-merge",
                Map.of(
                    "runtimeCatalogWrite", false,
                    "requiresGitReview", true,
                    "ciBlockingEnabled", false
                )
            )
        );
    }

    private static Map<String, Object> step(String id,
                                            String status,
                                            String recommendedAction,
                                            Map<String, Object> evidence) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("status", status);
        step.put("recommendedAction", recommendedAction);
        step.put("evidence", evidence != null ? Map.copyOf(evidence) : Map.of());
        return Map.copyOf(step);
    }

    private static Map<String, Object> buildPatchSummary(AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        // 中文说明：patchSummary 只提炼 JSON Patch 相关摘要，方便学习者理解“补丁建议”和“目录写入”的区别。
        // 安全边界：summary 里的 readyForGitReview 只表示可以进入人工 Git review，不代表已经发布。
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", proposal != null ? proposal.schemaVersion() : "");
        summary.put("proposalVerdict", proposal != null ? proposal.proposalVerdict() : "PROPOSAL_UNAVAILABLE");
        summary.put("readyForGitReview", proposal != null && proposal.readyForGitReview());
        summary.put("source", proposal != null ? proposal.source() : "");
        summary.put("targetResource", proposal != null ? proposal.targetResource() : "");
        summary.put("traceSetIndex", proposal != null ? proposal.traceSetIndex() : -1);
        summary.put("originalTraceSetTraceCount", proposal != null ? proposal.originalTraceSetTraceCount() : 0);
        summary.put("candidateTraceCount", proposal != null ? proposal.candidateTraceCount() : 0);
        summary.put("addedTraceCount", proposal != null ? proposal.addedTraceCount() : 0);
        summary.put("jsonPatchOperationCount", proposal != null ? proposal.jsonPatch().size() : 0);
        summary.put("catalogMutated", false);
        summary.put("runtimeCatalogWrite", false);
        summary.put("requiresGitReview", true);
        summary.put("releaseBlockingAfterMergeOnly", true);
        return Map.copyOf(summary);
    }

    private static Map<String, Object> buildCandidateGateSummary(AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        // 中文说明：candidateGateSummary 把 curation review 的 gate 信息压缩成前端卡片。
        // 安全边界：gate summary 是前端可读证据，不是 release authority。
        AgentEvalTraceSetCurationReviewArtifact review = proposal != null ? proposal.curationReview() : null;
        AgentEvalSuiteGateArtifact suiteGate = review != null ? review.candidateGate() : null;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", suiteGate != null ? suiteGate.schemaVersion() : "");
        summary.put("reviewVerdict", review != null ? review.reviewVerdict() : "REVIEW_UNAVAILABLE");
        summary.put("readyForCatalogReview", review != null && review.readyForCatalogReview());
        summary.put("gateVerdict", suiteGate != null ? suiteGate.gateVerdict() : "GATE_UNAVAILABLE");
        summary.put("pass", suiteGate != null && suiteGate.pass());
        summary.put("observedMinimumScore", suiteGate != null ? suiteGate.observedMinimumScore() : 0);
        summary.put("evaluatedCases", suiteGate != null ? suiteGate.evaluatedCases() : 0);
        summary.put("failedReports", suiteGate != null ? suiteGate.failedReports() : 0);
        summary.put("warningReports", suiteGate != null ? suiteGate.warningReports() : 0);
        summary.put("embeddedReports", false);
        summary.put("embeddedReplay", false);
        return Map.copyOf(summary);
    }

    private static List<String> buildNextActions(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                 AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        // 中文说明：nextActions 是工作台教学路径，告诉人类下一步该看哪张卡、哪份证据。
        // 安全边界：这些动作名只是导航建议，不是自动执行按钮。
        if (workflow == null) {
            return List.of("refresh-trace-set-detail");
        }
        if (proposal != null && proposal.readyForGitReview()) {
            return List.of(
                "open-catalog-patch-proposal",
                "create-human-git-review",
                "regenerate-gate-bundle-after-merge"
            );
        }
        if (workflow.selectedCandidateTraceCount() == 0) {
            return List.of(
                "inspect-candidate-discovery",
                "generate-more-redacted-agent-traces",
                "retry-promotion-workflow"
            );
        }
        return List.of(
            "inspect-candidate-gate",
            "open-replay-drill-down",
            "open-trace-eval-report"
        );
    }

    private static Map<String, Object> buildEndpointTemplates(String traceSetId) {
        // 中文说明：endpointTemplates 让前端和学习者看到 promotion workflow 相关只读入口的模板。
        // 安全边界：模板不等于权限，路径可见不等于可写。
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("overview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("detail", "/api/agent/observability/eval/workbench/trace-sets/" + id);
        endpoints.put("workbenchPromotionWorkflow",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("rawPromotionWorkflow",
            "/api/agent/observability/eval/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("catalogPatchProposal",
            "/api/agent/observability/eval/trace-sets/" + id + "/catalog-patch-proposal");
        endpoints.put("workbenchGateBundleSummary",
            "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("gateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildWorkbenchPolicy(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                            AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                            AgentEvalWorkbenchTraceSetView view) {
        // 中文说明：workbenchPolicy 直接把“哪些按钮不能出现、哪些能力不能打开”写出来。
        // 安全边界：policy 的 false 不是装饰字段，而是明确的权限关闭说明。
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("workbenchWrapperOnly", true);
        policy.put("workflowExecuted", workflow != null);
        policy.put("candidateDiscoveryExecuted", workflow != null && workflow.candidateDiscovery() != null);
        policy.put("catalogPatchProposalEmbedded", proposal != null);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("requiresGitReview", true);
        policy.put("requiresCiGateBundleRegeneration", proposal != null && proposal.readyForGitReview());
        policy.put("releaseBlockingAfterMergeOnly", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        policy.put("traceSetStatusBeforePromotion", view != null ? view.status() : "");
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                    AgentEvalWorkbenchTraceSetView view) {
        // 中文说明：privacyProof 证明工作台展示的是脱敏证据，而不是 raw 审计、raw prompt 或 raw endpoint。
        // 安全边界：一旦上游混入任何 raw 字段，这份 proof 就必须保持 fail-closed。
        Map<String, Object> workflowPrivacy = workflow != null ? workflow.privacy() : Map.of();
        Map<String, Object> viewPrivacy = view != null ? view.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(workflowPrivacy, "containsRawPrincipal")
            || truthy(viewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(workflowPrivacy, "containsRawOrganization")
            || truthy(viewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(workflowPrivacy, "containsRawConversation")
            || truthy(viewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(workflowPrivacy, "containsRawEndpoints")
            || truthy(viewPrivacy, "containsRawEndpoints");
        boolean containsRawKubeManagerEndpoints = truthy(viewPrivacy, "containsRawKubeManagerEndpoints");
        boolean containsRawReason = truthy(workflowPrivacy, "containsRawReason")
            || truthy(viewPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(workflowPrivacy, "containsRawParameterValues")
            || truthy(viewPrivacy, "containsRawParameterValues");
        boolean upstreamRedactedOnly = (workflow == null || Boolean.TRUE.equals(workflowPrivacy.get("redactedOnly")))
            && (view == null || Boolean.TRUE.equals(viewPrivacy.get("redactedOnly")));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", upstreamRedactedOnly && !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawEndpoints
            || containsRawKubeManagerEndpoints
            || containsRawReason
            || containsRawParameterValues));
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawConversation", containsRawConversation);
        proof.put("containsRawEndpoints", containsRawEndpoints);
        proof.put("containsRawKubeManagerEndpoints", containsRawKubeManagerEndpoints);
        proof.put("containsRawReason", containsRawReason);
        proof.put("containsRawParameterValues", containsRawParameterValues);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
