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
 * Advanced technology evidence-readiness contract tests.
 */
class AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.java"
    );

    @Test
    void readiness_shouldMapEveryCompatibilityLaneToReviewedEvidenceGapsWithoutRuntimeActions() {
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService service = service(
            Clock.fixed(Instant.parse("2026-06-10T06:00:00Z"), ZoneOffset.UTC)
        );

        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse readiness =
            service.readiness();

        assertThat(readiness.schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-evidence-readiness.v1");
        assertThat(readiness.generatedAt()).isEqualTo(Instant.parse("2026-06-10T06:00:00Z"));
        assertThat(readiness.readinessStatus())
            .isEqualTo("EVIDENCE_READINESS_BLOCKED_BY_REVIEWED_TRACE_GAPS");
        assertThat(readiness.phase1TopTierGoalPreserved()).isTrue();
        assertThat(readiness.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(readiness.sourceMatrixEmbedded()).isTrue();
        assertThat(readiness.reviewedEvalEvidenceEmbedded()).isTrue();
        assertThat(readiness.memoryRagManifestEmbedded()).isTrue();
        assertThat(readiness.runtimeControlAllowed()).isFalse();
        assertThat(readiness.runtimeUpgradeAllowedNow()).isFalse();
        assertThat(readiness.dependencyUpgradeAllowedNow()).isFalse();
        assertThat(readiness.ciBlockingAllowedNow()).isFalse();
        assertThat(readiness.catalogMutationAllowed()).isFalse();
        assertThat(readiness.matrixItemCount()).isEqualTo(10);
        assertThat(readiness.evidenceRowCount()).isEqualTo(10);
        assertThat(readiness.blockedEvidenceRowCount()).isEqualTo(10);
        assertThat(readiness.reviewedTraceSetCount()).isZero();
        assertThat(readiness.reviewedTraceAnchorCount()).isZero();
        assertThat(readiness.memoryRagRequiredTraceSetCount()).isEqualTo(3);
        assertThat(readiness.memoryRagReviewedTraceSetCount()).isZero();
        assertThat(readiness.blockingGateRowCount()).isEqualTo(7);
        assertThat(readiness.disabledRuntimeActionCount()).isEqualTo(14);
        assertThat(readiness.matrixEvidenceRows()).extracting(row -> row.get("laneId"))
            .containsExactly(
                "java-runtime-toolchains",
                "spring-boot-framework",
                "spring-ai-access-layer",
                "openai-responses-agents",
                "mcp-runtime-call-plane",
                "a2a-multi-agent-provenance",
                "otel-genai-mcp-semconv",
                "memory-rag-graphrag-reranker-vectorstore",
                "kubernetes-manager-control-plane",
                "supply-chain-ci-quality"
            );
        assertThat(readiness.matrixEvidenceRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("blocked", true)
            .containsEntry("reviewedEvalTraceEvidenceRequired", true)
            .containsEntry("reviewedEvalTraceEvidenceReady", false)
            .containsEntry("officialSourceReviewRequired", true)
            .containsEntry("focusedCompatibilityTestsRequired", true)
            .containsEntry("vueVisibilityRequired", true)
            .containsEntry("humanGitReviewRequired", true)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false));
        Map<String, Object> memoryRow = readiness.matrixEvidenceRows().stream()
            .filter(row -> "memory-rag-graphrag-reranker-vectorstore".equals(row.get("laneId")))
            .findFirst()
            .orElseThrow();
        assertThat(memoryRow)
            .containsEntry("memoryRagManifestRequired", true)
            .containsEntry("memoryRagReviewedTraceEvidenceReady", false)
            .containsEntry("evidenceReadiness", "BLOCKED_BY_MEMORY_RAG_REVIEWED_TRACE_FIXTURES");
        assertThat(memoryRow.get("requiredEvidence").toString())
            .contains("memory-rag-reviewed-trace-fixtures", "citation-source-digest-evidence");
        assertThat(readiness.blockingGateRows()).extracting(row -> row.get("id"))
            .containsExactly(
                "source-matrix-present",
                "reviewed-eval-trace-evidence",
                "memory-rag-reviewed-fixtures",
                "runtime-authority-closed",
                "ci-blocking-release-decision",
                "vue-readonly-binding",
                "human-git-review"
            );
        assertThat(readiness.blockingGateRows()).anySatisfy(row -> assertThat(row)
            .containsEntry("id", "reviewed-eval-trace-evidence")
            .containsEntry("satisfied", false)
            .containsEntry("status", "BLOCKED_BY_MISSING_REVIEWED_EVIDENCE"));
        assertThat(readiness.disabledRuntimeActions()).extracting(row -> row.get("actionId"))
            .contains(
                "upgrade-pom-from-readiness-page",
                "enable-mcp-tools-call",
                "enable-rag-runtime",
                "enable-kube-manager-write-runtime",
                "enable-ci-blocking"
            );
        assertThat(readiness.disabledRuntimeActions()).allSatisfy(action -> assertThat(action)
            .containsEntry("enabledNow", false)
            .containsEntry("buttonVisibleNow", false)
            .containsEntry("clickHandlerAllowed", false));
        assertThat(readiness.nextActions()).contains(
            "capture-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "render-evidence-readiness-in-vue-without-enable-buttons",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
        assertThat(readiness.endpointMap())
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("reviewedEvalTraceEvidence",
                "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest");
        assertThat(readiness.readinessPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("evidenceReadinessOnly", true)
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(readiness.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("evidenceReadinessOnly", true)
            .containsEntry("sourceMatrixReadOnly", true)
            .containsEntry("reviewedEvalEvidenceReadOnly", true)
            .containsEntry("memoryRagManifestReadOnly", true)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(readiness.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(readiness.sourceMatrix().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(readiness.reviewedEvalTraceEvidence().schemaVersion())
            .isEqualTo("agent-reviewed-eval-trace-evidence.v1");
        assertThat(readiness.memoryRagReviewedTraceEvidenceManifest().schemaVersion())
            .isEqualTo("agent-memory-rag-reviewed-trace-evidence-manifest.v1");
        assertThat(readiness.toString())
            .contains("evidence-readiness", "memory-rag-reviewed-trace-fixtures", "enable-ci-blocking")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void response_shouldStillRequireSeparateReleaseReviewWhenSourcesContainReviewedEvidence() {
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                new AgentOfficialVersionProtocolWatchService(
                    Clock.fixed(Instant.parse("2026-06-10T01:00:00Z"), ZoneOffset.UTC)
                ),
                Clock.fixed(Instant.parse("2026-06-10T02:00:00Z"), ZoneOffset.UTC)
            ).matrix();
        AgentReviewedEvalTraceEvidenceResponse reviewedEvidence =
            AgentReviewedEvalTraceEvidenceResponse.of(
                Instant.parse("2026-06-10T03:00:00Z"),
                AgentEvalTraceSetCatalogResponse.of(
                    "test",
                    List.of(traceSet("phase1-core-golden"), traceSet("phase1-red-team-safety")),
                    privacy()
                )
            );
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryManifest =
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.of(
                Instant.parse("2026-06-10T04:00:00Z"),
                reviewedMemoryRagCurationContract(),
                new AgentMemoryRagSourceEvidenceDigestContractService().contract(),
                new AgentMemoryRagDurableMemoryLifecycleContractService().contract(),
                new AgentMemoryRagEvalGateContractService().contract(),
                new AgentMemoryRagEvalSuiteBindingContractService(
                    new AgentMemoryRagEvalGateContractService(),
                    new AgentEvalSuiteCatalogService(
                        new AgentEvalReportService(new AgentReplayTimelineService(
                            new com.atlas.audit.InMemoryAgentAuditRecorder()
                        ))
                    ),
                    new AgentEvalTraceSetCatalogService(
                        new AgentEvalSuiteCatalogService(
                            new AgentEvalReportService(new AgentReplayTimelineService(
                                new com.atlas.audit.InMemoryAgentAuditRecorder()
                            ))
                        ),
                        new ObjectMapper()
                    )
                ).contract(),
                new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()).readiness()
            );

        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse readiness =
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.of(
                Instant.parse("2026-06-10T05:00:00Z"),
                sourceMatrix,
                reviewedEvidence,
                memoryManifest
            );

        assertThat(readiness.readinessStatus())
            .isEqualTo("EVIDENCE_READY_FOR_SEPARATE_RELEASE_REVIEW");
        assertThat(readiness.blockedEvidenceRowCount()).isZero();
        assertThat(readiness.runtimeControlAllowed()).isFalse();
        assertThat(readiness.ciBlockingAllowedNow()).isFalse();
        assertThat(readiness.matrixEvidenceRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("blocked", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("ciBlockingAllowedNow", false));
        assertThat(readiness.nextActions()).containsExactly(
            "review-evidence-readiness-before-release-branch",
            "prepare-separate-release-gate-promotion",
            "keep-runtime-controls-hidden-until-release-review"
        );
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("compatibilityMatrixService.matrix()")
            .contains("reviewedEvalTraceEvidenceService.evidence()")
            .contains("memoryRagReviewedTraceEvidenceManifestService.manifest()")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(")
            .doesNotContain(".gate(")
            .doesNotContain(".gateBundle(")
            .doesNotContain(".curationReview(");
        assertThat(responseSource)
            .contains("evidence-readiness")
            .contains("matrixEvidenceRows")
            .contains("callerTraceIdsAccepted")
            .contains("catalogMutationAllowed")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(")
            .doesNotContain(".gate(")
            .doesNotContain(".gateBundle(")
            .doesNotContain(".curationReview(");
    }

    private static AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService service(Clock clock) {
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
        return new AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                new AgentOfficialVersionProtocolWatchService(),
                Clock.fixed(Instant.parse("2026-06-10T05:00:00Z"), ZoneOffset.UTC)
            ),
            new AgentReviewedEvalTraceEvidenceService(traceSetCatalogService,
                Clock.fixed(Instant.parse("2026-06-10T05:30:00Z"), ZoneOffset.UTC)),
            new AgentMemoryRagReviewedTraceEvidenceManifestService(
                new AgentMemoryRagTraceSetCurationContractService(traceSetCatalogService, suiteCatalogService),
                new AgentMemoryRagSourceEvidenceDigestContractService(),
                new AgentMemoryRagDurableMemoryLifecycleContractService(),
                evalGateContractService,
                suiteBindingContractService,
                new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()),
                Clock.fixed(Instant.parse("2026-06-10T05:45:00Z"), ZoneOffset.UTC)
            ),
            clock
        );
    }

    private static AgentEvalTraceSetDefinition traceSet(String id) {
        return AgentEvalTraceSetDefinition.of(
            id,
            id,
            "reviewed test trace set",
            "Phase 1 top-tier kube-manager Agent Core",
            "release-gate-strict",
            List.of("11111111111111111111111111111111"),
            List.of("Persisted redacted replay evidence."),
            List.of("phase1", "reviewed"),
            Map.of(
                "requiresRealAuditCapture", true,
                "placeholderTraceIds", false,
                "requestTraceIdOverrideAllowed", false
            ),
            privacy()
        );
    }

    private static AgentMemoryRagTraceSetCurationContractResponse reviewedMemoryRagCurationContract() {
        AgentEvalTraceSetCatalogResponse traceSetCatalog = AgentEvalTraceSetCatalogResponse.of(
            "test",
            List.of(
                memoryRagTraceSet("memory-rag-citation-fidelity"),
                memoryRagTraceSet("memory-rag-privacy-tenant"),
                memoryRagTraceSet("memory-rag-lifecycle-policy")
            ),
            privacy()
        );
        AgentEvalSuiteCatalogResponse suiteCatalog = AgentEvalSuiteCatalogResponse.of(
            List.of(memoryRagSuite()),
            privacy()
        );
        return AgentMemoryRagTraceSetCurationContractResponse.of(
            Instant.parse("2026-06-10T04:00:00Z"),
            traceSetCatalog,
            suiteCatalog
        );
    }

    private static AgentEvalTraceSetDefinition memoryRagTraceSet(String traceSetId) {
        return AgentEvalTraceSetDefinition.of(
            traceSetId,
            traceSetId,
            "reviewed Memory/RAG evidence",
            "Phase 1 top-tier kube-manager Agent Core",
            "memory-rag-release-gate",
            List.of("22222222222222222222222222222222"),
            List.of("Persisted redacted Memory/RAG replay evidence."),
            List.of("memory-rag", "reviewed"),
            traceSetPolicy(),
            privacy()
        );
    }

    private static AgentEvalSuiteDefinition memoryRagSuite() {
        return AgentEvalSuiteDefinition.of(
            "memory-rag-release-gate",
            "Memory/RAG release gate",
            "Reviewed Memory/RAG evidence gate",
            "PHASE_1",
            90,
            true,
            20,
            20,
            List.of("redacted-replay-only"),
            List.of("reviewed redacted Memory/RAG trace evidence"),
            List.of("memory-rag"),
            suitePolicy()
        );
    }

    private static Map<String, Object> traceSetPolicy() {
        return Map.ofEntries(
            Map.entry("requiresRealAuditCapture", true),
            Map.entry("placeholderTraceIds", false),
            Map.entry("failClosedWhenEmpty", true),
            Map.entry("requestTraceIdOverrideAllowed", false),
            Map.entry("catalogOnlyUntilReviewed", true),
            Map.entry("suiteRuntimeExecutionAllowed", false),
            Map.entry("runtimeRetrievalAllowed", false),
            Map.entry("ciBlockingAllowed", false)
        );
    }

    private static Map<String, Object> suitePolicy() {
        return Map.of(
            "catalogOnly", true,
            "runtimeExecutionAllowed", false,
            "requiresReviewedTraceSetsBeforeRun", true,
            "ciBlockingAllowed", false,
            "retrievalRuntimeAllowed", false
        );
    }

    private static Map<String, Object> privacy() {
        return Map.ofEntries(
            Map.entry("redactedOnly", true),
            Map.entry("containsRawPrincipal", false),
            Map.entry("containsRawOrganization", false),
            Map.entry("containsRawConversation", false),
            Map.entry("containsRawEndpoints", false),
            Map.entry("containsRawReason", false),
            Map.entry("containsRawParameterValues", false),
            Map.entry("containsRawPrompt", false),
            Map.entry("containsRawDocument", false),
            Map.entry("deterministic", true),
            Map.entry("llmUsed", false),
            Map.entry("externalCalls", false),
            Map.entry("toolExecution", false),
            Map.entry("kubeManagerCalls", false)
        );
    }
}
