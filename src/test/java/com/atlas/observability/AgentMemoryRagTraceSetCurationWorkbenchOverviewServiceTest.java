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
 * Memory/RAG trace-set curation workbench contract tests.
 */
class AgentMemoryRagTraceSetCurationWorkbenchOverviewServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagTraceSetCurationWorkbenchOverviewService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.java"
    );

    @Test
    void overview_shouldBuildVueReadModelWithoutRuntimeCurationActions() {
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
        AgentMemoryRagTraceSetCurationWorkbenchOverviewService service =
            new AgentMemoryRagTraceSetCurationWorkbenchOverviewService(
                new AgentMemoryRagTraceSetCurationContractService(traceSetCatalogService, suiteCatalogService),
                suiteBindingContractService,
                new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()),
                Clock.fixed(Instant.parse("2026-06-09T14:20:00Z"), ZoneOffset.UTC)
            );

        AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse overview = service.overview();

        assertThat(overview.schemaVersion())
            .isEqualTo("agent-memory-rag-trace-set-curation-workbench-overview.v1");
        assertThat(overview.generatedAt()).isEqualTo(Instant.parse("2026-06-09T14:20:00Z"));
        assertThat(overview.workbenchStatus()).isEqualTo("WORKBENCH_READY_TO_RENDER_REVIEWED_EVIDENCE_GAPS");
        assertThat(overview.frontendTarget())
            .isEqualTo("vue-kube-manager Memory/RAG trace-set curation workbench");
        assertThat(overview.phase1TopTierGoalPreserved()).isTrue();
        assertThat(overview.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(overview.sourceReadModelsEmbedded()).isTrue();
        assertThat(overview.runtimeControlAllowed()).isFalse();
        assertThat(overview.curationCardCount()).isEqualTo(3);
        assertThat(overview.blockingCardCount()).isEqualTo(3);
        assertThat(overview.requiredTraceSetCount()).isEqualTo(3);
        assertThat(overview.definedTraceSetCount()).isEqualTo(3);
        assertThat(overview.reviewedTraceSetCount()).isZero();
        assertThat(overview.curationCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(overview.curationCards()).allSatisfy(card -> {
            assertThat(card)
                .containsEntry("status", "REVIEWED_EVIDENCE_MISSING")
                .containsEntry("severity", "BLOCKING")
                .containsEntry("reviewedTraceIdsPresent", false)
                .containsEntry("traceIdCount", 0)
                .containsEntry("traceIdsVisibleInWorkbench", false)
                .containsEntry("policyLatchDeclaredClosed", true)
                .containsEntry("policyKeysPresent", true)
                .containsEntry("gitReviewRequired", true)
                .containsEntry("humanReviewRequired", true)
                .containsEntry("readOnly", true)
                .containsEntry("frontendNavigationOnly", true)
                .containsEntry("runtimeControlAllowed", false)
                .containsEntry("runtimeCatalogMutationAllowed", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false)
                .containsEntry("llmUsed", false);
            assertThat(card.get("missingEvidence").toString()).contains("trace-id");
            assertThat(card.get("blockedReasons").toString())
                .contains("reviewed-redacted-trace-ids-missing");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> disabledActions =
                (List<Map<String, Object>>) card.get("disabledRuntimeActions");
            assertThat(disabledActions).hasSize(3).allSatisfy(action -> assertThat(action)
                .containsEntry("enabledNow", false)
                .containsEntry("buttonVisibleNow", false));
            @SuppressWarnings("unchecked")
            Map<String, Object> renderHints = (Map<String, Object>) card.get("renderHints");
            assertThat(renderHints)
                .containsEntry("showRuntimeButton", false)
                .containsEntry("showTraceIdValues", false)
                .containsEntry("allowInlineCatalogEdit", false);
        });
        assertThat(overview.suiteLatchCard())
            .containsEntry("id", "memory-rag-release-gate-runtime-latch")
            .containsEntry("status", "RUNTIME_LATCH_CLOSED")
            .containsEntry("severity", "INFO")
            .containsEntry("policyLatchDeclaredClosed", true)
            .containsEntry("runtimeExecutionAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false);
        assertThat(overview.recommendedWorkflow()).containsExactly(
            "memory-rag-readiness",
            "trace-set-curation-workbench-overview",
            "review-memory-rag-trace-evidence-manifest",
            "trace-set-curation-contract",
            "memory-rag-eval-suite-binding-contract",
            "git-review-redacted-trace-set-catalog-patch",
            "return-to-workbench-after-reviewed-trace-ids",
            "advisory-gate-bundle-review-before-ci-blocking"
        );
        assertThat(overview.nextActions()).contains(
            "render-blocking-cards-for-reviewed-trace-evidence-gaps",
            "curate-reviewed-redacted-trace-ids-through-human-git-review",
            "keep-gate-bundle-runtime-action-disabled-in-vue",
            "keep-retrieval-and-ci-blocking-closed-until-separate-reviewed-slice"
        );
        assertThat(overview.endpointMap())
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest")
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        @SuppressWarnings("unchecked")
        Map<String, Object> gateBundleEndpoint =
            (Map<String, Object>) overview.endpointMap().get("traceSetGateBundle");
        assertThat(gateBundleEndpoint)
            .containsEntry("method", "POST")
            .containsEntry("runtimeAllowedNow", false)
            .containsEntry("buttonEnabledNow", false)
            .containsEntry("buttonVisibleNow", false);
        assertThat(overview.workbenchPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("overviewOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceReadModelsEmbedded", true)
            .containsEntry("curationCardCount", 3)
            .containsEntry("blockingCardCount", 3)
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("traceIdsVisibleInWorkbench", false)
            .containsEntry("candidateDiscoveryAllowedNow", false)
            .containsEntry("curationReviewAllowedNow", false)
            .containsEntry("traceSetGateAllowedNow", false)
            .containsEntry("gateBundleButtonEnabledNow", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("requiresHumanReview", true)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("retrievalRuntimeAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("overviewOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("auditWrite", false)
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
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("dependencyUpgrade", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInWorkbench", false)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsEvalTracePayload", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.curationContract().schemaVersion())
            .isEqualTo("agent-memory-rag-trace-set-curation-contract.v1");
        assertThat(overview.suiteBindingContract().schemaVersion())
            .isEqualTo("agent-memory-rag-eval-suite-binding-contract.v1");
        assertThat(overview.memoryRagReadiness().schemaVersion()).isEqualTo("agent-memory-rag-readiness.v1");
        assertThat(overview.toString())
            .contains("trace-set-curation-workbench", "memory-rag-citation-fidelity")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw-session-id");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidEvalRuntimeRetrievalOrCatalogMutation() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("curationContractService.contract()")
            .contains("suiteBindingContractService.contract()")
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
            .contains("trace-set-curation-workbench")
            .contains("disabledRuntimeActions")
            .doesNotContain("import org.springframework.ai")
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
    }
}
