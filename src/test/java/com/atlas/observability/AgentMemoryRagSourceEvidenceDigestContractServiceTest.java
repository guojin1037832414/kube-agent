package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG source evidence digest contract tests.
 */
class AgentMemoryRagSourceEvidenceDigestContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagSourceEvidenceDigestContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagSourceEvidenceDigestContractResponse.java"
    );

    @Test
    void contract_shouldExposeDigestDerivationContractWithoutRuntimeBinding() {
        AgentMemoryRagSourceEvidenceDigestContractService service =
            new AgentMemoryRagSourceEvidenceDigestContractService(
                Clock.fixed(Instant.parse("2026-06-09T02:00:00Z"), ZoneOffset.UTC),
                new com.atlas.memoryrag.MemoryRagSourceEvidenceDigestDeriver()
            );

        AgentMemoryRagSourceEvidenceDigestContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-source-evidence-digest-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T02:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.sourceEvidenceDigestDeriverDefined()).isTrue();
        assertThat(contract.boundToIngestionRuntime()).isFalse();
        assertThat(contract.boundToRetrievalRuntime()).isFalse();
        assertThat(contract.sampleUsesSyntheticEvidenceOnly()).isTrue();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.sampleDigest().sourceDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(contract.sampleDigest().chunkDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(contract.sampleDigest().evidenceDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(contract.digestInputs()).extracting(input -> input.get("id"))
            .containsExactly(
                "sourceId",
                "sourceType",
                "sourceVersion",
                "sourceUriDigest",
                "tenantScopeDigest",
                "sourceAclDigest",
                "redactionStatus",
                "redactionPolicyDigest",
                "retentionPolicy",
                "sourceContentDigest",
                "sourceMetadataDigest",
                "chunkContentDigest",
                "retrievalPolicyDigest"
            );
        assertThat(contract.digestOutputs()).extracting(output -> output.get("id"))
            .containsExactly("sourceDigest", "chunkDigest", "evidenceDigest", "citationSeed", "digestSource");
        assertThat(contract.enforcementRules()).extracting(rule -> rule.get("id"))
            .containsExactly(
                "sha256-only",
                "redacted-or-summary-only",
                "tenant-scope-bound",
                "citation-seed-server-derived",
                "runtime-binding-gated"
            );
        assertThat(contract.endpointMap())
            .containsEntry("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract")
            .containsEntry("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(contract.standardsAlignment())
            .containsEntry("springAiVectorStoreMetadataReady", true)
            .containsEntry("mcpResourceEvidenceBoundaryReady", true)
            .containsEntry("a2aTaskArtifactCorrelationReady", true)
            .containsEntry("otelGenAiRetrievalSpanReady", true)
            .containsEntry("runtimeBound", false);
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("sampleDerivationLocalOnly", true)
            .containsEntry("ingestionExecuted", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("promptMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("sampleSyntheticOnly", true)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsSourceBody", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("CONTRACT_DEFINED_NOT_BOUND", "sourceDigest", "citationSeed")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void source_shouldStayContractOnlyAndAvoidRagRuntimeCalls() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("syntheticSampleInput")
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("source-evidence-digest-contract")
            .contains("sampleSyntheticOnly")
            .doesNotContain("import org.springframework.ai.vectorstore")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("execute(")
            .doesNotContain("append(");
    }
}
