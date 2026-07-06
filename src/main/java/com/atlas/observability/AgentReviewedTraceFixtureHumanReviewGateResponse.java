package com.atlas.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * reviewed fixture 人工 Git 审阅后的只读门禁校验结果。
 *
 * <p>中文说明：human review package 负责把候选证据整理成人能审的包；这个 response 再往后推进半步，
 * 校验人审字段是否完整、候选摘要是否仍然匹配当前自动候选、最终 {@code evidenceDigest} 是否由这些字段
 * 按固定规则重新计算得到。它把“可以进入人工 Git 提交”的判断做成可回归读模型，但不替人提交文件。</p>
 *
 * <p>安全边界：{@code readyForFixtureCommit=true} 只表示“人工 Git review 可以继续提交 JSON 文件”，
 * 不表示运行时获得写 fixture、写 catalog、开 CI blocking 或 release 的权力。本响应不嵌入 raw replay/report、
 * 不回显任意原始用户输入中的敏感路径，不调用 Tool/MCP/LLM/RAG/kube-manager，也不写 HITL/audit/memory。</p>
 */
public record AgentReviewedTraceFixtureHumanReviewGateResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String gateStatus,
    String selectedCandidateTraceId,
    String requestedCandidateTraceId,
    boolean readyForHumanGitReview,
    boolean readyForFixtureCommit,
    boolean runtimeFixtureCommitAllowed,
    boolean humanFieldsComplete,
    boolean selectedTraceMatchesPackage,
    boolean candidateEvidenceDigestMatches,
    boolean evidenceDigestMatchesExpected,
    String expectedEvidenceDigest,
    List<Map<String, Object>> fieldResults,
    Map<String, Object> manifestQualityGatePreview,
    AgentReviewedTraceFixtureHumanReviewPackageResponse humanReviewPackage,
    List<String> blockingReasons,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> gatePolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-human-review-gate.v1";
    public static final String ENDPOINT_TEMPLATE =
        "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-gate";

    private static final Pattern SOURCE_COMMIT_SHA = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Pattern SHA256_DIGEST = Pattern.compile("^sha256:[0-9a-fA-F]{64}$");
    private static final Pattern REVIEWER = Pattern.compile("^[\\p{Alnum}._@+\\-]{2,120}$");

    public static AgentReviewedTraceFixtureHumanReviewGateResponse from(
        AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage,
        AgentReviewedTraceFixtureHumanReviewGateRequest request) {
        AgentReviewedTraceFixtureHumanReviewGateRequest safeRequest = request != null
            ? request
            : new AgentReviewedTraceFixtureHumanReviewGateRequest(null, null, null, null, null, null);
        String traceSetId = reviewPackage != null ? safeText(reviewPackage.traceSetId()) : "";
        String selectedTraceId = reviewPackage != null ? safeText(reviewPackage.selectedCandidateTraceId()) : "";
        String requestedTraceId = safeText(safeRequest.selectedCandidateTraceId());
        String packageCandidateDigest = packageCandidateEvidenceDigest(reviewPackage);
        String requestCandidateDigest = safeText(safeRequest.candidateEvidenceDigest()).toLowerCase(Locale.ROOT);
        String normalizedCommitSha = safeText(safeRequest.sourceCommitSha()).toLowerCase(Locale.ROOT);
        String normalizedReviewer = safeText(safeRequest.reviewer()).trim();
        String normalizedTimestamp = normalizedInstantOrBlank(safeRequest.reviewTimestamp());
        String expectedDigest = expectedEvidenceDigest(
            traceSetId,
            selectedTraceId,
            normalizedCommitSha,
            normalizedReviewer,
            normalizedTimestamp,
            packageCandidateDigest
        );
        boolean packageReady = reviewPackage != null && reviewPackage.readyForHumanGitReview();
        boolean selectedTraceMatchesPackage = !selectedTraceId.isBlank() && selectedTraceId.equals(requestedTraceId);
        boolean sourceCommitValid = SOURCE_COMMIT_SHA.matcher(normalizedCommitSha).matches();
        boolean reviewerValid = reviewerValid(normalizedReviewer);
        boolean reviewTimestampValid = !normalizedTimestamp.isBlank();
        boolean candidateDigestMatches = !packageCandidateDigest.isBlank()
            && SHA256_DIGEST.matcher(requestCandidateDigest).matches()
            && packageCandidateDigest.equals(requestCandidateDigest);
        boolean evidenceDigestMatches = SHA256_DIGEST.matcher(safeText(safeRequest.evidenceDigest())).matches()
            && expectedDigest.equals(safeText(safeRequest.evidenceDigest()).toLowerCase(Locale.ROOT));
        boolean humanFieldsComplete = sourceCommitValid
            && reviewerValid
            && reviewTimestampValid
            && candidateDigestMatches
            && evidenceDigestMatches;
        boolean redactedOnly = reviewPackage != null && Boolean.TRUE.equals(reviewPackage.privacy().get("redactedOnly"));
        List<String> blockingReasons = blockingReasons(
            packageReady,
            selectedTraceMatchesPackage,
            sourceCommitValid,
            reviewerValid,
            reviewTimestampValid,
            candidateDigestMatches,
            evidenceDigestMatches,
            redactedOnly
        );
        boolean readyForFixtureCommit = blockingReasons.isEmpty();
        return new AgentReviewedTraceFixtureHumanReviewGateResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            traceSetId,
            reviewPackage != null ? safeText(reviewPackage.traceSetTitle()) : "",
            reviewPackage != null ? safeText(reviewPackage.suiteId()) : "",
            gateStatus(reviewPackage, readyForFixtureCommit, blockingReasons),
            selectedTraceId,
            selectedTraceMatchesPackage ? requestedTraceId : redactedMismatchMarker(requestedTraceId),
            packageReady,
            readyForFixtureCommit,
            false,
            humanFieldsComplete,
            selectedTraceMatchesPackage,
            candidateDigestMatches,
            evidenceDigestMatches,
            expectedDigest,
            fieldResults(
                selectedTraceMatchesPackage,
                sourceCommitValid,
                reviewerValid,
                reviewTimestampValid,
                candidateDigestMatches,
                evidenceDigestMatches
            ),
            manifestQualityGatePreview(reviewPackage, readyForFixtureCommit),
            reviewPackage,
            blockingReasons,
            nextActions(readyForFixtureCommit),
            endpointMap(traceSetId),
            gatePolicy(reviewPackage, readyForFixtureCommit),
            buildSafety(),
            privacy(reviewPackage)
        );
    }

    private static String packageCandidateEvidenceDigest(AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage) {
        if (reviewPackage == null || reviewPackage.candidateFixtureDraft() == null) {
            return "";
        }
        Object value = reviewPackage.candidateFixtureDraft().get("candidateEvidenceDigest");
        return safeText(value != null ? value.toString() : "").toLowerCase(Locale.ROOT);
    }

    private static boolean reviewerValid(String reviewer) {
        String value = safeText(reviewer).trim();
        String lower = value.toLowerCase(Locale.ROOT);
        return REVIEWER.matcher(value).matches()
            && !lower.contains("token")
            && !lower.contains("password")
            && !lower.contains("secret")
            && !lower.contains("bearer");
    }

    private static String normalizedInstantOrBlank(String reviewTimestamp) {
        String value = safeText(reviewTimestamp).trim();
        if (value.isBlank()) {
            return "";
        }
        try {
            return Instant.parse(value).toString();
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    private static String expectedEvidenceDigest(String traceSetId,
                                                 String selectedTraceId,
                                                 String sourceCommitSha,
                                                 String reviewer,
                                                 String reviewTimestamp,
                                                 String candidateEvidenceDigest) {
        return "sha256:" + sha256(
            "schema=" + SCHEMA_VERSION
                + "\ntraceSetId=" + safeText(traceSetId)
                + "\nselectedCandidateTraceId=" + safeText(selectedTraceId)
                + "\nsourceCommitSha=" + safeText(sourceCommitSha).toLowerCase(Locale.ROOT)
                + "\nreviewer=" + safeText(reviewer).trim()
                + "\nreviewTimestamp=" + safeText(reviewTimestamp)
                + "\ncandidateEvidenceDigest=" + safeText(candidateEvidenceDigest).toLowerCase(Locale.ROOT)
        );
    }

    private static List<String> blockingReasons(boolean packageReady,
                                                boolean selectedTraceMatchesPackage,
                                                boolean sourceCommitValid,
                                                boolean reviewerValid,
                                                boolean reviewTimestampValid,
                                                boolean candidateDigestMatches,
                                                boolean evidenceDigestMatches,
                                                boolean redactedOnly) {
        List<String> reasons = new ArrayList<>();
        if (!packageReady) {
            reasons.add("human-review-package-not-ready");
        }
        if (!selectedTraceMatchesPackage) {
            reasons.add("selected-candidate-trace-id-mismatch");
        }
        if (!sourceCommitValid) {
            reasons.add("source-commit-sha-missing-or-not-full-40-hex");
        }
        if (!reviewerValid) {
            reasons.add("reviewer-missing-or-unsafe");
        }
        if (!reviewTimestampValid) {
            reasons.add("review-timestamp-missing-or-not-instant");
        }
        if (!candidateDigestMatches) {
            reasons.add("candidate-evidence-digest-mismatch");
        }
        if (!evidenceDigestMatches) {
            reasons.add("final-evidence-digest-missing-or-mismatch");
        }
        if (!redactedOnly) {
            reasons.add("human-review-package-privacy-proof-not-redacted-only");
        }
        return List.copyOf(reasons);
    }

    private static String gateStatus(AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage,
                                     boolean readyForFixtureCommit,
                                     List<String> blockingReasons) {
        if (reviewPackage == null) {
            return "HUMAN_REVIEW_PACKAGE_UNAVAILABLE";
        }
        if (readyForFixtureCommit) {
            return "READY_FOR_MANUAL_GIT_FIXTURE_COMMIT";
        }
        if (blockingReasons != null && blockingReasons.contains("human-review-package-not-ready")) {
            return "BLOCKED_BY_HUMAN_REVIEW_PACKAGE";
        }
        return "HUMAN_REVIEW_GATE_REWORK_REQUIRED";
    }

    private static String redactedMismatchMarker(String requestedTraceId) {
        String value = safeText(requestedTraceId);
        if (value.isBlank()) {
            return "";
        }
        return "REDACTED_MISMATCH";
    }

    private static List<Map<String, Object>> fieldResults(boolean selectedTraceMatchesPackage,
                                                          boolean sourceCommitValid,
                                                          boolean reviewerValid,
                                                          boolean reviewTimestampValid,
                                                          boolean candidateDigestMatches,
                                                          boolean evidenceDigestMatches) {
        return List.of(
            fieldResult("selectedCandidateTraceId", selectedTraceMatchesPackage,
                "must-match-current-human-review-package-candidate"),
            fieldResult("sourceCommitSha", sourceCommitValid, "full-40-hex-git-commit-sha-required"),
            fieldResult("reviewer", reviewerValid, "non-sensitive-reviewer-id-required"),
            fieldResult("reviewTimestamp", reviewTimestampValid, "iso-8601-instant-required"),
            fieldResult("candidateEvidenceDigest", candidateDigestMatches,
                "must-match-candidate-fixture-draft-candidateEvidenceDigest"),
            fieldResult("evidenceDigest", evidenceDigestMatches, "must-match-expected-final-sha256")
        );
    }

    private static Map<String, Object> fieldResult(String name, boolean valid, String rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("required", true);
        result.put("valid", valid);
        result.put("rule", rule);
        result.put("source", "human-git-review");
        result.put("runtimeCallable", false);
        result.put("callerSuppliedAuthorityAccepted", false);
        return Map.copyOf(result);
    }

    private static Map<String, Object> manifestQualityGatePreview(
        AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage,
        boolean readyForFixtureCommit) {
        Map<String, Object> preview = new LinkedHashMap<>();
        Map<String, Object> packagePreview = reviewPackage != null ? reviewPackage.manifestQualityGatePreview() : Map.of();
        preview.put("traceSetId", reviewPackage != null ? safeText(reviewPackage.traceSetId()) : "");
        preview.put("packageStatus", reviewPackage != null ? safeText(reviewPackage.packageStatus()) : "");
        preview.put("packageReadyForHumanGitReview", reviewPackage != null && reviewPackage.readyForHumanGitReview());
        preview.put("humanReviewGateReady", readyForFixtureCommit);
        preview.put("readyForFixtureCommit", readyForFixtureCommit);
        preview.put("manualGitCommitOnly", true);
        preview.put("runtimeFixtureCommitAllowed", false);
        preview.put("qualityGateStatusGrantedNow", false);
        preview.put("manifestRescanRequiredAfterCommit", true);
        preview.put("expectedManifestQualityGateStatusAfterCommit",
            readyForFixtureCommit ? "PASS_AFTER_FILE_COMMIT_AND_MANIFEST_RESCAN" : "BLOCKED_UNTIL_HUMAN_FIELDS_PASS");
        preview.put("packageExpectedManifestQualityGateStatusAfterHumanFields",
            packagePreview.getOrDefault("expectedManifestQualityGateStatusAfterHumanFields", ""));
        preview.put("catalogMutationAllowed", false);
        preview.put("runtimeCatalogWrite", false);
        return Map.copyOf(preview);
    }

    private static List<String> nextActions(boolean readyForFixtureCommit) {
        if (readyForFixtureCommit) {
            return List.of(
                "commit-reviewed-fixture-json-through-human-git-review-only",
                "rerun-reviewed-fixture-manifest",
                "rerun-catalog-patch-review-after-manifest-pass",
                "keep-runtime-fixture-and-catalog-write-disabled"
            );
        }
        return List.of(
            "copy-latest-human-review-package",
            "fix-human-review-gate-fields",
            "recompute-final-sha256-evidence-digest",
            "rerun-reviewed-fixture-human-review-gate",
            "keep-runtime-fixture-and-catalog-write-disabled"
        );
    }

    private static Map<String, Object> endpointMap(String traceSetId) {
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("humanReviewGate", ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("humanReviewPackage",
            AgentReviewedTraceFixtureHumanReviewPackageResponse.ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("candidateWorkbench",
            AgentReviewedTraceFixtureCandidateWorkbenchResponse.ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/catalog-patch-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> gatePolicy(AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage,
                                                  boolean readyForFixtureCommit) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("traceSetId", reviewPackage != null ? safeText(reviewPackage.traceSetId()) : "");
        policy.put("suiteId", reviewPackage != null ? safeText(reviewPackage.suiteId()) : "");
        policy.put("adminOnly", true);
        policy.put("validateOnly", true);
        policy.put("readOnly", true);
        policy.put("acceptsHumanReviewFields", true);
        policy.put("callerTraceIdsAcceptedAsFixtureEvidence", false);
        policy.put("requiresHumanGitReviewBeforeCommit", true);
        policy.put("readyForFixtureCommit", readyForFixtureCommit);
        policy.put("manualGitCommitOnly", true);
        policy.put("runtimeFixtureCommitAllowed", false);
        policy.put("createsFixtureFile", false);
        policy.put("fixtureUploadAccepted", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("qualityGateStatusGrantedNow", false);
        policy.put("manifestRescanRequiredAfterCommit", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("releaseAuthority", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("validateOnly", true);
        safety.put("readOnly", true);
        safety.put("humanReviewGateOnly", true);
        safety.put("callerTraceIdsAcceptedAsFixtureEvidence", false);
        safety.put("createsFixtureFile", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("evalTraceSetsJsonWrite", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("releaseAuthority", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentReviewedTraceFixtureHumanReviewPackageResponse reviewPackage) {
        Map<String, Object> packagePrivacy = reviewPackage != null ? reviewPackage.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", Boolean.TRUE.equals(packagePrivacy.get("redactedOnly")));
        privacy.put("containsRawPrincipal", truthy(packagePrivacy, "containsRawPrincipal"));
        privacy.put("containsRawOrganization", truthy(packagePrivacy, "containsRawOrganization"));
        privacy.put("containsRawConversation", truthy(packagePrivacy, "containsRawConversation"));
        privacy.put("containsRawEndpoints", truthy(packagePrivacy, "containsRawEndpoints"));
        privacy.put("containsRawReason", truthy(packagePrivacy, "containsRawReason"));
        privacy.put("containsRawParameterValues", truthy(packagePrivacy, "containsRawParameterValues"));
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
        return value != null ? value.trim() : "";
    }
}
