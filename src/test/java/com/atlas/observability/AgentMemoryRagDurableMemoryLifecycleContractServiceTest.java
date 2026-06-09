package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG durable memory lifecycle contract tests.
 */
class AgentMemoryRagDurableMemoryLifecycleContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagDurableMemoryLifecycleContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagDurableMemoryLifecycleContractResponse.java"
    );

    @Test
    void contract_shouldExposeDurableLifecycleEvidenceWithoutRuntimeBinding() {
        AgentMemoryRagDurableMemoryLifecycleContractService service =
            new AgentMemoryRagDurableMemoryLifecycleContractService(
                Clock.fixed(Instant.parse("2026-06-09T03:00:00Z"), ZoneOffset.UTC)
            );

        AgentMemoryRagDurableMemoryLifecycleContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-durable-memory-lifecycle-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T03:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.lifecycleContractDefined()).isTrue();
        assertThat(contract.boundToDurableStoreRuntime()).isFalse();
        assertThat(contract.retentionEnforcedNow()).isFalse();
        assertThat(contract.deleteEndpointImplemented()).isFalse();
        assertThat(contract.exportEndpointImplemented()).isFalse();
        assertThat(contract.recoveryCheckpointBound()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.lifecycleFields()).extracting(field -> field.get("id"))
            .containsExactly(
                "memoryRecordId",
                "tenantPartitionDigest",
                "sourceEvidenceDigest",
                "retentionPolicyId",
                "deleteProofDigest",
                "exportProofDigest",
                "recoveryCheckpointDigest",
                "evalGateDigest"
            );
        assertThat(contract.tenantPartitionRules()).extracting(rule -> rule.get("id"))
            .contains("trusted-principal-required", "tenant-digest-only", "source-acl-required");
        assertThat(contract.retentionRules()).extracting(rule -> rule.get("id"))
            .contains("retention-policy-required", "ttl-and-legal-hold-separated", "metadata-first-no-purge-now");
        assertThat(contract.deletionProofRules()).extracting(rule -> rule.get("id"))
            .contains("delete-tombstone-required", "delete-proof-digest-required", "delete-runtime-disabled-now");
        assertThat(contract.exportProofRules()).extracting(rule -> rule.get("id"))
            .contains("redacted-export-only", "export-manifest-required", "export-runtime-disabled-now");
        assertThat(contract.recoveryRules()).extracting(rule -> rule.get("id"))
            .contains("recovery-manifest-required", "vector-index-rebuild-proof-required", "recovery-runtime-disabled-now");
        assertThat(contract.evalGateRules()).extracting(rule -> rule.get("id"))
            .contains("citation-fidelity-required", "privacy-leakage-required", "tenant-isolation-required", "staleness-required");
        assertThat(contract.blockedUntil())
            .contains(
                "durable-store-implementation-bound",
                "tenant-partition-index-bound",
                "retention-policy-runtime-bound",
                "delete-tombstone-proof-bound",
                "redacted-export-manifest-bound",
                "recovery-checkpoint-bound",
                "memory-rag-eval-gate-bound",
                "vue-memory-lifecycle-workbench-bound"
            );
        assertThat(contract.recommendedBuildOrder()).containsExactly(
            "bind-durable-memory-store-with-tenant-partition-digest",
            "add-retention-policy-runtime-and-reviewed-purge-plan",
            "add-delete-tombstone-proof-contract-and-admin-review-flow",
            "add-redacted-export-manifest-contract-without-download-first",
            "add-recovery-checkpoint-manifest-and-vector-index-rebuild-proof",
            "add-memory-rag-lifecycle-eval-suite",
            "wire-vue-memory-lifecycle-workbench"
        );
        assertThat(contract.endpointMap())
            .containsEntry("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract")
            .containsEntry("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(contract.standardsAlignment())
            .containsEntry("springAiVectorStoreMetadataLifecycleReady", true)
            .containsEntry("mcpResourceLifecycleBoundaryReady", true)
            .containsEntry("a2aArtifactRetentionExportReady", true)
            .containsEntry("otelGenAiRetrievalLifecycleAttributesReady", true)
            .containsEntry("openAiAgentsTracingGuardrailLifecycleReady", true)
            .containsEntry("runtimeBound", false);
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("durableStoreCalls", false)
            .containsEntry("retentionJobExecuted", false)
            .containsEntry("deleteExecuted", false)
            .containsEntry("exportExecuted", false)
            .containsEntry("recoveryExecuted", false)
            .containsEntry("ingestionExecuted", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("promptMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawOrganization", false)
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsSourceBody", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("exportContainsRawData", false);
        assertThat(contract.toString())
            .contains("CONTRACT_DEFINED_NOT_BOUND", "tenantPartitionDigest", "deleteProofDigest", "exportProofDigest")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void source_shouldStayContractOnlyAndAvoidDurableRagRuntimeCalls() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentMemoryRagDurableMemoryLifecycleContractResponse.of")
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("durable-memory-lifecycle-contract")
            .contains("deleteProofDigest")
            .contains("exportProofDigest")
            .contains("recoveryCheckpointDigest")
            .doesNotContain("import org.springframework.ai.vectorstore")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
