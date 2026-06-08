package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only catalog response for versioned eval trace sets.
 */
public record AgentEvalTraceSetCatalogResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String source,
    int traceSetCount,
    List<AgentEvalTraceSetDefinition> traceSets,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-catalog.v1";

    public static AgentEvalTraceSetCatalogResponse of(String source,
                                                      List<AgentEvalTraceSetDefinition> traceSets,
                                                      Map<String, Object> privacy) {
        List<AgentEvalTraceSetDefinition> safeTraceSets = traceSets != null ? List.copyOf(traceSets) : List.of();
        return new AgentEvalTraceSetCatalogResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            source != null ? source : "",
            safeTraceSets.size(),
            safeTraceSets,
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
