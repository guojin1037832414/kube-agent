package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG trace-set curation contract tests.
 */
class AgentMemoryRagTraceSetCurationContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagTraceSetCurationContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagTraceSetCurationContractResponse.java"
    );

    @Test
    void contract_shouldExposeReviewedTraceGapsWithoutRunningEvalOrRetrieval() {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(reportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentMemoryRagTraceSetCurationContractService service =
            new AgentMemoryRagTraceSetCurationContractService(
                traceSetCatalogService,
                suiteCatalogService,
                Clock.fixed(Instant.parse("2026-06-09T13:30:00Z"), ZoneOffset.UTC)
            );

        AgentMemoryRagTraceSetCurationContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-trace-set-curation-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T13:30:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.curationContractDefined()).isTrue();
        assertThat(contract.reviewedTraceEvidenceCurated()).isFalse();
        assertThat(contract.allRequiredTraceSetsDefined()).isTrue();
        assertThat(contract.allRequiredTraceSetsPolicyClosed()).isTrue();
        assertThat(contract.suiteRuntimePolicyClosed()).isTrue();
        assertThat(contract.evalRuntimeAllowedNow()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.ciBlockingAllowedNow()).isFalse();
        assertThat(contract.requiredTraceSetCount()).isEqualTo(3);
        assertThat(contract.definedTraceSetCount()).isEqualTo(3);
        assertThat(contract.reviewedTraceSetCount()).isZero();
        assertThat(contract.traceSetRows()).extracting(row -> row.get("traceSetId"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(contract.suiteRuntimeLatch())
            .containsEntry("suiteId", "memory-rag-release-gate")
            .containsEntry("definedInCatalog", true)
            .containsEntry("policyKeysPresent", true)
            .containsEntry("policyLatchDeclaredClosed", true)
            .containsEntry("runtimeExecutionAllowed", false)
            .containsEntry("runtimeExecutionAllowedNow", false);
        assertThat(contract.suiteRuntimeLatch().get("missingPolicyKeys").toString()).isEqualTo("[]");
        assertThat(contract.traceSetRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("definedInCatalog", true)
            .containsEntry("rowStatus", "REVIEWED_EVIDENCE_MISSING")
            .containsEntry("reviewedTraceIdsPresent", false)
            .containsEntry("traceIdCount", 0)
            .containsEntry("traceIdsVisibleInContract", false)
            .containsEntry("policyKeysPresent", true)
            .containsEntry("policyLatchDeclaredClosed", true)
            .containsEntry("catalogOnlyUntilReviewed", true)
            .containsEntry("suiteRuntimeExecutionAllowed", false)
            .containsEntry("retrievalRuntimeAllowed", false)
            .containsEntry("ciBlockingAllowed", false)
            .containsEntry("requiresRealAuditCapture", true)
            .containsEntry("placeholderTraceIds", false)
            .containsEntry("failClosedWhenEmpty", true)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("runtimeExecutionAllowedNow", false)
            .containsEntry("gitReviewRequired", true)
            .containsEntry("humanReviewRequired", true)
            .containsEntry("runtimeCatalogMutationAllowed", false)
            .containsEntry("nextAction", "curate-reviewed-redacted-trace-ids-through-git-review"));
        assertThat(contract.traceSetRows()).allSatisfy(row -> {
            assertThat(row.get("missingPolicyKeys").toString()).isEqualTo("[]");
            assertThat(row.get("policyMismatches").toString()).isEqualTo("[]");
            assertThat(row.get("blockedReasons").toString()).contains("reviewed-redacted-trace-ids-missing");
        });
        assertThat(contract.traceSetRows())
            .filteredOn(row -> "memory-rag-citation-fidelity".equals(row.get("traceSetId")))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.get("missingEvidence").toString())
                    .contains("reviewed-redacted-citation-fidelity-trace-id", "source-evidence-digest-anchor");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> safetyProof = (java.util.Map<String, Object>) row.get("safetyProof");
                assertThat(safetyProof)
                    .containsEntry("retrievalExecuted", false)
                    .containsEntry("vectorStoreCalls", false)
                    .containsEntry("embeddingModelCalls", false)
                    .containsEntry("rerankerCalls", false)
                    .containsEntry("memoryWrite", false)
                    .containsEntry("auditWrite", false);
            });
        assertThat(contract.blockedReasons()).contains(
            "reviewed-redacted-memory-rag-trace-ids-missing",
            "memory-rag-advisory-gate-bundle-not-generated",
            "memory-rag-eval-runtime-not-promoted",
            "retrieval-runtime-intentionally-closed",
            "ci-blocking-switch-intentionally-absent"
        );
        assertThat(contract.blockedReasons())
            .doesNotContain("memory-rag-trace-set-catalog-rows-missing",
                "memory-rag-trace-set-runtime-policy-misconfigured",
                "memory-rag-suite-runtime-policy-misconfigured");
        assertThat(contract.recommendedBuildOrder()).contains(
            "curate-reviewed-redacted-trace-ids-for-citation-privacy-tenant-lifecycle",
            "render-vue-memory-rag-curation-contract-and-blockers"
        );
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract")
            .containsEntry("traceSetCatalog", "/api/agent/observability/eval/trace-sets")
            .containsKey("traceSetGateBundle");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> gateBundleEndpoint =
            (java.util.Map<String, Object>) contract.endpointMap().get("traceSetGateBundle");
        assertThat(gateBundleEndpoint)
            .containsEntry("method", "POST")
            .containsEntry("path", "/api/agent/observability/eval/trace-sets/gate-bundle")
            .containsEntry("intendedStage", "after-reviewed-traces")
            .containsEntry("runtimeAllowedNow", false);
        assertThat(contract.evidencePolicy())
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("emptyTraceIdsFailClosed", true)
            .containsEntry("missingPolicyKeyOutcome", "fail-closed-visible-blocker");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInContract", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("memory-rag-trace-set-curation-contract", "memory-rag-citation-fidelity")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void response_shouldFailClosedWhenTraceSetPolicyKeyIsMissing() {
        AgentEvalTraceSetCatalogResponse traceSetCatalog = AgentEvalTraceSetCatalogResponse.of(
            "test-catalog",
            List.of(
                traceSet("memory-rag-citation-fidelity", Map.of("catalogOnlyUntilReviewed", true)),
                traceSet("memory-rag-privacy-tenant", closedTraceSetPolicy()),
                traceSet("memory-rag-lifecycle-policy", closedTraceSetPolicy())
            ),
            privacyProof()
        );
        AgentEvalSuiteCatalogResponse suiteCatalog = AgentEvalSuiteCatalogResponse.of(
            List.of(memoryRagSuite(closedSuitePolicy())),
            privacyProof()
        );

        AgentMemoryRagTraceSetCurationContractResponse contract =
            AgentMemoryRagTraceSetCurationContractResponse.of(
                Instant.parse("2026-06-09T13:45:00Z"),
                traceSetCatalog,
                suiteCatalog
            );

        assertThat(contract.contractStatus()).isEqualTo("TRACE_SET_RUNTIME_POLICY_MISCONFIGURED");
        assertThat(contract.allRequiredTraceSetsPolicyClosed()).isFalse();
        assertThat(contract.suiteRuntimePolicyClosed()).isTrue();
        assertThat(contract.blockedReasons()).contains("memory-rag-trace-set-runtime-policy-misconfigured");
        assertThat(contract.traceSetRows())
            .filteredOn(row -> "memory-rag-citation-fidelity".equals(row.get("traceSetId")))
            .singleElement()
            .satisfies(row -> {
                assertThat(row)
                    .containsEntry("policyKeysPresent", false)
                    .containsEntry("policyLatchDeclaredClosed", false)
                    .containsEntry("rowStatus", "POLICY_LATCH_MISCONFIGURED");
                assertThat(row.get("missingPolicyKeys").toString())
                    .contains("requiresRealAuditCapture", "failClosedWhenEmpty", "ciBlockingAllowed");
                assertThat(row.get("blockedReasons").toString())
                    .contains("trace-set-policy-keys-missing", "trace-set-policy-latch-not-closed");
            });
    }

    @Test
    void response_shouldFailClosedWhenSuiteRuntimeLatchIsOpened() {
        Map<String, Object> openedSuitePolicy = new LinkedHashMap<>(closedSuitePolicy());
        openedSuitePolicy.put("runtimeExecutionAllowed", true);
        AgentEvalTraceSetCatalogResponse traceSetCatalog = AgentEvalTraceSetCatalogResponse.of(
            "test-catalog",
            List.of(
                traceSet("memory-rag-citation-fidelity", closedTraceSetPolicy()),
                traceSet("memory-rag-privacy-tenant", closedTraceSetPolicy()),
                traceSet("memory-rag-lifecycle-policy", closedTraceSetPolicy())
            ),
            privacyProof()
        );
        AgentEvalSuiteCatalogResponse suiteCatalog = AgentEvalSuiteCatalogResponse.of(
            List.of(memoryRagSuite(openedSuitePolicy)),
            privacyProof()
        );

        AgentMemoryRagTraceSetCurationContractResponse contract =
            AgentMemoryRagTraceSetCurationContractResponse.of(
                Instant.parse("2026-06-09T13:50:00Z"),
                traceSetCatalog,
                suiteCatalog
            );

        assertThat(contract.contractStatus()).isEqualTo("SUITE_RUNTIME_POLICY_MISCONFIGURED");
        assertThat(contract.suiteRuntimePolicyClosed()).isFalse();
        assertThat(contract.suiteRuntimeLatch())
            .containsEntry("definedInCatalog", true)
            .containsEntry("policyLatchDeclaredClosed", false)
            .containsEntry("runtimeExecutionAllowed", true)
            .containsEntry("runtimeExecutionAllowedNow", false);
        assertThat(contract.suiteRuntimeLatch().get("policyMismatches").toString())
            .contains("runtimeExecutionAllowed");
        assertThat(contract.blockedReasons()).contains("memory-rag-suite-runtime-policy-misconfigured");
    }

    @Test
    void source_shouldStayCatalogOnlyAndAvoidEvalRuntimeRetrievalOrCatalogMutation() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("evalTraceSetCatalogService.catalog()")
            .contains("evalSuiteCatalogService.catalog()")
            .doesNotContain(".gate(")
            .doesNotContain(".run(")
            .doesNotContain(".curationReview(")
            .doesNotContain(".candidate")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("trace-set-curation-contract")
            .contains("requiresGitReview")
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

    private static AgentEvalTraceSetDefinition traceSet(String id, Map<String, Object> policy) {
        return AgentEvalTraceSetDefinition.of(
            id,
            id,
            "test " + id,
            "Phase 1 top-tier kube-manager Agent Core",
            "memory-rag-release-gate",
            List.of(),
            List.of("reviewed redacted replay evidence"),
            List.of("phase1", "memory-rag"),
            policy,
            privacyProof()
        );
    }

    private static AgentEvalSuiteDefinition memoryRagSuite(Map<String, Object> guarantees) {
        return AgentEvalSuiteDefinition.of(
            "memory-rag-release-gate",
            "Memory/RAG Release Gate",
            "test suite",
            "Phase 1 top-tier kube-manager Agent Core",
            95,
            true,
            AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS,
            AgentEvalReportService.MAX_SUITE_CASES,
            List.of("MEMORY_RAG_CITATION_FIDELITY"),
            List.of("reviewed trace sets"),
            List.of("phase1", "memory-rag"),
            guarantees
        );
    }

    private static Map<String, Object> closedTraceSetPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("versionedSource", "test-catalog");
        policy.put("requiresRealAuditCapture", true);
        policy.put("requiresReviewedSourceEvidenceDigest", true);
        policy.put("requiresReviewedMemoryLifecycleEvidence", true);
        policy.put("placeholderTraceIds", false);
        policy.put("failClosedWhenEmpty", true);
        policy.put("requestTraceIdOverrideAllowed", false);
        policy.put("catalogOnlyUntilReviewed", true);
        policy.put("suiteRuntimeExecutionAllowed", false);
        policy.put("runtimeRetrievalAllowed", false);
        policy.put("ciBlockingAllowed", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> closedSuitePolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("redactedOnly", true);
        policy.put("catalogOnly", true);
        policy.put("runtimeExecutionAllowed", false);
        policy.put("requiresReviewedTraceSetsBeforeRun", true);
        policy.put("ciBlockingAllowed", false);
        policy.put("retrievalRuntimeAllowed", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof() {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", true);
        proof.put("containsRawPrincipal", false);
        proof.put("containsRawOrganization", false);
        proof.put("containsRawConversation", false);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }
}
