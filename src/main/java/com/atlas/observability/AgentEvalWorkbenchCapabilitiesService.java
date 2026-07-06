package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a stable capability manifest for eval/replay frontend workbenches.
 *
 * <p>中文说明：这个服务只发布“前端可以调用哪些治理读模型”的目录，帮助 Vue 工作台按顺序拼装
 * candidate discovery、fixture candidate preview、人审/Git review 和 gate bundle 页面。</p>
 *
 * <p>安全边界：本服务是 metadata-only，不查询 raw audit，不运行 eval/replay，不调用 kube-manager，
 * 不执行 Tool/MCP/LLM/RAG，也不修改 trace-set catalog 或 reviewed fixture 文件。</p>
 */
@Service
public class AgentEvalWorkbenchCapabilitiesService {

    public AgentEvalWorkbenchCapabilitiesResponse capabilities() {
        List<AgentEvalWorkbenchCapability> capabilities = List.of(
            capability(
                "workbench-overview",
                "Eval workbench overview",
                "orient",
                "GET",
                "/api/agent/observability/eval/workbench/overview",
                "",
                AgentEvalWorkbenchOverviewResponse.SCHEMA_VERSION,
                List.of("traceSetCatalog", "capabilityManifest", "gateBundle"),
                List.of("traceSetWorkbenchRows", "nextActions", "workbenchPolicy")
            ),
            capability(
                "trace-set-catalog",
                "Trace-set catalog",
                "discover",
                "GET",
                "/api/agent/observability/eval/trace-sets",
                "",
                AgentEvalTraceSetCatalogResponse.SCHEMA_VERSION,
                List.of(),
                List.of("traceSetId", "suiteId", "traceSetPolicy")
            ),
            capability(
                "workbench-trace-set-detail",
                "Eval workbench trace-set detail",
                "orient",
                "GET",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}",
                "",
                AgentEvalWorkbenchTraceSetDetailResponse.SCHEMA_VERSION,
                List.of("traceSetId", "traceSetCatalog", "traceSetGate"),
                List.of("traceSetView", "promotionChecklist", "endpointTemplates")
            ),
            capability(
                "workbench-promotion-workflow",
                "Eval workbench promotion workflow result",
                "orchestrate",
                "POST",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/promotion-workflow",
                "AgentEvalTraceSetPromotionWorkflowRequest",
                AgentEvalWorkbenchPromotionWorkflowResponse.SCHEMA_VERSION,
                List.of("traceSetId", "candidateLimit"),
                List.of("uiSteps", "patchSummary", "workflowVerdict", "nextActions")
            ),
            capability(
                "workbench-catalog-patch-review",
                "Eval workbench catalog patch review",
                "review",
                "POST",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review",
                "AgentEvalSuiteRequest",
                AgentEvalWorkbenchCatalogPatchReviewResponse.SCHEMA_VERSION,
                List.of("traceSetId", "candidateTraceIds"),
                List.of("patchOperations", "traceDelta", "reviewChecklist", "nextActions")
            ),
            capability(
                "workbench-reviewed-fixture-candidate-autopreview",
                "Reviewed fixture candidate auto-preview workbench",
                "review",
                "GET",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate-workbench?limit={limit}",
                "",
                AgentReviewedTraceFixtureCandidateWorkbenchResponse.SCHEMA_VERSION,
                List.of("traceSetId", "redactedRecentAudit", "candidateDiscovery"),
                List.of("candidateDiscoverySummary", "selectedCandidateTraceId", "candidatePreview", "nextActions")
            ),
            capability(
                "workbench-reviewed-fixture-candidate",
                "Reviewed fixture candidate preview",
                "review",
                "POST",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate",
                "AgentEvalSuiteRequest",
                AgentReviewedTraceFixtureCandidateResponse.SCHEMA_VERSION,
                List.of("traceSetId", "candidateTraceIds", "redactedReplay", "deterministicEvalReport"),
                List.of("candidateFixtureDraft", "candidateGateSummary", "remainingHumanReviewFields", "nextActions")
            ),
            capability(
                "workbench-gate-bundle-summary",
                "Eval workbench gate bundle summary",
                "release-gate",
                "GET",
                "/api/agent/observability/eval/workbench/gate-bundle-summary",
                "",
                AgentEvalWorkbenchGateBundleSummaryResponse.SCHEMA_VERSION,
                List.of("traceSetCatalog", "traceSetGateBundle"),
                List.of("bundleSummary", "traceSetGateRows", "ciArtifact", "blockerSummary")
            ),
            capability(
                "reviewed-trace-evidence",
                "Reviewed eval trace evidence",
                "review",
                "GET",
                "/api/agent/observability/eval/reviewed-trace-evidence",
                "",
                AgentReviewedEvalTraceEvidenceResponse.SCHEMA_VERSION,
                List.of("traceSetCatalog", "reviewedTraceAnchors", "standardsAlignment"),
                List.of("traceSetEvidence", "reviewPipeline", "qualityGates", "nextActions")
            ),
            capability(
                "release-blocking-gate-contract",
                "Release-blocking eval gate contract",
                "release-gate",
                "GET",
                "/api/agent/observability/eval/release-blocking-gate-contract",
                "",
                AgentReleaseBlockingEvalGateContractResponse.SCHEMA_VERSION,
                List.of("reviewedTraceEvidence", "gateBundleSummary"),
                List.of("releaseGateChecks", "blockedReasons", "promotionPlan")
            ),
            capability(
                "memory-rag-eval-suite-binding-contract",
                "Memory/RAG eval-suite binding contract",
                "release-gate",
                "GET",
                "/api/agent/observability/memory-rag/eval-suite-binding-contract",
                "",
                AgentMemoryRagEvalSuiteBindingContractResponse.SCHEMA_VERSION,
                List.of("memoryRagEvalGateContract", "evalSuiteCatalog", "traceSetCatalog"),
                List.of("bindingRows", "requiredTraceSets", "blockedReasons", "recommendedBuildOrder")
            ),
            capability(
                "trace-set-candidate-discovery",
                "Trace-set candidate discovery",
                "discover",
                "GET",
                "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates?limit={limit}",
                "",
                AgentEvalTraceSetCandidateDiscoveryResponse.SCHEMA_VERSION,
                List.of("traceSetId"),
                List.of("recommendedTraceIds", "candidateEvidenceTags")
            ),
            capability(
                "trace-set-curation-review",
                "Trace-set curation review",
                "review",
                "POST",
                "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review",
                "AgentEvalSuiteRequest",
                AgentEvalTraceSetCurationReviewArtifact.SCHEMA_VERSION,
                List.of("traceSetId", "candidateTraceIds"),
                List.of("reviewVerdict", "candidateGate")
            ),
            capability(
                "trace-set-catalog-patch-proposal",
                "Trace-set catalog patch proposal",
                "promote",
                "POST",
                "/api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal",
                "AgentEvalSuiteRequest",
                AgentEvalTraceSetCatalogPatchProposalArtifact.SCHEMA_VERSION,
                List.of("traceSetId", "candidateTraceIds"),
                List.of("jsonPatch", "readyForGitReview")
            ),
            capability(
                "trace-set-promotion-workflow",
                "Trace-set promotion workflow",
                "orchestrate",
                "POST",
                "/api/agent/observability/eval/trace-sets/{traceSetId}/promotion-workflow",
                "AgentEvalTraceSetPromotionWorkflowRequest",
                AgentEvalTraceSetPromotionWorkflowArtifact.SCHEMA_VERSION,
                List.of("traceSetId", "candidateLimit"),
                List.of("candidateDiscovery", "catalogPatchProposal", "workflowVerdict")
            ),
            capability(
                "trace-set-gate-bundle",
                "Trace-set gate bundle",
                "release-gate",
                "POST",
                "/api/agent/observability/eval/trace-sets/gate-bundle",
                "AgentEvalSuiteRequest",
                AgentEvalTraceSetGateBundleArtifact.SCHEMA_VERSION,
                List.of("curatedTraceSetCatalog"),
                List.of("releaseEligible", "traceSetGateVerdicts")
            ),
            capability(
                "trace-replay-timeline",
                "Trace replay timeline",
                "drill-down",
                "GET",
                "/api/agent/observability/replay/trace/{traceId}?limit={limit}",
                "",
                AgentReplayTimelineResponse.SCHEMA_VERSION,
                List.of("traceId"),
                List.of("redactedTimelineSteps")
            ),
            capability(
                "trace-eval-report",
                "Trace eval report",
                "drill-down",
                "GET",
                "/api/agent/observability/eval/trace/{traceId}?limit={limit}",
                "",
                AgentEvalReportResponse.SCHEMA_VERSION,
                List.of("traceId"),
                List.of("deterministicChecks", "score", "verdict")
            )
        );
        return AgentEvalWorkbenchCapabilitiesResponse.of(
            capabilities,
            List.of(
                "workbench-overview",
                "trace-set-catalog",
                "workbench-trace-set-detail",
                "workbench-promotion-workflow",
                "workbench-catalog-patch-review",
                "workbench-reviewed-fixture-candidate-autopreview",
                "workbench-reviewed-fixture-candidate",
                "workbench-gate-bundle-summary",
                "reviewed-trace-evidence",
                "release-blocking-gate-contract",
                "memory-rag-eval-suite-binding-contract",
                "trace-set-catalog-patch-proposal",
                "trace-set-gate-bundle",
                "trace-replay-timeline",
                "trace-eval-report"
            ),
            workbenchPolicy(capabilities),
            privacyProof()
        );
    }

    private AgentEvalWorkbenchCapability capability(String id,
                                                    String title,
                                                    String stage,
                                                    String httpMethod,
                                                    String pathTemplate,
                                                    String requestSchema,
                                                    String responseSchema,
                                                    List<String> consumes,
                                                    List<String> produces) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("metadataOnly", "trace-set-catalog".equals(id));
        policy.put("mutatesCatalog", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requiresGitReviewForPromotion", id.contains("patch") || id.contains("workflow") || id.contains("fixture-candidate"));
        policy.put("requiresHumanFixtureReviewBeforeCommit", id.contains("fixture-candidate"));
        policy.put("createsFixtureFile", false);
        policy.put("fixtureUploadAccepted", false);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        return new AgentEvalWorkbenchCapability(
            id,
            title,
            stage,
            httpMethod,
            pathTemplate,
            requestSchema,
            responseSchema,
            true,
            true,
            false,
            false,
            false,
            consumes,
            produces,
            policy
        );
    }

    private Map<String, Object> workbenchPolicy(List<AgentEvalWorkbenchCapability> capabilities) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", AgentEvalWorkbenchCapabilitiesResponse.SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("capabilityCount", capabilities.size());
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("recommendedPrimaryFlow", "workbench-promotion-workflow");
        policy.put("drillDownFlow", "trace-replay-timeline -> trace-eval-report");
        policy.put("runtimeCatalogWrite", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        return Map.copyOf(policy);
    }

    private Map<String, Object> privacyProof() {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", true);
        proof.put("containsRawPrincipal", false);
        proof.put("containsRawOrganization", false);
        proof.put("containsRawConversation", false);
        proof.put("containsRawEndpoints", false);
        proof.put("containsRawKubeManagerEndpoints", false);
        proof.put("containsRawReason", false);
        proof.put("containsRawParameterValues", false);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }
}
