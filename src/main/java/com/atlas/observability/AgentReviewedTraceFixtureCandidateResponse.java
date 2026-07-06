package com.atlas.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reviewed redacted trace fixture 的候选证据预检响应。
 *
 * <p>中文说明：这个 record 把某条候选 trace 的 redacted replay / deterministic eval 证据整理成
 * “人审可复制、Git review 可补齐”的 fixture candidate。它故意不宣称 candidate 已经是 reviewed fixture，
 * 因为 source commit、reviewer、reviewTimestamp 和最终 evidenceDigest 必须来自人审/Git 流程。</p>
 *
 * <p>安全边界：response 只返回摘要、digest、计数和布尔证明；不嵌入 replay steps、eval reports、
 * raw fixture rows、raw audit、原始 endpoint、原始 reason 或参数值。它不写文件、不写 catalog、不上传 fixture，
 * 不调用 Tool/MCP/LLM/RAG/kube-manager，不写 audit/memory，也不授予 CI/release 权力。</p>
 */
public record AgentReviewedTraceFixtureCandidateResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String selectedTraceId,
    String candidateStatus,
    boolean readyForHumanGitReview,
    boolean readyForFixtureCommit,
    int acceptedCandidateTraceIdCount,
    int rejectedCandidateTraceIdCount,
    Map<String, Object> replaySource,
    Map<String, Object> redactionProof,
    Map<String, Object> deterministicEvalProof,
    Map<String, Object> privacyProof,
    Map<String, Object> candidateGateSummary,
    Map<String, Object> candidateFixtureDraft,
    List<String> blockingReasons,
    List<String> remainingHumanReviewFields,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-candidate.v1";
    public static final String ENDPOINT_TEMPLATE =
        "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate";

    public static AgentReviewedTraceFixtureCandidateResponse from(AgentEvalTraceSetDefinition definition,
                                                                  String selectedTraceId,
                                                                  int acceptedCandidateTraceIdCount,
                                                                  int rejectedCandidateTraceIdCount,
                                                                  AgentEvalReportResponse report) {
        String traceId = safeText(selectedTraceId);
        AgentReplayTimelineResponse replay = report != null ? report.replay() : null;
        List<String> blockingReasons = blockingReasons(traceId, replay, report);
        boolean readyForHumanGitReview = blockingReasons.isEmpty();
        Map<String, Object> replaySource = replaySource(traceId, replay, readyForHumanGitReview);
        Map<String, Object> redactionProof = redactionProof(replay);
        Map<String, Object> deterministicEvalProof = deterministicEvalProof(report);
        Map<String, Object> privacyProof = privacyProof(report);
        Map<String, Object> candidateGateSummary = candidateGateSummary(definition, report);
        Map<String, Object> candidateFixtureDraft = candidateFixtureDraft(
            definition,
            traceId,
            replaySource,
            redactionProof,
            deterministicEvalProof,
            privacyProof,
            candidateGateSummary
        );
        return new AgentReviewedTraceFixtureCandidateResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            definition.id(),
            definition.title(),
            definition.suiteId(),
            traceId,
            candidateStatus(readyForHumanGitReview, traceId, replay, report),
            readyForHumanGitReview,
            false,
            Math.max(0, acceptedCandidateTraceIdCount),
            Math.max(0, rejectedCandidateTraceIdCount),
            replaySource,
            redactionProof,
            deterministicEvalProof,
            privacyProof,
            candidateGateSummary,
            candidateFixtureDraft,
            blockingReasons,
            buildRemainingHumanReviewFields(),
            nextActions(readyForHumanGitReview),
            endpointMap(definition.id()),
            buildSafety(),
            privacy(report, replay)
        );
    }

    private static List<String> blockingReasons(String traceId,
                                                AgentReplayTimelineResponse replay,
                                                AgentEvalReportResponse report) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (traceId.isBlank()) {
            reasons.add("candidate-trace-id-missing-or-invalid");
        }
        if (replay == null || replay.resultCount() == 0) {
            reasons.add("redacted-replay-timeline-missing");
        }
        if (replay != null && !Boolean.TRUE.equals(replay.privacy().get("redactedOnly"))) {
            reasons.add("redacted-replay-privacy-proof-failed");
        }
        if (report == null || !report.pass()) {
            reasons.add("deterministic-eval-not-passing");
        }
        return List.copyOf(reasons);
    }

    private static String candidateStatus(boolean readyForHumanGitReview,
                                          String traceId,
                                          AgentReplayTimelineResponse replay,
                                          AgentEvalReportResponse report) {
        if (traceId.isBlank()) {
            return "TRACE_ID_REQUIRED_FOR_FIXTURE_CANDIDATE";
        }
        if (replay == null || replay.resultCount() == 0) {
            return "TRACE_REPLAY_EVIDENCE_MISSING";
        }
        if (report == null || !report.pass()) {
            return "TRACE_EVAL_REWORK_REQUIRED";
        }
        return readyForHumanGitReview ? "READY_FOR_HUMAN_FIXTURE_REVIEW" : "FIXTURE_CANDIDATE_REWORK_REQUIRED";
    }

    private static Map<String, Object> replaySource(String traceId,
                                                    AgentReplayTimelineResponse replay,
                                                    boolean readyForHumanGitReview) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "redacted-replay-timeline");
        source.put("digest", replayDigest(traceId, replay));
        source.put("timelineStepCount", replay != null ? replay.resultCount() : 0);
        source.put("redactedOnly", replay != null && Boolean.TRUE.equals(replay.privacy().get("redactedOnly")));
        source.put("embeddedReplay", false);
        source.put("readyForHumanGitReview", readyForHumanGitReview);
        return Map.copyOf(source);
    }

    private static Map<String, Object> redactionProof(AgentReplayTimelineResponse replay) {
        Map<String, Object> proof = new LinkedHashMap<>();
        Map<String, Object> privacy = replay != null ? replay.privacy() : Map.of();
        proof.put("containsRawPrincipal", truthy(privacy, "containsRawPrincipal"));
        proof.put("containsRawOrganization", truthy(privacy, "containsRawOrganization"));
        proof.put("containsRawConversation", truthy(privacy, "containsRawConversation"));
        proof.put("containsRawEndpoints", truthy(privacy, "containsRawEndpoints"));
        proof.put("containsRawReason", truthy(privacy, "containsRawReason"));
        proof.put("containsRawParameterValues", truthy(privacy, "containsRawParameterValues"));
        return Map.copyOf(proof);
    }

    private static Map<String, Object> deterministicEvalProof(AgentEvalReportResponse report) {
        Map<String, Object> proof = new LinkedHashMap<>();
        Map<String, Object> privacy = report != null ? report.privacy() : Map.of();
        proof.put("deterministic", Boolean.TRUE.equals(privacy.get("deterministic")));
        proof.put("llmUsed", truthy(privacy, "llmUsed"));
        proof.put("externalCalls", truthy(privacy, "externalCalls"));
        proof.put("toolExecution", false);
        proof.put("mcpToolCall", false);
        proof.put("kubeManagerCalls", false);
        proof.put("embeddedReports", false);
        return Map.copyOf(proof);
    }

    private static Map<String, Object> privacyProof(AgentEvalReportResponse report) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("containsAuthorizationHeader", false);
        proof.put("containsToken", false);
        proof.put("containsPassword", false);
        proof.put("containsRawPrompt", false);
        proof.put("containsRawDocument", false);
        proof.put("containsRawPrincipal", report != null && truthy(report.privacy(), "containsRawPrincipal"));
        proof.put("containsRawOrganization", report != null && truthy(report.privacy(), "containsRawOrganization"));
        proof.put("containsRawConversation", report != null && truthy(report.privacy(), "containsRawConversation"));
        return Map.copyOf(proof);
    }

    private static Map<String, Object> candidateGateSummary(AgentEvalTraceSetDefinition definition,
                                                            AgentEvalReportResponse report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceSetId", definition.id());
        summary.put("suiteId", definition.suiteId());
        summary.put("evaluationVersion", report != null ? report.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION);
        summary.put("gateVerdict", report != null ? report.verdict() : "TRACE_EVAL_UNAVAILABLE");
        summary.put("pass", report != null && report.pass());
        summary.put("score", report != null ? report.score() : 0);
        summary.put("warningCount", report != null ? countChecks(report, "WARN") : 0L);
        summary.put("failureCount", report != null ? countChecks(report, "FAIL") : 0L);
        summary.put("embeddedReports", false);
        summary.put("embeddedReplay", false);
        return Map.copyOf(summary);
    }

    private static Map<String, Object> candidateFixtureDraft(AgentEvalTraceSetDefinition definition,
                                                             String traceId,
                                                             Map<String, Object> replaySource,
                                                             Map<String, Object> redactionProof,
                                                             Map<String, Object> deterministicEvalProof,
                                                             Map<String, Object> privacyProof,
                                                             Map<String, Object> candidateGateSummary) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("traceId", traceId);
        draft.put("traceSetId", definition.id());
        draft.put("suiteId", definition.suiteId());
        draft.put("replaySource", replaySource);
        draft.put("redactionProof", redactionProof);
        draft.put("deterministicEvalProof", deterministicEvalProof);
        draft.put("privacyProof", privacyProof);
        draft.put("sourceCommitSha", "<fill-during-human-git-review>");
        draft.put("reviewer", "<fill-during-human-git-review>");
        draft.put("reviewTimestamp", "<fill-during-human-git-review>");
        draft.put("evidenceDigest", "<sha256-after-human-review>");
        draft.put("candidateEvidenceDigest", candidateEvidenceDigest(definition.id(), traceId, replaySource, candidateGateSummary));
        draft.put("candidateGateSummary", candidateGateSummary);
        draft.put("forbiddenRuntimeClaims", forbiddenRuntimeClaims());
        draft.put("readyForManifestQualityGateNow", false);
        draft.put("requiresHumanGitReviewBeforeCommit", true);
        return Map.copyOf(draft);
    }

    private static List<String> buildRemainingHumanReviewFields() {
        return List.of(
            "sourceCommitSha",
            "reviewer",
            "reviewTimestamp",
            "evidenceDigest"
        );
    }

    private static List<String> nextActions(boolean readyForHumanGitReview) {
        if (readyForHumanGitReview) {
            return List.of(
                "copy-candidate-draft-outside-runtime",
                "fill-human-git-review-fields",
                "compute-final-fixture-evidence-digest",
                "commit-reviewed-fixture-json-through-human-git-review",
                "rerun-reviewed-fixture-manifest"
            );
        }
        return List.of(
            "inspect-redacted-replay-and-deterministic-eval",
            "collect-a-valid-w3c-trace-id-candidate",
            "rerun-fixture-candidate-preview",
            "keep-runtime-fixture-upload-and-catalog-write-disabled"
        );
    }

    private static Map<String, Object> endpointMap(String traceSetId) {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("fixtureCandidate", ENDPOINT_TEMPLATE.replace("{traceSetId}", traceSetId));
        endpoints.put("fixtureTemplate", AgentReviewedTraceFixtureTemplateResponse.ENDPOINT);
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + traceSetId + "/catalog-patch-review");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("previewOnly", true);
        safety.put("createsFixtureFile", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("callerTraceIdsAcceptedAsFixtureEvidence", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("runtimeEvalAllowed", false);
        safety.put("embeddedReplay", false);
        safety.put("embeddedReports", false);
        safety.put("toolExecution", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("releaseBlockingAllowedNow", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentEvalReportResponse report, AgentReplayTimelineResponse replay) {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", replay != null && Boolean.TRUE.equals(replay.privacy().get("redactedOnly"))
            && report != null && !truthy(report.privacy(), "containsRawPrincipal")
            && !truthy(report.privacy(), "containsRawOrganization")
            && !truthy(report.privacy(), "containsRawConversation")
            && !truthy(report.privacy(), "containsRawEndpoints")
            && !truthy(report.privacy(), "containsRawReason")
            && !truthy(report.privacy(), "containsRawParameterValues"));
        privacy.put("containsRawPrincipal", report != null && truthy(report.privacy(), "containsRawPrincipal"));
        privacy.put("containsRawOrganization", report != null && truthy(report.privacy(), "containsRawOrganization"));
        privacy.put("containsRawConversation", report != null && truthy(report.privacy(), "containsRawConversation"));
        privacy.put("containsRawEndpoints", report != null && truthy(report.privacy(), "containsRawEndpoints"));
        privacy.put("containsRawReason", report != null && truthy(report.privacy(), "containsRawReason"));
        privacy.put("containsRawParameterValues", report != null && truthy(report.privacy(), "containsRawParameterValues"));
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static List<String> forbiddenRuntimeClaims() {
        return List.of(
            "runtimeCatalogWrite:false",
            "catalogMutationAllowed:false",
            "runtimeEvalAllowed:false",
            "ciBlockingEnabled:false",
            "releaseAuthority:false",
            "toolExecution:false",
            "mcpToolCall:false",
            "kubeManagerCalls:false",
            "auditWrite:false",
            "memoryWrite:false",
            "phase2Authority:false"
        );
    }

    private static long countChecks(AgentEvalReportResponse report, String status) {
        return report.checks().stream()
            .filter(check -> status.equals(check.status()))
            .count();
    }

    private static String replayDigest(String traceId, AgentReplayTimelineResponse replay) {
        if (replay == null) {
            return "sha256:" + sha256("missing-replay:" + safeText(traceId));
        }
        StringBuilder builder = new StringBuilder();
        builder.append("trace=").append(safeText(traceId)).append('\n');
        builder.append("steps=").append(replay.resultCount()).append('\n');
        for (AgentReplayTimelineStep step : replay.steps()) {
            builder.append(step.position()).append('|')
                .append(step.phase()).append('|')
                .append(step.status()).append('|')
                .append(step.toolName()).append('|')
                .append(step.source()).append('|')
                .append(step.operationType()).append('|')
                .append(step.httpMethod()).append('|')
                .append(step.executed()).append('|')
                .append(step.success()).append('|')
                .append(step.apiEndpointCount()).append('\n');
        }
        return "sha256:" + sha256(builder.toString());
    }

    private static String candidateEvidenceDigest(String traceSetId,
                                                  String traceId,
                                                  Map<String, Object> replaySource,
                                                  Map<String, Object> candidateGateSummary) {
        return "sha256:" + sha256(
            "traceSet=" + safeText(traceSetId)
                + "\ntrace=" + safeText(traceId)
                + "\nreplayDigest=" + replaySource.get("digest")
                + "\nverdict=" + candidateGateSummary.get("gateVerdict")
                + "\nscore=" + candidateGateSummary.get("score")
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(safeText(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 digest is required by the JDK", impossible);
        }
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
