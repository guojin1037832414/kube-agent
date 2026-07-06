package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reviewed fixture 候选进入人工 Git review 前的人审包。
 *
 * <p>中文说明：M5.85-44 已经能自动发现首个 redacted trace 候选并生成 candidate preview；
 * 这个 response 再往前推进一步，把候选草稿、人工必填字段、复核清单、建议文件名和禁止捷径组织成
 * “人可以带去 Git review 的包”。它仍然不创建 fixture 文件，也不宣称 fixture 已经通过质量门。</p>
 *
 * <p>安全边界：这是 admin-only / read-only / human-review-package-only 读模型。
 * 它不接收 caller traceId，不写 {@code eval-trace-sets.json}，不写 reviewed fixture 目录，
 * 不上传 fixture，不执行 Tool/MCP/LLM/RAG/kube-manager，不写 HITL/audit/memory，
 * 也不授予 CI blocking、release authority 或 Phase 2 运行时权力。</p>
 */
public record AgentReviewedTraceFixtureHumanReviewPackageResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String packageStatus,
    String selectedCandidateTraceId,
    boolean candidateSelected,
    boolean readyForHumanGitReview,
    boolean readyForFixtureCommit,
    String suggestedFixtureFilename,
    String fixtureDirectory,
    Map<String, Object> candidateFixtureDraft,
    List<Map<String, Object>> manualReviewFields,
    List<String> reviewChecklist,
    Map<String, Object> manifestQualityGatePreview,
    AgentReviewedTraceFixtureCandidateWorkbenchResponse candidateWorkbench,
    List<String> blockingReasons,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> packagePolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-human-review-package.v1";
    public static final String ENDPOINT_TEMPLATE =
        "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-package";

    public static AgentReviewedTraceFixtureHumanReviewPackageResponse from(
        AgentReviewedTraceFixtureCandidateWorkbenchResponse workbench) {
        AgentReviewedTraceFixtureCandidateResponse candidatePreview =
            workbench != null ? workbench.candidatePreview() : null;
        boolean readyForHumanGitReview = workbench != null && workbench.readyForHumanGitReview();
        String traceSetId = workbench != null ? safeText(workbench.traceSetId()) : "";
        String selectedTraceId = workbench != null ? safeText(workbench.selectedCandidateTraceId()) : "";
        return new AgentReviewedTraceFixtureHumanReviewPackageResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            traceSetId,
            workbench != null ? safeText(workbench.traceSetTitle()) : "",
            workbench != null ? safeText(workbench.suiteId()) : "",
            packageStatus(workbench, readyForHumanGitReview),
            selectedTraceId,
            workbench != null && workbench.candidateSelected(),
            readyForHumanGitReview,
            false,
            suggestedFixtureFilename(traceSetId),
            AgentReviewedTraceFixtureTemplateResponse.FIXTURE_DIRECTORY,
            candidatePreview != null ? candidatePreview.candidateFixtureDraft() : Map.of(),
            manualReviewFields(candidatePreview),
            reviewChecklist(readyForHumanGitReview),
            manifestQualityGatePreview(traceSetId, candidatePreview),
            workbench,
            workbench != null ? List.copyOf(workbench.blockingReasons()) : List.of("candidate-workbench-unavailable"),
            nextActions(readyForHumanGitReview),
            endpointMap(traceSetId),
            packagePolicy(workbench, readyForHumanGitReview),
            buildSafety(),
            privacy(workbench)
        );
    }

    private static String packageStatus(AgentReviewedTraceFixtureCandidateWorkbenchResponse workbench,
                                        boolean readyForHumanGitReview) {
        if (workbench == null) {
            return "CANDIDATE_WORKBENCH_UNAVAILABLE";
        }
        if (!readyForHumanGitReview) {
            return "HUMAN_REVIEW_PACKAGE_BLOCKED_BY_CANDIDATE_EVIDENCE";
        }
        return "READY_FOR_HUMAN_GIT_REVIEW_PACKAGE";
    }

    private static String suggestedFixtureFilename(String traceSetId) {
        String id = safeText(traceSetId);
        return id.isBlank() ? "" : id + ".reviewed-trace-fixture.json";
    }

    private static List<Map<String, Object>> manualReviewFields(
        AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        List<String> fields = candidatePreview != null
            ? candidatePreview.remainingHumanReviewFields()
            : List.of("sourceCommitSha", "reviewer", "reviewTimestamp", "evidenceDigest");
        return fields.stream()
            .map(field -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", field);
                row.put("required", true);
                row.put("source", "human-git-review");
                row.put("runtimeCallable", false);
                row.put("callerSuppliedAuthorityAccepted", false);
                row.put("status", "MUST_BE_FILLED_OUTSIDE_RUNTIME");
                return Map.copyOf(row);
            })
            .toList();
    }

    private static List<String> reviewChecklist(boolean readyForHumanGitReview) {
        if (!readyForHumanGitReview) {
            return List.of(
                "inspect-candidate-workbench-blockers",
                "capture-real-redacted-audit-evidence-before-fixture-review",
                "rerun-reviewed-fixture-human-review-package",
                "keep-fixture-directory-and-catalog-write-closed"
            );
        }
        return List.of(
            "verify-selected-trace-is-real-redacted-observability-anchor",
            "copy-candidate-fixture-draft-outside-runtime",
            "fill-sourceCommitSha-reviewer-reviewTimestamp-evidenceDigest-in-git-review",
            "verify-redaction-determinism-privacy-and-forbidden-runtime-claims",
            "commit-reviewed-fixture-json-through-human-git-review-only",
            "rerun-reviewed-fixture-manifest-and-catalog-patch-review-after-commit"
        );
    }

    private static Map<String, Object> manifestQualityGatePreview(
        String traceSetId,
        AgentReviewedTraceFixtureCandidateResponse candidatePreview) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("traceSetId", safeText(traceSetId));
        preview.put("candidateStatus", candidatePreview != null ? candidatePreview.candidateStatus() : "");
        preview.put("candidateReadyForHumanGitReview", candidatePreview != null && candidatePreview.readyForHumanGitReview());
        preview.put("candidateReadyForManifestQualityGateNow", false);
        preview.put("readyForFixtureCommit", false);
        preview.put("expectedManifestQualityGateStatusAfterHumanFields", "PASS_IF_ALL_PROOFS_RETAINED_AND_DIGEST_SHA256");
        preview.put("qualityGateStatusGrantedNow", false);
        preview.put("requiredManualFields", candidatePreview != null
            ? candidatePreview.remainingHumanReviewFields()
            : List.of("sourceCommitSha", "reviewer", "reviewTimestamp", "evidenceDigest"));
        preview.put("fixtureRowsEmbedded", false);
        preview.put("catalogMutationAllowed", false);
        preview.put("runtimeCatalogWrite", false);
        return Map.copyOf(preview);
    }

    private static List<String> nextActions(boolean readyForHumanGitReview) {
        if (readyForHumanGitReview) {
            return List.of(
                "open-human-git-review",
                "copy-candidate-fixture-draft-outside-runtime",
                "fill-human-review-fields",
                "compute-final-sha256-evidence-digest",
                "commit-reviewed-fixture-json-through-human-review",
                "rerun-reviewed-fixture-manifest"
            );
        }
        return List.of(
            "inspect-candidate-workbench-blockers",
            "capture-real-redacted-audit-evidence",
            "rerun-reviewed-fixture-candidate-workbench",
            "keep-runtime-fixture-upload-and-catalog-write-disabled"
        );
    }

    private static Map<String, Object> endpointMap(String traceSetId) {
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("humanReviewPackage", ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("candidateWorkbench",
            AgentReviewedTraceFixtureCandidateWorkbenchResponse.ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("candidatePreview",
            AgentReviewedTraceFixtureCandidateResponse.ENDPOINT_TEMPLATE.replace("{traceSetId}", id));
        endpoints.put("fixtureTemplate", AgentReviewedTraceFixtureTemplateResponse.ENDPOINT);
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("fixtureIntakeContract", AgentReviewedTraceFixtureIntakeContractResponse.ENDPOINT);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/catalog-patch-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> packagePolicy(AgentReviewedTraceFixtureCandidateWorkbenchResponse workbench,
                                                     boolean readyForHumanGitReview) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("traceSetId", workbench != null ? workbench.traceSetId() : "");
        policy.put("suiteId", workbench != null ? workbench.suiteId() : "");
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("humanReviewPackageOnly", true);
        policy.put("requestTraceIdsAccepted", false);
        policy.put("requiresHumanGitReviewBeforeCommit", true);
        policy.put("readyForHumanGitReview", readyForHumanGitReview);
        policy.put("readyForFixtureCommit", false);
        policy.put("createsFixtureFile", false);
        policy.put("fixtureUploadAccepted", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("qualityGateStatusGrantedNow", false);
        policy.put("ciBlockingEnabled", false);
        policy.put("releaseAuthority", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("humanReviewPackageOnly", true);
        safety.put("callerTraceIdsAccepted", false);
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

    private static Map<String, Object> privacy(AgentReviewedTraceFixtureCandidateWorkbenchResponse workbench) {
        Map<String, Object> workbenchPrivacy = workbench != null ? workbench.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(workbenchPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(workbenchPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(workbenchPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(workbenchPrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(workbenchPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(workbenchPrivacy, "containsRawParameterValues");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawEndpoints
            && !containsRawReason
            && !containsRawParameterValues);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawEndpoints", containsRawEndpoints);
        privacy.put("containsRawReason", containsRawReason);
        privacy.put("containsRawParameterValues", containsRawParameterValues);
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

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(String value) {
        return value != null ? value.trim() : "";
    }
}
