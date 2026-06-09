package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory/RAG eval gate contract for future retrieval and durable-memory release.
 *
 * <p>中文说明：这个响应只描述未来记忆/RAG 进入 prompt 前必须通过哪些评测门禁。
 * 它不会执行评测、不会读取 trace、不会运行检索，也不会调用模型或工具。</p>
 */
public record AgentMemoryRagEvalGateContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean evalGateContractDefined,
    boolean boundToEvalRuntime,
    boolean ciBlockingEnabled,
    boolean traceEvidenceCurated,
    boolean promptEvidenceAllowedNow,
    boolean retrievalRuntimeAllowedNow,
    List<Map<String, Object>> gateInputs,
    List<Map<String, Object>> gateChecks,
    List<Map<String, Object>> passCriteria,
    List<Map<String, Object>> failureClasses,
    List<String> blockedUntil,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> standardsAlignment,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-eval-gate-contract.v1";

    public static AgentMemoryRagEvalGateContractResponse of(Instant generatedAt) {
        return new AgentMemoryRagEvalGateContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "CONTRACT_DEFINED_NOT_BOUND",
            "deterministic Memory/RAG eval gate before prompt influence",
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            buildGateInputs(),
            buildGateChecks(),
            buildPassCriteria(),
            buildFailureClasses(),
            buildBlockedUntil(),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildStandardsAlignment(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildGateInputs() {
        return List.of(
            input("traceSetId", "Reviewed redacted trace-set id that represents the Memory/RAG behavior under test.", "caller-inline-trace"),
            input("evalSuiteId", "Server-owned suite id for Memory/RAG citation, privacy, tenant, and lifecycle checks.", "ad-hoc-prompt-score"),
            input("sourceEvidenceDigest", "M5.60 source evidence digest for every retrieved memory chunk.", "digestless-source"),
            input("durableLifecycleDigest", "M5.61 lifecycle evidence digest for retention, delete/export, and recovery proof.", "lifecycle-free-memory"),
            input("retrievalPolicyDigest", "Server-derived retrieval policy digest, including budget and source ACL rules.", "caller-retrieval-policy"),
            input("tenantPartitionDigest", "Tenant partition digest derived from trusted principal and organization facts.", "raw-principal-or-organization"),
            input("expectedCitationSeed", "Server-derived citation seed that future answers must cite.", "uncited-answer"),
            input("redactionPolicyDigest", "Digest of the policy that proves raw source or prompt text has been removed.", "raw-source-body")
        );
    }

    private static List<Map<String, Object>> buildGateChecks() {
        return List.of(
            check("citation-fidelity", "Every answer using memory must cite a source digest and chunk digest that match retrieved evidence."),
            check("source-digest-integrity", "Retrieved chunks must match server-derived source, chunk, and evidence digests."),
            check("privacy-leakage", "Responses and eval artifacts must not contain raw prompts, raw source bodies, tokens, passwords, or Authorization headers."),
            check("tenant-isolation", "Cross-tenant negative cases must prove memory cannot be reused outside its tenant partition."),
            check("retention-staleness", "Expired, stale, legally held, or retention-unknown memory must fail closed before prompt use."),
            check("delete-export-recovery-proof", "Deleted, exported, or recovered memory must carry tombstone/export/recovery proof before eligibility."),
            check("retrieval-policy-budget", "Retrieval must obey source ACL, top-k, token budget, freshness, and reranker budget policies."),
            check("unsupported-answer", "Answers with no cited source evidence must fail even if the text appears plausible."),
            check("prompt-injection-boundary", "Source text cannot grant Tool, kube-manager, MCP, HITL, or release authority.")
        );
    }

    private static List<Map<String, Object>> buildPassCriteria() {
        return List.of(
            criterion("minimum-score", "Future suite score must meet a server-owned threshold before retrieval can be enabled.", 95),
            criterion("fail-on-warning", "Warnings in privacy, tenant isolation, or citation fidelity must block release.", true),
            criterion("curated-trace-required", "Real redacted trace evidence must be curated through Git review before CI blocking.", true),
            criterion("empty-suite-fails-closed", "An empty Memory/RAG trace set cannot authorize retrieval runtime.", true),
            criterion("digest-mismatch-fails-closed", "Any source, chunk, lifecycle, or retrieval policy digest mismatch blocks prompt evidence.", true)
        );
    }

    private static List<Map<String, Object>> buildFailureClasses() {
        return List.of(
            failure("MISSING_CITATION", "Answer uses memory but omits citation id or digest evidence."),
            failure("SOURCE_DIGEST_MISMATCH", "Retrieved chunk does not match source evidence digest contract."),
            failure("TENANT_PARTITION_VIOLATION", "Memory crosses tenant or principal boundary."),
            failure("RAW_SECRET_OR_PROMPT_LEAK", "Eval artifact exposes raw prompt, source body, token, password, or Authorization header."),
            failure("RETENTION_OR_DELETE_PROOF_MISSING", "Memory lacks retention, tombstone, export, or recovery lifecycle evidence."),
            failure("STALE_MEMORY_USED", "Expired or stale memory influences an answer."),
            failure("POLICY_BUDGET_BYPASS", "Retrieval exceeds source ACL, freshness, top-k, token, or reranker budget."),
            failure("PROMPT_INJECTION_AUTHORITY_ESCALATION", "Retrieved source attempts to grant runtime Tool, kube-manager, MCP, HITL, or release authority.")
        );
    }

    private static List<String> buildBlockedUntil() {
        return List.of(
            "memory-rag-eval-suite-implemented",
            "reviewed-redacted-memory-trace-set-curated",
            "citation-fidelity-gate-bound",
            "privacy-leakage-gate-bound",
            "tenant-isolation-gate-bound",
            "retention-staleness-gate-bound",
            "delete-export-recovery-gate-bound",
            "ci-blocking-promotion-reviewed",
            "vue-memory-rag-eval-workbench-bound"
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "define-memory-rag-eval-gate-contract",
            "add-memory-rag-eval-suite-with-synthetic-red-team-cases",
            "curate-reviewed-redacted-memory-rag-trace-set",
            "bind-citation-fidelity-and-source-digest-checks",
            "bind-privacy-tenant-retention-staleness-checks",
            "bind-delete-export-recovery-lifecycle-checks",
            "promote-memory-rag-gate-bundle-to-reviewed-ci-blocking",
            "wire-vue-memory-rag-eval-workbench"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract");
        endpoints.put("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        endpoints.put("evalWorkbenchCapabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("evalTraceSetGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildStandardsAlignment() {
        Map<String, Object> standards = new LinkedHashMap<>();
        standards.put("openAiAgentsGuardrailsAndTracingReady", true);
        standards.put("mcpResourceAndToolBoundaryReady", true);
        standards.put("a2aArtifactGateEvidenceReady", true);
        standards.put("springAiVectorStoreEvalMetadataReady", true);
        standards.put("otelGenAiEvalAndRetrievalSpanReady", true);
        standards.put("runtimeBound", false);
        return Map.copyOf(standards);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("evalRuntimeExecuted", false);
        safety.put("ciBlockingChanged", false);
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
        safety.put("nimHpcSlurmBcmTouched", false);
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
        privacy.put("containsEvalTracePayload", false);
        privacy.put("containsRawScoreExplanation", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> input(String id, String purpose, String rejectedInput) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", id);
        input.put("purpose", purpose);
        input.put("required", true);
        input.put("rejectedInput", rejectedInput);
        input.put("runtimeBound", false);
        return Map.copyOf(input);
    }

    private static Map<String, Object> check(String id, String summary) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("summary", summary);
        check.put("required", true);
        check.put("runtimeBound", false);
        check.put("readOnly", true);
        return Map.copyOf(check);
    }

    private static Map<String, Object> criterion(String id, String summary, Object value) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("id", id);
        criterion.put("summary", summary);
        criterion.put("value", value);
        criterion.put("runtimeEnforced", false);
        return Map.copyOf(criterion);
    }

    private static Map<String, Object> failure(String id, String summary) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("id", id);
        failure.put("summary", summary);
        failure.put("blocksRuntime", true);
        failure.put("runtimeDetected", false);
        return Map.copyOf(failure);
    }
}
