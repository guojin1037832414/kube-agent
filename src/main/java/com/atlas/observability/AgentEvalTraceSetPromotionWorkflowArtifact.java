package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only orchestration artifact for the eval evidence promotion workflow.
 *
 * <p>This object is designed for a future Vue eval workbench: it shows the
 * operator discovery, curation review, and patch proposal in one response,
 * while preserving Git review as the only catalog promotion authority.</p>
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
