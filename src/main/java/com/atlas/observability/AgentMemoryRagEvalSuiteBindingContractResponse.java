package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Memory/RAG eval-suite binding contract before retrieval runtime.
 *
 * <p>中文说明：这个响应只描述 Memory/RAG 评测门禁如何绑定到未来的
 * eval suite / trace set / CI artifact。它不会运行 eval，不会读取真实 trace，
 * 不会执行检索，也不会调用模型、向量库、工具或 kube-manager。</p>
 */
public record AgentMemoryRagEvalSuiteBindingContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean evalSuiteBindingContractDefined,
    boolean memoryRagEvalSuiteBound,
    boolean memoryRagTraceSetBound,
    boolean reviewedTraceEvidenceRequired,
    boolean evalRuntimeExecuted,
    boolean ciBlockingEnabled,
    boolean retrievalRuntimeAllowedNow,
    int requiredGateCheckCount,
    int mappedGateCheckCount,
    int missingGateCheckCount,
    int availableSuiteCount,
    int availableTraceSetCount,
    List<Map<String, Object>> bindingRows,
    List<Map<String, Object>> requiredTraceSets,
    List<Map<String, Object>> suiteCandidates,
    List<String> blockedReasons,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-eval-suite-binding-contract.v1";

    private static final List<String> REQUIRED_GATE_CHECKS = List.of(
        "citation-fidelity",
        "source-digest-integrity",
        "privacy-leakage",
        "tenant-isolation",
        "retention-staleness",
        "delete-export-recovery-proof",
        "retrieval-policy-budget",
        "unsupported-answer",
        "prompt-injection-boundary"
    );

    private static final Map<String, String> FUTURE_CHECK_CODES = Map.of(
        "citation-fidelity", "MEMORY_RAG_CITATION_FIDELITY",
        "source-digest-integrity", "MEMORY_RAG_SOURCE_DIGEST_INTEGRITY",
        "privacy-leakage", "MEMORY_RAG_PRIVACY_LEAKAGE",
        "tenant-isolation", "MEMORY_RAG_TENANT_ISOLATION",
        "retention-staleness", "MEMORY_RAG_RETENTION_STALENESS",
        "delete-export-recovery-proof", "MEMORY_RAG_DELETE_EXPORT_RECOVERY_PROOF",
        "retrieval-policy-budget", "MEMORY_RAG_RETRIEVAL_POLICY_BUDGET",
        "unsupported-answer", "MEMORY_RAG_UNSUPPORTED_ANSWER",
        "prompt-injection-boundary", "MEMORY_RAG_PROMPT_INJECTION_BOUNDARY"
    );

    public static AgentMemoryRagEvalSuiteBindingContractResponse of(
        Instant generatedAt,
        AgentMemoryRagEvalGateContractResponse evalGateContract,
        AgentEvalSuiteCatalogResponse suiteCatalog,
        AgentEvalTraceSetCatalogResponse traceSetCatalog
    ) {
        List<Map<String, Object>> bindingRows = buildBindingRows(evalGateContract, suiteCatalog);
        int mapped = (int) bindingRows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("suiteCheckCodePresent")))
            .count();
        int required = REQUIRED_GATE_CHECKS.size();
        int suiteCount = suiteCatalog != null ? suiteCatalog.suiteCount() : 0;
        int traceSetCount = traceSetCatalog != null ? traceSetCatalog.traceSetCount() : 0;
        boolean suiteBound = mapped == required;
        return new AgentMemoryRagEvalSuiteBindingContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            suiteBound ? "SUITE_CHECKS_DEFINED_TRACE_SETS_NOT_CURATED" : "CONTRACT_DEFINED_NOT_BOUND",
            "Memory/RAG eval suite binding before retrieval runtime",
            true,
            true,
            suiteBound,
            false,
            true,
            false,
            false,
            false,
            required,
            mapped,
            required - mapped,
            suiteCount,
            traceSetCount,
            bindingRows,
            buildRequiredTraceSets(traceSetCatalog),
            buildSuiteCandidates(suiteCatalog),
            buildBlockedReasons(mapped, required),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy(evalGateContract, suiteCatalog, traceSetCatalog)
        );
    }

    private static List<Map<String, Object>> buildBindingRows(AgentMemoryRagEvalGateContractResponse evalGateContract,
                                                              AgentEvalSuiteCatalogResponse suiteCatalog) {
        Set<String> availableCheckCodes = suiteCatalog != null
            ? suiteCatalog.suites().stream()
            .flatMap(suite -> suite.checkCodes().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet())
            : Set.of();
        Set<String> gateChecks = evalGateContract != null
            ? evalGateContract.gateChecks().stream()
            .map(check -> safeText(check.get("id")))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
            : Set.of();
        return REQUIRED_GATE_CHECKS.stream()
            .map(checkId -> bindingRow(checkId, gateChecks.contains(checkId), availableCheckCodes))
            .toList();
    }

    private static Map<String, Object> bindingRow(String gateCheckId,
                                                  boolean gateCheckDeclared,
                                                  Set<String> availableCheckCodes) {
        String futureCheckCode = FUTURE_CHECK_CODES.getOrDefault(gateCheckId, "");
        boolean present = availableCheckCodes.contains(futureCheckCode);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gateCheckId", gateCheckId);
        row.put("gateCheckDeclared", gateCheckDeclared);
        row.put("futureSuiteCheckCode", futureCheckCode);
        row.put("suiteCheckCodePresent", present);
        row.put("bindingStatus", present ? "MAPPED" : "NEEDS_SUITE_CHECK");
        row.put("runtimeBound", false);
        row.put("blocksRetrievalRuntime", true);
        return Map.copyOf(row);
    }

    private static List<Map<String, Object>> buildRequiredTraceSets(AgentEvalTraceSetCatalogResponse traceSetCatalog) {
        Map<String, AgentEvalTraceSetDefinition> existing = new LinkedHashMap<>();
        if (traceSetCatalog != null) {
            for (AgentEvalTraceSetDefinition definition : traceSetCatalog.traceSets()) {
                existing.put(definition.id(), definition);
            }
        }
        return List.of(
            requiredTraceSet("memory-rag-citation-fidelity", "memory-rag-release-gate",
                "Reviewed traces proving cited answer text matches source/chunk/evidence digests.", existing),
            requiredTraceSet("memory-rag-privacy-tenant", "memory-rag-release-gate",
                "Reviewed red-team traces for raw prompt/source leakage and tenant partition failures.", existing),
            requiredTraceSet("memory-rag-lifecycle-policy", "memory-rag-release-gate",
                "Reviewed traces for retention, delete/export/recovery proof, stale memory, and retrieval budget.", existing)
        );
    }

    private static Map<String, Object> requiredTraceSet(String traceSetId,
                                                        String suiteId,
                                                        String purpose,
                                                        Map<String, AgentEvalTraceSetDefinition> existing) {
        AgentEvalTraceSetDefinition current = existing.get(traceSetId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSetId);
        row.put("suiteId", suiteId);
        row.put("purpose", purpose);
        row.put("definedInCatalog", current != null);
        row.put("reviewedTraceIdsPresent", current != null && !current.traceIds().isEmpty());
        row.put("runtimeCatalogMutationAllowed", false);
        row.put("nextAction", current == null
            ? "propose-trace-set-through-git-review"
            : "populate-reviewed-redacted-trace-ids-through-git-review");
        return Map.copyOf(row);
    }

    private static List<Map<String, Object>> buildSuiteCandidates(AgentEvalSuiteCatalogResponse suiteCatalog) {
        if (suiteCatalog == null) {
            return List.of();
        }
        return suiteCatalog.suites().stream()
            .map(AgentMemoryRagEvalSuiteBindingContractResponse::suiteCandidate)
            .toList();
    }

    private static Map<String, Object> suiteCandidate(AgentEvalSuiteDefinition suite) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("suiteId", suite.id());
        row.put("title", suite.title());
        row.put("defaultMinimumScore", suite.defaultMinimumScore());
        row.put("defaultFailOnWarnings", suite.defaultFailOnWarnings());
        row.put("checkCount", suite.checkCodes().size());
        row.put("memoryRagSpecific", suite.id().startsWith("memory-rag"));
        row.put("eligibleForMemoryRagBinding", suite.id().startsWith("memory-rag"));
        row.put("runtimeBound", false);
        return Map.copyOf(row);
    }

    private static List<String> buildBlockedReasons(int mapped, int required) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (mapped < required) {
            reasons.add("memory-rag-suite-check-codes-missing");
        }
        reasons.add("memory-rag-suite-runtime-not-promoted");
        reasons.add("memory-rag-trace-sets-not-curated");
        reasons.add("reviewed-redacted-memory-rag-trace-evidence-missing");
        reasons.add("ci-blocking-switch-intentionally-absent");
        reasons.add("retrieval-runtime-intentionally-closed");
        return List.copyOf(reasons);
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "define-memory-rag-release-gate-suite",
            "add-memory-rag-suite-check-codes-for-all-gate-checks",
            "add-memory-rag-trace-set-catalog-entries-through-git-review",
            "curate-reviewed-redacted-memory-rag-traces",
            "generate-advisory-memory-rag-gate-bundle",
            "review-vue-memory-rag-eval-workbench",
            "promote-ci-blocking-only-in-a-separate-reviewed-slice",
            "keep-retrieval-runtime-closed-until-suite-and-trace-evidence-pass"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("evalSuiteCatalog", "/api/agent/observability/eval/suites");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("traceSetGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract");
        endpoints.put("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("summaryOnly", true);
        safety.put("evalRuntimeExecuted", false);
        safety.put("evalSuiteRunInvoked", false);
        safety.put("traceSetGateInvoked", false);
        safety.put("ciBlockingChanged", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("ingestionExecuted", false);
        safety.put("memoryWrite", false);
        safety.put("durableStoreCalls", false);
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
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentMemoryRagEvalGateContractResponse evalGateContract,
                                                    AgentEvalSuiteCatalogResponse suiteCatalog,
                                                    AgentEvalTraceSetCatalogResponse traceSetCatalog) {
        boolean containsRawPrincipal = truthy(evalGateContract != null ? evalGateContract.privacy() : Map.of(), "containsRawPrincipal")
            || truthy(suiteCatalog != null ? suiteCatalog.privacy() : Map.of(), "containsRawPrincipal")
            || truthy(traceSetCatalog != null ? traceSetCatalog.privacy() : Map.of(), "containsRawPrincipal");
        boolean containsRawConversation = truthy(evalGateContract != null ? evalGateContract.privacy() : Map.of(), "containsRawConversation")
            || truthy(suiteCatalog != null ? suiteCatalog.privacy() : Map.of(), "containsRawConversation")
            || truthy(traceSetCatalog != null ? traceSetCatalog.privacy() : Map.of(), "containsRawConversation");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal && !containsRawConversation);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawRetrievedChunk", false);
        privacy.put("containsSourceBody", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsEvalTracePayload", false);
        privacy.put("deterministic", true);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(Object value) {
        return value != null ? value.toString() : "";
    }
}
