package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the read-only eval trace-set promotion workflow.
 *
 * <p>The workflow deliberately composes existing safe steps. It discovers
 * redacted candidates, sends recommended trace anchors through curation review,
 * and returns a catalog patch proposal. It never mutates the catalog.</p>
 */
@Service
public class AgentEvalTraceSetPromotionWorkflowService {

    public static final int DEFAULT_MAX_RECOMMENDED_CANDIDATES = 10;
    public static final int MAX_RECOMMENDED_CANDIDATES = 25;

    private final AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService;
    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalTraceSetPromotionWorkflowService(
        AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService,
        AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public Optional<AgentEvalTraceSetPromotionWorkflowArtifact> workflow(
        String traceSetId,
        AgentEvalTraceSetPromotionWorkflowRequest request) {
        AgentEvalTraceSetPromotionWorkflowRequest safeRequest = request != null
            ? request
            : new AgentEvalTraceSetPromotionWorkflowRequest(null, null, null, null, null);
        int maxSelectedCandidates = boundMaxRecommendedCandidates(safeRequest.maxRecommendedCandidates());
        return candidateDiscoveryService.discover(traceSetId, safeRequest.candidateLimit())
            .flatMap(discovery -> {
                List<String> selected = selectedRecommendedTraceIds(discovery, maxSelectedCandidates);
                AgentEvalSuiteRequest reviewRequest = new AgentEvalSuiteRequest(
                    selected,
                    safeRequest.evaluationLimit(),
                    safeRequest.minimumScore(),
                    safeRequest.failOnWarnings()
                );
                return traceSetCatalogService.catalogPatchProposal(traceSetId, reviewRequest)
                    .map(proposal -> AgentEvalTraceSetPromotionWorkflowArtifact.from(
                        discovery,
                        selected,
                        proposal,
                        maxSelectedCandidates
                    ));
            });
    }

    private int boundMaxRecommendedCandidates(Integer maxRecommendedCandidates) {
        if (maxRecommendedCandidates == null) {
            return DEFAULT_MAX_RECOMMENDED_CANDIDATES;
        }
        return Math.max(1, Math.min(maxRecommendedCandidates, MAX_RECOMMENDED_CANDIDATES));
    }

    private List<String> selectedRecommendedTraceIds(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                     int maxSelectedCandidates) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String traceId : discovery.candidateTraceIds()) {
            if (selected.size() >= maxSelectedCandidates) {
                break;
            }
            selected.add(traceId);
        }
        return List.copyOf(selected);
    }
}
