package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a read-only overview for the future eval workbench.
 *
 * <p>The overview composes existing safe artifacts. It does not discover
 * candidates, query raw audit storage, run Tool code, call kube-manager, or
 * mutate the trace-set catalog.</p>
 */
@Service
public class AgentEvalWorkbenchOverviewService {

    private final AgentEvalWorkbenchCapabilitiesService capabilitiesService;
    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalWorkbenchOverviewService(AgentEvalWorkbenchCapabilitiesService capabilitiesService,
                                             AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.capabilitiesService = capabilitiesService;
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public AgentEvalWorkbenchOverviewResponse overview() {
        AgentEvalWorkbenchCapabilitiesResponse capabilities = capabilitiesService.capabilities();
        AgentEvalTraceSetCatalogResponse catalog = traceSetCatalogService.catalog();
        AgentEvalTraceSetGateBundleArtifact gateBundle = traceSetCatalogService.gateBundle(null);
        Map<String, AgentEvalTraceSetGateArtifact> gatesByTraceSetId = gatesByTraceSetId(gateBundle);
        List<AgentEvalWorkbenchTraceSetView> traceSets = catalog.traceSets().stream()
            .map(definition -> AgentEvalWorkbenchTraceSetView.from(definition, gatesByTraceSetId.get(definition.id())))
            .toList();
        return AgentEvalWorkbenchOverviewResponse.of(capabilities, gateBundle, traceSets);
    }

    private Map<String, AgentEvalTraceSetGateArtifact> gatesByTraceSetId(AgentEvalTraceSetGateBundleArtifact gateBundle) {
        Map<String, AgentEvalTraceSetGateArtifact> gates = new LinkedHashMap<>();
        if (gateBundle == null) {
            return Map.of();
        }
        for (AgentEvalTraceSetGateArtifact gate : gateBundle.traceSetGates()) {
            if (gate != null && !gate.traceSetId().isBlank()) {
                gates.put(gate.traceSetId(), gate);
            }
        }
        return Map.copyOf(gates);
    }
}
