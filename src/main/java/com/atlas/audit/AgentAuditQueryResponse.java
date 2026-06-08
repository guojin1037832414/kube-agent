package com.atlas.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redacted audit query response.
 */
public record AgentAuditQueryResponse(
    String schemaVersion,
    Instant generatedAt,
    String queryType,
    String query,
    int resultCount,
    int maxResults,
    boolean truncated,
    Map<String, Object> index,
    List<AgentAuditQueryEvent> events
) {

    public static final String SCHEMA_VERSION = "agent-audit-query.v1";

    public static AgentAuditQueryResponse of(String queryType,
                                             String query,
                                             int maxResults,
                                             boolean truncated,
                                             Map<String, Object> index,
                                             List<AgentAuditQueryEvent> events) {
        List<AgentAuditQueryEvent> safeEvents = events != null ? List.copyOf(events) : List.of();
        return new AgentAuditQueryResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            queryType != null ? queryType : "",
            query != null ? query : "",
            safeEvents.size(),
            Math.max(0, maxResults),
            truncated,
            index != null ? Map.copyOf(index) : Map.of(),
            safeEvents
        );
    }
}
