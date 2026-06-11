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
 * Multi-agent review aggregate contract tests.
 */
class AgentMultiAgentReviewServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMultiAgentReviewService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentMultiAgentReviewResponse.java"
    );

    @Test
    void review_shouldAggregateExpertReviewEvidenceWithoutRuntimeAuthority() {
        AgentMultiAgentReviewService service = service(
            Clock.fixed(Instant.parse("2026-06-11T08:00:00Z"), ZoneOffset.UTC)
        );

        AgentMultiAgentReviewResponse review = service.review();

        assertThat(review.schemaVersion()).isEqualTo("agent-multi-agent-review.v1");
        assertThat(review.generatedAt()).isEqualTo(Instant.parse("2026-06-11T08:00:00Z"));
        assertThat(review.reviewStatus()).isEqualTo("MULTI_AGENT_REVIEW_READY_RUNTIME_HANDOFF_CLOSED");
        assertThat(review.frontendTarget()).isEqualTo("vue-kube-manager multi-agent expert review board");
        assertThat(review.phase1TopTierGoalPreserved()).isTrue();
        assertThat(review.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(review.playbookEmbedded()).isTrue();
        assertThat(review.phase1RoadmapEmbedded()).isTrue();
        assertThat(review.compatibilityEvidenceEmbedded()).isTrue();
        assertThat(review.officialWatchDashboardEmbedded()).isTrue();
        assertThat(review.backendDecisionEmbedded()).isTrue();
        assertThat(review.runtimeControlAllowed()).isFalse();
        assertThat(review.a2aRuntimeHandoffAllowed()).isFalse();
        assertThat(review.mcpToolsCallAllowed()).isFalse();
        assertThat(review.toolExecutionAllowed()).isFalse();
        assertThat(review.expertReviewRoundCount()).isEqualTo(6);
        assertThat(review.roadmapStepCount()).isEqualTo(8);
        assertThat(review.a2aEvidenceRowCount()).isEqualTo(5);
        assertThat(review.reviewGateCount()).isEqualTo(40);
        assertThat(review.blockedRuntimeShortcutCount()).isEqualTo(25);
        assertThat(review.disabledRuntimeActionCount()).isEqualTo(30);
        assertThat(review.expertReviewRounds()).extracting(row -> row.get("id"))
            .containsExactly("architecture-review", "security-review", "frontend-vue-review",
                "eval-quality-review", "memory-rag-review", "release-manager-review");
        assertThat(review.expertReviewRounds()).allSatisfy(row -> assertThat(row)
            .containsEntry("requiredBeforeRuntimeBinding", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("toolExecutionAllowed", false)
            .containsEntry("readOnly", true));
        assertThat(review.orchestrationReviewRows()).extracting(row -> row.get("stepId"))
            .containsExactly(
                "vue-readiness-control-plane",
                "reviewed-eval-trace-evidence",
                "release-blocking-eval-gates",
                "memory-rag-eval-suite-binding",
                "durable-memory-store-binding",
                "retrieval-runtime-binding",
                "mcp-runtime-safe-call-plane",
                "agent-handoff-and-a2a-provenance"
            );
        assertThat(review.orchestrationReviewRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("mcpToolsCallAllowed", false)
            .containsEntry("toolExecutionAllowed", false)
            .containsEntry("readOnly", true));
        assertThat(review.a2aProvenanceRows()).extracting(row -> row.get("source"))
            .containsExactly(
                "technology-introduction-playbook",
                "phase1-execution-roadmap",
                "advanced-technology-evidence-readiness",
                "official-version-protocol-watch-dashboard",
                "backend-technology-modernization-decision"
            );
        assertThat(review.a2aProvenanceRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("agentCardRuntimeExportAllowed", false)
            .containsEntry("taskRuntimeHandoffAllowed", false)
            .containsEntry("artifactDigestRequired", true)
            .containsEntry("localAuthorityRequired", true)
            .containsEntry("traceAuditReplayRequired", true)
            .containsEntry("evalCoverageRequired", true)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("readOnly", true));
        assertThat(review.reviewGateRows()).extracting(row -> row.get("id"))
            .contains("safe-tool-authority-proven", "eval-before-runtime",
                "reviewed-eval-trace-evidence", "safe-authority-boundary",
                "trace-audit-replay-eval-evidence");
        assertThat(review.blockedRuntimeShortcuts()).extracting(row -> row.get("id"))
            .contains("open-mcp-tools-call-before-consent", "run-a2a-handoff-before-provenance",
                "direct-a2a-handoff-authority", "direct-retrieval-prompt-influence",
                "reopen-phase2-domain-plugins");
        assertThat(review.disabledRuntimeActions()).extracting(row -> row.get("actionId"))
            .contains("run-a2a-runtime-handoff", "call-mcp-tools", "execute-agent-tool",
                "run-retrieval-runtime", "upgrade-dependencies", "mutate-kube-manager",
                "run-eval-runtime", "enable-ci-blocking", "write-durable-memory",
                "reopen-phase2-domain-plugins", "enable-mcp-tools-call", "enable-a2a-runtime-handoff");
        assertThat(review.blockedActions())
            .contains("run-a2a-runtime-handoff", "call-mcp-tools", "run-retrieval-runtime",
                "mutate-kube-manager", "enable-a2a-runtime-handoff");
        assertThat(review.recommendedImplementationOrder()).containsExactly(
            "publish-multi-agent-review-read-model",
            "bind-vue-multi-agent-review-page",
            "assign-parallel-expert-review-rounds",
            "keep-a2a-runtime-handoff-closed-until-provenance-evidence",
            "populate-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "run-compatibility-branches-after-mainline-green",
            "prepare-separate-release-decision-before-runtime-binding",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
        assertThat(review.learningNotes())
            .contains("Multi-agent review is an evidence workflow before it is a runtime handoff protocol.");
        assertThat(review.endpointMap())
            .containsEntry("multiAgentReview", "/api/agent/observability/top-tier/multi-agent-review")
            .containsEntry("topTierTechnologyIntroductionPlaybook",
                "/api/agent/observability/top-tier/technology-introduction-playbook")
            .containsEntry("phase1ExecutionRoadmap",
                "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("backendTechnologyModernizationDecision",
                "/api/agent/observability/top-tier/backend-technology-modernization-decision");
        assertThat(review.reviewPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("aggregateReadModelOnly", true)
            .containsEntry("multiExpertReviewVisible", true)
            .containsEntry("officialSourceWinsOverConversationMemory", true)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("mcpToolsCallAllowed", false)
            .containsEntry("toolExecutionAllowed", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("ciBlockingAllowedNow", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(review.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("aggregateReadModelOnly", true)
            .containsEntry("playbookReadOnly", true)
            .containsEntry("phase1RoadmapReadOnly", true)
            .containsEntry("compatibilityEvidenceReadOnly", true)
            .containsEntry("officialWatchDashboardReadOnly", true)
            .containsEntry("backendDecisionReadOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("compatibilityBranchCreationTriggered", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawOrganization", false)
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(review.playbook().schemaVersion()).isEqualTo("agent-top-tier-technology-introduction-playbook.v1");
        assertThat(review.phase1Roadmap().schemaVersion()).isEqualTo("agent-phase1-execution-roadmap.v1");
        assertThat(review.compatibilityEvidence().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-evidence-readiness.v1");
        assertThat(review.officialWatchDashboard().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-dashboard.v1");
        assertThat(review.backendDecision().schemaVersion())
            .isEqualTo("agent-backend-technology-modernization-decision.v1");
        assertThat(review.toString())
            .contains("multi-agent-review", "A2A", "MCP", "GraphRAG", "architecture-review")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("playbookService.playbook()")
            .contains("phase1ExecutionRoadmapService.roadmap()")
            .contains("evidenceReadinessService.readiness()")
            .contains("officialVersionProtocolWatchDashboardService.dashboard()")
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
            .contains("MULTI_AGENT_REVIEW_READY_RUNTIME_HANDOFF_CLOSED")
            .contains("a2aRuntimeHandoffAllowed")
            .contains("mcpToolsCallAllowed")
            .contains("toolExecutionAllowed")
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

    private static AgentMultiAgentReviewService service(Clock clock) {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-11T06:00:00Z"), ZoneOffset.UTC)
        );
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-11T06:15:00Z"), ZoneOffset.UTC)
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
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService =
            new AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
                matrixService,
                new AgentReviewedEvalTraceEvidenceService(traceSetCatalogService,
                    Clock.fixed(Instant.parse("2026-06-11T06:30:00Z"), ZoneOffset.UTC)),
                new AgentMemoryRagReviewedTraceEvidenceManifestService(
                    new AgentMemoryRagTraceSetCurationContractService(traceSetCatalogService, suiteCatalogService),
                    new AgentMemoryRagSourceEvidenceDigestContractService(),
                    new AgentMemoryRagDurableMemoryLifecycleContractService(),
                    evalGateContractService,
                    suiteBindingContractService,
                    new AgentMemoryRagReadinessService(new ConversationSummaryMemoryStore()),
                    Clock.fixed(Instant.parse("2026-06-11T06:45:00Z"), ZoneOffset.UTC)
                ),
                Clock.fixed(Instant.parse("2026-06-11T07:00:00Z"), ZoneOffset.UTC)
            );
        AgentBackendTechnologyModernizationDecisionService backendDecisionService =
            new AgentBackendTechnologyModernizationDecisionService(
                watchService,
                evidenceReadinessService,
                Clock.fixed(Instant.parse("2026-06-11T07:15:00Z"), ZoneOffset.UTC)
            );
        AgentTopTierTechnologyIntroductionPlaybookService playbookService =
            new AgentTopTierTechnologyIntroductionPlaybookService(
                watchService,
                matrixService,
                evidenceReadinessService,
                backendDecisionService,
                Clock.fixed(Instant.parse("2026-06-11T07:30:00Z"), ZoneOffset.UTC)
            );
        AgentOfficialVersionProtocolWatchDashboardService dashboardService =
            new AgentOfficialVersionProtocolWatchDashboardService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-11T07:45:00Z"), ZoneOffset.UTC)
            );
        return new AgentMultiAgentReviewService(
            playbookService,
            new AgentPhase1ExecutionRoadmapService(
                Clock.fixed(Instant.parse("2026-06-11T07:40:00Z"), ZoneOffset.UTC)
            ),
            evidenceReadinessService,
            dashboardService,
            backendDecisionService,
            clock
        );
    }
}
