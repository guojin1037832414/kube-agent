package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable Memory/RAG lifecycle contract for future persistent memory storage.
 *
 * <p>中文说明：这个响应只描述未来“持久化记忆”必须具备的生命周期证据：
 * 租户分区、保留策略、删除证明、导出证明、恢复检查点、来源 ACL 和评测准入。
 * 它不创建数据库表、不写 memory、不执行删除/导出任务，也不调用向量库或 LLM。</p>
 */
public record AgentMemoryRagDurableMemoryLifecycleContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean lifecycleContractDefined,
    boolean boundToDurableStoreRuntime,
    boolean retentionEnforcedNow,
    boolean deleteEndpointImplemented,
    boolean exportEndpointImplemented,
    boolean recoveryCheckpointBound,
    boolean promptEvidenceAllowedNow,
    List<Map<String, Object>> lifecycleFields,
    List<Map<String, Object>> tenantPartitionRules,
    List<Map<String, Object>> retentionRules,
    List<Map<String, Object>> deletionProofRules,
    List<Map<String, Object>> exportProofRules,
    List<Map<String, Object>> recoveryRules,
    List<Map<String, Object>> evalGateRules,
    List<String> blockedUntil,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> standardsAlignment,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-durable-memory-lifecycle-contract.v1";

    public static AgentMemoryRagDurableMemoryLifecycleContractResponse of(Instant generatedAt) {
        return new AgentMemoryRagDurableMemoryLifecycleContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "CONTRACT_DEFINED_NOT_BOUND",
            "tenant-scoped durable Memory/RAG lifecycle contract",
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            buildLifecycleFields(),
            buildTenantPartitionRules(),
            buildRetentionRules(),
            buildDeletionProofRules(),
            buildExportProofRules(),
            buildRecoveryRules(),
            buildEvalGateRules(),
            buildBlockedUntil(),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildStandardsAlignment(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildLifecycleFields() {
        return List.of(
            field("memoryRecordId", "Stable server-derived durable memory id.", "caller-provided-memory-id"),
            field("tenantPartitionDigest", "Digest of principal, organization, and policy partition.", "raw-principal-or-organization"),
            field("sourceEvidenceDigest", "M5.60 evidence digest that links the memory record to redacted source custody.", "digestless-source"),
            field("retentionPolicyId", "Bounded retention policy id with TTL, legal hold, and purge semantics.", "unknown-retention"),
            field("deleteProofDigest", "Server-derived proof for future delete requests and tombstone records.", "caller-delete-claim"),
            field("exportProofDigest", "Server-derived proof for future redacted export jobs.", "raw-export-body"),
            field("recoveryCheckpointDigest", "Digest of recovery checkpoint metadata for restart-safe memory.", "volatile-only-memory"),
            field("evalGateDigest", "Digest of the eval gate bundle that allows memory to influence runtime prompts.", "unevaluated-memory")
        );
    }

    private static List<Map<String, Object>> buildTenantPartitionRules() {
        return List.of(
            rule("trusted-principal-required", "Durable memory ownership must come from AgentPrincipalResolver, not request fields."),
            rule("tenant-digest-only", "Read models expose tenant partition digests and never raw principal or organization identifiers."),
            rule("source-acl-required", "Every durable memory record must bind a source ACL digest before retrieval."),
            rule("cross-tenant-reuse-forbidden", "Memory records and retrieval chunks are not reusable across tenant partitions.")
        );
    }

    private static List<Map<String, Object>> buildRetentionRules() {
        return List.of(
            rule("retention-policy-required", "Every record must bind an explicit retention policy before persistence."),
            rule("ttl-and-legal-hold-separated", "TTL expiration and legal hold must be represented as separate server-side facts."),
            rule("purge-job-reviewed", "Any future purge job must be reviewed, auditable, and replayable before runtime binding."),
            rule("metadata-first-no-purge-now", "M5.61 publishes policy metadata only and does not delete stored data.")
        );
    }

    private static List<Map<String, Object>> buildDeletionProofRules() {
        return List.of(
            rule("delete-request-authenticated", "Delete requests must be authenticated, tenant-scoped, and source-bound."),
            rule("delete-tombstone-required", "A future delete must create a redacted tombstone proof before removing retrieval eligibility."),
            rule("delete-proof-digest-required", "Deletion proof must be digest-addressed and safe for audit/replay."),
            rule("delete-runtime-disabled-now", "No delete endpoint or deletion executor exists in M5.61.")
        );
    }

    private static List<Map<String, Object>> buildExportProofRules() {
        return List.of(
            rule("redacted-export-only", "Future exports may contain redacted summaries and digests, not raw prompts or source bodies."),
            rule("export-manifest-required", "Exports must include source digest, tenant partition digest, retention policy, and generated-at metadata."),
            rule("export-download-reviewed", "A download endpoint requires separate review and admin-only audit coverage."),
            rule("export-runtime-disabled-now", "No export endpoint, archive file, or download action exists in M5.61.")
        );
    }

    private static List<Map<String, Object>> buildRecoveryRules() {
        return List.of(
            rule("restart-safe-index-required", "Durable memory must survive process restart with a verifiable checkpoint digest."),
            rule("recovery-manifest-required", "Recovery must publish a manifest that can prove record count, tenant partitions, and policy versions."),
            rule("vector-index-rebuild-proof-required", "Future VectorStore indexes need rebuild proof before retrieval is enabled."),
            rule("recovery-runtime-disabled-now", "M5.61 does not create checkpoints or rebuild indexes.")
        );
    }

    private static List<Map<String, Object>> buildEvalGateRules() {
        return List.of(
            rule("citation-fidelity-required", "Retrieved memory must pass citation fidelity checks before prompt use."),
            rule("privacy-leakage-required", "Eval must prove no raw prompt, token, principal, organization, or source body leakage."),
            rule("tenant-isolation-required", "Eval must include cross-tenant negative cases."),
            rule("staleness-required", "Eval must catch stale or expired memory before retrieval is enabled.")
        );
    }

    private static List<String> buildBlockedUntil() {
        return List.of(
            "durable-store-implementation-bound",
            "tenant-partition-index-bound",
            "retention-policy-runtime-bound",
            "delete-tombstone-proof-bound",
            "redacted-export-manifest-bound",
            "recovery-checkpoint-bound",
            "memory-rag-eval-gate-bound",
            "vue-memory-lifecycle-workbench-bound"
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "bind-durable-memory-store-with-tenant-partition-digest",
            "add-retention-policy-runtime-and-reviewed-purge-plan",
            "add-delete-tombstone-proof-contract-and-admin-review-flow",
            "add-redacted-export-manifest-contract-without-download-first",
            "add-recovery-checkpoint-manifest-and-vector-index-rebuild-proof",
            "add-memory-rag-lifecycle-eval-suite",
            "wire-vue-memory-lifecycle-workbench"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildStandardsAlignment() {
        Map<String, Object> standards = new LinkedHashMap<>();
        standards.put("springAiVectorStoreMetadataLifecycleReady", true);
        standards.put("mcpResourceLifecycleBoundaryReady", true);
        standards.put("a2aArtifactRetentionExportReady", true);
        standards.put("otelGenAiRetrievalLifecycleAttributesReady", true);
        standards.put("openAiAgentsTracingGuardrailLifecycleReady", true);
        standards.put("runtimeBound", false);
        return Map.copyOf(standards);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("memoryWrite", false);
        safety.put("durableStoreCalls", false);
        safety.put("retentionJobExecuted", false);
        safety.put("deleteExecuted", false);
        safety.put("exportExecuted", false);
        safety.put("recoveryExecuted", false);
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
        privacy.put("exportContainsRawData", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> field(String id, String purpose, String rejectedInput) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", id);
        field.put("purpose", purpose);
        field.put("required", true);
        field.put("rejectedInput", rejectedInput);
        field.put("runtimeBound", false);
        return Map.copyOf(field);
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
}
