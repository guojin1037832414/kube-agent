package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue-ready read model for Memory/RAG trace-set curation.
 */
public record AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String workbenchStatus,
    String frontendTarget,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean sourceReadModelsEmbedded,
    boolean runtimeControlAllowed,
    int curationCardCount,
    int blockingCardCount,
    int requiredTraceSetCount,
    int definedTraceSetCount,
    int reviewedTraceSetCount,
    List<Map<String, Object>> curationCards,
    Map<String, Object> suiteLatchCard,
    List<String> recommendedWorkflow,
    List<String> nextActions,
    AgentMemoryRagTraceSetCurationContractResponse curationContract,
    AgentMemoryRagEvalSuiteBindingContractResponse suiteBindingContract,
    AgentMemoryRagReadinessResponse memoryRagReadiness,
    Map<String, Object> endpointMap,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-memory-rag-trace-set-curation-workbench-overview.v1";
    public static final String OVERVIEW_ENDPOINT =
        "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview";

    public static AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse of(
        Instant generatedAt,
        AgentMemoryRagTraceSetCurationContractResponse curationContract,
        AgentMemoryRagEvalSuiteBindingContractResponse suiteBindingContract,
        AgentMemoryRagReadinessResponse memoryRagReadiness
    ) {
        List<Map<String, Object>> cards = curationCards(curationContract);
        int blockingCardCount = (int) cards.stream()
            .filter(card -> "BLOCKING".equals(card.get("severity")))
            .count();
        Map<String, Object> suiteLatchCard = suiteLatchCard(curationContract);
        return new AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse(
            SCHEMA_VERSION,
            generatedAt,
            workbenchStatus(curationContract, blockingCardCount),
            "vue-kube-manager Memory/RAG trace-set curation workbench",
            curationContract != null && curationContract.phase1TopTierGoalPreserved(),
            true,
            curationContract != null && suiteBindingContract != null && memoryRagReadiness != null,
            false,
            cards.size(),
            blockingCardCount,
            curationContract != null ? curationContract.requiredTraceSetCount() : 0,
            curationContract != null ? curationContract.definedTraceSetCount() : 0,
            curationContract != null ? curationContract.reviewedTraceSetCount() : 0,
            cards,
            suiteLatchCard,
            buildRecommendedWorkflow(),
            buildNextActions(curationContract, blockingCardCount),
            curationContract,
            suiteBindingContract,
            memoryRagReadiness,
            buildEndpointMap(),
            workbenchPolicy(curationContract, suiteBindingContract, memoryRagReadiness, cards, blockingCardCount),
            safety(curationContract),
            privacy(curationContract, suiteBindingContract, memoryRagReadiness)
        );
    }

    private static String workbenchStatus(AgentMemoryRagTraceSetCurationContractResponse contract,
                                          int blockingCardCount) {
        if (contract == null) {
            return "WORKBENCH_SOURCE_CONTRACT_MISSING";
        }
        if (!contract.suiteRuntimePolicyClosed() || !contract.allRequiredTraceSetsPolicyClosed()) {
            return "WORKBENCH_BLOCKED_BY_POLICY_LATCH";
        }
        if (!contract.allRequiredTraceSetsDefined()) {
            return "WORKBENCH_BLOCKED_BY_TRACE_SET_CATALOG";
        }
        if (!contract.reviewedTraceEvidenceCurated() || blockingCardCount > 0) {
            return "WORKBENCH_READY_TO_RENDER_REVIEWED_EVIDENCE_GAPS";
        }
        return "WORKBENCH_READY_FOR_ADVISORY_GATE_BUNDLE_REVIEW";
    }

    private static List<Map<String, Object>> curationCards(
        AgentMemoryRagTraceSetCurationContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return contract.traceSetRows().stream()
            .map(AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse::curationCard)
            .toList();
    }

    private static Map<String, Object> curationCard(Map<String, Object> row) {
        String traceSetId = string(row, "traceSetId");
        String rowStatus = string(row, "rowStatus");
        List<String> blockedReasons = stringList(row.get("blockedReasons"));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", traceSetId);
        card.put("title", title(traceSetId, string(row, "title")));
        card.put("status", rowStatus);
        card.put("severity", severity(rowStatus));
        card.put("suiteId", string(row, "suiteId"));
        card.put("purpose", string(row, "purpose"));
        card.put("endpoint", "/api/agent/observability/memory-rag/trace-set-curation-contract");
        card.put("definedInCatalog", bool(row, "definedInCatalog"));
        card.put("reviewedTraceIdsPresent", bool(row, "reviewedTraceIdsPresent"));
        card.put("traceIdCount", intValue(row.get("traceIdCount")));
        card.put("traceIdsVisibleInWorkbench", false);
        card.put("policyLatchDeclaredClosed", bool(row, "policyLatchDeclaredClosed"));
        card.put("policyKeysPresent", bool(row, "policyKeysPresent"));
        card.put("missingPolicyKeys", stringList(row.get("missingPolicyKeys")));
        card.put("policyMismatches", stringList(row.get("policyMismatches")));
        card.put("evidenceRequirements", stringList(row.get("evidenceRequirements")));
        card.put("missingEvidence", stringList(row.get("missingEvidence")));
        card.put("blockedReasonCount", blockedReasons.size());
        card.put("blockedReasons", blockedReasons);
        card.put("nextAction", string(row, "nextAction"));
        card.put("gitReviewRequired", bool(row, "gitReviewRequired"));
        card.put("humanReviewRequired", bool(row, "humanReviewRequired"));
        card.put("renderHints", renderHints(rowStatus, blockedReasons));
        card.put("disabledRuntimeActions", disabledRuntimeActions(traceSetId));
        card.put("evidence", evidence(row));
        card.put("readOnly", true);
        card.put("frontendNavigationOnly", true);
        card.put("runtimeControlAllowed", false);
        card.put("runtimeCatalogMutationAllowed", false);
        card.put("toolExecution", false);
        card.put("kubeManagerCalls", false);
        card.put("llmUsed", false);
        return Map.copyOf(card);
    }

    private static String title(String traceSetId, String title) {
        if (!title.isBlank()) {
            return title;
        }
        return switch (traceSetId) {
            case "memory-rag-citation-fidelity" -> "Memory/RAG citation fidelity";
            case "memory-rag-privacy-tenant" -> "Memory/RAG privacy and tenant isolation";
            case "memory-rag-lifecycle-policy" -> "Memory/RAG lifecycle policy";
            default -> traceSetId;
        };
    }

    private static String severity(String rowStatus) {
        if ("READY_FOR_ADVISORY_GATE_BUNDLE".equals(rowStatus)) {
            return "INFO";
        }
        return "BLOCKING";
    }

    private static Map<String, Object> renderHints(String rowStatus, List<String> blockedReasons) {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("tone", "READY_FOR_ADVISORY_GATE_BUNDLE".equals(rowStatus) ? "success" : "warning");
        hints.put("icon", "READY_FOR_ADVISORY_GATE_BUNDLE".equals(rowStatus) ? "shield-check" : "circle-alert");
        hints.put("primaryMetric", "traceIdCount");
        hints.put("showMissingEvidence", !blockedReasons.isEmpty());
        hints.put("showPolicyLatch", true);
        hints.put("showRuntimeButton", false);
        hints.put("showTraceIdValues", false);
        hints.put("allowInlineCatalogEdit", false);
        return Map.copyOf(hints);
    }

    private static List<Map<String, Object>> disabledRuntimeActions(String traceSetId) {
        return List.of(
            disabledAction(
                "discover-candidates",
                "GET",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/candidates",
                "requires-explicit-eval-workbench-review"
            ),
            disabledAction(
                "curation-review",
                "POST",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/curation-review",
                "requires-reviewed-redacted-candidates"
            ),
            disabledAction(
                "trace-set-gate",
                "POST",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/gate",
                "memory-rag-release-gate-runtime-disabled"
            )
        );
    }

    private static Map<String, Object> disabledAction(String id,
                                                      String method,
                                                      String path,
                                                      String disabledReason) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", id);
        action.put("method", method);
        action.put("path", path);
        action.put("enabledNow", false);
        action.put("buttonVisibleNow", false);
        action.put("disabledReason", disabledReason);
        return Map.copyOf(action);
    }

    private static Map<String, Object> evidence(Map<String, Object> row) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("catalogOnlyUntilReviewed", bool(row, "catalogOnlyUntilReviewed"));
        evidence.put("suiteRuntimeExecutionAllowed", bool(row, "suiteRuntimeExecutionAllowed"));
        evidence.put("retrievalRuntimeAllowed", bool(row, "retrievalRuntimeAllowed"));
        evidence.put("ciBlockingAllowed", bool(row, "ciBlockingAllowed"));
        evidence.put("requiresReviewedSourceEvidenceDigest", bool(row, "requiresReviewedSourceEvidenceDigest"));
        evidence.put("requiresReviewedMemoryLifecycleEvidence", bool(row,
            "requiresReviewedMemoryLifecycleEvidence"));
        evidence.put("requiresRealAuditCapture", bool(row, "requiresRealAuditCapture"));
        evidence.put("placeholderTraceIds", bool(row, "placeholderTraceIds"));
        evidence.put("failClosedWhenEmpty", bool(row, "failClosedWhenEmpty"));
        evidence.put("requestTraceIdOverrideAllowed", bool(row, "requestTraceIdOverrideAllowed"));
        evidence.put("runtimeExecutionAllowedNow", bool(row, "runtimeExecutionAllowedNow"));
        evidence.put("safetyProof", row.getOrDefault("safetyProof", Map.of()));
        return Map.copyOf(evidence);
    }

    private static Map<String, Object> suiteLatchCard(AgentMemoryRagTraceSetCurationContractResponse contract) {
        Map<String, Object> latch = contract != null ? contract.suiteRuntimeLatch() : Map.of();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "memory-rag-release-gate-runtime-latch");
        card.put("title", "Memory/RAG release gate runtime latch");
        card.put("status", bool(latch, "policyLatchDeclaredClosed") ? "RUNTIME_LATCH_CLOSED" : "POLICY_LATCH_MISCONFIGURED");
        card.put("severity", bool(latch, "policyLatchDeclaredClosed") ? "INFO" : "BLOCKING");
        card.put("suiteId", latch.getOrDefault("suiteId", "memory-rag-release-gate"));
        card.put("definedInCatalog", bool(latch, "definedInCatalog"));
        card.put("policyKeysPresent", bool(latch, "policyKeysPresent"));
        card.put("policyLatchDeclaredClosed", bool(latch, "policyLatchDeclaredClosed"));
        card.put("runtimeExecutionAllowedNow", false);
        card.put("latch", Map.copyOf(latch));
        card.put("readOnly", true);
        card.put("runtimeControlAllowed", false);
        return Map.copyOf(card);
    }

    private static List<String> buildRecommendedWorkflow() {
        return List.of(
            "memory-rag-readiness",
            "trace-set-curation-workbench-overview",
            "trace-set-curation-contract",
            "memory-rag-eval-suite-binding-contract",
            "git-review-redacted-trace-set-catalog-patch",
            "return-to-workbench-after-reviewed-trace-ids",
            "advisory-gate-bundle-review-before-ci-blocking"
        );
    }

    private static List<String> buildNextActions(AgentMemoryRagTraceSetCurationContractResponse contract,
                                                 int blockingCardCount) {
        List<String> actions = new ArrayList<>();
        if (contract == null) {
            actions.add("restore-memory-rag-trace-set-curation-contract");
            return List.copyOf(actions);
        }
        if (!contract.suiteRuntimePolicyClosed() || !contract.allRequiredTraceSetsPolicyClosed()) {
            actions.add("restore-policy-latches-through-git-review");
        }
        if (!contract.allRequiredTraceSetsDefined()) {
            actions.add("add-required-memory-rag-trace-set-catalog-rows");
        }
        if (!contract.reviewedTraceEvidenceCurated() || blockingCardCount > 0) {
            actions.add("render-blocking-cards-for-reviewed-trace-evidence-gaps");
            actions.add("curate-reviewed-redacted-trace-ids-through-human-git-review");
        }
        actions.add("keep-gate-bundle-runtime-action-disabled-in-vue");
        actions.add("keep-retrieval-and-ci-blocking-closed-until-separate-reviewed-slice");
        return List.copyOf(actions);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagTraceSetCurationWorkbenchOverview", OVERVIEW_ENDPOINT);
        endpoints.put("memoryRagTraceSetCurationContract",
            "/api/agent/observability/memory-rag/trace-set-curation-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memoryRagEvalSuiteBindingContract",
            "/api/agent/observability/memory-rag/eval-suite-binding-contract");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("traceSetGateBundle", disabledEndpoint(
            "POST",
            "/api/agent/observability/eval/trace-sets/gate-bundle",
            "after-reviewed-traces-and-separate-advisory-review"
        ));
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> disabledEndpoint(String method, String path, String intendedStage) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("intendedStage", intendedStage);
        endpoint.put("runtimeAllowedNow", false);
        endpoint.put("buttonEnabledNow", false);
        endpoint.put("buttonVisibleNow", false);
        return Map.copyOf(endpoint);
    }

    private static Map<String, Object> workbenchPolicy(
        AgentMemoryRagTraceSetCurationContractResponse curationContract,
        AgentMemoryRagEvalSuiteBindingContractResponse suiteBindingContract,
        AgentMemoryRagReadinessResponse memoryRagReadiness,
        List<Map<String, Object>> cards,
        int blockingCardCount
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager Memory/RAG trace-set curation workbench");
        policy.put("overviewOnly", true);
        policy.put("readOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("sourceReadModelsEmbedded", curationContract != null
            && suiteBindingContract != null
            && memoryRagReadiness != null);
        policy.put("curationContractEmbedded", curationContract != null);
        policy.put("suiteBindingContractEmbedded", suiteBindingContract != null);
        policy.put("memoryRagReadinessEmbedded", memoryRagReadiness != null);
        policy.put("curationCardCount", cards.size());
        policy.put("blockingCardCount", blockingCardCount);
        policy.put("traceIdsAcceptedFromCaller", false);
        policy.put("traceIdsVisibleInWorkbench", false);
        policy.put("candidateDiscoveryAllowedNow", false);
        policy.put("curationReviewAllowedNow", false);
        policy.put("traceSetGateAllowedNow", false);
        policy.put("gateBundleButtonEnabledNow", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requiresGitReview", true);
        policy.put("requiresHumanReview", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("retrievalRuntimeAllowedNow", false);
        policy.put("runtimeControlAllowed", false);
        policy.put("toolExecution", false);
        policy.put("safeToolExecutorInvocation", false);
        policy.put("kubeManagerCalls", false);
        policy.put("mcpToolCall", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> safety(AgentMemoryRagTraceSetCurationContractResponse contract) {
        Map<String, Object> sourceSafety = contract != null ? contract.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("overviewOnly", true);
        safety.put("vueWorkbenchOnly", true);
        safety.put("sourceContractReadOnly", bool(sourceSafety, "readOnly"));
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
        safety.put("dependencyUpgrade", false);
        safety.put("durableReceiptIssued", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentMemoryRagTraceSetCurationContractResponse curationContract,
                                               AgentMemoryRagEvalSuiteBindingContractResponse suiteBindingContract,
                                               AgentMemoryRagReadinessResponse memoryRagReadiness) {
        Map<String, Object> curationPrivacy = curationContract != null ? curationContract.privacy() : Map.of();
        Map<String, Object> bindingPrivacy = suiteBindingContract != null ? suiteBindingContract.privacy() : Map.of();
        Map<String, Object> readinessPrivacy = memoryRagReadiness != null ? memoryRagReadiness.privacy() : Map.of();
        boolean containsRawPrincipal = truthyAny("containsRawPrincipal", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsRawOrganization = truthyAny("containsRawOrganization", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsRawConversation = truthyAny("containsRawConversation", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsRawDocument = truthyAny("containsRawDocument", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsRawPrompt = truthyAny("containsRawPrompt", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsRawRetrievedChunk = truthyAny("containsRawRetrievedChunk", curationPrivacy, bindingPrivacy,
            readinessPrivacy);
        boolean containsAuthorizationHeader = truthyAny("containsAuthorizationHeader", curationPrivacy,
            bindingPrivacy, readinessPrivacy);
        boolean containsToken = truthyAny("containsToken", curationPrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsPassword = truthyAny("containsPassword", curationPrivacy, bindingPrivacy, readinessPrivacy);
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawDocument
            && !containsRawPrompt
            && !containsRawRetrievedChunk
            && !containsAuthorizationHeader
            && !containsToken
            && !containsPassword);
        privacy.put("traceIdsVisibleInWorkbench", false);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawDocument", containsRawDocument);
        privacy.put("containsRawPrompt", containsRawPrompt);
        privacy.put("containsRawRetrievedChunk", containsRawRetrievedChunk);
        privacy.put("containsAuthorizationHeader", containsAuthorizationHeader);
        privacy.put("containsToken", containsToken);
        privacy.put("containsPassword", containsPassword);
        privacy.put("containsEvalTracePayload", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean truthyAny(String key, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            if (bool(map, key)) {
                return true;
            }
        }
        return false;
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
