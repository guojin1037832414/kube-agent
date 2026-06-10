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
 * Backend technology modernization decision contract tests.
 */
class AgentBackendTechnologyModernizationDecisionServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentBackendTechnologyModernizationDecisionService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentBackendTechnologyModernizationDecisionResponse.java"
    );

    @Test
    void decision_shouldKeepJavaSpringControlPlaneAndGateLatestTechnologiesByEvidence() {
        AgentBackendTechnologyModernizationDecisionService service = service(
            Clock.fixed(Instant.parse("2026-06-10T07:00:00Z"), ZoneOffset.UTC)
        );

        AgentBackendTechnologyModernizationDecisionResponse decision = service.decision();

        assertThat(decision.schemaVersion())
            .isEqualTo("agent-backend-technology-modernization-decision.v1");
        assertThat(decision.generatedAt()).isEqualTo(Instant.parse("2026-06-10T07:00:00Z"));
        assertThat(decision.decisionStatus())
            .isEqualTo("JAVA_SPRING_MAINLINE_ADVANCED_COMPATIBILITY_LANES_BLOCKED_BY_EVIDENCE");
        assertThat(decision.phase1TopTierGoalPreserved()).isTrue();
        assertThat(decision.javaBackendStillPreferred()).isTrue();
        assertThat(decision.javaSpringControlPlanePreserved()).isTrue();
        assertThat(decision.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(decision.mainlineRuntimeUpgradeAllowedNow()).isFalse();
        assertThat(decision.dependencyUpgradeAllowedNow()).isFalse();
        assertThat(decision.compatibilityBranchAllowed()).isTrue();
        assertThat(decision.runtimeControlAllowed()).isFalse();
        assertThat(decision.ciBlockingAllowedNow()).isFalse();
        assertThat(decision.officialSourceCount()).isEqualTo(8);
        assertThat(decision.mainlineDecisionCount()).isEqualTo(8);
        assertThat(decision.compatibilityLaneCount()).isEqualTo(10);
        assertThat(decision.blockedCompatibilityLaneCount()).isEqualTo(10);
        assertThat(decision.modernizationGateCount()).isEqualTo(8);
        assertThat(decision.blockedShortcutCount()).isEqualTo(9);
        assertThat(decision.learningStepCount()).isEqualTo(8);
        assertThat(decision.mainlineDecisions()).extracting(row -> row.get("id"))
            .containsExactly(
                "java-17-build-baseline",
                "spring-boot-3-5-control-plane",
                "spring-ai-1-1-access-layer",
                "safe-tool-executor-hitl-audit",
                "deterministic-eval-replay",
                "mcp-manifest-governance",
                "memory-rag-contract-stack",
                "vue-readonly-learning-workbench"
            );
        assertThat(decision.mainlineDecisions()).allSatisfy(row -> assertThat(row)
            .containsEntry("mainlineAllowedNow", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("dependencyUpgradeAllowedNow", false));
        assertThat(decision.compatibilityLaneDecisions()).extracting(row -> row.get("laneId"))
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
        assertThat(decision.compatibilityLaneDecisions()).allSatisfy(row -> assertThat(row)
            .containsEntry("blocked", true)
            .containsEntry("compatibilityBranchAllowed", true)
            .containsEntry("mainlineUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(decision.compatibilityLaneDecisions()).anySatisfy(row -> assertThat(row)
            .containsEntry("laneId", "java-runtime-toolchains")
            .containsEntry("decision", "COMPATIBILITY_BRANCH_REQUIRED_BEFORE_BASELINE_CHANGE"));
        assertThat(decision.compatibilityLaneDecisions()).anySatisfy(row -> assertThat(row)
            .containsEntry("laneId", "memory-rag-graphrag-reranker-vectorstore")
            .containsEntry("decision", "REVIEWED_MEMORY_RAG_TRACE_FIXTURES_FIRST"));
        assertThat(decision.modernizationGates()).extracting(row -> row.get("id"))
            .contains(
                "official-source-git-review",
                "current-mainline-green",
                "compatibility-branch-before-major-upgrade",
                "trace-audit-replay-eval-evidence",
                "phase2-domain-pause"
            );
        assertThat(decision.blockedShortcuts()).extracting(row -> row.get("id"))
            .contains(
                "replace-java-control-plane-with-agent-runtime",
                "blind-java-25-baseline-bump",
                "blind-spring-boot-4-mainline-bump",
                "open-mcp-tools-call-directly",
                "turn-ci-blocking-on-with-empty-fixtures"
            );
        assertThat(decision.learningPath()).extracting(row -> row.get("id"))
            .contains(
                "control-plane-thinking",
                "tool-authority",
                "compatibility-matrix",
                "memory-rag-governance",
                "operator-vue-workbench"
            );
        assertThat(decision.recommendedImplementationOrder()).contains(
            "publish-backend-technology-modernization-decision",
            "wire-vue-backend-modernization-decision-page",
            "run-java-21-compatibility-branch-before-baseline-change",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
        assertThat(decision.endpointMap())
            .containsEntry("backendTechnologyModernizationDecision",
                "/api/agent/observability/top-tier/backend-technology-modernization-decision")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch");
        assertThat(decision.decisionPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("decisionOnly", true)
            .containsEntry("javaBackendStillPreferred", true)
            .containsEntry("compatibilityBranchAllowed", true)
            .containsEntry("mainlineRuntimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(decision.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("decisionOnly", true)
            .containsEntry("sourceWatchReadOnly", true)
            .containsEntry("evidenceReadinessReadOnly", true)
            .containsEntry("compatibilityBranchCreationTriggered", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(decision.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(decision.sourceWatch().schemaVersion()).isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(decision.evidenceReadiness().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-evidence-readiness.v1");
        assertThat(decision.toString())
            .contains("Java 17", "Spring Boot 3.5", "Spring AI 1.1", "Java 21", "Java 25", "MCP", "A2A")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("officialVersionProtocolWatchService.watch()")
            .contains("evidenceReadinessService.readiness()")
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
            .doesNotContain(".curationReview(");
        assertThat(responseSource)
            .contains("backend-technology-modernization-decision")
            .contains("mainlineDecisions")
            .contains("compatibilityLaneDecisions")
            .contains("learningPath")
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
            .doesNotContain(".curationReview(");
    }

    private static AgentBackendTechnologyModernizationDecisionService service(Clock clock) {
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
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService =
            new AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
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
                Clock.fixed(Instant.parse("2026-06-10T06:00:00Z"), ZoneOffset.UTC)
            );
        return new AgentBackendTechnologyModernizationDecisionService(
            new AgentOfficialVersionProtocolWatchService(
                Clock.fixed(Instant.parse("2026-06-10T06:30:00Z"), ZoneOffset.UTC)
            ),
            evidenceReadinessService,
            clock
        );
    }
}
