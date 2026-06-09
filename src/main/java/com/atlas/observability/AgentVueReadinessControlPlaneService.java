package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Vue readiness control plane contract without touching frontend state.
 */
@Service
public class AgentVueReadinessControlPlaneService {

    private final Clock clock;

    public AgentVueReadinessControlPlaneService() {
        this(Clock.systemUTC());
    }

    AgentVueReadinessControlPlaneService(Clock clock) {
        this.clock = clock;
    }

    public AgentVueReadinessControlPlaneResponse controlPlane() {
        return AgentVueReadinessControlPlaneResponse.of(Instant.now(clock));
    }
}
