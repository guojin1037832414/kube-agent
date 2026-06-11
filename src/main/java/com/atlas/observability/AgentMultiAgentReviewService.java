package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 multi-agent/expert review projection from read models only.
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
        this.playbookService = playbookService;
        this.phase1ExecutionRoadmapService = phase1ExecutionRoadmapService;
        this.evidenceReadinessService = evidenceReadinessService;
        this.officialVersionProtocolWatchDashboardService = officialVersionProtocolWatchDashboardService;
        this.backendTechnologyModernizationDecisionService = backendTechnologyModernizationDecisionService;
        this.clock = clock;
    }

    public AgentMultiAgentReviewResponse review() {
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
