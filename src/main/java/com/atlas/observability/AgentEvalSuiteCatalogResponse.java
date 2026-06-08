package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only Eval Suite 目录响应。
 */
public record AgentEvalSuiteCatalogResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    int suiteCount,
    List<AgentEvalSuiteDefinition> suites,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-suite-catalog.v1";

    public static AgentEvalSuiteCatalogResponse of(List<AgentEvalSuiteDefinition> suites,
                                                   Map<String, Object> privacy) {
        List<AgentEvalSuiteDefinition> safeSuites = suites != null ? List.copyOf(suites) : List.of();
        return new AgentEvalSuiteCatalogResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            safeSuites.size(),
            safeSuites,
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
