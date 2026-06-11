package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 Vue workbench implementation package from existing binding specs.
 */
@Service
public class AgentTopTierVueWorkbenchImplementationPackageService {

    private final AgentOfficialVersionProtocolWatchVueBindingSpecService officialWatchBindingSpecService;
    private final AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService compatibilityMatrixBindingSpecService;
    private final Clock clock;

    @Autowired
    public AgentTopTierVueWorkbenchImplementationPackageService(
        AgentOfficialVersionProtocolWatchVueBindingSpecService officialWatchBindingSpecService,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService compatibilityMatrixBindingSpecService
    ) {
        this(officialWatchBindingSpecService, compatibilityMatrixBindingSpecService, Clock.systemUTC());
    }

    AgentTopTierVueWorkbenchImplementationPackageService(
        AgentOfficialVersionProtocolWatchVueBindingSpecService officialWatchBindingSpecService,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService compatibilityMatrixBindingSpecService,
        Clock clock
    ) {
        this.officialWatchBindingSpecService = officialWatchBindingSpecService;
        this.compatibilityMatrixBindingSpecService = compatibilityMatrixBindingSpecService;
        this.clock = clock;
    }

    public AgentTopTierVueWorkbenchImplementationPackageResponse implementationPackage() {
        return AgentTopTierVueWorkbenchImplementationPackageResponse.of(
            Instant.now(clock),
            officialWatchBindingSpecService.spec(),
            compatibilityMatrixBindingSpecService.spec()
        );
    }
}
