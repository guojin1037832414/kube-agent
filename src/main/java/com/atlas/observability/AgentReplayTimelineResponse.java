package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only redacted replay timeline response.
 */
public record AgentReplayTimelineResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceId,
    int resultCount,
    int maxResults,
    boolean truncated,
    String order,
    Map<String, Object> privacy,
    Map<String, Object> index,
    List<AgentReplayTimelineStep> steps
) {

    public static final String SCHEMA_VERSION = "agent-replay-timeline.v1";

    public static AgentReplayTimelineResponse of(String traceId,
                                                 int maxResults,
                                                 boolean truncated,
                                                 Map<String, Object> index,
                                                 List<AgentReplayTimelineStep> steps) {
        List<AgentReplayTimelineStep> safeSteps = steps != null ? List.copyOf(steps) : List.of();
        return new AgentReplayTimelineResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            traceId != null ? traceId : "",
            safeSteps.size(),
            Math.max(0, maxResults),
            truncated,
            "oldest-first",
            privacyMetadata(),
            index != null ? Map.copyOf(index) : Map.of(),
            safeSteps
        );
    }

    private static Map<String, Object> privacyMetadata() {
        return Map.of(
            "redactedOnly", true,
            "containsRawPrincipal", false,
            "containsRawOrganization", false,
            "containsRawConversation", false,
            "containsRawEndpoints", false,
            "containsRawReason", false,
            "containsRawParameterValues", false
        );
    }
}
