package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Vue-ready governance workbench overview for kube-manager HTTP.
 *
 * <p>This service only composes existing safe read models. It does not call
 * kube-manager, run Tools, invoke HITL, write audit records, mutate durable
 * storage, or enable write retry.</p>
 */
@Service
public class AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService {

    private final AgentKubeManagerHttpOutletHealthSummaryService healthSummaryService;
    private final AgentKubeManagerWriteRetryReadinessService writeRetryReadinessService;
    private final AgentKubeManagerWriteIdempotencyContractService idempotencyContractService;
    private final AgentKubeManagerWriteOperationSafetyContractService operationSafetyContractService;
    private final AgentKubeManagerWriteRetryGovernanceContractService retryGovernanceContractService;
    private final AgentKubeManagerWriteReleaseGateContractService releaseGateContractService;
    private final Clock clock;

    @Autowired
    public AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService(
        AgentKubeManagerHttpOutletHealthSummaryService healthSummaryService,
        AgentKubeManagerWriteRetryReadinessService writeRetryReadinessService,
        AgentKubeManagerWriteIdempotencyContractService idempotencyContractService,
        AgentKubeManagerWriteOperationSafetyContractService operationSafetyContractService,
        AgentKubeManagerWriteRetryGovernanceContractService retryGovernanceContractService,
        AgentKubeManagerWriteReleaseGateContractService releaseGateContractService
    ) {
        this(
            healthSummaryService,
            writeRetryReadinessService,
            idempotencyContractService,
            operationSafetyContractService,
            retryGovernanceContractService,
            releaseGateContractService,
            Clock.systemUTC()
        );
    }

    AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService(
        AgentKubeManagerHttpOutletHealthSummaryService healthSummaryService,
        AgentKubeManagerWriteRetryReadinessService writeRetryReadinessService,
        AgentKubeManagerWriteIdempotencyContractService idempotencyContractService,
        AgentKubeManagerWriteOperationSafetyContractService operationSafetyContractService,
        AgentKubeManagerWriteRetryGovernanceContractService retryGovernanceContractService,
        AgentKubeManagerWriteReleaseGateContractService releaseGateContractService,
        Clock clock
    ) {
        this.healthSummaryService = healthSummaryService;
        this.writeRetryReadinessService = writeRetryReadinessService;
        this.idempotencyContractService = idempotencyContractService;
        this.operationSafetyContractService = operationSafetyContractService;
        this.retryGovernanceContractService = retryGovernanceContractService;
        this.releaseGateContractService = releaseGateContractService;
        this.clock = clock;
    }

    public AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse overview() {
        AgentKubeManagerHttpOutletHealthSummaryResponse healthSummary = healthSummaryService.summary();
        AgentKubeManagerWriteRetryReadinessResponse writeRetryReadiness = writeRetryReadinessService.readiness();
        AgentKubeManagerWriteIdempotencyContractResponse idempotencyContract = idempotencyContractService.contract();
        AgentKubeManagerWriteOperationSafetyContractResponse operationSafetyContract =
            operationSafetyContractService.contract();
        AgentKubeManagerWriteRetryGovernanceContractResponse retryGovernanceContract =
            retryGovernanceContractService.contract();
        AgentKubeManagerWriteReleaseGateContractResponse releaseGateContract =
            releaseGateContractService.contract();
        return AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse.of(
            Instant.now(clock),
            healthSummary,
            writeRetryReadiness,
            idempotencyContract,
            operationSafetyContract,
            retryGovernanceContract,
            releaseGateContract
        );
    }
}
