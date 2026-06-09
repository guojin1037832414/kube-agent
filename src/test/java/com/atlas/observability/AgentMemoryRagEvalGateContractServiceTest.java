package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG eval gate contract tests.
 */
class AgentMemoryRagEvalGateContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagEvalGateContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagEvalGateContractResponse.java"
    );

    @Test
    void contract_shouldExposeMemoryRagEvalGateWithoutRuntimeBinding() {
        AgentMemoryRagEvalGateContractService service = new AgentMemoryRagEvalGateContractService(
            Clock.fixed(Instant.parse("2026-06-09T04:00:00Z"), ZoneOffset.UTC)
        );

        AgentMemoryRagEvalGateContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-eval-gate-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T04:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.evalGateContractDefined()).isTrue();
        assertThat(contract.boundToEvalRuntime()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.traceEvidenceCurated()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.gateInputs()).extracting(input -> input.get("id"))
            .containsExactly(
                "traceSetId",
                "evalSuiteId",
                "sourceEvidenceDigest",
                "durableLifecycleDigest",
                "retrievalPolicyDigest",
                "tenantPartitionDigest",
                "expectedCitationSeed",
                "redactionPolicyDigest"
            );
        assertThat(contract.gateChecks()).extracting(check -> check.get("id"))
            .containsExactly(
                "citation-fidelity",
                "source-digest-integrity",
                "privacy-leakage",
                "tenant-isolation",
                "retention-staleness",
                "delete-export-recovery-proof",
                "retrieval-policy-budget",
                "unsupported-answer",
                "prompt-injection-boundary"
            );
        assertThat(contract.passCriteria()).extracting(criteria -> criteria.get("id"))
            .contains(
                "minimum-score",
                "fail-on-warning",
                "curated-trace-required",
                "empty-suite-fails-closed",
                "digest-mismatch-fails-closed"
            );
        assertThat(contract.failureClasses()).extracting(failure -> failure.get("id"))
            .contains(
                "MISSING_CITATION",
                "SOURCE_DIGEST_MISMATCH",
                "TENANT_PARTITION_VIOLATION",
                "RAW_SECRET_OR_PROMPT_LEAK",
                "RETENTION_OR_DELETE_PROOF_MISSING",
                "STALE_MEMORY_USED",
                "POLICY_BUDGET_BYPASS",
                "PROMPT_INJECTION_AUTHORITY_ESCALATION"
            );
        assertThat(contract.blockedUntil())
            .contains(
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
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract")
            .containsEntry("citationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("evalTraceSetGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        assertThat(contract.standardsAlignment())
            .containsEntry("openAiAgentsGuardrailsAndTracingReady", true)
            .containsEntry("mcpResourceAndToolBoundaryReady", true)
            .containsEntry("a2aArtifactGateEvidenceReady", true)
            .containsEntry("springAiVectorStoreEvalMetadataReady", true)
            .containsEntry("otelGenAiEvalAndRetrievalSpanReady", true)
            .containsEntry("runtimeBound", false);
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("ingestionExecuted", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("durableStoreCalls", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("promptMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
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
            .containsEntry("containsEvalTracePayload", false);
        assertThat(contract.toString())
            .contains("CONTRACT_DEFINED_NOT_BOUND", "citation-fidelity", "tenant-isolation", "MISSING_CITATION")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void source_shouldStayContractOnlyAndAvoidEvalRetrievalRuntimeCalls() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentMemoryRagEvalGateContractResponse.of")
            .doesNotContain("AgentEvalReportService")
            .doesNotContain("AgentEvalSuiteCatalogService")
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
            .contains("eval-gate-contract")
            .contains("citation-fidelity")
            .contains("tenant-isolation")
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
