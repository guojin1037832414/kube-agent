package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG eval-suite binding contract tests.
 */
class AgentMemoryRagEvalSuiteBindingContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagEvalSuiteBindingContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagEvalSuiteBindingContractResponse.java"
    );

    @Test
    void contract_shouldExposeSuiteBindingGapsWithoutRunningEvalOrRetrieval() {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(reportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentMemoryRagEvalSuiteBindingContractService service = new AgentMemoryRagEvalSuiteBindingContractService(
            new AgentMemoryRagEvalGateContractService(),
            suiteCatalogService,
            traceSetCatalogService,
            Clock.fixed(Instant.parse("2026-06-09T11:00:00Z"), ZoneOffset.UTC)
        );

        AgentMemoryRagEvalSuiteBindingContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-eval-suite-binding-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T11:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("SUITE_CHECKS_DEFINED_TRACE_SETS_NOT_CURATED");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.evalSuiteBindingContractDefined()).isTrue();
        assertThat(contract.memoryRagEvalSuiteBound()).isTrue();
        assertThat(contract.memoryRagTraceSetBound()).isFalse();
        assertThat(contract.reviewedTraceEvidenceRequired()).isTrue();
        assertThat(contract.evalRuntimeExecuted()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.requiredGateCheckCount()).isEqualTo(9);
        assertThat(contract.mappedGateCheckCount()).isEqualTo(9);
        assertThat(contract.missingGateCheckCount()).isZero();
        assertThat(contract.availableSuiteCount()).isEqualTo(5);
        assertThat(contract.availableTraceSetCount()).isEqualTo(4);
        assertThat(contract.bindingRows()).extracting(row -> row.get("gateCheckId"))
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
        assertThat(contract.bindingRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("suiteCheckCodePresent", true)
            .containsEntry("bindingStatus", "MAPPED")
            .containsEntry("runtimeBound", false)
            .containsEntry("blocksRetrievalRuntime", true));
        assertThat(contract.requiredTraceSets()).extracting(row -> row.get("traceSetId"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(contract.requiredTraceSets()).allSatisfy(row -> assertThat(row)
            .containsEntry("definedInCatalog", false)
            .containsEntry("reviewedTraceIdsPresent", false)
            .containsEntry("runtimeCatalogMutationAllowed", false));
        assertThat(contract.suiteCandidates()).extracting(row -> row.get("suiteId"))
            .contains("core-safety-smoke", "redaction-regression", "release-gate-strict", "memory-rag-release-gate");
        assertThat(contract.suiteCandidates())
            .filteredOn(row -> "memory-rag-release-gate".equals(row.get("suiteId")))
            .singleElement()
            .satisfies(row -> assertThat(row)
                .containsEntry("memoryRagSpecific", true)
                .containsEntry("eligibleForMemoryRagBinding", true)
                .containsEntry("checkCount", 9));
        assertThat(contract.blockedReasons()).doesNotContain("memory-rag-suite-check-codes-missing");
        assertThat(contract.blockedReasons()).contains(
            "memory-rag-suite-runtime-not-promoted",
            "memory-rag-trace-sets-not-curated",
            "reviewed-redacted-memory-rag-trace-evidence-missing",
            "ci-blocking-switch-intentionally-absent",
            "retrieval-runtime-intentionally-closed"
        );
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("evalSuiteCatalog", "/api/agent/observability/eval/suites")
            .containsEntry("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("evalSuiteRunInvoked", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("memory-rag-eval-suite-binding-contract", "MEMORY_RAG_CITATION_FIDELITY")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void source_shouldStayCatalogOnlyAndAvoidEvalRuntimeRetrievalOrCatalogMutation() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("evalGateContractService.contract()")
            .contains("evalSuiteCatalogService.catalog()")
            .contains("evalTraceSetCatalogService.catalog()")
            .doesNotContain(".run(")
            .doesNotContain(".gate(")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("eval-suite-binding-contract")
            .contains("MEMORY_RAG_CITATION_FIDELITY")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
