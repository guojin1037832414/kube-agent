package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 Memory/RAG readiness contract.
 *
 * <p>中文说明：该响应只描述“记忆与 RAG 能力是否具备上线条件”，不执行检索、
 * 不调用向量库、不调用 LLM，也不写入任何记忆。它把顶级 Agent 的学习能力拆成
 * 可测试、可审计、可给前端展示的准入契约。</p>
 */
public record AgentMemoryRagReadinessResponse(
    String schemaVersion,
    Instant generatedAt,
    String readinessVerdict,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean currentSafeSummaryMemoryEnabled,
    boolean durableMemoryReady,
    boolean ragReady,
    boolean citationContractReady,
    boolean evalCoverageReady,
    int currentMemoryUserCount,
    int maxSummariesPerUser,
    List<Map<String, Object>> readinessCards,
    List<String> blockingGaps,
    List<String> recommendedBuildOrder,
    Map<String, Object> currentEvidence,
    Map<String, Object> endpointMap,
    Map<String, Object> futureEnablementProtocol,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-readiness.v1";

    public static AgentMemoryRagReadinessResponse of(Instant generatedAt,
                                                     long currentMemoryUserCount,
                                                     int maxSummariesPerUser) {
        List<Map<String, Object>> cards = readinessCards(currentMemoryUserCount, maxSummariesPerUser);
        List<String> gaps = cards.stream()
            .filter(card -> !"READY".equals(card.get("status")))
            .map(card -> String.valueOf(card.get("id")))
            .toList();
        return new AgentMemoryRagReadinessResponse(
            SCHEMA_VERSION,
            generatedAt,
            "MEMORY_RAG_CONTRACT_DEFINED_NOT_READY",
            "durable, cited, tenant-isolated Memory/RAG learning layer",
            true,
            true,
            false,
            false,
            false,
            false,
            Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0, currentMemoryUserCount))),
            maxSummariesPerUser,
            cards,
            gaps,
            buildRecommendedBuildOrder(),
            currentEvidence(currentMemoryUserCount, maxSummariesPerUser),
            buildEndpointMap(),
            buildFutureEnablementProtocol(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> readinessCards(long currentMemoryUserCount, int maxSummariesPerUser) {
        return List.of(
            card(
                "safe-summary-memory",
                "Safe summary memory",
                "READY",
                "Current memory stores redacted conversation summaries by trusted principal username with bounded per-user retention.",
                List.of("Caffeine", "trusted principal ownership", "secret redaction", "bounded recent summaries"),
                Map.of(
                    "enabled", true,
                    "currentMemoryUserCount", Math.max(0, currentMemoryUserCount),
                    "maxSummariesPerUser", maxSummariesPerUser,
                    "storesRawConversation", false
                )
            ),
            card(
                "durable-memory-store",
                "Durable memory store",
                "BLOCKED",
                "Phase 1 still needs a durable store with retention, deletion, export metadata, and recovery semantics before memory can be called top-tier.",
                List.of("future JDBC/Redis/vector metadata store", "retention policy", "delete/export contract"),
                Map.of(
                    "durableStoreImplemented", false,
                    "retentionPolicyImplemented", false,
                    "deleteEndpointImplemented", false,
                    "exportEndpointImplemented", false
                )
            ),
            card(
                "tenant-and-privacy-governance",
                "Tenant and privacy governance",
                "PARTIAL",
                "Trusted principal ownership exists, but durable tenant isolation, per-source ACLs, and deletion proofs are not yet bound to a persistent store.",
                List.of("AgentPrincipalResolver", "tenant isolation", "source ACL", "right-to-delete proof"),
                Map.of(
                    "trustedPrincipalOwner", true,
                    "rawSessionIdAsOwner", false,
                    "persistentTenantPartition", false,
                    "deletionProof", false
                )
            ),
            card(
                "rag-retrieval-layer",
                "RAG retrieval layer",
                "BLOCKED",
                "No vector retrieval, reranker, corpus ingestion, or retrieval policy is bound to runtime prompts yet.",
                List.of("future Spring AI VectorStore", "embedding model", "reranker", "retrieval budget"),
                Map.of(
                    "vectorStoreBound", false,
                    "embeddingModelBound", false,
                    "rerankerBound", false,
                    "retrievalExecutedByReadinessEndpoint", false
                )
            ),
            card(
                "citation-and-source-contract",
                "Citation and source contract",
                "BLOCKED",
                "Future RAG answers must cite source documents, source type, digest, tenant scope, and redaction status before evidence can enter prompts.",
                List.of("citation contract", "source digest", "redacted evidence", "prompt evidence budget"),
                Map.of(
                    "citationContractImplemented", false,
                    "sourceDigestRequired", true,
                    "rawDocumentExposureAllowed", false,
                    "uncitedAnswerAllowed", false
                )
            ),
            card(
                "eval-and-observability",
                "Memory/RAG eval and observability",
                "BLOCKED",
                "Memory/RAG needs deterministic eval coverage for citation fidelity, privacy leakage, tenant isolation, and stale retrieval behavior.",
                List.of("deterministic eval", "red-team trace set", "privacy leakage check", "staleness check"),
                Map.of(
                    "memoryRagEvalSuiteExists", false,
                    "citationFidelityGateExists", false,
                    "privacyLeakageGateExists", false,
                    "retrievalTelemetryExists", false
                )
            )
        );
    }

    private static Map<String, Object> card(String id,
                                            String title,
                                            String status,
                                            String summary,
                                            List<String> technologyPoints,
                                            Map<String, Object> evidence) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("title", title);
        card.put("status", status);
        card.put("summary", summary);
        card.put("technologyPoints", List.copyOf(technologyPoints));
        card.put("evidence", Map.copyOf(evidence));
        card.put("readOnly", true);
        card.put("runtimeMutationAllowed", false);
        card.put("retrievalExecuted", false);
        card.put("llmUsed", false);
        card.put("kubeManagerCalls", false);
        return Map.copyOf(card);
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "define-memory-rag-citation-source-contract",
            "add-durable-memory-store-with-retention-delete-export-metadata",
            "bind-tenant-isolated-vector-store-through-reviewed-retrieval-policy",
            "add-redacted-runbook-and-kube-manager-doc-ingestion-pipeline",
            "add-memory-rag-eval-suite-for-citation-privacy-tenant-isolation-staleness",
            "wire-vue-memory-rag-readiness-workbench"
        );
    }

    private static Map<String, Object> currentEvidence(long currentMemoryUserCount, int maxSummariesPerUser) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("safeSummaryMemoryControllerExists", true);
        evidence.put("summaryEndpoint", "/api/agent/memory/summaries");
        evidence.put("authenticatedMemoryApi", true);
        evidence.put("trustedPrincipalOwner", true);
        evidence.put("rawSessionIdAsOwner", false);
        evidence.put("secretRedactionExists", true);
        evidence.put("storesFullConversation", false);
        evidence.put("currentMemoryUserCount", Math.max(0, currentMemoryUserCount));
        evidence.put("maxSummariesPerUser", maxSummariesPerUser);
        evidence.put("currentStore", "caffeine-in-memory");
        evidence.put("currentTtlDays", 30);
        evidence.put("durableStoreBound", false);
        evidence.put("vectorStoreBound", false);
        evidence.put("retrievalPolicyBound", false);
        evidence.put("citationContractBound", false);
        evidence.put("memoryRagEvalSuiteExists", false);
        evidence.put("vueWorkbenchBound", false);
        return Map.copyOf(evidence);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memorySummaries", "/api/agent/memory/summaries");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("evalWorkbenchCapabilities", "/api/agent/observability/eval/workbench/capabilities");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildFutureEnablementProtocol() {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("defaultMode", "safe-summary-memory-only");
        protocol.put("runtimeRagAllowedNow", false);
        protocol.put("requiresDurableStore", true);
        protocol.put("requiresTenantPartition", true);
        protocol.put("requiresCitationContract", true);
        protocol.put("requiresRedactedIngestion", true);
        protocol.put("requiresEvalGate", true);
        protocol.put("requiresFrontendGovernance", true);
        protocol.put("missingEvidenceOutcome", "fail-closed-no-retrieval");
        protocol.put("futureStableMainlineCandidates", List.of(
            "Spring AI VectorStore abstraction",
            "tenant-aware document metadata",
            "citation digest contract",
            "deterministic Memory/RAG eval suite"
        ));
        protocol.put("compatibilityMatrixCandidates", List.of(
            "GraphRAG",
            "reranker",
            "multi-vector retrieval",
            "hybrid lexical/vector search",
            "OpenTelemetry GenAI retrieval spans"
        ));
        return Map.copyOf(protocol);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("summaryOnly", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("llmUsed", false);
        safety.put("toolExecution", false);
        safety.put("kubeManagerCalls", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawSessionId", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawRetrievedChunk", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("rawDocumentExposureAllowed", false);
        return Map.copyOf(privacy);
    }
}
