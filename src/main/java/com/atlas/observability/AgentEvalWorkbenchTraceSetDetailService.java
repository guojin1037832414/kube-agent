package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Builds the trace-set detail read model for the future eval workbench.
 */
@Service
public class AgentEvalWorkbenchTraceSetDetailService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalWorkbenchTraceSetDetailService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public Optional<AgentEvalWorkbenchTraceSetDetailResponse> detail(String traceSetId) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .map(definition -> {
                AgentEvalTraceSetGateArtifact gate = traceSetCatalogService.gate(definition.id(), null)
                    .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(
                        definition,
                        null,
                        null,
                        AgentEvalTraceSetCatalogService.CATALOG_SOURCE
                    ));
                return AgentEvalWorkbenchTraceSetDetailResponse.of(definition, gate);
            });
    }
}
