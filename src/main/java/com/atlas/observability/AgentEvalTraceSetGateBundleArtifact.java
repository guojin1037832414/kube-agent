package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable CI artifact for the whole versioned trace-set catalog.
 *
 * <p>The bundle is intentionally compact: it embeds trace-set gate artifacts,
 * but still avoids per-trace reports and replay timelines.</p>
 */
public record AgentEvalTraceSetGateBundleArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String source,
    String gateVerdict,
    boolean pass,
    boolean releaseEligible,
    int traceSetCount,
    int passedTraceSets,
    int failedTraceSets,
    int emptyTraceSets,
    List<String> traceSetIds,
    List<String> failedTraceSetIds,
    List<String> emptyTraceSetIds,
    List<AgentEvalTraceSetGateArtifact> traceSetGates,
    Map<String, Object> bundlePolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-gate-bundle.v1";

    public static AgentEvalTraceSetGateBundleArtifact of(String source,
                                                         List<AgentEvalTraceSetGateArtifact> traceSetGates) {
        List<AgentEvalTraceSetGateArtifact> safeGates = traceSetGates != null
            ? List.copyOf(traceSetGates)
            : List.of();
        List<String> traceSetIds = safeGates.stream()
            .map(AgentEvalTraceSetGateArtifact::traceSetId)
            .toList();
        List<String> failedTraceSetIds = safeGates.stream()
            .filter(gate -> !gate.pass())
            .map(AgentEvalTraceSetGateArtifact::traceSetId)
            .toList();
        List<String> emptyTraceSetIds = safeGates.stream()
            .filter(AgentEvalTraceSetGateArtifact::emptyInput)
            .map(AgentEvalTraceSetGateArtifact::traceSetId)
            .toList();
        int passedTraceSets = safeGates.size() - failedTraceSetIds.size();
        boolean pass = !safeGates.isEmpty() && failedTraceSetIds.isEmpty() && emptyTraceSetIds.isEmpty();
        return new AgentEvalTraceSetGateBundleArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            source != null ? source : "",
            pass ? "PASS" : "FAIL",
            pass,
            pass,
            safeGates.size(),
            passedTraceSets,
            failedTraceSetIds.size(),
            emptyTraceSetIds.size(),
            traceSetIds,
            failedTraceSetIds,
            emptyTraceSetIds,
            safeGates,
            bundlePolicy(source, safeGates, pass),
            privacyProof(safeGates)
        );
    }

    private static Map<String, Object> bundlePolicy(String source,
                                                    List<AgentEvalTraceSetGateArtifact> traceSetGates,
                                                    boolean pass) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("source", source != null ? source : "");
        policy.put("artifactOnly", true);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("ciArtifactPath", "target/agent-eval/trace-set-gate-bundle.json");
        policy.put("ciBlockingEnabled", false);
        policy.put("ciBlockingEnablementCondition", "Enable only after curated traceIds are populated with real redacted replay captures.");
        policy.put("releaseEligible", pass);
        policy.put("failClosedWhenEmpty", true);
        policy.put("requestTraceIdOverrideAllowed", false);
        policy.put("traceSetCount", traceSetGates.size());
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(List<AgentEvalTraceSetGateArtifact> traceSetGates) {
        boolean containsRawPrincipal = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawPrincipal"));
        boolean containsRawOrganization = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawOrganization"));
        boolean containsRawConversation = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawConversation"));
        boolean containsRawEndpoints = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawEndpoints"));
        boolean containsRawReason = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawReason"));
        boolean containsRawParameterValues = traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "containsRawParameterValues"));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", traceSetGates.stream().allMatch(gate -> truthy(gate.privacy(), "redactedOnly"))
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
        proof.put("deterministic", traceSetGates.stream().allMatch(gate -> truthy(gate.privacy(), "deterministic")));
        proof.put("llmUsed", traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "llmUsed")));
        proof.put("externalCalls", traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "externalCalls")));
        proof.put("toolExecution", traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "toolExecution")));
        proof.put("kubeManagerCalls", traceSetGates.stream().anyMatch(gate -> truthy(gate.privacy(), "kubeManagerCalls")));
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
