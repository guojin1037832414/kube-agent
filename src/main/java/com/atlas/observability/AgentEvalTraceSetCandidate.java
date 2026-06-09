package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redacted trace-level candidate summary for trace-set curation review.
 */
public record AgentEvalTraceSetCandidate(
    String traceId,
    String recommendation,
    boolean recommendedForCurationReview,
    List<String> recommendationReasons,
    Instant firstSeenAt,
    Instant lastSeenAt,
    int eventCount,
    int preExecutionEvents,
    int finalEvents,
    int highRiskEvents,
    int readEvents,
    int executedEvents,
    int successEvents,
    int blockedEvents,
    int errorEvents,
    int businessFailureEvents,
    boolean requiresConfirmation,
    boolean protectedParameterEvidence,
    List<String> operationTypes,
    List<String> outcomes,
    List<String> evidenceTags,
    Map<String, Object> privacy
) {

    public AgentEvalTraceSetCandidate {
        traceId = traceId != null ? traceId : "";
        recommendation = recommendation != null ? recommendation : "NEEDS_MORE_REVIEW";
        recommendationReasons = recommendationReasons != null ? List.copyOf(recommendationReasons) : List.of();
        operationTypes = operationTypes != null ? List.copyOf(operationTypes) : List.of();
        outcomes = outcomes != null ? List.copyOf(outcomes) : List.of();
        evidenceTags = evidenceTags != null ? List.copyOf(evidenceTags) : List.of();
        privacy = privacy != null ? Map.copyOf(privacy) : Map.of();
    }
}
