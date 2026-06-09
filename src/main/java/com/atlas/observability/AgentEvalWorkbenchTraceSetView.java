package com.atlas.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frontend-ready trace-set row for the eval workbench overview.
 */
public record AgentEvalWorkbenchTraceSetView(
    String id,
    String title,
    String purpose,
    String phase,
    String suiteId,
    List<String> tags,
    int curatedTraceCount,
    String gateVerdict,
    boolean pass,
    boolean emptyInput,
    boolean readyForCiBlocking,
    String status,
    String nextAction,
    List<String> workflowStages,
    String candidateDiscoveryPath,
    String curationReviewPath,
    String catalogPatchProposalPath,
    String promotionWorkflowPath,
    String gatePath,
    String replayTimelinePathTemplate,
    String evalReportPathTemplate,
    Map<String, Object> policy,
    Map<String, Object> privacy
) {

    public AgentEvalWorkbenchTraceSetView {
        id = safeText(id);
        title = safeText(title);
        purpose = safeText(purpose);
        phase = safeText(phase);
        suiteId = safeText(suiteId);
        tags = tags != null ? List.copyOf(tags) : List.of();
        gateVerdict = safeText(gateVerdict);
        status = safeText(status);
        nextAction = safeText(nextAction);
        workflowStages = workflowStages != null ? List.copyOf(workflowStages) : List.of();
        candidateDiscoveryPath = safeText(candidateDiscoveryPath);
        curationReviewPath = safeText(curationReviewPath);
        catalogPatchProposalPath = safeText(catalogPatchProposalPath);
        promotionWorkflowPath = safeText(promotionWorkflowPath);
        gatePath = safeText(gatePath);
        replayTimelinePathTemplate = safeText(replayTimelinePathTemplate);
        evalReportPathTemplate = safeText(evalReportPathTemplate);
        policy = policy != null ? Map.copyOf(policy) : Map.of();
        privacy = privacy != null ? Map.copyOf(privacy) : Map.of();
    }

    public static AgentEvalWorkbenchTraceSetView from(AgentEvalTraceSetDefinition definition,
                                                      AgentEvalTraceSetGateArtifact gate) {
        String traceSetId = definition != null ? definition.id() : "";
        int curatedTraceCount = definition != null ? definition.traceIds().size() : 0;
        boolean empty = gate == null || gate.emptyInput() || curatedTraceCount == 0;
        boolean pass = gate != null && gate.pass();
        boolean readyForCiBlocking = pass && !empty;
        String status = resolveStatus(empty, pass, gate);
        return new AgentEvalWorkbenchTraceSetView(
            traceSetId,
            definition != null ? definition.title() : "",
            definition != null ? definition.purpose() : "",
            definition != null ? definition.phase() : "",
            definition != null ? definition.suiteId() : "",
            definition != null ? definition.tags() : List.of(),
            curatedTraceCount,
            gate != null ? gate.gateVerdict() : "UNKNOWN",
            pass,
            empty,
            readyForCiBlocking,
            status,
            resolveNextAction(status),
            defaultWorkflowStages(),
            endpoint(traceSetId, "candidates"),
            endpoint(traceSetId, "curation-review"),
            endpoint(traceSetId, "catalog-patch-proposal"),
            endpoint(traceSetId, "promotion-workflow"),
            endpoint(traceSetId, "gate"),
            "/api/agent/observability/replay/trace/{traceId}?limit={limit}",
            "/api/agent/observability/eval/trace/{traceId}?limit={limit}",
            policyProof(definition, gate, readyForCiBlocking),
            privacyProof(definition, gate)
        );
    }

    private static String resolveStatus(boolean empty, boolean pass, AgentEvalTraceSetGateArtifact gate) {
        if (gate != null && "SUITE_RUNTIME_DISABLED".equals(gate.gateVerdict())) {
            return "SUITE_RUNTIME_DISABLED_CATALOG_ONLY";
        }
        if (empty) {
            return "NEEDS_REDACTED_EVIDENCE";
        }
        if (pass) {
            return "GATE_PASS";
        }
        return gate != null ? "GATE_FAIL_REVIEW_REQUIRED" : "GATE_UNKNOWN";
    }

    private static String resolveNextAction(String status) {
        return switch (status) {
            case "SUITE_RUNTIME_DISABLED_CATALOG_ONLY" -> "keep-catalog-only-until-reviewed-runtime-promotion";
            case "NEEDS_REDACTED_EVIDENCE" -> "discover-candidates-and-open-git-reviewed-patch";
            case "GATE_PASS" -> "keep-gate-bundle-current-before-ci-blocking";
            case "GATE_FAIL_REVIEW_REQUIRED" -> "open-replay-and-eval-drill-down";
            default -> "refresh-workbench-overview";
        };
    }

    private static List<String> defaultWorkflowStages() {
        return List.of(
            "candidate-discovery",
            "curation-review",
            "catalog-patch-proposal",
            "human-git-review",
            "gate-bundle",
            "replay-drill-down",
            "eval-report"
        );
    }

    private static String endpoint(String traceSetId, String suffix) {
        String id = safeText(traceSetId);
        if (id.isBlank()) {
            return "";
        }
        return "/api/agent/observability/eval/trace-sets/" + id + "/" + suffix;
    }

    private static Map<String, Object> policyProof(AgentEvalTraceSetDefinition definition,
                                                   AgentEvalTraceSetGateArtifact gate,
                                                   boolean readyForCiBlocking) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", AgentEvalWorkbenchOverviewResponse.SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("overviewOnly", true);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requiresHumanReview", true);
        policy.put("requiresGitReview", true);
        policy.put("readyForCiBlocking", readyForCiBlocking);
        policy.put("ciBlockingEnabled", false);
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
            Map<String, Object> gatePolicy = gate.gatePolicy();
            policy.put("suiteRuntimeDisabled", truthy(gatePolicy, "suiteRuntimeDisabled"));
            policy.put("runtimeExecutionAllowed", truthy(gatePolicy, "runtimeExecutionAllowed"));
            policy.put("retrievalRuntimeAllowed", truthy(gatePolicy, "retrievalRuntimeAllowed"));
            policy.put("traceSetGateRuntimeDisabled", "SUITE_RUNTIME_DISABLED".equals(gate.gateVerdict()));
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetDefinition definition,
                                                    AgentEvalTraceSetGateArtifact gate) {
        Map<String, Object> definitionPrivacy = definition != null ? definition.guarantees() : Map.of();
        Map<String, Object> gatePrivacy = gate != null ? gate.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(definitionPrivacy, "containsRawPrincipal")
            || truthy(gatePrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(definitionPrivacy, "containsRawOrganization")
            || truthy(gatePrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(definitionPrivacy, "containsRawConversation")
            || truthy(gatePrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(definitionPrivacy, "containsRawEndpoints")
            || truthy(gatePrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(definitionPrivacy, "containsRawReason")
            || truthy(gatePrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(definitionPrivacy, "containsRawParameterValues")
            || truthy(gatePrivacy, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawEndpoints
            && !containsRawReason
            && !containsRawParameterValues);
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawConversation", containsRawConversation);
        proof.put("containsRawEndpoints", containsRawEndpoints);
        proof.put("containsRawReason", containsRawReason);
        proof.put("containsRawParameterValues", containsRawParameterValues);
        proof.put("containsRawKubeManagerEndpoints", false);
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
