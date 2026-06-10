package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the frontend acceptance contract from the backend-owned Vue implementation package.
 */
@Service
public class AgentTopTierVueWorkbenchAcceptanceContractService {

    private final AgentTopTierVueWorkbenchImplementationPackageService implementationPackageService;
    private final Clock clock;

    public AgentTopTierVueWorkbenchAcceptanceContractService(
        AgentTopTierVueWorkbenchImplementationPackageService implementationPackageService
    ) {
        this(implementationPackageService, Clock.systemUTC());
    }

    AgentTopTierVueWorkbenchAcceptanceContractService(
        AgentTopTierVueWorkbenchImplementationPackageService implementationPackageService,
        Clock clock
    ) {
        this.implementationPackageService = implementationPackageService;
        this.clock = clock;
    }

    public AgentTopTierVueWorkbenchAcceptanceContractResponse contract() {
        return AgentTopTierVueWorkbenchAcceptanceContractResponse.of(
            Instant.now(clock),
            implementationPackageService.implementationPackage()
        );
    }
}
