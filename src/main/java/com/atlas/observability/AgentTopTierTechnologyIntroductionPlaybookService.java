package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 latest-technology introduction playbook from read models only.
 */
@Service
public class AgentTopTierTechnologyIntroductionPlaybookService {

    private final AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService;
    private final AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService;
    private final AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService;
    private final AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService;
    private final Clock clock;

    @Autowired
    public AgentTopTierTechnologyIntroductionPlaybookService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
        AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService,
        AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService
    ) {
        this(
            officialVersionProtocolWatchService,
            compatibilityMatrixService,
            evidenceReadinessService,
            backendTechnologyModernizationDecisionService,
            Clock.systemUTC()
        );
    }

    AgentTopTierTechnologyIntroductionPlaybookService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
        AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService,
        AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService,
        Clock clock
    ) {
        this.officialVersionProtocolWatchService = officialVersionProtocolWatchService;
        this.compatibilityMatrixService = compatibilityMatrixService;
        this.evidenceReadinessService = evidenceReadinessService;
        this.backendTechnologyModernizationDecisionService = backendTechnologyModernizationDecisionService;
        this.clock = clock;
    }

    public AgentTopTierTechnologyIntroductionPlaybookResponse playbook() {
        return AgentTopTierTechnologyIntroductionPlaybookResponse.of(
            Instant.now(clock),
            officialVersionProtocolWatchService.watch(),
            compatibilityMatrixService.matrix(),
            evidenceReadinessService.readiness(),
            backendTechnologyModernizationDecisionService.decision()
        );
    }
}
