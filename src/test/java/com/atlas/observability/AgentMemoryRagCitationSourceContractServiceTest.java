package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG citation-source contract tests.
 */
class AgentMemoryRagCitationSourceContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagCitationSourceContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagCitationSourceContractResponse.java"
    );

    @Test
    void contract_shouldDefineCitationSourceFieldsWithoutBindingRetrievalRuntime() {
        AgentMemoryRagCitationSourceContractService service =
            new AgentMemoryRagCitationSourceContractService(
                Clock.fixed(Instant.parse("2026-06-09T01:00:00Z"), ZoneOffset.UTC)
            );

        AgentMemoryRagCitationSourceContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-citation-source-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T01:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.contractDefined()).isTrue();
        assertThat(contract.boundToRetrievalRuntime()).isFalse();
        assertThat(contract.citationRequired()).isTrue();
        assertThat(contract.uncitedAnswerAllowed()).isFalse();
        assertThat(contract.rawDocumentExposureAllowed()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.sourceEvidenceFields()).extracting(field -> field.get("id"))
            .containsExactly(
                "sourceId",
                "sourceType",
                "sourceDigest",
                "tenantScope",
                "redactionStatus",
                "retentionPolicy"
            );
        assertThat(contract.citationFields()).extracting(field -> field.get("id"))
            .containsExactly(
                "citationId",
                "sourceDigest",
                "chunkDigest",
                "retrievalReason",
                "freshness"
            );
        assertThat(contract.promptEvidenceRules()).extracting(rule -> rule.get("id"))
            .containsExactly(
                "source-evidence-digest-required",
                "redacted-evidence-only",
                "citation-required-for-rag-answer",
                "tenant-scope-match-required",
                "prompt-evidence-budget-required",
                "eval-gate-required"
            );
        assertThat(contract.blockedUntil()).containsExactly(
            "durable-memory-store-bound",
            "tenant-aware-source-acl-bound",
            "redacted-ingestion-pipeline-bound",
            "retrieval-policy-bound",
            "citation-fidelity-eval-gate-bound",
            "privacy-leakage-eval-gate-bound",
            "vue-citation-workbench-bound"
        );
        assertThat(contract.endpointMap())
            .containsEntry("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("promptMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsSourceBody", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("CONTRACT_DEFINED_NOT_BOUND", "sourceDigest", "citation-required-for-rag-answer")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayContractOnlyAndAvoidRagRuntimeCalls() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("CONTRACT_DEFINED_NOT_BOUND")
            .contains("citation-required-for-rag-answer")
            .contains("redacted-evidence-only")
            .doesNotContain("import org.springframework.ai.vectorstore")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("execute(")
            .doesNotContain("append(");
    }
}
