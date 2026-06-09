package com.atlas.observability;

import com.atlas.memoryrag.MemoryRagSourceEvidenceDigestDeriver;
import com.atlas.memoryrag.MemoryRagSourceEvidenceDigestResult;
import com.atlas.memoryrag.MemoryRagSourceEvidenceInput;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only observability contract for Memory/RAG source evidence digests.
 *
 * <p>中文说明：这个响应把“未来 RAG 引用必须如何证明来源、租户、脱敏和片段身份”
 * 公开成管理员可读的合同。它可以计算一组非敏感样例摘要，但不会绑定真实 ingestion、
 * retrieval、VectorStore、embedding、reranker 或 LLM。</p>
 */
public record AgentMemoryRagSourceEvidenceDigestContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean sourceEvidenceDigestDeriverDefined,
    boolean boundToIngestionRuntime,
    boolean boundToRetrievalRuntime,
    boolean sampleUsesSyntheticEvidenceOnly,
    boolean promptEvidenceAllowedNow,
    MemoryRagSourceEvidenceDigestResult sampleDigest,
    List<Map<String, Object>> digestInputs,
    List<Map<String, Object>> digestOutputs,
    List<Map<String, Object>> enforcementRules,
    List<String> blockedUntil,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> standardsAlignment,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-source-evidence-digest-contract.v1";

    public static AgentMemoryRagSourceEvidenceDigestContractResponse of(Instant generatedAt,
                                                                        MemoryRagSourceEvidenceDigestResult sampleDigest) {
        return new AgentMemoryRagSourceEvidenceDigestContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "CONTRACT_DEFINED_NOT_BOUND",
            "server-derived source/chunk/evidence digest contract for cited Memory/RAG",
            true,
            true,
            false,
            false,
            true,
            false,
            sampleDigest,
            buildDigestInputs(),
            buildDigestOutputs(),
            buildEnforcementRules(),
            buildBlockedUntil(),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildStandardsAlignment(),
            buildSafety(),
            buildPrivacy()
        );
    }

    public static MemoryRagSourceEvidenceInput syntheticSampleInput() {
        return new MemoryRagSourceEvidenceInput(
            "kube-doc.sample",
            "kube-manager-doc",
            "v1",
            sha("01"),
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        );
    }

    private static List<Map<String, Object>> buildDigestInputs() {
        return List.of(
            input("sourceId", "Stable source identity; never unredacted source text."),
            input("sourceType", "Bounded source type such as kube-manager-doc, runbook, audit-summary, or operator-note."),
            input("sourceVersion", "Stable version of the redacted source representation."),
            input("sourceUriDigest", "SHA-256 digest of the source location or logical URI after redaction."),
            input("tenantScopeDigest", "SHA-256 digest of trusted tenant/principal scope, not raw tenant IDs."),
            input("sourceAclDigest", "SHA-256 digest of source-level access policy."),
            input("redactionStatus", "Bounded status proving raw sensitive material was removed or never present."),
            input("redactionPolicyDigest", "SHA-256 digest of the redaction policy version."),
            input("retentionPolicy", "Bounded retention/delete/export policy marker."),
            input("sourceContentDigest", "SHA-256 digest of the redacted source body or summary."),
            input("sourceMetadataDigest", "SHA-256 digest of redacted source metadata."),
            input("chunkContentDigest", "SHA-256 digest of the redacted retrieved chunk or summary slice."),
            input("retrievalPolicyDigest", "SHA-256 digest of retrieval budget and ranking policy.")
        );
    }

    private static List<Map<String, Object>> buildDigestOutputs() {
        return List.of(
            output("sourceDigest", "Binds source identity, tenant scope, ACL, redaction, retention, content, and metadata."),
            output("chunkDigest", "Binds sourceDigest to a redacted chunk and retrieval policy."),
            output("evidenceDigest", "Binds the full evidence envelope used by future citations and evals."),
            output("citationSeed", "Server-derived citation seed; not caller supplied."),
            output("digestSource", MemoryRagSourceEvidenceDigestDeriver.DIGEST_SOURCE)
        );
    }

    private static List<Map<String, Object>> buildEnforcementRules() {
        return List.of(
            rule("sha256-only", "All URI, tenant, ACL, policy, content, metadata, chunk, and retrieval fields must be SHA-256 digests."),
            rule("redacted-or-summary-only", "Unredacted source body, prompt, retrieved chunk, tenant id, token, and Authorization header are rejected."),
            rule("tenant-scope-bound", "Evidence is not reusable across tenant scopes."),
            rule("citation-seed-server-derived", "Citation identity must be derived from server evidence, not prompt text or caller input."),
            rule("runtime-binding-gated", "Ingestion/retrieval binding remains blocked until durable store, ACL, eval, and observability gates exist.")
        );
    }

    private static List<String> buildBlockedUntil() {
        return List.of(
            "durable-memory-store-bound",
            "source-ingestion-runtime-bound",
            "tenant-aware-source-acl-bound",
            "retrieval-policy-bound",
            "citation-fidelity-eval-gate-bound",
            "privacy-leakage-eval-gate-bound",
            "otel-genai-retrieval-spans-bound",
            "vue-citation-workbench-bound"
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "bind-durable-memory-store-with-retention-delete-export-metadata",
            "add-redacted-kube-manager-doc-and-runbook-ingestion",
            "bind-tenant-aware-retrieval-policy-and-prompt-evidence-budget",
            "add-source-digest-to-citation-fidelity-privacy-tenant-staleness-evals",
            "emit-opentelemetry-genai-retrieval-and-citation-spans",
            "wire-vue-citation-source-workbench"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildStandardsAlignment() {
        Map<String, Object> standards = new LinkedHashMap<>();
        standards.put("springAiVectorStoreMetadataReady", true);
        standards.put("mcpResourceEvidenceBoundaryReady", true);
        standards.put("a2aTaskArtifactCorrelationReady", true);
        standards.put("otelGenAiRetrievalSpanReady", true);
        standards.put("openAiAgentsGuardrailEvidenceReady", true);
        standards.put("runtimeBound", false);
        return Map.copyOf(standards);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("sampleDerivationLocalOnly", true);
        safety.put("ingestionExecuted", false);
        safety.put("retrievalExecuted", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("llmUsed", false);
        safety.put("promptMutation", false);
        safety.put("toolExecution", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("sampleSyntheticOnly", true);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawRetrievedChunk", false);
        privacy.put("containsSourceBody", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> input(String id, String purpose) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", id);
        input.put("purpose", purpose);
        input.put("required", true);
        input.put("rawValueAllowed", false);
        input.put("runtimeBound", false);
        return Map.copyOf(input);
    }

    private static Map<String, Object> output(String id, String purpose) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("id", id);
        output.put("purpose", purpose);
        output.put("serverDerived", true);
        output.put("callerProvided", false);
        output.put("runtimeBound", false);
        return Map.copyOf(output);
    }

    private static Map<String, Object> rule(String id, String summary) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("summary", summary);
        rule.put("required", true);
        rule.put("runtimeEnforced", false);
        rule.put("readOnly", true);
        return Map.copyOf(rule);
    }

    private static String sha(String suffix) {
        return "sha256:" + "0".repeat(62) + suffix;
    }
}
