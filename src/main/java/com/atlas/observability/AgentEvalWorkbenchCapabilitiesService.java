package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a stable capability manifest for eval/replay frontend workbenches.
 *
 * <p>This service is metadata-only. It does not query audit storage, run evals,
 * call kube-manager, execute Tools, or mutate trace-set catalogs.</p>
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
        policy.put("requiresGitReviewForPromotion", id.contains("patch") || id.contains("workflow"));
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
