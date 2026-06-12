package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review artifact for promoting candidate trace anchors into a versioned trace set.
 *
 * <p>This is intentionally review-only. It evaluates candidate trace IDs against
 * the trace set's attached suite, but it never mutates the classpath catalog.</p>
 *
 * <p>中文说明：这是“候选 traceId 能否进入目录”的审阅产物。输入来自管理员提交的候选 traceIds，
 * 先用 attached suite 做 deterministic gate，再输出 READY / REJECT 的审阅结论给前端和 Git review。</p>
 *
 * <p>安全边界：本 artifact 是 review-only，不修改 {@code eval-trace-sets.json}，不把候选 traceId
 * 直接提升成 release gate，不执行 Tool/MCP/LLM/RAG/kube-manager，不写 audit/memory。只有通过人审、
 * Git diff、CI gate bundle 和恢复记忆后，traceIds 才能成为版本化 evidence anchor。</p>
 */
public record AgentEvalTraceSetCurationReviewArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String source,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String reviewVerdict,
    boolean readyForCatalogReview,
    boolean catalogMutated,
    boolean emptyCandidates,
    int originalTraceSetTraceCount,
    int candidateTraceCount,
    List<String> candidateTraceIds,
    AgentEvalSuiteGateArtifact candidateGate,
    Map<String, Object> curationPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-curation-review.v1";

    public static AgentEvalTraceSetCurationReviewArtifact from(AgentEvalTraceSetDefinition traceSet,
                                                               AgentEvalSuiteGateArtifact candidateGate,
                                                               String source) {
        List<String> candidateTraceIds = candidateGate != null
            ? List.copyOf(candidateGate.traceIds())
            : List.of();
        boolean empty = candidateGate == null || candidateGate.emptyInput() || candidateTraceIds.isEmpty();
        boolean ready = candidateGate != null && candidateGate.pass() && !empty;
        return new AgentEvalTraceSetCurationReviewArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            candidateGate != null ? candidateGate.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            source != null ? source : "",
            traceSet != null ? traceSet.id() : "",
            traceSet != null ? traceSet.title() : "",
            traceSet != null ? traceSet.suiteId() : "",
            verdict(candidateGate, empty),
            ready,
            false,
            empty,
            traceSet != null ? traceSet.traceIds().size() : 0,
            candidateTraceIds.size(),
            candidateTraceIds,
            candidateGate,
            curationPolicy(traceSet, candidateGate, source, ready),
            privacyProof(traceSet, candidateGate)
        );
    }

    private static String verdict(AgentEvalSuiteGateArtifact candidateGate, boolean empty) {
        if (candidateGate == null) {
            return "UNKNOWN_SUITE";
        }
        if (empty) {
            return "REJECT_EMPTY_CANDIDATES";
        }
        if (!candidateGate.pass()) {
            return "REJECT_EVAL_GATE_FAILED";
        }
        return "READY_FOR_CATALOG_REVIEW";
    }

    private static Map<String, Object> curationPolicy(AgentEvalTraceSetDefinition traceSet,
                                                      AgentEvalSuiteGateArtifact candidateGate,
                                                      String source,
                                                      boolean ready) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("source", source != null ? source : "");
        policy.put("artifactOnly", true);
        policy.put("reviewOnly", true);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("candidateTraceIdsUsedForReview", true);
        policy.put("candidateTraceIdsPromotedToCatalog", false);
        policy.put("requestTraceIdOverrideAllowedForPublishedGate", false);
        policy.put("requiresHumanReview", true);
        policy.put("requiresGitReview", true);
        policy.put("requiresPersistedRedactedReplayEvidence", true);
        policy.put("requiresCiGateBundleRegeneration", ready);
        policy.put("nextCatalogPatchField", "traceIds");
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("suiteGateEmbedded", true);
        policy.put("readyForCatalogReview", ready);
        policy.put("candidateTraceCount", candidateGate != null ? candidateGate.traceIds().size() : 0);
        if (traceSet != null) {
            policy.put("traceSetId", traceSet.id());
            policy.put("suiteId", traceSet.suiteId());
            policy.put("originalTraceSetTraceCount", traceSet.traceIds().size());
            policy.putAll(traceSet.curationPolicy());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetDefinition traceSet,
                                                    AgentEvalSuiteGateArtifact candidateGate) {
        Map<String, Object> traceSetProof = traceSet != null ? traceSet.guarantees() : Map.of();
        Map<String, Object> gateProof = candidateGate != null ? candidateGate.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(traceSetProof, "containsRawPrincipal")
            || truthy(gateProof, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(traceSetProof, "containsRawOrganization")
            || truthy(gateProof, "containsRawOrganization");
        boolean containsRawConversation = truthy(traceSetProof, "containsRawConversation")
            || truthy(gateProof, "containsRawConversation");
        boolean containsRawEndpoints = truthy(traceSetProof, "containsRawEndpoints")
            || truthy(gateProof, "containsRawEndpoints");
        boolean containsRawReason = truthy(traceSetProof, "containsRawReason")
            || truthy(gateProof, "containsRawReason");
        boolean containsRawParameterValues = truthy(traceSetProof, "containsRawParameterValues")
            || truthy(gateProof, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(traceSetProof.get("redactedOnly"))
            && Boolean.TRUE.equals(gateProof.get("redactedOnly"))
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
            && Boolean.TRUE.equals(gateProof.get("deterministic")));
        proof.put("llmUsed", truthy(traceSetProof, "llmUsed") || truthy(gateProof, "llmUsed"));
        proof.put("externalCalls", truthy(traceSetProof, "externalCalls") || truthy(gateProof, "externalCalls"));
        proof.put("toolExecution", truthy(traceSetProof, "toolExecution") || truthy(gateProof, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(traceSetProof, "kubeManagerCalls") || truthy(gateProof, "kubeManagerCalls"));
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
