package com.atlas.observability;

/**
 * Request options for the trace-set evidence promotion workflow.
 *
 * <p>All fields tune review artifacts only. They do not grant catalog write
 * authority and cannot authorize Tool or kube-manager execution.</p>
 */
public record AgentEvalTraceSetPromotionWorkflowRequest(
    Integer candidateLimit,
    Integer evaluationLimit,
    Integer minimumScore,
    Boolean failOnWarnings,
    Integer maxRecommendedCandidates
) {
}
