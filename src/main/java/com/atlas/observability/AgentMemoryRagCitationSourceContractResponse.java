package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory/RAG citation and source contract for future retrieval evidence.
 *
 * <p>中文说明：这是未来 RAG 证据进入 prompt 前必须满足的“引用与来源”契约。
 * 它只描述字段、规则和阻断原因，不读取文档、不执行检索、不调用向量库或 LLM。</p>
 */
public record AgentMemoryRagCitationSourceContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean contractDefined,
    boolean boundToRetrievalRuntime,
    boolean citationRequired,
    boolean uncitedAnswerAllowed,
    boolean rawDocumentExposureAllowed,
    boolean promptEvidenceAllowedNow,
    List<Map<String, Object>> sourceEvidenceFields,
    List<Map<String, Object>> citationFields,
    List<Map<String, Object>> promptEvidenceRules,
    List<String> blockedUntil,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-citation-source-contract.v1";

    public static AgentMemoryRagCitationSourceContractResponse of(Instant generatedAt) {
        return new AgentMemoryRagCitationSourceContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "CONTRACT_DEFINED_NOT_BOUND",
            "cited, redacted, tenant-scoped Memory/RAG evidence contract",
            true,
            true,
            false,
            true,
            false,
            false,
            false,
            buildSourceEvidenceFields(),
            buildCitationFields(),
            buildPromptEvidenceRules(),
            buildBlockedUntil(),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildSourceEvidenceFields() {
        return List.of(
            field(
                "sourceId",
                "Stable source identity",
                true,
                "Server-derived stable id for a source document or memory record.",
                "caller-provided-source-id"
            ),
            field(
                "sourceType",
                "Source type",
                true,
                "Bounded enum such as kube-manager-doc, runbook, audit-summary, conversation-summary, or operator-note.",
                "free-form-source-type"
            ),
            field(
                "sourceDigest",
                "Source digest",
                true,
                "Server-derived SHA-256 digest over redacted source evidence; see source-evidence-digest-contract.",
                "raw-document-body"
            ),
            field(
                "tenantScope",
                "Tenant scope",
                true,
                "Trusted principal and organization scope proving the source may be retrieved for this user.",
                "raw-principal-or-organization"
            ),
            field(
                "redactionStatus",
                "Redaction status",
                true,
                "Machine-readable proof that secrets and sensitive payloads were removed before indexing or prompting.",
                "unredacted-source"
            ),
            field(
                "retentionPolicy",
                "Retention policy",
                true,
                "Retention/delete/export metadata for durable memory and document evidence.",
                "indefinite-unknown-retention"
            )
        );
    }

    private static List<Map<String, Object>> buildCitationFields() {
        return List.of(
            field(
                "citationId",
                "Citation identity",
                true,
                "Stable citation id emitted with an answer when retrieved evidence is used.",
                "caller-provided-citation-id"
            ),
            field(
                "sourceDigest",
                "Cited source digest",
                true,
                "Digest that links the answer citation back to the exact redacted source version.",
                "digestless-citation"
            ),
            field(
                "chunkDigest",
                "Chunk digest",
                true,
                "Digest of the redacted retrieved chunk or summary slice.",
                "raw-chunk-text"
            ),
            field(
                "retrievalReason",
                "Retrieval reason",
                true,
                "Bounded explanation of why the evidence was retrieved, without raw prompt text.",
                "raw-user-prompt"
            ),
            field(
                "freshness",
                "Freshness metadata",
                true,
                "Indexed-at and source-updated-at metadata used by stale retrieval evals.",
                "unknown-freshness"
            )
        );
    }

    private static List<Map<String, Object>> buildPromptEvidenceRules() {
        return List.of(
            rule(
                "source-evidence-digest-required",
                "Source, chunk, and evidence digests must be server-derived before any retrieved evidence can enter prompts.",
                true
            ),
            rule(
                "redacted-evidence-only",
                "Only redacted source snippets or summaries may enter prompts.",
                true
            ),
            rule(
                "citation-required-for-rag-answer",
                "Any answer influenced by retrieval must emit citations linked to source and chunk digests.",
                true
            ),
            rule(
                "tenant-scope-match-required",
                "Retrieved evidence must match the trusted principal and organization scope.",
                true
            ),
            rule(
                "prompt-evidence-budget-required",
                "Runtime prompts need a bounded evidence budget before retrieval can be enabled.",
                true
            ),
            rule(
                "eval-gate-required",
                "Citation fidelity, privacy leakage, tenant isolation, and stale retrieval evals must pass before runtime binding.",
                true
            )
        );
    }

    private static Map<String, Object> field(String id,
                                             String title,
                                             boolean required,
                                             String purpose,
                                             String rejectedInput) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", id);
        field.put("title", title);
        field.put("required", required);
        field.put("purpose", purpose);
        field.put("rejectedInput", rejectedInput);
        field.put("runtimeBound", false);
        return Map.copyOf(field);
    }

    private static Map<String, Object> rule(String id, String summary, boolean required) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("summary", summary);
        rule.put("required", required);
        rule.put("runtimeEnforced", false);
        rule.put("readOnly", true);
        return Map.copyOf(rule);
    }

    private static List<String> buildBlockedUntil() {
        return List.of(
            "durable-memory-store-bound",
            "tenant-aware-source-acl-bound",
            "redacted-ingestion-pipeline-bound",
            "retrieval-policy-bound",
            "citation-fidelity-eval-gate-bound",
            "privacy-leakage-eval-gate-bound",
            "vue-citation-workbench-bound"
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "implement-source-evidence-dto-and-digest-deriver",
            "bind-durable-memory-store-with-retention-delete-export-metadata",
            "add-redacted-kube-manager-doc-and-runbook-ingestion",
            "bind-tenant-aware-retrieval-policy-and-prompt-evidence-budget",
            "add-citation-fidelity-privacy-tenant-staleness-evals",
            "wire-vue-citation-source-workbench"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        endpoints.put("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("retrievalExecuted", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("llmUsed", false);
        safety.put("promptMutation", false);
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
}
