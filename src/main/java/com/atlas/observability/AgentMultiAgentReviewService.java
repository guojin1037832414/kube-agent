package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 构建 Phase 1 顶级 Agent 的 Multi-Agent / Expert Review 只读聚合视图。
 *
 * <p>中文说明：这里的“多 Agent”不是运行时 A2A handoff，也不是让外部 Agent 获得本地权限。
 * 本服务只把已经存在的后端读模型组合起来，形成一个给前端和学习文档使用的审阅面板。
 * 这样做的价值是：先让架构、安全、前端、Eval、Memory/RAG、发布管理等专家审阅轮次可见，
 * 再讨论运行时编排；避免把“多 Agent”误解成直接开放 Tool、MCP、RAG 或 kube-manager 写能力。</p>
 *
 * <p>安全边界：本服务不调用 LLM、不访问外部网络、不执行 Tool、不调用 kube-manager、
 * 不写审计/记忆、不运行 eval、不触发 MCP tools/call，也不执行 A2A runtime handoff。</p>
 */
@Service
public class AgentMultiAgentReviewService {

    private final AgentTopTierTechnologyIntroductionPlaybookService playbookService;
    private final AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService;
    private final AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService;
    private final AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService;
    private final AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService;
    private final Clock clock;

    @Autowired
    public AgentMultiAgentReviewService(
        AgentTopTierTechnologyIntroductionPlaybookService playbookService,
        AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService,
        AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService,
        AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService
    ) {
        // 生产构造器使用系统 UTC 时钟；测试构造器可以注入固定 Clock，从而让 generatedAt 可断言。
        this(
            playbookService,
            phase1ExecutionRoadmapService,
            evidenceReadinessService,
            officialVersionProtocolWatchDashboardService,
            backendTechnologyModernizationDecisionService,
            Clock.systemUTC()
        );
    }

    AgentMultiAgentReviewService(
        AgentTopTierTechnologyIntroductionPlaybookService playbookService,
        AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService,
        AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService,
        AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService,
        Clock clock
    ) {
        // 这里全部是只读读模型服务依赖。不要在本服务中注入执行器、HTTP 客户端、工具注册表或 LLM 客户端。
        this.playbookService = playbookService;
        this.phase1ExecutionRoadmapService = phase1ExecutionRoadmapService;
        this.evidenceReadinessService = evidenceReadinessService;
        this.officialVersionProtocolWatchDashboardService = officialVersionProtocolWatchDashboardService;
        this.backendTechnologyModernizationDecisionService = backendTechnologyModernizationDecisionService;
        this.clock = clock;
    }

    public AgentMultiAgentReviewResponse review() {
        // 聚合顺序刻意保持清晰：先取顶级技术引入 playbook，再取 Phase 1 路线图、证据就绪度、
        // 官方 watch dashboard 和后端现代化决策。每个调用都必须是 GET/read-model 语义，
        // 不能在这里触发候选发现、评测执行、兼容分支创建或任何运行时权限变更。
        return AgentMultiAgentReviewResponse.of(
            Instant.now(clock),
            playbookService.playbook(),
            phase1ExecutionRoadmapService.roadmap(),
            evidenceReadinessService.readiness(),
            officialVersionProtocolWatchDashboardService.dashboard(),
            backendTechnologyModernizationDecisionService.decision()
        );
    }
}
