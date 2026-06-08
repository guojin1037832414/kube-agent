package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact CI/release artifact for running a named suite against a named trace set.
 *
 * <p>This artifact intentionally embeds only the compact suite gate artifact,
 * never per-trace reports or replay timelines.</p>
 */
public record AgentEvalTraceSetGateArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String suiteTitle,
    String gateVerdict,
    boolean pass,
    boolean emptyInput,
    List<String> traceIds,
    AgentEvalSuiteGateArtifact suiteGate,
    Map<String, Object> gatePolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-gate.v1";

    public static AgentEvalTraceSetGateArtifact from(AgentEvalTraceSetDefinition traceSet,
                                                     AgentEvalSuiteGateArtifact suiteGate,
                                                     AgentEvalSuiteRequest request,
                                                     String source) {
        Map<String, Object> policy = gatePolicy(traceSet, suiteGate, request, source);
        return new AgentEvalTraceSetGateArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            suiteGate != null ? suiteGate.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            traceSet != null ? traceSet.id() : "",
            traceSet != null ? traceSet.title() : "",
            traceSet != null ? traceSet.suiteId() : "",
            suiteGate != null ? suiteGate.suiteTitle() : "",
            suiteGate != null ? suiteGate.gateVerdict() : "UNKNOWN",
            suiteGate != null && suiteGate.pass(),
            suiteGate != null && suiteGate.emptyInput(),
            suiteGate != null ? List.copyOf(suiteGate.traceIds()) : List.of(),
            suiteGate,
            policy,
            privacyProof(traceSet, suiteGate)
        );
    }

    private static Map<String, Object> gatePolicy(AgentEvalTraceSetDefinition traceSet,
                                                  AgentEvalSuiteGateArtifact suiteGate,
                                                  AgentEvalSuiteRequest request,
                                                  String source) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("source", source != null ? source : "");
        policy.put("artifactOnly", true);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("suiteGateEmbedded", true);
        policy.put("traceSetTraceCount", traceSet != null ? traceSet.traceIds().size() : 0);
        policy.put("failClosedWhenEmpty", true);
        policy.put("traceSetTraceIdsOverridden", false);
        policy.put("requestTraceIdsIgnored", request != null && request.traceIds() != null && !request.traceIds().isEmpty());
        policy.put("requestedLimit", suiteGate != null ? suiteGate.maxResults() : 0);
        policy.put("requestedMinimumScore", suiteGate != null ? suiteGate.requiredMinimumScore() : 0);
        policy.put("failOnWarnings", suiteGate != null && suiteGate.failOnWarnings());
        if (traceSet != null) {
            policy.put("traceSetId", traceSet.id());
            policy.put("suiteId", traceSet.suiteId());
            policy.putAll(traceSet.curationPolicy());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetDefinition traceSet,
                                                    AgentEvalSuiteGateArtifact suiteGate) {
        Map<String, Object> traceSetProof = traceSet != null ? traceSet.guarantees() : Map.of();
        Map<String, Object> suiteProof = suiteGate != null ? suiteGate.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(traceSetProof, "containsRawPrincipal")
            || truthy(suiteProof, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(traceSetProof, "containsRawOrganization")
            || truthy(suiteProof, "containsRawOrganization");
        boolean containsRawConversation = truthy(traceSetProof, "containsRawConversation")
            || truthy(suiteProof, "containsRawConversation");
        boolean containsRawEndpoints = truthy(traceSetProof, "containsRawEndpoints")
            || truthy(suiteProof, "containsRawEndpoints");
        boolean containsRawReason = truthy(traceSetProof, "containsRawReason")
            || truthy(suiteProof, "containsRawReason");
        boolean containsRawParameterValues = truthy(traceSetProof, "containsRawParameterValues")
            || truthy(suiteProof, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(traceSetProof.get("redactedOnly"))
            && Boolean.TRUE.equals(suiteProof.get("redactedOnly"))
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
            && Boolean.TRUE.equals(suiteProof.get("deterministic")));
        proof.put("llmUsed", truthy(traceSetProof, "llmUsed") || truthy(suiteProof, "llmUsed"));
        proof.put("externalCalls", truthy(traceSetProof, "externalCalls") || truthy(suiteProof, "externalCalls"));
        proof.put("toolExecution", truthy(traceSetProof, "toolExecution") || truthy(suiteProof, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(traceSetProof, "kubeManagerCalls") || truthy(suiteProof, "kubeManagerCalls"));
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
