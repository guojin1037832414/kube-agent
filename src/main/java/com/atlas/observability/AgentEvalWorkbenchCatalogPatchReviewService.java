package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Builds the eval workbench catalog patch review model.
 */
@Service
public class AgentEvalWorkbenchCatalogPatchReviewService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalWorkbenchCatalogPatchReviewService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> review(
        String traceSetId,
        AgentEvalSuiteRequest request) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .flatMap(definition -> traceSetCatalogService.catalogPatchProposal(traceSetId, request)
                .map(proposal -> {
                    AgentEvalTraceSetGateArtifact gate = traceSetCatalogService.gate(definition.id(), null)
                        .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(
                            definition,
                            null,
                            null,
                            AgentEvalTraceSetCatalogService.CATALOG_SOURCE
                        ));
                    return AgentEvalWorkbenchCatalogPatchReviewResponse.from(definition, gate, proposal);
                }));
    }
}
