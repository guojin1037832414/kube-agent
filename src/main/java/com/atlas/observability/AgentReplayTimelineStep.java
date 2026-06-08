package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redacted replay step for the admin trace timeline API.
 */
public record AgentReplayTimelineStep(
    String stepId,
    int position,
    Instant occurredAt,
    String phase,
    String kind,
    String status,
    String auditId,
    String traceId,
    String intentId,
    String toolName,
    String source,
    String operationType,
    String httpMethod,
    boolean requiresConfirmation,
    boolean executed,
    boolean success,
    int apiEndpointCount,
    Map<String, Object> reasonSummary,
    Map<String, Object> parameterSummary,
    Map<String, Object> telemetry,
    List<String> labels
) {
}
