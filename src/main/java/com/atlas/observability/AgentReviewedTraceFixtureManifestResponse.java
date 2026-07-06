package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reviewed redacted trace fixture repository manifest.
 *
 * <p>中文说明：这个 record 是 fixture intake 合同之后的下一层只读证据。
 * 它不接收运行时上传，而是扫描 classpath 中约定的 reviewed fixture JSON 目录，
 * 汇总“哪些 trace set 已经有可进入人审/Git review 的 fixture 文件，哪些还缺文件”。</p>
 *
 * <p>安全边界：manifest 只是 repo-native / classpath-native read model，不写
 * {@code eval-trace-sets.json}，不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不创建 HITL/audit/memory，也不启用 CI blocking 或 release authority。</p>
 */
public record AgentReviewedTraceFixtureManifestResponse(
    String schemaVersion,
    Instant generatedAt,
    String manifestStatus,
    String target,
    String fixtureResourcePattern,
    boolean phase1TopTierGoalPreserved,
    boolean runtimeIntakeAllowedNow,
    boolean fixtureUploadAccepted,
    boolean callerTraceIdsAccepted,
    boolean runtimeCatalogWrite,
    boolean catalogMutationAllowed,
    boolean releaseBlockingAllowedNow,
    boolean ciBlockingEnabled,
    boolean runtimeEvalAllowed,
    int traceSetCount,
    int fixtureFileCount,
    int matchedFixtureTraceSetCount,
    int missingFixtureTraceSetCount,
    List<Map<String, Object>> fixtureRows,
    List<Map<String, Object>> traceSetCoverage,
    List<Map<String, Object>> requiredFixtureFields,
    List<String> forbiddenShortcuts,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-manifest.v1";
    public static final String ENDPOINT =
        "/api/agent/observability/eval/reviewed-trace-fixture-manifest";

    public static AgentReviewedTraceFixtureManifestResponse of(Instant generatedAt,
                                                               AgentEvalTraceSetCatalogResponse catalog,
                                                               List<Map<String, Object>> fixturePayloads,
                                                               String fixtureResourcePattern) {
        List<AgentEvalTraceSetDefinition> traceSets = catalog != null ? catalog.traceSets() : List.of();
        Set<String> catalogTraceSetIds = traceSets.stream()
            .map(AgentEvalTraceSetDefinition::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> fixtureRows = fixturePayloads != null
            ? fixturePayloads.stream()
            .map(payload -> fixtureRow(payload, catalogTraceSetIds))
            .toList()
            : List.of();
        Set<String> readyTraceSetIds = fixtureRows.stream()
            .filter(row -> "READY_FOR_HUMAN_GIT_REVIEW".equals(row.get("status")))
            .map(row -> safeText(row.get("traceSetId")))
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> coverage = traceSets.stream()
            .map(traceSet -> traceSetCoverage(traceSet, readyTraceSetIds))
            .toList();
        int matched = readyTraceSetIds.size();
        int missing = Math.max(0, traceSets.size() - matched);
        String status = manifestStatus(fixtureRows, matched, missing);
        return new AgentReviewedTraceFixtureManifestResponse(
            SCHEMA_VERSION,
            generatedAt,
            status,
            "Reviewed redacted trace fixture manifest before catalog patch review",
            fixtureResourcePattern,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            traceSets.size(),
            fixtureRows.size(),
            matched,
            missing,
            fixtureRows,
            coverage,
            buildRequiredFixtureFields(),
            buildForbiddenShortcuts(),
            nextActions(missing),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy(fixtureRows, catalog)
        );
    }

    private static Map<String, Object> fixtureRow(Map<String, Object> payload, Set<String> catalogTraceSetIds) {
        Map<String, Object> row = new LinkedHashMap<>();
        String traceSetId = safeText(payload.get("traceSetId"));
        String traceId = safeText(payload.get("traceId"));
        String parseError = safeText(payload.get("parseError"));
        boolean knownTraceSet = catalogTraceSetIds.contains(traceSetId);
        boolean traceIdValid = !AgentTraceContext.safeCandidateOrBlank(traceId).isBlank()
            && !AgentTraceContext.w3cTraceIdOrBlank(traceId).isBlank();
        List<String> missingFields = requiredFieldNames().stream()
            .filter(field -> safeText(payload.get(field)).isBlank() && !presentStructured(payload.get(field)))
            .toList();
        Map<String, Object> replaySource = objectMap(payload.get("replaySource"));
        Map<String, Object> redactionProof = objectMap(payload.get("redactionProof"));
        Map<String, Object> deterministicEvalProof = objectMap(payload.get("deterministicEvalProof"));
        Map<String, Object> privacyProof = objectMap(payload.get("privacyProof"));
        List<String> failedQualityGates = failedQualityGates(
            payload,
            replaySource,
            redactionProof,
            deterministicEvalProof,
            privacyProof
        );
        boolean replaySourceRedacted = failedQualityGates.stream()
            .noneMatch(gate -> gate.startsWith("replay-source-"));
        boolean redactionProofComplete = failedQualityGates.stream()
            .noneMatch(gate -> gate.startsWith("redaction-proof-"));
        boolean deterministicEvalProofComplete = failedQualityGates.stream()
            .noneMatch(gate -> gate.startsWith("deterministic-eval-proof-"));
        boolean privacyProofComplete = failedQualityGates.stream()
            .noneMatch(gate -> gate.startsWith("privacy-proof-"));
        boolean forbiddenRuntimeClaimsClosed = failedQualityGates.stream()
            .noneMatch(gate -> gate.startsWith("forbidden-runtime-claims-"));
        boolean privacySafe = redactionProofComplete && privacyProofComplete;
        boolean ready = parseError.isBlank()
            && knownTraceSet
            && traceIdValid
            && missingFields.isEmpty()
            && failedQualityGates.isEmpty();

        row.put("fixtureResource", safeText(payload.get("fixtureResource")));
        row.put("traceSetId", traceSetId);
        row.put("traceId", traceId);
        row.put("suiteId", safeText(payload.get("suiteId")));
        row.put("status", ready ? "READY_FOR_HUMAN_GIT_REVIEW" : "FIXTURE_NEEDS_REVIEW_REWORK");
        row.put("knownTraceSet", knownTraceSet);
        row.put("traceIdValid", traceIdValid);
        row.put("missingRequiredFields", missingFields);
        row.put("parseErrorPresent", !parseError.isBlank());
        row.put("qualityGateStatus", failedQualityGates.isEmpty() ? "PASS" : "FAIL");
        row.put("failedQualityGates", failedQualityGates);
        row.put("replaySourceRedacted", replaySourceRedacted);
        row.put("redactionProofComplete", redactionProofComplete);
        row.put("deterministicEvalProofComplete", deterministicEvalProofComplete);
        row.put("privacyProofComplete", privacyProofComplete);
        row.put("forbiddenRuntimeClaimsClosed", forbiddenRuntimeClaimsClosed);
        row.put("redactedOnly", privacySafe);
        row.put("runtimeCatalogWrite", false);
        row.put("catalogMutated", false);
        row.put("runtimeEvalAllowed", false);
        row.put("releaseBlockingAllowedNow", false);
        return Map.copyOf(row);
    }

    private static String manifestStatus(List<Map<String, Object>> fixtureRows, int matched, int missing) {
        if (fixtureRows.isEmpty()) {
            return "NO_REVIEWED_FIXTURE_FILES_FOUND";
        }
        if (matched == 0 && missing > 0) {
            return "REVIEWED_FIXTURES_PRESENT_BUT_NOT_READY";
        }
        return missing == 0 ? "REVIEWED_FIXTURES_READY_FOR_CATALOG_PATCH_REVIEW" : "REVIEWED_FIXTURES_PARTIAL";
    }

    /**
     * 校验 reviewed fixture 的结构化证明块。
     *
     * <p>中文说明：fixture 文件来自 repo/classpath，不来自运行时请求；但只要它将来能进入
     * catalog patch review，就必须先显式证明自己是 redacted replay、确定性 eval 证据，并且没有
     * 打开 Tool/MCP/kube-manager/CI/release 等运行时权力。本方法只返回失败原因，不修正 payload、
     * 不读取 raw audit、不执行 replay/eval，也不把 traceId 写回 catalog。</p>
     */
    private static List<String> failedQualityGates(Map<String, Object> payload,
                                                   Map<String, Object> replaySource,
                                                   Map<String, Object> redactionProof,
                                                   Map<String, Object> deterministicEvalProof,
                                                   Map<String, Object> privacyProof) {
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        requireText(replaySource, "type", "replay-source-type-missing", failures);
        requireText(replaySource, "digest", "replay-source-digest-missing", failures);
        if (!truthy(replaySource, "redactedOnly")) {
            failures.add("replay-source-redacted-proof-missing");
        }
        if (!replaySource.containsKey("timelineStepCount")) {
            failures.add("replay-source-timeline-step-count-missing");
        }
        requireFalse(redactionProof, "containsRawPrincipal", "redaction-proof-raw-principal-not-closed", failures);
        requireFalse(redactionProof, "containsRawOrganization", "redaction-proof-raw-organization-not-closed", failures);
        requireFalse(redactionProof, "containsRawConversation", "redaction-proof-raw-conversation-not-closed", failures);
        requireFalse(redactionProof, "containsRawEndpoints", "redaction-proof-raw-endpoints-not-closed", failures);
        requireFalse(redactionProof, "containsRawReason", "redaction-proof-raw-reason-not-closed", failures);
        requireFalse(redactionProof, "containsRawParameterValues", "redaction-proof-raw-parameter-values-not-closed", failures);
        if (!truthy(deterministicEvalProof, "deterministic")) {
            failures.add("deterministic-eval-proof-deterministic-not-true");
        }
        requireFalse(deterministicEvalProof, "llmUsed", "deterministic-eval-proof-llm-not-closed", failures);
        requireFalse(deterministicEvalProof, "externalCalls", "deterministic-eval-proof-external-calls-not-closed", failures);
        requireFalse(deterministicEvalProof, "toolExecution", "deterministic-eval-proof-tool-execution-not-closed", failures);
        requireFalse(deterministicEvalProof, "mcpToolCall", "deterministic-eval-proof-mcp-tool-call-not-closed", failures);
        requireFalse(deterministicEvalProof, "kubeManagerCalls", "deterministic-eval-proof-kube-manager-not-closed", failures);
        requireFalse(privacyProof, "containsAuthorizationHeader", "privacy-proof-authorization-header-not-closed", failures);
        requireFalse(privacyProof, "containsToken", "privacy-proof-token-not-closed", failures);
        requireFalse(privacyProof, "containsPassword", "privacy-proof-password-not-closed", failures);
        requireFalse(privacyProof, "containsRawPrompt", "privacy-proof-raw-prompt-not-closed", failures);
        requireFalse(privacyProof, "containsRawDocument", "privacy-proof-raw-document-not-closed", failures);
        requireText(payload, "evidenceDigest", "evidence-digest-missing", failures);
        if (!safeText(payload.get("evidenceDigest")).startsWith("sha256:")) {
            failures.add("evidence-digest-sha256-missing");
        }
        requireForbiddenRuntimeClaimsClosed(payload.get("forbiddenRuntimeClaims"), failures);
        return List.copyOf(failures);
    }

    private static void requireText(Map<String, Object> map, String key, String failure, List<String> failures) {
        if (safeText(map.get(key)).isBlank()) {
            failures.add(failure);
        }
    }

    private static void requireFalse(Map<String, Object> map, String key, String failure, List<String> failures) {
        if (!Boolean.FALSE.equals(map.get(key))) {
            failures.add(failure);
        }
    }

    private static void requireForbiddenRuntimeClaimsClosed(Object value, List<String> failures) {
        Set<String> claims = value instanceof List<?> list
            ? list.stream()
            .map(AgentReviewedTraceFixtureManifestResponse::safeText)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            : Set.of();
        List<String> requiredClaims = List.of(
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
        for (String required : requiredClaims) {
            if (!claims.contains(required)) {
                failures.add("forbidden-runtime-claims-missing-" + required.replace(':', '-'));
            }
        }
    }

    private static Map<String, Object> traceSetCoverage(AgentEvalTraceSetDefinition traceSet, Set<String> readyTraceSetIds) {
        boolean catalogHasTraceIds = traceSet.traceIds() != null && !traceSet.traceIds().isEmpty();
        boolean fixturePresent = readyTraceSetIds.contains(traceSet.id());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSet.id());
        row.put("suiteId", traceSet.suiteId());
        row.put("catalogTraceIdsPresent", catalogHasTraceIds);
        row.put("reviewedFixtureFilePresent", fixturePresent);
        row.put("status", coverageStatus(catalogHasTraceIds, fixturePresent));
        row.put("catalogMutationAllowed", false);
        row.put("requiresHumanGitReview", true);
        row.put("nextAction", fixturePresent
            ? "prepare-catalog-patch-review-through-git"
            : "prepare-reviewed-redacted-fixture-file");
        return Map.copyOf(row);
    }

    private static String coverageStatus(boolean catalogHasTraceIds, boolean fixturePresent) {
        if (catalogHasTraceIds) {
            return "CATALOG_HAS_REVIEWED_TRACE_IDS";
        }
        if (fixturePresent) {
            return "REVIEWED_FIXTURE_PRESENT_AWAITING_CATALOG_PATCH";
        }
        return "MISSING_REVIEWED_FIXTURE_FILE";
    }

    private static List<Map<String, Object>> buildRequiredFixtureFields() {
        return requiredFieldNames().stream()
            .map(name -> {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", name);
                field.put("required", true);
                field.put("callerSuppliedRuntimeAuthority", false);
                return Map.copyOf(field);
            })
            .toList();
    }

    private static List<String> requiredFieldNames() {
        return List.of(
            "traceId",
            "traceSetId",
            "suiteId",
            "replaySource",
            "redactionProof",
            "deterministicEvalProof",
            "privacyProof",
            "sourceCommitSha",
            "reviewer",
            "reviewTimestamp",
            "evidenceDigest",
            "candidateGateSummary",
            "forbiddenRuntimeClaims"
        );
    }

    private static List<String> buildForbiddenShortcuts() {
        return List.of(
            "runtime-fixture-upload",
            "caller-trace-id-intake",
            "raw-audit-export",
            "runtime-catalog-write",
            "eval-trace-sets-json-mutation",
            "eval-or-replay-runtime-execution",
            "ci-blocking-switch",
            "release-authority",
            "mcp-tools-call",
            "safe-tool-executor-invocation",
            "kube-manager-call",
            "audit-or-memory-write",
            "nim-hpc-slurm-bcm-phase2-authority"
        );
    }

    private static List<String> nextActions(int missingFixtureTraceSetCount) {
        if (missingFixtureTraceSetCount == 0) {
            return List.of(
                "open-human-git-review-for-catalog-patch",
                "regenerate-gate-bundle-after-reviewed-catalog-merge",
                "keep-ci-blocking-disabled-until-separate-release-slice"
            );
        }
        return List.of(
            "create-reviewed-redacted-fixture-files-under-observability-reviewed-trace-fixtures",
            "attach-redaction-privacy-determinism-and-digest-proofs",
            "review-fixture-files-through-human-git-review",
            "then-prepare-catalog-patch-proposal",
            "keep-runtime-upload-catalog-write-and-ci-blocking-disabled"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("fixtureManifest", ENDPOINT);
        endpoints.put("fixtureTemplate", AgentReviewedTraceFixtureTemplateResponse.ENDPOINT);
        endpoints.put("fixtureIntakeContract", AgentReviewedTraceFixtureIntakeContractResponse.ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("catalogPatchReview", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("manifestOnly", true);
        safety.put("classpathScanOnly", true);
        safety.put("runtimeIntakeAllowedNow", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("callerTraceIdsAccepted", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("evalTraceSetsJsonWrite", false);
        safety.put("releaseBlockingAllowedNow", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("runtimeEvalAllowed", false);
        safety.put("replayExecuted", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("ragPromptInfluence", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(List<Map<String, Object>> fixtureRows,
                                                    AgentEvalTraceSetCatalogResponse catalog) {
        Map<String, Object> catalogPrivacy = catalog != null ? catalog.privacy() : Map.of();
        boolean rowsRedacted = fixtureRows == null || fixtureRows.stream()
            .allMatch(row -> Boolean.TRUE.equals(row.get("redactedOnly")));
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", rowsRedacted && !truthy(catalogPrivacy, "containsRawPrincipal")
            && !truthy(catalogPrivacy, "containsRawOrganization")
            && !truthy(catalogPrivacy, "containsRawConversation")
            && !truthy(catalogPrivacy, "containsRawEndpoints")
            && !truthy(catalogPrivacy, "containsRawReason")
            && !truthy(catalogPrivacy, "containsRawParameterValues"));
        privacy.put("rawAuditExportAllowed", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawEndpoints", false);
        privacy.put("containsRawReason", false);
        privacy.put("containsRawParameterValues", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("deterministic", true);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean presentStructured(Object value) {
        return value instanceof Map<?, ?> map && !map.isEmpty()
            || value instanceof List<?> list && !list.isEmpty();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                safe.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(safe);
    }

    private static boolean truthy(Map<String, Object> map, String key) {
        return map != null && Boolean.TRUE.equals(map.get(key));
    }

    private static String safeText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
