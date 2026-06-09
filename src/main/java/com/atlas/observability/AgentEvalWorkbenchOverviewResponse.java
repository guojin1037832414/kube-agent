package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only read model that lets Vue render the eval workbench landing view.
 */
public record AgentEvalWorkbenchOverviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    int capabilityCount,
    int traceSetCount,
    int traceSetReadyCount,
    int traceSetNeedsEvidenceCount,
    String gateVerdict,
    boolean releaseEligible,
    List<AgentEvalWorkbenchTraceSetView> traceSets,
    List<String> recommendedWorkflow,
    List<String> nextActions,
    AgentEvalWorkbenchCapabilitiesResponse capabilities,
    AgentEvalTraceSetGateBundleArtifact gateBundle,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-overview.v1";

    public static AgentEvalWorkbenchOverviewResponse of(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                        AgentEvalTraceSetGateBundleArtifact gateBundle,
                                                        List<AgentEvalWorkbenchTraceSetView> traceSets) {
        List<AgentEvalWorkbenchTraceSetView> safeTraceSets = traceSets != null
            ? List.copyOf(traceSets)
            : List.of();
        int readyCount = (int) safeTraceSets.stream()
            .filter(AgentEvalWorkbenchTraceSetView::readyForCiBlocking)
            .count();
        int needsEvidenceCount = (int) safeTraceSets.stream()
            .filter(AgentEvalWorkbenchOverviewResponse::needsReviewedEvidence)
            .count();
        return new AgentEvalWorkbenchOverviewResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            capabilities != null ? capabilities.capabilityCount() : 0,
            safeTraceSets.size(),
            readyCount,
            needsEvidenceCount,
            gateBundle != null ? gateBundle.gateVerdict() : "UNKNOWN",
            gateBundle != null && gateBundle.releaseEligible(),
            safeTraceSets,
            capabilities != null ? List.copyOf(capabilities.recommendedWorkflow()) : List.of(),
            buildNextActions(readyCount, needsEvidenceCount, gateBundle),
            capabilities,
            gateBundle,
            buildWorkbenchPolicy(capabilities, gateBundle, safeTraceSets, readyCount, needsEvidenceCount),
            buildPrivacyProof(capabilities, gateBundle, safeTraceSets)
        );
    }

    private static List<String> buildNextActions(int readyCount,
                                                 int needsEvidenceCount,
                                                 AgentEvalTraceSetGateBundleArtifact gateBundle) {
        List<String> actions = new ArrayList<>();
        if (needsEvidenceCount > 0) {
            actions.add("inspect-reviewed-trace-evidence-readiness");
            actions.add("discover-redacted-candidates");
            actions.add("promote-candidates-through-git-review");
        }
        if (gateBundle == null || !gateBundle.releaseEligible()) {
            actions.add("regenerate-gate-bundle-after-curation");
        }
        if (readyCount > 0) {
            actions.add("inspect-passing-traces-before-ci-blocking");
        }
        actions.add("use-replay-and-eval-drill-down-for-failures");
        return List.copyOf(actions);
    }

    private static boolean needsReviewedEvidence(AgentEvalWorkbenchTraceSetView traceSet) {
        return traceSet != null && (
            "NEEDS_REDACTED_EVIDENCE".equals(traceSet.status())
                || "SUITE_RUNTIME_DISABLED_CATALOG_ONLY".equals(traceSet.status())
        );
    }

    private static Map<String, Object> buildWorkbenchPolicy(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                            AgentEvalTraceSetGateBundleArtifact gateBundle,
                                                            List<AgentEvalWorkbenchTraceSetView> traceSets,
                                                            int readyCount,
                                                            int needsEvidenceCount) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("overviewOnly", true);
        policy.put("readOnly", true);
        policy.put("capabilitiesEmbedded", capabilities != null);
        policy.put("gateBundleEmbedded", gateBundle != null);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("releaseBlockingAfterReviewedEvidenceOnly", true);
        policy.put("traceSetCount", traceSets.size());
        policy.put("traceSetReadyCount", readyCount);
        policy.put("traceSetNeedsEvidenceCount", needsEvidenceCount);
        policy.put("ciBlockingEnabled", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildPrivacyProof(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                         AgentEvalTraceSetGateBundleArtifact gateBundle,
                                                         List<AgentEvalWorkbenchTraceSetView> traceSets) {
        Map<String, Object> capabilityPrivacy = capabilities != null ? capabilities.privacy() : Map.of();
        Map<String, Object> bundlePrivacy = gateBundle != null ? gateBundle.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(capabilityPrivacy, "containsRawPrincipal")
            || truthy(bundlePrivacy, "containsRawPrincipal")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawPrincipal"));
        boolean containsRawOrganization = truthy(capabilityPrivacy, "containsRawOrganization")
            || truthy(bundlePrivacy, "containsRawOrganization")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawOrganization"));
        boolean containsRawConversation = truthy(capabilityPrivacy, "containsRawConversation")
            || truthy(bundlePrivacy, "containsRawConversation")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawConversation"));
        boolean containsRawEndpoints = truthy(capabilityPrivacy, "containsRawEndpoints")
            || truthy(bundlePrivacy, "containsRawEndpoints")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawEndpoints"));
        boolean containsRawReason = truthy(capabilityPrivacy, "containsRawReason")
            || truthy(bundlePrivacy, "containsRawReason")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawReason"));
        boolean containsRawParameterValues = truthy(capabilityPrivacy, "containsRawParameterValues")
            || truthy(bundlePrivacy, "containsRawParameterValues")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawParameterValues"));
        boolean containsRawKubeManagerEndpoints = truthy(capabilityPrivacy, "containsRawKubeManagerEndpoints")
            || truthy(bundlePrivacy, "containsRawKubeManagerEndpoints")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawKubeManagerEndpoints"));
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
}
