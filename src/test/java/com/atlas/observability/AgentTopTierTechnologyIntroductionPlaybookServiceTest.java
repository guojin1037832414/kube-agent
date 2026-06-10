package com.atlas.observability;

import com.atlas.memory.ConversationSummaryMemoryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-tier technology introduction playbook contract tests.
 */
class AgentTopTierTechnologyIntroductionPlaybookServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierTechnologyIntroductionPlaybookService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierTechnologyIntroductionPlaybookResponse.java"
    );

    @Test
    void playbook_shouldIntroduceLatestTechnologiesThroughEvidenceFirstGates() {
        AgentTopTierTechnologyIntroductionPlaybookService service = service(
            Clock.fixed(Instant.parse("2026-06-10T08:00:00Z"), ZoneOffset.UTC)
        );

        AgentTopTierTechnologyIntroductionPlaybookResponse playbook = service.playbook();

        assertThat(playbook.schemaVersion())
            .isEqualTo("agent-top-tier-technology-introduction-playbook.v1");
        assertThat(playbook.generatedAt()).isEqualTo(Instant.parse("2026-06-10T08:00:00Z"));
        assertThat(playbook.playbookStatus()).isEqualTo("PLAYBOOK_READY_EVIDENCE_GAPS_BLOCK_RUNTIME");
        assertThat(playbook.phase1TopTierGoalPreserved()).isTrue();
        assertThat(playbook.javaSpringControlPlanePreserved()).isTrue();
        assertThat(playbook.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(playbook.sourceWatchEmbedded()).isTrue();
        assertThat(playbook.compatibilityMatrixEmbedded()).isTrue();
        assertThat(playbook.evidenceReadinessEmbedded()).isTrue();
        assertThat(playbook.backendDecisionEmbedded()).isTrue();
        assertThat(playbook.runtimeControlAllowed()).isFalse();
        assertThat(playbook.runtimeUpgradeAllowedNow()).isFalse();
        assertThat(playbook.dependencyUpgradeAllowedNow()).isFalse();
        assertThat(playbook.ciBlockingAllowedNow()).isFalse();
        assertThat(playbook.officialSourceCount()).isEqualTo(8);
        assertThat(playbook.technologyLaneCount()).isEqualTo(10);
        assertThat(playbook.playbookStageCount()).isEqualTo(8);
        assertThat(playbook.releaseGateCount()).isEqualTo(10);
        assertThat(playbook.expertReviewRoundCount()).isEqualTo(6);
        assertThat(playbook.learningModuleCount()).isEqualTo(8);
        assertThat(playbook.forbiddenShortcutCount()).isEqualTo(10);
        assertThat(playbook.vueRouteCount()).isEqualTo(5);
        assertThat(playbook.officialSourceSnapshot()).extracting(source -> source.get("sourceId"))
            .contains("spring-ai-reference", "openai-responses-api", "openai-agents-sdk",
                "mcp-2025-11-25", "a2a-latest-spec", "otel-genai-semconv", "owasp-llm-top-10-2025");
        assertThat(playbook.officialSourceSnapshot()).allSatisfy(source -> assertThat(source)
            .containsEntry("officialSourceWinsOverConversationMemory", true)
            .containsEntry("runtimeBound", false));
        assertThat(playbook.technologyIntroductionStages()).extracting(stage -> stage.get("id"))
            .containsExactly(
                "official-source-watch",
                "compatibility-matrix",
                "evidence-readiness",
                "compatibility-branch",
                "focused-regression-tests",
                "vue-readonly-workbench",
                "multi-expert-release-review",
                "runtime-binding-slice"
            );
        assertThat(playbook.technologyLanePlaybookRows()).extracting(row -> row.get("laneId"))
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
        assertThat(playbook.technologyLanePlaybookRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("blocked", true)
            .containsEntry("compatibilityBranchRequired", true)
            .containsEntry("mainlineUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("ciBlockingAllowedNow", false));
        assertThat(playbook.technologyLanePlaybookRows()).anySatisfy(row -> assertThat(row)
            .containsEntry("laneId", "mcp-runtime-call-plane")
            .containsEntry("introductionMode", "MANIFEST_AND_GOVERNANCE_BEFORE_TOOLS_CALL"));
        assertThat(playbook.technologyLanePlaybookRows()).anySatisfy(row -> assertThat(row)
            .containsEntry("laneId", "memory-rag-graphrag-reranker-vectorstore")
            .containsEntry("introductionMode", "MEMORY_RAG_EVAL_FIXTURES_BEFORE_RETRIEVAL"));
        assertThat(playbook.technologyLanePlaybookRows()).anySatisfy(row -> assertThat(row)
            .containsEntry("laneId", "supply-chain-ci-quality")
            .containsEntry("introductionMode", "SBOM_DEPENDENCY_DIFF_AND_REVIEWED_TRACES_BEFORE_BLOCKING"));
        assertThat(playbook.releaseGateRows()).extracting(gate -> gate.get("id"))
            .contains("official-source-reviewed", "compatibility-branch-green",
                "safe-tool-authority-proven", "reviewed-trace-evidence-present",
                "vue-readonly-evidence-visible", "human-release-decision");
        assertThat(playbook.expertReviewRounds()).extracting(round -> round.get("id"))
            .containsExactly("architecture-review", "security-review", "frontend-vue-review",
                "eval-quality-review", "memory-rag-review", "release-manager-review");
        assertThat(playbook.learningModules()).extracting(module -> module.get("id"))
            .contains("official-source-literacy", "compatibility-matrix-practice",
                "mcp-a2a-protocol-governance", "advanced-memory-rag");
        assertThat(playbook.forbiddenShortcuts()).extracting(shortcut -> shortcut.get("id"))
            .contains("replace-java-spring-control-plane", "upgrade-pom-from-ui",
                "open-mcp-tools-call-before-consent", "enable-rag-before-reviewed-fixtures",
                "open-kube-manager-write-before-release-gate", "reopen-phase2-domain-plugins");
        assertThat(playbook.vueWorkbenchRequirements()).extracting(route -> route.get("id"))
            .containsExactly(
                "technology-introduction-playbook",
                "official-version-protocol-watch",
                "advanced-technology-compatibility-matrix",
                "advanced-technology-evidence-readiness",
                "backend-technology-modernization-decision"
            );
        assertThat(playbook.recommendedImplementationOrder()).containsExactly(
            "publish-top-tier-technology-introduction-playbook",
            "wire-vue-playbook-page",
            "keep-java-spring-control-plane-as-phase1-mainline",
            "populate-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "run-java-21-and-java-25-compatibility-branches-after-mainline-green",
            "run-spring-boot-4-and-spring-ai-2-compatibility-branches-after-source-review",
            "prototype-mcp-a2a-rag-only-after-safe-tool-executor-release-gates",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
        assertThat(playbook.endpointMap())
            .containsEntry("topTierTechnologyIntroductionPlaybook",
                "/api/agent/observability/top-tier/technology-introduction-playbook")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("backendTechnologyModernizationDecision",
                "/api/agent/observability/top-tier/backend-technology-modernization-decision");
        assertThat(playbook.playbookPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("playbookOnly", true)
            .containsEntry("officialSourceWinsOverConversationMemory", true)
            .containsEntry("compatibilityBranchRequiredBeforeMajorUpgrade", true)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(playbook.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("playbookOnly", true)
            .containsEntry("sourceWatchReadOnly", true)
            .containsEntry("compatibilityMatrixReadOnly", true)
            .containsEntry("evidenceReadinessReadOnly", true)
            .containsEntry("backendDecisionReadOnly", true)
            .containsEntry("compatibilityBranchCreationTriggered", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(playbook.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(playbook.sourceWatch().schemaVersion()).isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(playbook.compatibilityMatrix().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(playbook.evidenceReadiness().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-evidence-readiness.v1");
        assertThat(playbook.backendDecision().schemaVersion())
            .isEqualTo("agent-backend-technology-modernization-decision.v1");
        assertThat(playbook.toString())
            .contains("technology-introduction-playbook", "officialSourceWinsOverConversationMemory",
                "Spring AI 2.0.0-RC2", "MCP", "A2A", "GraphRAG")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("officialVersionProtocolWatchService.watch()")
            .contains("compatibilityMatrixService.matrix()")
            .contains("evidenceReadinessService.readiness()")
            .contains("backendTechnologyModernizationDecisionService.decision()")
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
            .contains("technology-introduction-playbook")
            .contains("technologyLanePlaybookRows")
            .contains("officialSourceWinsOverConversationMemory")
            .contains("expertReviewRounds")
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

    private static AgentTopTierTechnologyIntroductionPlaybookService service(Clock clock) {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-10T06:00:00Z"), ZoneOffset.UTC)
        );
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
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-10T06:15:00Z"), ZoneOffset.UTC)
            );
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService =
            new AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
                matrixService,
                new AgentReviewedEvalTraceEvidenceService(traceSetCatalogService,
                    Clock.fixed(Instant.parse("2026-06-10T06:30:00Z"), ZoneOffset.UTC)),
                new AgentMemoryRagReviewedTraceEvidenceManifestService(
                    new AgentMemoryRagTraceSetCurationContractService(traceSetCatalogService, suiteCatalogService),
                    new AgentMemoryRagSourceEvidenceDigestContractService(),
                    new AgentMemoryRagDurableMemoryLifecycleContractService(),
                    evalGateContractService,
                    suiteBindingContractService,
                    new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()),
                    Clock.fixed(Instant.parse("2026-06-10T06:45:00Z"), ZoneOffset.UTC)
                ),
                Clock.fixed(Instant.parse("2026-06-10T07:00:00Z"), ZoneOffset.UTC)
            );
        AgentBackendTechnologyModernizationDecisionService backendDecisionService =
            new AgentBackendTechnologyModernizationDecisionService(
                watchService,
                evidenceReadinessService,
                Clock.fixed(Instant.parse("2026-06-10T07:30:00Z"), ZoneOffset.UTC)
            );
        return new AgentTopTierTechnologyIntroductionPlaybookService(
            watchService,
            matrixService,
            evidenceReadinessService,
            backendDecisionService,
            clock
        );
    }
}
