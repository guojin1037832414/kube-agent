package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向 CI / release gate 的紧凑机器可读评测产物。
 *
 * <p>它刻意不嵌入 per-trace report 或 replay timeline，避免自动化日志携带过多诊断细节。
 * 需要排障时再用 admin-only replay/eval 接口按 traceId 下钻。</p>
 */
public record AgentEvalSuiteGateArtifact(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String suiteId,
    String suiteTitle,
    String gateVerdict,
    boolean pass,
    int requiredMinimumScore,
    int observedMinimumScore,
    double observedAverageScore,
    boolean failOnWarnings,
    int maxResults,
    int requestedCases,
    int evaluatedCases,
    int maxCases,
    boolean caseLimitExceeded,
    boolean emptyInput,
    int failedReports,
    int warningReports,
    int failedChecks,
    int warningChecks,
    List<String> traceIds,
    List<String> failedTraceIds,
    List<String> warningTraceIds,
    List<String> skippedTraceIds,
    Map<String, Object> gatePolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-suite-gate.v1";

    public static AgentEvalSuiteGateArtifact from(AgentEvalSuiteRunResponse run) {
        AgentEvalSuiteDefinition definition = run != null ? run.definition() : null;
        AgentEvalSuiteResponse report = run != null ? run.report() : null;
        Map<String, Object> summary = report != null && report.summary() != null
            ? report.summary()
            : Map.of();
        Map<String, Object> gatePolicy = gatePolicy(run, report);
        return new AgentEvalSuiteGateArtifact(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            report != null ? report.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            definition != null ? definition.id() : "",
            definition != null ? definition.title() : "",
            report != null ? report.gateVerdict() : "UNKNOWN",
            report != null && report.pass(),
            report != null ? report.minimumScore() : 0,
            intValue(summary, "minimumScore"),
            doubleValue(summary, "averageScore"),
            report != null && report.failOnWarnings(),
            report != null ? report.maxResults() : 0,
            intValue(summary, "requestedCases"),
            intValue(summary, "evaluatedCases"),
            intValue(summary, "maxCases"),
            booleanValue(summary, "caseLimitExceeded"),
            booleanValue(summary, "emptyInput"),
            intValue(summary, "failedReports"),
            intValue(summary, "warningReports"),
            intValue(summary, "failedChecks"),
            intValue(summary, "warningChecks"),
            report != null ? List.copyOf(report.traceIds()) : List.of(),
            stringList(summary, "failedTraceIds"),
            stringList(summary, "warningTraceIds"),
            stringList(summary, "skippedTraceIds"),
            gatePolicy,
            privacyProof(run, report)
        );
    }

    private static Map<String, Object> gatePolicy(AgentEvalSuiteRunResponse run,
                                                  AgentEvalSuiteResponse report) {
        Map<String, Object> policy = new LinkedHashMap<>();
        if (run != null && run.runPolicy() != null) {
            policy.putAll(run.runPolicy());
        }
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("artifactOnly", true);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        if (report != null) {
            policy.put("gateVerdict", report.gateVerdict());
            policy.put("pass", report.pass());
        }
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalSuiteRunResponse run,
                                                    AgentEvalSuiteResponse report) {
        Map<String, Object> runPrivacy = run != null && run.privacy() != null ? run.privacy() : Map.of();
        Map<String, Object> reportPrivacy = report != null && report.privacy() != null ? report.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(runPrivacy, "containsRawPrincipal")
            || truthy(reportPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(runPrivacy, "containsRawOrganization")
            || truthy(reportPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(runPrivacy, "containsRawConversation")
            || truthy(reportPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(runPrivacy, "containsRawEndpoints")
            || truthy(reportPrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(runPrivacy, "containsRawReason")
            || truthy(reportPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(runPrivacy, "containsRawParameterValues")
            || truthy(reportPrivacy, "containsRawParameterValues");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(runPrivacy.get("redactedOnly"))
            && Boolean.TRUE.equals(reportPrivacy.get("redactedOnly"))
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
        proof.put("deterministic", Boolean.TRUE.equals(runPrivacy.get("deterministic"))
            && Boolean.TRUE.equals(reportPrivacy.get("deterministic")));
        proof.put("llmUsed", truthy(runPrivacy, "llmUsed") || truthy(reportPrivacy, "llmUsed"));
        proof.put("externalCalls", truthy(runPrivacy, "externalCalls") || truthy(reportPrivacy, "externalCalls"));
        proof.put("toolExecution", truthy(runPrivacy, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(runPrivacy, "kubeManagerCalls"));
        return Map.copyOf(proof);
    }

    private static int intValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static double doubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static boolean booleanValue(Map<String, Object> data, String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    private static List<String> stringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
