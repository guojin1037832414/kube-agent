package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the backend-owned migration package for the future vue-kube-manager workbench patch.
 */
@Service
public class AgentTopTierVueWorkbenchMigrationPackageService {

    private final AgentTopTierVueWorkbenchAcceptanceContractService acceptanceContractService;
    private final Clock clock;

    @Autowired
    public AgentTopTierVueWorkbenchMigrationPackageService(
        AgentTopTierVueWorkbenchAcceptanceContractService acceptanceContractService
    ) {
        this(acceptanceContractService, Clock.systemUTC());
    }

    AgentTopTierVueWorkbenchMigrationPackageService(
        AgentTopTierVueWorkbenchAcceptanceContractService acceptanceContractService,
        Clock clock
    ) {
        this.acceptanceContractService = acceptanceContractService;
        this.clock = clock;
    }

    public AgentTopTierVueWorkbenchMigrationPackageResponse migrationPackage() {
        return AgentTopTierVueWorkbenchMigrationPackageResponse.of(
            Instant.now(clock),
            acceptanceContractService.contract()
        );
    }
}
