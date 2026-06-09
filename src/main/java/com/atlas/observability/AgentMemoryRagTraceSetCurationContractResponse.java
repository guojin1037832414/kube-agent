package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory/RAG trace-set curation contract before reviewed trace IDs are promoted.
 *
 * <p>中文说明：这个响应只描述 Memory/RAG 三条证据车道的 Git 审查条件、缺口和安全闸门。
 * 它不会运行 eval、不会查询原始审计、不会修改 trace-set catalog，也不会执行检索、向量库、
 * 模型、工具、MCP 或 kube-manager 调用。教学重点是：顶级 Agent 的证据链必须显式声明，
 * 配置缺失不能被“安全默认值”悄悄掩盖。</p>
 */
public record AgentMemoryRagTraceSetCurationContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean curationContractDefined,
    boolean reviewedTraceEvidenceCurated,
    boolean allRequiredTraceSetsDefined,
    boolean allRequiredTraceSetsPolicyClosed,
    boolean suiteRuntimePolicyClosed,
    boolean evalRuntimeAllowedNow,
    boolean retrievalRuntimeAllowedNow,
    boolean ciBlockingAllowedNow,
    int requiredTraceSetCount,
    int definedTraceSetCount,
    int reviewedTraceSetCount,
    Map<String, Object> suiteRuntimeLatch,
    List<Map<String, Object>> traceSetRows,
    List<String> blockedReasons,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> evidencePolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-trace-set-curation-contract.v1";
    private static final String MEMORY_RAG_SUITE_ID = "memory-rag-release-gate";

    private static final List<String> REQUIRED_TRACE_SET_IDS = List.of(
        "memory-rag-citation-fidelity",
        "memory-rag-privacy-tenant",
        "memory-rag-lifecycle-policy"
    );

    private static final Map<String, Boolean> REQUIRED_TRACE_SET_POLICY = requiredTraceSetPolicy();
    private static final Map<String, Boolean> REQUIRED_SUITE_POLICY = requiredSuitePolicy();

    public static AgentMemoryRagTraceSetCurationContractResponse of(
        Instant generatedAt,
        AgentEvalTraceSetCatalogResponse traceSetCatalog,
        AgentEvalSuiteCatalogResponse suiteCatalog
    ) {
        Map<String, AgentEvalTraceSetDefinition> existing = catalogIndex(traceSetCatalog);
        List<Map<String, Object>> rows = REQUIRED_TRACE_SET_IDS.stream()
            .map(traceSetId -> traceSetRow(traceSetId, existing.get(traceSetId)))
            .toList();
        long defined = rows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("definedInCatalog")))
            .count();
        long reviewed = rows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("reviewedTraceIdsPresent")))
            .count();
        boolean allDefined = defined == REQUIRED_TRACE_SET_IDS.size();
        boolean allReviewed = reviewed == REQUIRED_TRACE_SET_IDS.size();
        boolean traceSetPoliciesClosed = rows.stream()
            .allMatch(row -> Boolean.TRUE.equals(row.get("policyLatchDeclaredClosed")));
        Map<String, Object> suiteLatch = suiteRuntimeLatch(suiteCatalog);
        boolean suitePolicyClosed = Boolean.TRUE.equals(suiteLatch.get("policyLatchDeclaredClosed"));
        return new AgentMemoryRagTraceSetCurationContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            resolveStatus(allDefined, allReviewed, traceSetPoliciesClosed, suitePolicyClosed),
            "Memory/RAG reviewed trace-set curation before advisory gate bundles",
            true,
            true,
            allReviewed,
            allDefined,
            traceSetPoliciesClosed,
            suitePolicyClosed,
            false,
            false,
            false,
            REQUIRED_TRACE_SET_IDS.size(),
            Math.toIntExact(defined),
            Math.toIntExact(reviewed),
            suiteLatch,
            rows,
            blockedReasons(allDefined, allReviewed, traceSetPoliciesClosed, suitePolicyClosed),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildEvidencePolicy(),
            buildSafety(),
            buildPrivacy(traceSetCatalog, suiteCatalog)
        );
    }

    private static String resolveStatus(boolean allDefined,
                                        boolean allReviewed,
                                        boolean traceSetPoliciesClosed,
                                        boolean suitePolicyClosed) {
        if (!suitePolicyClosed) {
            return "SUITE_RUNTIME_POLICY_MISCONFIGURED";
        }
        if (!allDefined) {
            return "TRACE_SET_CATALOG_ROWS_MISSING";
        }
        if (!traceSetPoliciesClosed) {
            return "TRACE_SET_RUNTIME_POLICY_MISCONFIGURED";
        }
        if (!allReviewed) {
            return "TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED";
        }
        return "TRACE_SETS_REVIEWED_READY_FOR_ADVISORY_GATE_BUNDLE";
    }

    private static Map<String, AgentEvalTraceSetDefinition> catalogIndex(AgentEvalTraceSetCatalogResponse catalog) {
        Map<String, AgentEvalTraceSetDefinition> index = new LinkedHashMap<>();
        if (catalog != null) {
            for (AgentEvalTraceSetDefinition definition : catalog.traceSets()) {
                index.put(definition.id(), definition);
            }
        }
        return Map.copyOf(index);
    }

    private static Map<String, Object> suiteRuntimeLatch(AgentEvalSuiteCatalogResponse suiteCatalog) {
        AgentEvalSuiteDefinition suite = suiteCatalog != null
            ? suiteCatalog.suites().stream()
            .filter(definition -> MEMORY_RAG_SUITE_ID.equals(definition.id()))
            .findFirst()
            .orElse(null)
            : null;
        Map<String, Object> guarantees = suite != null ? suite.guarantees() : Map.of();
        List<String> missingPolicyKeys = missingPolicyKeys(guarantees, REQUIRED_SUITE_POLICY);
        List<String> policyMismatches = policyMismatches(guarantees, REQUIRED_SUITE_POLICY);
        boolean policyClosed = suite != null && missingPolicyKeys.isEmpty() && policyMismatches.isEmpty();
        Map<String, Object> latch = new LinkedHashMap<>();
        latch.put("suiteId", MEMORY_RAG_SUITE_ID);
        latch.put("definedInCatalog", suite != null);
        latch.put("policyKeysPresent", missingPolicyKeys.isEmpty());
        latch.put("missingPolicyKeys", missingPolicyKeys);
        latch.put("policyMismatches", policyMismatches);
        latch.put("policyLatchDeclaredClosed", policyClosed);
        latch.put("catalogOnly", policyValue(guarantees, "catalogOnly"));
        latch.put("runtimeExecutionAllowed", policyValue(guarantees, "runtimeExecutionAllowed"));
        latch.put("requiresReviewedTraceSetsBeforeRun", policyValue(guarantees, "requiresReviewedTraceSetsBeforeRun"));
        latch.put("ciBlockingAllowed", policyValue(guarantees, "ciBlockingAllowed"));
        latch.put("retrievalRuntimeAllowed", policyValue(guarantees, "retrievalRuntimeAllowed"));
        latch.put("runtimeExecutionAllowedNow", false);
        latch.put("nextAction", policyClosed
            ? "keep-suite-runtime-closed-until-reviewed-trace-sets-pass"
            : "restore-memory-rag-suite-runtime-latch-through-git-review");
        return Map.copyOf(latch);
    }

    private static Map<String, Object> traceSetRow(String traceSetId,
                                                   AgentEvalTraceSetDefinition definition) {
        Map<String, Object> policy = definition != null ? definition.curationPolicy() : Map.of();
        List<String> missingPolicyKeys = missingPolicyKeys(policy, REQUIRED_TRACE_SET_POLICY);
        List<String> policyMismatches = policyMismatches(policy, REQUIRED_TRACE_SET_POLICY);
        boolean defined = definition != null;
        boolean reviewed = defined && !definition.traceIds().isEmpty();
        boolean policyClosed = defined && missingPolicyKeys.isEmpty() && policyMismatches.isEmpty();
        List<String> missingEvidence = missingEvidence(traceSetId, definition);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSetId);
        row.put("title", definition != null ? definition.title() : "");
        row.put("purpose", purpose(traceSetId, definition));
        row.put("suiteId", definition != null ? definition.suiteId() : MEMORY_RAG_SUITE_ID);
        row.put("definedInCatalog", defined);
        row.put("rowStatus", rowStatus(defined, reviewed, policyClosed));
        row.put("reviewedTraceIdsPresent", reviewed);
        row.put("traceIdCount", definition != null ? definition.traceIds().size() : 0);
        row.put("traceIdsVisibleInContract", false);
        row.put("policyKeysPresent", missingPolicyKeys.isEmpty());
        row.put("missingPolicyKeys", missingPolicyKeys);
        row.put("policyMismatches", policyMismatches);
        row.put("policyLatchDeclaredClosed", policyClosed);
        row.put("catalogOnlyUntilReviewed", policyValue(policy, "catalogOnlyUntilReviewed"));
        row.put("suiteRuntimeExecutionAllowed", policyValue(policy, "suiteRuntimeExecutionAllowed"));
        row.put("retrievalRuntimeAllowed", policyValue(policy, "runtimeRetrievalAllowed"));
        row.put("ciBlockingAllowed", policyValue(policy, "ciBlockingAllowed"));
        row.put("requiresReviewedSourceEvidenceDigest", policyValue(policy,
            "requiresReviewedSourceEvidenceDigest"));
        row.put("requiresReviewedMemoryLifecycleEvidence", policyValue(policy,
            "requiresReviewedMemoryLifecycleEvidence"));
        row.put("requiresRealAuditCapture", policyValue(policy, "requiresRealAuditCapture"));
        row.put("placeholderTraceIds", policyValue(policy, "placeholderTraceIds"));
        row.put("failClosedWhenEmpty", policyValue(policy, "failClosedWhenEmpty"));
        row.put("requestTraceIdOverrideAllowed", policyValue(policy, "requestTraceIdOverrideAllowed"));
        row.put("runtimeExecutionAllowedNow", false);
        row.put("nextAction", nextAction(definition, policyClosed));
        row.put("gitReviewRequired", true);
        row.put("humanReviewRequired", true);
        row.put("runtimeCatalogMutationAllowed", false);
        row.put("evidenceRequirements", definition != null
            ? List.copyOf(definition.evidenceRequirements())
            : List.of());
        row.put("missingEvidence", missingEvidence);
        row.put("blockedReasons", rowBlockedReasons(defined, reviewed, policyClosed, missingPolicyKeys));
        row.put("safetyProof", safetyProof(definition));
        return Map.copyOf(row);
    }

    private static String rowStatus(boolean defined, boolean reviewed, boolean policyClosed) {
        if (!defined) {
            return "CATALOG_ROW_MISSING";
        }
        if (!policyClosed) {
            return "POLICY_LATCH_MISCONFIGURED";
        }
        if (!reviewed) {
            return "REVIEWED_EVIDENCE_MISSING";
        }
        return "READY_FOR_ADVISORY_GATE_BUNDLE";
    }

    private static String purpose(String traceSetId, AgentEvalTraceSetDefinition definition) {
        if (definition != null && !definition.purpose().isBlank()) {
            return definition.purpose();
        }
        return switch (traceSetId) {
            case "memory-rag-citation-fidelity" -> "Reviewed citation/source digest traces for Memory/RAG answers.";
            case "memory-rag-privacy-tenant" -> "Reviewed privacy leakage and tenant isolation red-team traces.";
            case "memory-rag-lifecycle-policy" -> "Reviewed retention, delete/export/recovery, stale memory, and retrieval budget traces.";
            default -> "";
        };
    }

    private static String nextAction(AgentEvalTraceSetDefinition definition, boolean policyClosed) {
        if (definition == null) {
            return "propose-trace-set-catalog-row-through-git-review";
        }
        if (!policyClosed) {
            return "restore-policy-latch-through-git-review";
        }
        if (definition.traceIds().isEmpty()) {
            return "curate-reviewed-redacted-trace-ids-through-git-review";
        }
        return "generate-advisory-memory-rag-gate-bundle";
    }

    private static List<String> missingEvidence(String traceSetId,
                                                AgentEvalTraceSetDefinition definition) {
        if (definition == null) {
            return List.of("trace-set-catalog-row");
        }
        if (!definition.traceIds().isEmpty()) {
            return List.of();
        }
        return switch (traceSetId) {
            case "memory-rag-citation-fidelity" -> List.of(
                "reviewed-redacted-citation-fidelity-trace-id",
                "source-evidence-digest-anchor",
                "chunk-digest-and-citation-match-proof"
            );
            case "memory-rag-privacy-tenant" -> List.of(
                "reviewed-redacted-privacy-leakage-negative-trace-id",
                "tenant-isolation-negative-trace-id",
                "raw-prompt-and-source-redaction-proof"
            );
            case "memory-rag-lifecycle-policy" -> List.of(
                "reviewed-redacted-retention-staleness-trace-id",
                "delete-export-recovery-proof-trace-id",
                "retrieval-policy-budget-trace-id"
            );
            default -> List.of("reviewed-redacted-trace-id");
        };
    }

    private static List<String> rowBlockedReasons(boolean defined,
                                                  boolean reviewed,
                                                  boolean policyClosed,
                                                  List<String> missingPolicyKeys) {
        List<String> reasons = new ArrayList<>();
        if (!defined) {
            reasons.add("trace-set-catalog-row-missing");
        }
        if (!missingPolicyKeys.isEmpty()) {
            reasons.add("trace-set-policy-keys-missing");
        }
        if (defined && !policyClosed) {
            reasons.add("trace-set-policy-latch-not-closed");
        }
        if (defined && !reviewed) {
            reasons.add("reviewed-redacted-trace-ids-missing");
        }
        return List.copyOf(reasons);
    }

    private static Map<String, Object> safetyProof(AgentEvalTraceSetDefinition definition) {
        Map<String, Object> guarantees = definition != null ? definition.guarantees() : Map.of();
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(guarantees.get("redactedOnly")));
        proof.put("containsRawDocument", truthy(guarantees, "containsRawDocument"));
        proof.put("containsRawPrompt", truthy(guarantees, "containsRawPrompt"));
        proof.put("containsRawRetrievedChunk", truthy(guarantees, "containsRawRetrievedChunk"));
        proof.put("retrievalExecuted", truthy(guarantees, "retrievalExecuted"));
        proof.put("vectorStoreCalls", truthy(guarantees, "vectorStoreCalls"));
        proof.put("embeddingModelCalls", truthy(guarantees, "embeddingModelCalls"));
        proof.put("rerankerCalls", truthy(guarantees, "rerankerCalls"));
        proof.put("llmUsed", truthy(guarantees, "llmUsed"));
        proof.put("toolExecution", truthy(guarantees, "toolExecution"));
        proof.put("kubeManagerCalls", truthy(guarantees, "kubeManagerCalls"));
        proof.put("memoryWrite", truthy(guarantees, "memoryWrite"));
        proof.put("auditWrite", truthy(guarantees, "auditWrite"));
        return Map.copyOf(proof);
    }

    private static boolean policyValue(Map<String, Object> policy, String key) {
        Object value = policy != null ? policy.get(key) : null;
        return value instanceof Boolean bool && bool;
    }

    private static List<String> missingPolicyKeys(Map<String, Object> policy, Map<String, Boolean> requiredPolicy) {
        List<String> missing = new ArrayList<>();
        for (String key : requiredPolicy.keySet()) {
            if (policy == null || !policy.containsKey(key) || !(policy.get(key) instanceof Boolean)) {
                missing.add(key);
            }
        }
        return List.copyOf(missing);
    }

    private static List<String> policyMismatches(Map<String, Object> policy, Map<String, Boolean> requiredPolicy) {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, Boolean> expected : requiredPolicy.entrySet()) {
            Object value = policy != null ? policy.get(expected.getKey()) : null;
            if (value instanceof Boolean actual && actual != expected.getValue()) {
                mismatches.add(expected.getKey());
            }
        }
        return List.copyOf(mismatches);
    }

    private static List<String> blockedReasons(boolean allDefined,
                                               boolean allReviewed,
                                               boolean traceSetPoliciesClosed,
                                               boolean suitePolicyClosed) {
        List<String> reasons = new ArrayList<>();
        if (!suitePolicyClosed) {
            reasons.add("memory-rag-suite-runtime-policy-misconfigured");
        }
        if (!allDefined) {
            reasons.add("memory-rag-trace-set-catalog-rows-missing");
        }
        if (!traceSetPoliciesClosed) {
            reasons.add("memory-rag-trace-set-runtime-policy-misconfigured");
        }
        if (!allReviewed) {
            reasons.add("reviewed-redacted-memory-rag-trace-ids-missing");
            reasons.add("memory-rag-advisory-gate-bundle-not-generated");
        }
        reasons.add("memory-rag-eval-runtime-not-promoted");
        reasons.add("retrieval-runtime-intentionally-closed");
        reasons.add("ci-blocking-switch-intentionally-absent");
        return List.copyOf(reasons);
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "keep-memory-rag-suite-runtime-latch-closed",
            "keep-memory-rag-trace-set-catalog-rows-git-reviewed",
            "curate-reviewed-redacted-trace-ids-for-citation-privacy-tenant-lifecycle",
            "regenerate-memory-rag-trace-set-gate-bundle-in-advisory-mode",
            "render-vue-memory-rag-curation-contract-and-blockers",
            "promote-ci-blocking-only-in-a-separate-reviewed-slice",
            "bind-retrieval-runtime-only-after-source-digest-lifecycle-tenant-privacy-and-eval-evidence-pass"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagTraceSetCurationContract",
            "/api/agent/observability/memory-rag/trace-set-curation-contract");
        endpoints.put("memoryRagTraceSetCurationWorkbenchOverview",
            AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.OVERVIEW_ENDPOINT);
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("memoryRagEvalSuiteBindingContract",
            "/api/agent/observability/memory-rag/eval-suite-binding-contract");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("traceSetGateBundle", endpointDescriptor(
            "POST",
            "/api/agent/observability/eval/trace-sets/gate-bundle",
            "after-reviewed-traces",
            false
        ));
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> endpointDescriptor(String method,
                                                          String path,
                                                          String intendedStage,
                                                          boolean runtimeAllowedNow) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("intendedStage", intendedStage);
        endpoint.put("runtimeAllowedNow", runtimeAllowedNow);
        endpoint.put("buttonEnabledNow", false);
        return Map.copyOf(endpoint);
    }

    private static Map<String, Object> buildEvidencePolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("traceIdsAcceptedFromCaller", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requiresGitReview", true);
        policy.put("requiresHumanReview", true);
        policy.put("requiresPersistedRedactedReplayEvidence", true);
        policy.put("requiresSourceEvidenceDigest", true);
        policy.put("requiresMemoryLifecycleEvidence", true);
        policy.put("emptyTraceIdsFailClosed", true);
        policy.put("requiredTraceSetPolicyKeys", List.copyOf(REQUIRED_TRACE_SET_POLICY.keySet()));
        policy.put("requiredSuitePolicyKeys", List.copyOf(REQUIRED_SUITE_POLICY.keySet()));
        policy.put("missingPolicyKeyOutcome", "fail-closed-visible-blocker");
        policy.put("nextRuntimeStage", "advisory-memory-rag-gate-bundle-after-reviewed-traces");
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("summaryOnly", true);
        safety.put("evalRuntimeExecuted", false);
        safety.put("traceSetGateInvoked", false);
        safety.put("curationReviewInvoked", false);
        safety.put("candidateDiscoveryInvoked", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("ingestionExecuted", false);
        safety.put("memoryWrite", false);
        safety.put("auditWrite", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("llmUsed", false);
        safety.put("promptMutation", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("externalCalls", false);
        safety.put("ciBlockingChanged", false);
        safety.put("durableReceiptIssued", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentEvalTraceSetCatalogResponse traceSetCatalog,
                                                    AgentEvalSuiteCatalogResponse suiteCatalog) {
        Map<String, Object> traceSetPrivacy = traceSetCatalog != null ? traceSetCatalog.privacy() : Map.of();
        Map<String, Object> suitePrivacy = suiteCatalog != null ? suiteCatalog.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", Boolean.TRUE.equals(traceSetPrivacy.get("redactedOnly"))
            && Boolean.TRUE.equals(suitePrivacy.get("redactedOnly")));
        privacy.put("traceIdsVisibleInContract", false);
        privacy.put("containsRawPrincipal", truthy(traceSetPrivacy, "containsRawPrincipal")
            || truthy(suitePrivacy, "containsRawPrincipal"));
        privacy.put("containsRawOrganization", truthy(traceSetPrivacy, "containsRawOrganization")
            || truthy(suitePrivacy, "containsRawOrganization"));
        privacy.put("containsRawConversation", truthy(traceSetPrivacy, "containsRawConversation")
            || truthy(suitePrivacy, "containsRawConversation"));
        privacy.put("containsRawDocument", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawRetrievedChunk", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsEvalTracePayload", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Boolean> requiredTraceSetPolicy() {
        Map<String, Boolean> policy = new LinkedHashMap<>();
        policy.put("requiresRealAuditCapture", true);
        policy.put("placeholderTraceIds", false);
        policy.put("failClosedWhenEmpty", true);
        policy.put("requestTraceIdOverrideAllowed", false);
        policy.put("catalogOnlyUntilReviewed", true);
        policy.put("suiteRuntimeExecutionAllowed", false);
        policy.put("runtimeRetrievalAllowed", false);
        policy.put("ciBlockingAllowed", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Boolean> requiredSuitePolicy() {
        Map<String, Boolean> policy = new LinkedHashMap<>();
        policy.put("catalogOnly", true);
        policy.put("runtimeExecutionAllowed", false);
        policy.put("requiresReviewedTraceSetsBeforeRun", true);
        policy.put("ciBlockingAllowed", false);
        policy.put("retrievalRuntimeAllowed", false);
        return Map.copyOf(policy);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
