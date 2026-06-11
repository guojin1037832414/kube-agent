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
 * Multi-Agent Review 聚合读模型契约测试。
 *
 * <p>中文说明：这组测试不仅验证字段值，也验证“多 Agent 审阅只是证据聚合，不是运行时授权”。
 * 对学习项目来说，测试本身也是教学材料：它告诉后续开发者哪些字段可以展示，哪些动作必须继续关闭。</p>
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
        // 使用固定 Clock 构建整条只读证据链，保证 generatedAt 和聚合计数稳定可断言。
        AgentMultiAgentReviewService service = service(
            Clock.fixed(Instant.parse("2026-06-11T08:00:00Z"), ZoneOffset.UTC)
        );

        AgentMultiAgentReviewResponse review = service.review();

        // 第一组断言保护响应身份和顶级目标：这是 Phase 1 顶级 Agent 的审阅面板，不是临时 demo。
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
        // 第二组断言保护聚合形状：这些数量来自 M5.85 的源读模型组合，前端可据此渲染审阅概览。
        assertThat(review.expertReviewRoundCount()).isEqualTo(6);
        assertThat(review.roadmapStepCount()).isEqualTo(8);
        assertThat(review.a2aEvidenceRowCount()).isEqualTo(5);
        assertThat(review.reviewGateCount()).isEqualTo(40);
        assertThat(review.blockedRuntimeShortcutCount()).isEqualTo(25);
        assertThat(review.disabledRuntimeActionCount()).isEqualTo(30);
        assertThat(review.expertReviewRounds()).extracting(row -> row.get("id"))
            .containsExactly("architecture-review", "security-review", "frontend-vue-review",
                "eval-quality-review", "memory-rag-review", "release-manager-review");
        // 专家轮次可以并行推进，但不能被误解为 runtime 执行权限。
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
        // Roadmap 行只说明“谁来审、审什么”，不能成为 A2A/MCP/Tool 的执行入口。
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
        // A2A provenance 行必须持续表达“证据要求”，不能表达“现在可以 handoff”。
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
        // endpointMap 只暴露 GET/read-model 证据来源，不允许混入 POST、tools/call 或 handoff 执行端点。
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
        // 安全 map 显式列出所有关闭的运行时能力，防止前端或后续代码用“字段缺失”做乐观推断。
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
        // 隐私 map 采用保守聚合；任何源如果暴露敏感字段，这里都不应继续声明 redactedOnly=true。
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

        // 源码级断言保护服务只能调用只读读模型服务，不能偷偷注入 HTTP/Tool/LLM/执行器。
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
        // 源码级断言保护响应类继续声明关键安全字段，并且不引入 Spring AI、HTTP 客户端或运行时执行入口。
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
        // 中文注释契约：M5.85 作为学习项目模板，必须保留中文设计说明和安全边界说明。
        assertThat(serviceSource)
            .contains("中文说明")
            .contains("安全边界")
            .contains("只读读模型服务依赖");
        assertThat(responseSource)
            .contains("中文说明")
            .contains("fail-closed")
            .contains("A2A provenance")
            .contains("安全边界")
            .contains("隐私边界");
    }

    private static AgentMultiAgentReviewService service(Clock clock) {
        // 测试装配链尽量模拟真实依赖，但所有服务仍是内存/只读/fixed-clock，不触发外部调用。
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
