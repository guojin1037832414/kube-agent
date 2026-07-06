package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reviewed fixture candidate 的前端工作台组合读模型。
 *
 * <p>中文说明：M5.85-43 的 candidate preview 仍需要管理员先提供候选 traceId。本响应把前一步
 * redacted candidate discovery 和后一步 reviewed fixture candidate preview 合并成一个页面可渲染的
 * “自动预选 + 预检”包，帮助人审者更快找到首个可能进入 Git review 的 trace 证据。</p>
 *
 * <p>安全边界：这是 admin-only / read-only / workbench-only 聚合视图。它不接收 caller traceId，
 * 不创建 fixture 文件，不写 `eval-trace-sets.json`，不上传 fixture，不执行 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 HITL/audit/memory，也不授予 CI blocking 或 release authority。</p>
 */
public record AgentReviewedTraceFixtureCandidateWorkbenchResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String workbenchStatus,
    int maxEvents,
    int inspectedEvents,
    int inspectedTraceCount,
    int discoveredCandidateCount,
    int recommendedCandidateCount,
    String selectedCandidateTraceId,
    boolean candidateSelected,
    boolean readyForHumanGitReview,
    boolean readyForFixtureCommit,
    Map<String, Object> candidateDiscoverySummary,
    AgentEvalTraceSetCandidateDiscoveryResponse candidateDiscovery,
    AgentReviewedTraceFixtureCandidateResponse candidatePreview,
    List<String> blockingReasons,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-candidate-workbench.v1";
    public static final String ENDPOINT_TEMPLATE =
        "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate-workbench";

    public static AgentReviewedTraceFixtureCandidateWorkbenchResponse from(
        AgentEvalTraceSetCandidateDiscoveryResponse discovery,
        AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        List<String> recommendedTraceIds = discovery != null ? discovery.candidateTraceIds() : List.of();
        String selectedTraceId = candidatePreview != null ? candidatePreview.selectedTraceId() : "";
        boolean selected = selectedTraceId != null && !selectedTraceId.isBlank();
        boolean readyForHumanGitReview = candidatePreview != null && candidatePreview.readyForHumanGitReview();
        boolean readyForFixtureCommit = candidatePreview != null && candidatePreview.readyForFixtureCommit();
        List<String> blockingReasons = buildBlockingReasons(recommendedTraceIds, candidatePreview);
        return new AgentReviewedTraceFixtureCandidateWorkbenchResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            discovery != null ? discovery.traceSetId() : "",
            discovery != null ? discovery.traceSetTitle() : "",
            discovery != null ? discovery.suiteId() : "",
            workbenchStatus(recommendedTraceIds, candidatePreview, readyForHumanGitReview),
            discovery != null ? discovery.maxEvents() : 0,
            discovery != null ? discovery.inspectedEvents() : 0,
            discovery != null ? discovery.inspectedTraceCount() : 0,
            discovery != null ? discovery.candidates().size() : 0,
            recommendedTraceIds.size(),
            selectedTraceId != null ? selectedTraceId : "",
            selected,
            readyForHumanGitReview,
            readyForFixtureCommit,
            candidateDiscoverySummary(discovery, selectedTraceId),
            discovery,
            candidatePreview,
            blockingReasons,
            nextActions(readyForHumanGitReview, selected, blockingReasons),
            endpointMap(discovery != null ? discovery.traceSetId() : ""),
            workbenchPolicy(discovery, candidatePreview),
            buildSafety(),
            privacy(discovery, candidatePreview)
        );
    }

    private static String workbenchStatus(List<String> recommendedTraceIds,
                                          AgentReviewedTraceFixtureCandidateResponse candidatePreview,
                                          boolean readyForHumanGitReview) {
        if (recommendedTraceIds == null || recommendedTraceIds.isEmpty()) {
            return "NO_RECOMMENDED_CANDIDATE_FROM_REDACTED_AUDIT";
        }
        if (candidatePreview == null) {
            return "CANDIDATE_PREVIEW_UNAVAILABLE";
        }
        return readyForHumanGitReview
            ? "READY_FOR_HUMAN_FIXTURE_REVIEW"
            : "CANDIDATE_PREVIEW_BLOCKED";
    }

    private static Map<String, Object> candidateDiscoverySummary(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                                 String selectedTraceId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", AgentEvalTraceSetCandidateDiscoveryResponse.SCHEMA_VERSION);
        summary.put("auditQueryBackend", discovery != null ? discovery.auditQueryBackend() : "");
        summary.put("maxEvents", discovery != null ? discovery.maxEvents() : 0);
        summary.put("inspectedEvents", discovery != null ? discovery.inspectedEvents() : 0);
        summary.put("inspectedTraceCount", discovery != null ? discovery.inspectedTraceCount() : 0);
        summary.put("candidateTraceCount", discovery != null ? discovery.candidateTraceCount() : 0);
        summary.put("recommendedCandidateCount", discovery != null ? discovery.candidateTraceIds().size() : 0);
        summary.put("selectedCandidateTraceId", selectedTraceId != null ? selectedTraceId : "");
        summary.put("autoSelectedFirstRecommendedCandidate", selectedTraceId != null && !selectedTraceId.isBlank());
        summary.put("candidateListEmbedded", true);
        summary.put("rawAuditEmbedded", false);
        summary.put("auditQueryTruncated", discovery != null && discovery.auditQueryTruncated());
        return Map.copyOf(summary);
    }

    private static List<String> buildBlockingReasons(List<String> recommendedTraceIds,
                                                     AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        List<String> reasons = new ArrayList<>();
        if (recommendedTraceIds == null || recommendedTraceIds.isEmpty()) {
            reasons.add("no-recommended-redacted-trace-candidate");
        }
        if (candidatePreview == null) {
            reasons.add("candidate-preview-unavailable");
        } else {
            reasons.addAll(candidatePreview.blockingReasons());
        }
        return List.copyOf(reasons);
    }

    private static List<String> nextActions(boolean readyForHumanGitReview,
                                            boolean selected,
                                            List<String> blockingReasons) {
        if (readyForHumanGitReview) {
            return List.of(
                "open-human-git-review-for-selected-candidate",
                "copy-candidate-draft-outside-runtime",
                "fill-human-git-review-fields",
                "compute-final-fixture-evidence-digest",
                "commit-reviewed-fixture-json-through-human-git-review"
            );
        }
        if (!selected) {
            return List.of(
                "capture-real-redacted-audit-evidence",
                "rerun-candidate-discovery",
                "keep-runtime-fixture-upload-and-catalog-write-disabled"
            );
        }
        List<String> actions = new ArrayList<>();
        actions.add("inspect-selected-candidate-preview-blockers");
        actions.addAll(blockingReasons != null && !blockingReasons.isEmpty()
            ? List.of("fix-redacted-replay-or-deterministic-eval-evidence")
            : List.of());
        actions.add("rerun-reviewed-fixture-candidate-workbench");
        actions.add("keep-runtime-fixture-upload-and-catalog-write-disabled");
        return List.copyOf(actions);
    }

    private static Map<String, Object> endpointMap(String traceSetId) {
        String id = traceSetId != null ? traceSetId : "";
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("fixtureCandidateWorkbench", ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("fixtureCandidate", AgentReviewedTraceFixtureCandidateResponse.ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("candidateDiscovery", "/api/agent/observability/eval/trace-sets/" + id + "/candidates?limit={limit}");
        endpoints.put("fixtureTemplate", AgentReviewedTraceFixtureTemplateResponse.ENDPOINT);
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/catalog-patch-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> workbenchPolicy(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                       AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("traceSetId", discovery != null ? discovery.traceSetId() : "");
        policy.put("suiteId", discovery != null ? discovery.suiteId() : "");
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("workbenchOnly", true);
        policy.put("requestTraceIdsAccepted", false);
        policy.put("autoSelectsFirstRecommendedTraceId", true);
        policy.put("requiresHumanFixtureReviewBeforeCommit", true);
        policy.put("readyForFixtureCommit", candidatePreview != null && candidatePreview.readyForFixtureCommit());
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("createsFixtureFile", false);
        policy.put("fixtureUploadAccepted", false);
        policy.put("toolExecution", false);
        policy.put("mcpToolCall", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("previewOnly", true);
        safety.put("workbenchOnly", true);
        safety.put("callerTraceIdsAccepted", false);
        safety.put("callerTraceIdsAcceptedAsFixtureEvidence", false);
        safety.put("createsFixtureFile", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("toolExecution", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("releaseBlockingAllowedNow", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                               AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        Map<String, Object> discoveryPrivacy = discovery != null ? discovery.privacy() : Map.of();
        Map<String, Object> previewPrivacy = candidatePreview != null ? candidatePreview.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(discoveryPrivacy, "containsRawPrincipal")
            || truthy(previewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(discoveryPrivacy, "containsRawOrganization")
            || truthy(previewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(discoveryPrivacy, "containsRawConversation")
            || truthy(previewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(discoveryPrivacy, "containsRawEndpoints")
            || truthy(previewPrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(discoveryPrivacy, "containsRawReason")
            || truthy(previewPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(discoveryPrivacy, "containsRawParameterValues")
            || truthy(previewPrivacy, "containsRawParameterValues");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawEndpoints
            && !containsRawReason
            && !containsRawParameterValues);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawEndpoints", containsRawEndpoints);
        privacy.put("containsRawReason", containsRawReason);
        privacy.put("containsRawParameterValues", containsRawParameterValues);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
