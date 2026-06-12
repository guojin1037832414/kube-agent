package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理员 trace timeline 中的一步脱敏 replay 事件。
 *
 * <p>中文说明：每个 step 对应一条脱敏审计事件或 durable audit 相位，用于解释 Tool
 * 是否准备、是否执行、是否成功、是否被阻断，以及是否需要确认。</p>
 *
 * <p>安全边界：step 只包含 reasonSummary、parameterSummary、telemetry 和计数类信息；
 * 不包含原始 prompt、原始 reason、原始 endpoint、原始 principal 或参数值。它是只读证据，
 * 不会触发任何 Tool/MCP/kube-manager 重放。</p>
 */
public record AgentReplayTimelineStep(
    String stepId,
    int position,
    Instant occurredAt,
    String phase,
    String recordPhase,
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
