package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Builds the eval workbench promotion workflow result model.
 */
@Service
public class AgentEvalWorkbenchPromotionWorkflowService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final AgentEvalTraceSetPromotionWorkflowService promotionWorkflowService;

    public AgentEvalWorkbenchPromotionWorkflowService(
        AgentEvalTraceSetCatalogService traceSetCatalogService,
        AgentEvalTraceSetPromotionWorkflowService promotionWorkflowService) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.promotionWorkflowService = promotionWorkflowService;
    }

    public Optional<AgentEvalWorkbenchPromotionWorkflowResponse> workflow(
        String traceSetId,
        AgentEvalTraceSetPromotionWorkflowRequest request) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .flatMap(definition -> promotionWorkflowService.workflow(traceSetId, request)
                .map(workflow -> {
                    AgentEvalTraceSetGateArtifact gate = traceSetCatalogService.gate(definition.id(), null)
                        .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(
                            definition,
                            null,
                            null,
                            AgentEvalTraceSetCatalogService.CATALOG_SOURCE
                        ));
                    return AgentEvalWorkbenchPromotionWorkflowResponse.from(definition, gate, workflow);
                }));
    }
}
