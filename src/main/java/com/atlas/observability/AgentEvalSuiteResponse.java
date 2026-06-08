package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理员专用的批量 Agent eval suite 报告。
 *
 * <p>它面向未来 CI/release gate：只聚合已脱敏的单 trace eval 报告，
 * 不重新读取 raw audit，也不执行任何 Tool。</p>
 */
public record AgentEvalSuiteResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String gateVerdict,
    boolean pass,
    int minimumScore,
    boolean failOnWarnings,
    int maxResults,
    List<String> traceIds,
    Map<String, Object> summary,
    Map<String, Object> privacy,
    List<AgentEvalReportResponse> reports
) {

    public static final String SCHEMA_VERSION = "agent-eval-suite.v1";

    public static AgentEvalSuiteResponse of(String gateVerdict,
                                            boolean pass,
                                            int minimumScore,
                                            boolean failOnWarnings,
                                            int maxResults,
                                            List<String> traceIds,
                                            Map<String, Object> summary,
                                            Map<String, Object> privacy,
                                            List<AgentEvalReportResponse> reports) {
        return new AgentEvalSuiteResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            gateVerdict != null ? gateVerdict : "UNKNOWN",
            pass,
            Math.max(0, Math.min(100, minimumScore)),
            failOnWarnings,
            Math.max(0, maxResults),
            traceIds != null ? List.copyOf(traceIds) : List.of(),
            summary != null ? Map.copyOf(summary) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of(),
            reports != null ? List.copyOf(reports) : List.of()
        );
    }
}
