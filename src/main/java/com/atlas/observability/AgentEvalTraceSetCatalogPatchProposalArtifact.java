package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Review-only JSON Patch proposal for promoting curated trace anchors.
 *
 * <p>The artifact is intentionally deterministic and non-mutating. It can be
 * attached to a Git review, but runtime code must not write the classpath
 * catalog directly.</p>
 */
public record AgentEvalTraceSetCatalogPatchProposalArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String source,
    String targetResource,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String proposalVerdict,
    boolean readyForGitReview,
    boolean catalogMutated,
    int traceSetIndex,
    int originalTraceSetTraceCount,
    int candidateTraceCount,
    int addedTraceCount,
    List<String> originalTraceIds,
    List<String> candidateTraceIds,
    List<String> addedTraceIds,
    List<String> proposedTraceIds,
    List<Map<String, Object>> jsonPatch,
    AgentEvalTraceSetCurationReviewArtifact curationReview,
    Map<String, Object> proposalPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-catalog-patch-proposal.v1";

    public static AgentEvalTraceSetCatalogPatchProposalArtifact from(AgentEvalTraceSetDefinition traceSet,
                                                                     int traceSetIndex,
                                                                     AgentEvalTraceSetCurationReviewArtifact review,
                                                                     String source,
                                                                     String targetResource) {
        List<String> originalTraceIds = traceSet != null ? List.copyOf(traceSet.traceIds()) : List.of();
        List<String> candidateTraceIds = review != null ? List.copyOf(review.candidateTraceIds()) : List.of();
        LinkedHashSet<String> proposed = new LinkedHashSet<>(originalTraceIds);
        List<String> added = new ArrayList<>();
        for (String candidate : candidateTraceIds) {
            if (proposed.add(candidate)) {
                added.add(candidate);
            }
        }
        boolean reviewReady = review != null && review.readyForCatalogReview();
        boolean hasNewTraceIds = !added.isEmpty();
        boolean ready = reviewReady && hasNewTraceIds && traceSetIndex >= 0;
        List<String> proposedTraceIds = List.copyOf(proposed);
        return new AgentEvalTraceSetCatalogPatchProposalArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            review != null ? review.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            source != null ? source : "",
            targetResource != null ? targetResource : "",
            traceSet != null ? traceSet.id() : "",
            traceSet != null ? traceSet.title() : "",
            traceSet != null ? traceSet.suiteId() : "",
            verdict(review, ready, hasNewTraceIds, traceSetIndex),
            ready,
            false,
            traceSetIndex,
            originalTraceIds.size(),
            candidateTraceIds.size(),
            added.size(),
            originalTraceIds,
            candidateTraceIds,
            List.copyOf(added),
            proposedTraceIds,
            ready ? jsonPatch(traceSetIndex, proposedTraceIds) : List.of(),
            review,
            proposalPolicy(traceSet, review, source, targetResource, traceSetIndex, ready, hasNewTraceIds),
            privacyProof(traceSet, review)
        );
    }

    private static String verdict(AgentEvalTraceSetCurationReviewArtifact review,
                                  boolean ready,
                                  boolean hasNewTraceIds,
                                  int traceSetIndex) {
        if (ready) {
            return "READY_FOR_GIT_REVIEW";
        }
        if (traceSetIndex < 0) {
            return "REJECT_TRACE_SET_NOT_FOUND";
        }
        if (review == null) {
            return "REJECT_REVIEW_UNAVAILABLE";
        }
        if (!review.readyForCatalogReview()) {
            return review.reviewVerdict();
        }
        if (!hasNewTraceIds) {
            return "NO_NEW_TRACE_IDS";
        }
        return "REJECT_NOT_READY";
    }

    private static List<Map<String, Object>> jsonPatch(int traceSetIndex, List<String> proposedTraceIds) {
        Map<String, Object> replaceTraceIds = new LinkedHashMap<>();
        replaceTraceIds.put("op", "replace");
        replaceTraceIds.put("path", "/" + traceSetIndex + "/traceIds");
        replaceTraceIds.put("value", proposedTraceIds);
        return List.of(Map.copyOf(replaceTraceIds));
    }

    private static Map<String, Object> proposalPolicy(AgentEvalTraceSetDefinition traceSet,
                                                      AgentEvalTraceSetCurationReviewArtifact review,
                                                      String source,
                                                      String targetResource,
                                                      int traceSetIndex,
                                                      boolean ready,
                                                      boolean hasNewTraceIds) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("source", source != null ? source : "");
        policy.put("targetResource", targetResource != null ? targetResource : "");
        policy.put("artifactOnly", true);
        policy.put("reviewOnly", true);
        policy.put("patchFormat", "RFC6902 JSON Patch");
        policy.put("jsonPointerPath", traceSetIndex >= 0 ? "/" + traceSetIndex + "/traceIds" : "");
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("requiresCurationReview", true);
        policy.put("requiresHumanReview", true);
        policy.put("requiresGitReview", true);
        policy.put("requiresCiGateBundleRegeneration", ready);
        policy.put("readyForGitReview", ready);
        policy.put("hasNewTraceIds", hasNewTraceIds);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("suiteGateEmbedded", true);
        policy.put("candidateTraceIdsPromotedToCatalog", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("releaseBlockingAfterMergeOnly", true);
        if (review != null) {
            policy.put("reviewVerdict", review.reviewVerdict());
            policy.put("readyForCatalogReview", review.readyForCatalogReview());
        }
        if (traceSet != null) {
            policy.put("traceSetId", traceSet.id());
            policy.put("suiteId", traceSet.suiteId());
            policy.put("originalTraceSetTraceCount", traceSet.traceIds().size());
            policy.putAll(traceSet.curationPolicy());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetDefinition traceSet,
                                                    AgentEvalTraceSetCurationReviewArtifact review) {
        Map<String, Object> traceSetProof = traceSet != null ? traceSet.guarantees() : Map.of();
        Map<String, Object> reviewProof = review != null ? review.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(traceSetProof, "containsRawPrincipal")
            || truthy(reviewProof, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(traceSetProof, "containsRawOrganization")
            || truthy(reviewProof, "containsRawOrganization");
        boolean containsRawConversation = truthy(traceSetProof, "containsRawConversation")
            || truthy(reviewProof, "containsRawConversation");
        boolean containsRawEndpoints = truthy(traceSetProof, "containsRawEndpoints")
            || truthy(reviewProof, "containsRawEndpoints");
        boolean containsRawReason = truthy(traceSetProof, "containsRawReason")
            || truthy(reviewProof, "containsRawReason");
        boolean containsRawParameterValues = truthy(traceSetProof, "containsRawParameterValues")
            || truthy(reviewProof, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(traceSetProof.get("redactedOnly"))
            && Boolean.TRUE.equals(reviewProof.get("redactedOnly"))
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
        proof.put("deterministic", Boolean.TRUE.equals(traceSetProof.get("deterministic"))
            && Boolean.TRUE.equals(reviewProof.get("deterministic")));
        proof.put("llmUsed", truthy(traceSetProof, "llmUsed") || truthy(reviewProof, "llmUsed"));
        proof.put("externalCalls", truthy(traceSetProof, "externalCalls") || truthy(reviewProof, "externalCalls"));
        proof.put("toolExecution", truthy(traceSetProof, "toolExecution") || truthy(reviewProof, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(traceSetProof, "kubeManagerCalls") || truthy(reviewProof, "kubeManagerCalls"));
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
