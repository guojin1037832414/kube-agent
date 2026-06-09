package com.atlas.observability;

import org.springframework.stereotype.Service;

/**
 * Builds the eval workbench gate bundle summary.
 */
@Service
public class AgentEvalWorkbenchGateBundleSummaryService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalWorkbenchGateBundleSummaryService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public AgentEvalWorkbenchGateBundleSummaryResponse summary() {
        return AgentEvalWorkbenchGateBundleSummaryResponse.from(
            traceSetCatalogService.catalog(),
            traceSetCatalogService.gateBundle(null)
        );
    }
}
