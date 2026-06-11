package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the reviewed trace-evidence control-plane view without catalog mutation.
 */
@Service
public class AgentReviewedEvalTraceEvidenceService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final Clock clock;

    @Autowired
    public AgentReviewedEvalTraceEvidenceService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this(traceSetCatalogService, Clock.systemUTC());
    }

    AgentReviewedEvalTraceEvidenceService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                          Clock clock) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.clock = clock;
    }

    public AgentReviewedEvalTraceEvidenceResponse evidence() {
        return AgentReviewedEvalTraceEvidenceResponse.of(
            Instant.now(clock),
            traceSetCatalogService.catalog()
        );
    }
}
