package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 execution roadmap without starting any runtime work.
 */
@Service
public class AgentPhase1ExecutionRoadmapService {

    private final Clock clock;

    public AgentPhase1ExecutionRoadmapService() {
        this(Clock.systemUTC());
    }

    AgentPhase1ExecutionRoadmapService(Clock clock) {
        this.clock = clock;
    }

    public AgentPhase1ExecutionRoadmapResponse roadmap() {
        return AgentPhase1ExecutionRoadmapResponse.of(Instant.now(clock));
    }
}
