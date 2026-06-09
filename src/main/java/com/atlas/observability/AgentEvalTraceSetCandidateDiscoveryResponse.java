package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only redacted candidate discovery result for trace-set curation.
 */
public record AgentEvalTraceSetCandidateDiscoveryResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String auditQueryBackend,
    int maxEvents,
    int inspectedEvents,
    int inspectedTraceCount,
    int candidateTraceCount,
    boolean auditQueryTruncated,
    List<String> candidateTraceIds,
    List<AgentEvalTraceSetCandidate> candidates,
    Map<String, Object> discoveryPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-candidates.v1";

    public static AgentEvalTraceSetCandidateDiscoveryResponse of(AgentEvalTraceSetDefinition traceSet,
                                                                 String auditQueryBackend,
                                                                 int maxEvents,
                                                                 boolean auditQueryTruncated,
                                                                 List<AgentEvalTraceSetCandidate> candidates,
                                                                 Map<String, Object> discoveryPolicy,
                                                                 Map<String, Object> privacy) {
        List<AgentEvalTraceSetCandidate> safeCandidates = candidates != null
            ? List.copyOf(candidates)
            : List.of();
        List<String> candidateTraceIds = safeCandidates.stream()
            .filter(AgentEvalTraceSetCandidate::recommendedForCurationReview)
            .map(AgentEvalTraceSetCandidate::traceId)
            .toList();
        int inspectedEvents = safeCandidates.stream()
            .mapToInt(AgentEvalTraceSetCandidate::eventCount)
            .sum();
        return new AgentEvalTraceSetCandidateDiscoveryResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            traceSet != null ? traceSet.id() : "",
            traceSet != null ? traceSet.title() : "",
            traceSet != null ? traceSet.suiteId() : "",
            auditQueryBackend != null ? auditQueryBackend : "",
            Math.max(0, maxEvents),
            inspectedEvents,
            safeCandidates.size(),
            candidateTraceIds.size(),
            auditQueryTruncated,
            candidateTraceIds,
            safeCandidates,
            discoveryPolicy != null ? Map.copyOf(discoveryPolicy) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
