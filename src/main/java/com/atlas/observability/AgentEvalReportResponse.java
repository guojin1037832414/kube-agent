package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理员专用的确定性 Agent 评测报告。
 *
 * <p>它是回归/发布门禁证据，不是 Tool 执行授权来源。</p>
 */
public record AgentEvalReportResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceId,
    String timelineSchemaVersion,
    int stepCount,
    int maxResults,
    boolean truncated,
    String order,
    String verdict,
    int score,
    boolean pass,
    Map<String, Object> summary,
    Map<String, Object> privacy,
    AgentReplayTimelineResponse replay,
    List<AgentEvalCheck> checks
) {

    public static final String SCHEMA_VERSION = "agent-eval-report.v1";
    public static final String EVALUATION_VERSION = "deterministic-replay-eval.v1";

    public static AgentEvalReportResponse of(String traceId,
                                             String timelineSchemaVersion,
                                             int stepCount,
                                             int maxResults,
                                             boolean truncated,
                                             String order,
                                             String verdict,
                                             int score,
                                             boolean pass,
                                             Map<String, Object> summary,
                                             Map<String, Object> privacy,
                                             AgentReplayTimelineResponse replay,
                                             List<AgentEvalCheck> checks) {
        return new AgentEvalReportResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            EVALUATION_VERSION,
            traceId != null ? traceId : "",
            timelineSchemaVersion != null ? timelineSchemaVersion : "",
            Math.max(0, stepCount),
            Math.max(0, maxResults),
            truncated,
            order != null ? order : "",
            verdict != null ? verdict : "UNKNOWN",
            Math.max(0, Math.min(100, score)),
            pass,
            summary != null ? Map.copyOf(summary) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of(),
            replay,
            checks != null ? List.copyOf(checks) : List.of()
        );
    }
}
