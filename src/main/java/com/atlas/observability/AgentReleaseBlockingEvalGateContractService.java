package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds release-blocking eval gate readiness without enabling CI blocking.
 */
@Service
public class AgentReleaseBlockingEvalGateContractService {

    private final AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService;
    private final AgentEvalWorkbenchGateBundleSummaryService gateBundleSummaryService;
    private final Clock clock;

    @Autowired
    public AgentReleaseBlockingEvalGateContractService(
        AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService,
        AgentEvalWorkbenchGateBundleSummaryService gateBundleSummaryService
    ) {
        this(reviewedEvalTraceEvidenceService, gateBundleSummaryService, Clock.systemUTC());
    }

    AgentReleaseBlockingEvalGateContractService(
        AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService,
        AgentEvalWorkbenchGateBundleSummaryService gateBundleSummaryService,
        Clock clock
    ) {
        this.reviewedEvalTraceEvidenceService = reviewedEvalTraceEvidenceService;
        this.gateBundleSummaryService = gateBundleSummaryService;
        this.clock = clock;
    }

    public AgentReleaseBlockingEvalGateContractResponse contract() {
        return AgentReleaseBlockingEvalGateContractResponse.of(
            Instant.now(clock),
            reviewedEvalTraceEvidenceService.evidence(),
            gateBundleSummaryService.summary()
        );
    }
}
