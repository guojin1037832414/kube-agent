package com.atlas.observability;

import com.atlas.memory.ConversationSummaryMemoryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory/RAG reviewed trace-evidence manifest contract tests.
 */
class AgentMemoryRagReviewedTraceEvidenceManifestServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagReviewedTraceEvidenceManifestService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagReviewedTraceEvidenceManifestResponse.java"
    );

    @Test
    void manifest_shouldPublishTraceFixtureIntakePreflightWithoutRuntimeActions() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentMemoryRagEvalGateContractService evalGateContractService =
            new AgentMemoryRagEvalGateContractService();
        AgentMemoryRagEvalSuiteBindingContractService suiteBindingContractService =
            new AgentMemoryRagEvalSuiteBindingContractService(
                evalGateContractService,
                suiteCatalogService,
                traceSetCatalogService
            );
        AgentMemoryRagReviewedTraceEvidenceManifestService service =
            new AgentMemoryRagReviewedTraceEvidenceManifestService(
                new AgentMemoryRagTraceSetCurationContractService(traceSetCatalogService, suiteCatalogService),
                new AgentMemoryRagSourceEvidenceDigestContractService(),
                new AgentMemoryRagDurableMemoryLifecycleContractService(),
                evalGateContractService,
                suiteBindingContractService,
                new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()),
                Clock.fixed(Instant.parse("2026-06-09T15:30:00Z"), ZoneOffset.UTC)
            );

        AgentMemoryRagReviewedTraceEvidenceManifestResponse manifest = service.manifest();

        assertThat(manifest.schemaVersion())
            .isEqualTo("agent-memory-rag-reviewed-trace-evidence-manifest.v1");
        assertThat(manifest.generatedAt()).isEqualTo(Instant.parse("2026-06-09T15:30:00Z"));
        assertThat(manifest.manifestStatus()).isEqualTo("WAITING_FOR_REVIEWED_REDACTED_TRACE_FIXTURES");
        assertThat(manifest.phase1TopTierGoalPreserved()).isTrue();
        assertThat(manifest.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(manifest.sourceContractsEmbedded()).isTrue();
        assertThat(manifest.runtimeControlAllowed()).isFalse();
        assertThat(manifest.requiredTraceSetCount()).isEqualTo(3);
        assertThat(manifest.reviewedTraceSetCount()).isZero();
        assertThat(manifest.reviewedTraceAnchorCount()).isZero();
        assertThat(manifest.authoritativeFixtureCount()).isZero();
        assertThat(manifest.promotionReadyTraceSetCount()).isZero();
        assertThat(manifest.blockingRequirementCount()).isGreaterThan(0);
        assertThat(manifest.requiredTraceSets()).extracting(row -> row.get("traceSetId"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(manifest.requiredTraceSets()).allSatisfy(row -> assertThat(row)
            .containsEntry("catalogPatchTarget", "src/main/resources/observability/eval-trace-sets.json")
            .containsEntry("manifestRowStatus", "FIXTURE_NOT_PRESENT")
            .containsEntry("curatedTraceCount", 0)
            .containsEntry("traceIdsVisibleInManifest", false)
            .containsEntry("authoritativeFixturePresent", false)
            .containsEntry("safeToPromoteNow", false)
            .containsEntry("safeToRunEvalNow", false)
            .containsEntry("safeToEnableRetrievalNow", false)
            .containsEntry("safeToEnableCiBlockingNow", false)
            .containsEntry("humanGitReviewRequired", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("runtimeRetrievalAllowed", false)
            .containsEntry("ciBlockingAllowed", false));
        assertThat(manifest.requiredTraceSets()).allSatisfy(row -> {
            assertThat(row.get("missingEvidence").toString()).contains("trace-id");
            assertThat(row.get("requiredDigestEvidence").toString()).contains("Digest");
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) row.get("requiredTraceAnchorSchema");
            assertThat(schema)
                .containsEntry("rawValuesAllowed", false)
                .containsEntry("callerSubmittedToThisEndpoint", false);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> disabledActions =
                (List<Map<String, Object>>) row.get("disabledRuntimeActions");
            assertThat(disabledActions).hasSize(5).allSatisfy(action -> assertThat(action)
                .containsEntry("enabledNow", false)
                .containsEntry("buttonVisibleNow", false));
        });
        assertThat(manifest.evidenceIntakeSchema()).extracting(field -> field.get("id"))
            .contains("traceId", "sourceDigest", "tenantPartitionDigest", "retentionPolicyId");
        assertThat(manifest.evidenceIntakeSchema()).allSatisfy(field -> assertThat(field)
            .containsEntry("acceptedByThisEndpoint", false)
            .containsEntry("rawValueAllowed", false));
        assertThat(manifest.reviewWorkflow()).extracting(step -> step.get("id"))
            .containsExactly(
                "capture-redacted-replay-evidence",
                "verify-memory-rag-digest-evidence",
                "prepare-catalog-patch-review",
                "merge-through-human-git-review",
                "return-to-workbench",
                "keep-runtime-closed"
            );
        assertThat(manifest.advancedTechnologyMappings()).extracting(mapping -> mapping.get("id"))
            .contains(
                "spring-ai-memory-rag-vectorstore",
                "openai-agents-tracing-guardrails-evals",
                "mcp-2025-11-25-tools-resources-prompts",
                "otel-genai-semantic-conventions",
                "a2a-agent-card-task-artifact-provenance",
                "owasp-llm-risk-gates"
            );
        assertThat(manifest.nextActions()).contains(
            "capture-authoritative-redacted-memory-rag-trace-fixtures",
            "keep-eval-runtime-retrieval-vector-store-and-ci-blocking-closed",
            "do-not-touch-nim-hpc-slurm-bcm-phase2"
        );
        assertThat(manifest.endpointMap())
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview");
        assertThat(manifest.manifestPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("manifestOnly", true)
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("traceIdsVisibleInManifest", false)
            .containsEntry("authoritativeFixturesRequired", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("requiresSourceEvidenceDigest", true)
            .containsEntry("requiresMemoryLifecycleEvidence", true)
            .containsEntry("requiresTenantPrivacyEvidence", true)
            .containsEntry("evalRuntimeAllowedNow", false)
            .containsEntry("retrievalRuntimeAllowedNow", false)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(manifest.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("manifestOnly", true)
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("auditWrite", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("externalCalls", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(manifest.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInManifest", false)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsEvalTracePayload", false);
        assertThat(manifest.curationContract().schemaVersion())
            .isEqualTo("agent-memory-rag-trace-set-curation-contract.v1");
        assertThat(manifest.sourceEvidenceDigestContract().schemaVersion())
            .isEqualTo("agent-memory-rag-source-evidence-digest-contract.v1");
        assertThat(manifest.durableMemoryLifecycleContract().schemaVersion())
            .isEqualTo("agent-memory-rag-durable-memory-lifecycle-contract.v1");
        assertThat(manifest.evalGateContract().schemaVersion())
            .isEqualTo("agent-memory-rag-eval-gate-contract.v1");
        assertThat(manifest.evalSuiteBindingContract().schemaVersion())
            .isEqualTo("agent-memory-rag-eval-suite-binding-contract.v1");
        assertThat(manifest.memoryRagReadiness().schemaVersion()).isEqualTo("agent-memory-rag-readiness.v1");
        assertThat(manifest.toString())
            .contains("review-manifest", "memory-rag-citation-fidelity", "openai-agents")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw-session-id");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidRuntimeTraceFixturePromotion() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("curationContractService.contract()")
            .contains("sourceEvidenceDigestContractService.contract()")
            .contains("durableMemoryLifecycleContractService.contract()")
            .contains("evalGateContractService.contract()")
            .contains("evalSuiteBindingContractService.contract()")
            .contains("memoryRagReadinessService.readiness()")
            .doesNotContain(".gate(")
            .doesNotContain(".gateBundle(")
            .doesNotContain(".run(")
            .doesNotContain(".curationReview(")
            .doesNotContain(".candidate")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("record(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("review-manifest")
            .contains("advancedTechnologyMappings")
            .contains("traceIdsAcceptedFromCaller")
            .contains("catalogMutationAllowed")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("record(")
            .doesNotContain("recent(");
    }
}
