package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the backend technology modernization decision from existing read models only.
 */
@Service
public class AgentBackendTechnologyModernizationDecisionService {

    private final AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService;
    private final AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService;
    private final Clock clock;

    @Autowired
    public AgentBackendTechnologyModernizationDecisionService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService
    ) {
        this(officialVersionProtocolWatchService, evidenceReadinessService, Clock.systemUTC());
    }

    AgentBackendTechnologyModernizationDecisionService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService evidenceReadinessService,
        Clock clock
    ) {
        this.officialVersionProtocolWatchService = officialVersionProtocolWatchService;
        this.evidenceReadinessService = evidenceReadinessService;
        this.clock = clock;
    }

    public AgentBackendTechnologyModernizationDecisionResponse decision() {
        return AgentBackendTechnologyModernizationDecisionResponse.of(
            Instant.now(clock),
            officialVersionProtocolWatchService.watch(),
            evidenceReadinessService.readiness()
        );
    }
}
