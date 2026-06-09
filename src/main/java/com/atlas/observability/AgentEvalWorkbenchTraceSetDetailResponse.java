package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only detail read model for one trace set in the eval workbench.
 */
public record AgentEvalWorkbenchTraceSetDetailResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String status,
    boolean readyForCiBlocking,
    int curatedTraceCount,
    List<String> curatedTraceIds,
    List<String> evidenceRequirements,
    List<String> tags,
    AgentEvalWorkbenchTraceSetView traceSetView,
    AgentEvalTraceSetDefinition traceSet,
    AgentEvalTraceSetGateArtifact gate,
    List<String> promotionChecklist,
    List<String> nextActions,
    Map<String, Object> endpointTemplates,
    Map<String, Object> detailPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-trace-set-detail.v1";

    public static AgentEvalWorkbenchTraceSetDetailResponse of(AgentEvalTraceSetDefinition definition,
                                                              AgentEvalTraceSetGateArtifact gate) {
        AgentEvalWorkbenchTraceSetView view = AgentEvalWorkbenchTraceSetView.from(definition, gate);
        List<String> curatedTraceIds = definition != null ? List.copyOf(definition.traceIds()) : List.of();
        return new AgentEvalWorkbenchTraceSetDetailResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            definition != null ? definition.id() : "",
            definition != null ? definition.title() : "",
            definition != null ? definition.suiteId() : "",
            view.status(),
            view.readyForCiBlocking(),
            curatedTraceIds.size(),
            curatedTraceIds,
            definition != null ? List.copyOf(definition.evidenceRequirements()) : List.of(),
            definition != null ? List.copyOf(definition.tags()) : List.of(),
            view,
            definition,
            gate,
            buildPromotionChecklist(view.status()),
            buildNextActions(view),
            buildEndpointTemplates(view.id()),
            buildDetailPolicy(definition, gate, view),
            privacyProof(definition, gate, view)
        );
    }

    private static List<String> buildPromotionChecklist(String status) {
        List<String> checklist = new ArrayList<>();
        checklist.add("confirm-trace-set-purpose-and-suite");
        checklist.add("discover-redacted-candidate-traces");
        checklist.add("run-curation-review");
        checklist.add("review-catalog-patch-proposal");
        checklist.add("merge-catalog-change-through-git-review");
        checklist.add("regenerate-trace-set-gate-bundle");
        checklist.add("drill-down-failures-with-replay-and-eval");
        if ("GATE_PASS".equals(status)) {
            checklist.add("consider-ci-blocking-only-after-reviewed-evidence");
        }
        return List.copyOf(checklist);
    }

    private static List<String> buildNextActions(AgentEvalWorkbenchTraceSetView view) {
        if (view == null) {
            return List.of("refresh-workbench-overview");
        }
        return switch (view.status()) {
            case "NEEDS_REDACTED_EVIDENCE" -> List.of(
                "open-candidate-discovery",
                "run-promotion-workflow",
                "review-catalog-patch-proposal"
            );
            case "GATE_PASS" -> List.of(
                "inspect-curated-traces",
                "regenerate-gate-bundle-before-ci-blocking"
            );
            case "GATE_FAIL_REVIEW_REQUIRED" -> List.of(
                "open-replay-drill-down",
                "open-eval-report",
                "review-catalog-evidence"
            );
            default -> List.of("refresh-workbench-overview");
        };
    }

    private static Map<String, Object> buildEndpointTemplates(String traceSetId) {
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("overview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("detail", "/api/agent/observability/eval/workbench/trace-sets/" + id);
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("candidateDiscovery", "/api/agent/observability/eval/trace-sets/" + id + "/candidates?limit={limit}");
        endpoints.put("curationReview", "/api/agent/observability/eval/trace-sets/" + id + "/curation-review");
        endpoints.put("catalogPatchProposal", "/api/agent/observability/eval/trace-sets/" + id + "/catalog-patch-proposal");
        endpoints.put("promotionWorkflow", "/api/agent/observability/eval/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("traceSetGate", "/api/agent/observability/eval/trace-sets/" + id + "/gate");
        endpoints.put("gateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildDetailPolicy(AgentEvalTraceSetDefinition definition,
                                                         AgentEvalTraceSetGateArtifact gate,
                                                         AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("detailOnly", true);
        policy.put("readOnly", true);
        policy.put("gateEmbedded", gate != null);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("candidateDiscoveryExecuted", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("releaseBlockingAfterReviewedEvidenceOnly", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("readyForCiBlocking", view != null && view.readyForCiBlocking());
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        if (definition != null) {
            policy.put("traceSetId", definition.id());
            policy.put("suiteId", definition.suiteId());
            policy.put("curatedTraceCount", definition.traceIds().size());
        }
        if (gate != null) {
            policy.put("gateVerdict", gate.gateVerdict());
            policy.put("emptyInput", gate.emptyInput());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetDefinition definition,
                                                    AgentEvalTraceSetGateArtifact gate,
                                                    AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> definitionPrivacy = definition != null ? definition.guarantees() : Map.of();
        Map<String, Object> gatePrivacy = gate != null ? gate.privacy() : Map.of();
        Map<String, Object> viewPrivacy = view != null ? view.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(definitionPrivacy, "containsRawPrincipal")
            || truthy(gatePrivacy, "containsRawPrincipal")
            || truthy(viewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(definitionPrivacy, "containsRawOrganization")
            || truthy(gatePrivacy, "containsRawOrganization")
            || truthy(viewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(definitionPrivacy, "containsRawConversation")
            || truthy(gatePrivacy, "containsRawConversation")
            || truthy(viewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(definitionPrivacy, "containsRawEndpoints")
            || truthy(gatePrivacy, "containsRawEndpoints")
            || truthy(viewPrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(definitionPrivacy, "containsRawReason")
            || truthy(gatePrivacy, "containsRawReason")
            || truthy(viewPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(definitionPrivacy, "containsRawParameterValues")
            || truthy(gatePrivacy, "containsRawParameterValues")
            || truthy(viewPrivacy, "containsRawParameterValues");
        boolean containsRawKubeManagerEndpoints = truthy(viewPrivacy, "containsRawKubeManagerEndpoints");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawEndpoints
            && !containsRawReason
            && !containsRawParameterValues
            && !containsRawKubeManagerEndpoints);
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
