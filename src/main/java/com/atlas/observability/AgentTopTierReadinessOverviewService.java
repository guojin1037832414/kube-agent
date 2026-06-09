package com.atlas.observability;

import com.atlas.mcp.McpGovernanceOverviewResponse;
import com.atlas.mcp.McpGovernanceOverviewService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 top-tier Agent readiness overview.
 *
 * <p>中文说明：该服务只组合已有治理 read model，不执行工具、不访问 kube-manager、
 * 不调用 LLM、不写审计。它的作用是把“顶级 Agent 还缺什么”变成后端可测试契约。</p>
 */
@Service
public class AgentTopTierReadinessOverviewService {

    private final AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerGovernanceService;
    private final AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService;
    private final McpGovernanceOverviewService mcpGovernanceOverviewService;
    private final Clock clock;

    public AgentTopTierReadinessOverviewService(
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerGovernanceService,
        AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService,
        McpGovernanceOverviewService mcpGovernanceOverviewService
    ) {
        this(
            kubeManagerGovernanceService,
            evalWorkbenchCapabilitiesService,
            mcpGovernanceOverviewService,
            Clock.systemUTC()
        );
    }

    AgentTopTierReadinessOverviewService(
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerGovernanceService,
        AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService,
        McpGovernanceOverviewService mcpGovernanceOverviewService,
        Clock clock
    ) {
        this.kubeManagerGovernanceService = kubeManagerGovernanceService;
        this.evalWorkbenchCapabilitiesService = evalWorkbenchCapabilitiesService;
        this.mcpGovernanceOverviewService = mcpGovernanceOverviewService;
        this.clock = clock;
    }

    public AgentTopTierReadinessOverviewResponse overview() {
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse kubeManagerGovernance =
            kubeManagerGovernanceService.overview();
        AgentEvalWorkbenchCapabilitiesResponse evalWorkbenchCapabilities =
            evalWorkbenchCapabilitiesService.capabilities();
        McpGovernanceOverviewResponse mcpGovernance = mcpGovernanceOverviewService.overview();
        return AgentTopTierReadinessOverviewResponse.of(
            Instant.now(clock),
            kubeManagerGovernance,
            evalWorkbenchCapabilities,
            mcpGovernance
        );
    }
}
