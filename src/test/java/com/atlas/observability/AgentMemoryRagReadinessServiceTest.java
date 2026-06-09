package com.atlas.observability;

import com.atlas.memory.ConversationSummaryMemoryStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG readiness contract tests.
 */
class AgentMemoryRagReadinessServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagReadinessService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagReadinessResponse.java"
    );

    @Test
    void readiness_shouldExposeBlockedRagContractWithoutRunningRetrieval() {
        ConversationSummaryMemoryStore store = new ConversationSummaryMemoryStore();
        store.append("alice", "conv-1", "安全摘要 token=secret password:abc");
        AgentMemoryRagReadinessService service = new AgentMemoryRagReadinessService(
            store,
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );

        AgentMemoryRagReadinessResponse readiness = service.readiness();

        assertThat(readiness.schemaVersion()).isEqualTo("agent-memory-rag-readiness.v1");
        assertThat(readiness.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(readiness.readinessVerdict()).isEqualTo("MEMORY_RAG_CONTRACT_DEFINED_NOT_READY");
        assertThat(readiness.phase1TopTierGoalPreserved()).isTrue();
        assertThat(readiness.currentSafeSummaryMemoryEnabled()).isTrue();
        assertThat(readiness.durableMemoryReady()).isFalse();
        assertThat(readiness.ragReady()).isFalse();
        assertThat(readiness.citationContractReady()).isFalse();
        assertThat(readiness.evalCoverageReady()).isFalse();
        assertThat(readiness.currentMemoryUserCount()).isEqualTo(1);
        assertThat(readiness.maxSummariesPerUser()).isEqualTo(ConversationSummaryMemoryStore.MAX_SUMMARIES_PER_USER);
        assertThat(readiness.readinessCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "safe-summary-memory",
                "durable-memory-store",
                "tenant-and-privacy-governance",
                "rag-retrieval-layer",
                "citation-and-source-contract",
                "eval-and-observability"
            );
        assertThat(readiness.blockingGaps()).containsExactly(
            "durable-memory-store",
            "tenant-and-privacy-governance",
            "rag-retrieval-layer",
            "citation-and-source-contract",
            "eval-and-observability"
        );
        assertThat(readiness.endpointMap())
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness")
            .containsEntry("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract")
            .containsEntry("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("memorySummaries", "/api/agent/memory/summaries");
        assertThat(readiness.currentEvidence())
            .containsEntry("safeSummaryMemoryControllerExists", true)
            .containsEntry("trustedPrincipalOwner", true)
            .containsEntry("rawSessionIdAsOwner", false)
            .containsEntry("durableMemoryLifecycleContractDefined", true)
            .containsEntry("durableMemoryLifecycleContractBound", false)
            .containsEntry("durableStoreBound", false)
            .containsEntry("vectorStoreBound", false)
            .containsEntry("citationSourceContractDefined", true)
            .containsEntry("sourceEvidenceDigestContractDefined", true)
            .containsEntry("sourceEvidenceDigestContractBound", false)
            .containsEntry("citationContractBound", false)
            .containsEntry("memoryRagEvalGateContractDefined", true)
            .containsEntry("memoryRagEvalGateContractBound", false)
            .containsEntry("memoryRagEvalSuiteBindingContractDefined", true)
            .containsEntry("memoryRagEvalSuiteBindingContractBound", false);
        assertThat(readiness.futureEnablementProtocol())
            .containsEntry("runtimeRagAllowedNow", false)
            .containsEntry("requiresDurableMemoryLifecycleContract", true)
            .containsEntry("requiresMemoryRagEvalGateContract", true)
            .containsEntry("requiresMemoryRagEvalSuiteBindingContract", true)
            .containsEntry("missingEvidenceOutcome", "fail-closed-no-retrieval");
        assertThat(readiness.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(readiness.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsToken", false);
        assertThat(readiness.toString())
            .contains("MEMORY_RAG_CONTRACT_DEFINED_NOT_READY", "safe-summary-memory", "durable-memory-store", "eval-and-observability")
            .doesNotContain("token=secret", "password:abc", "Bearer raw-secret-value", "raw-session-id");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidRagRuntimeCalls() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("memoryStore.userCount()")
            .doesNotContain("append(")
            .doesNotContain("recent(")
            .doesNotContain("import org.springframework.ai.vectorstore")
            .doesNotContain("import org.springframework.ai.embedding")
            .doesNotContain("vectorStore.")
            .doesNotContain("embeddingModel.")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping");
        assertThat(responseSource)
            .contains("fail-closed-no-retrieval")
            .contains("runtimeRagAllowedNow")
            .doesNotContain("import org.springframework.ai.vectorstore")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("execute(")
            .doesNotContain("append(");
    }
}
