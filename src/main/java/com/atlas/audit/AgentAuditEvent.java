package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent 安全审计事件。
 *
 * <p>这是 M5.25 的最小审计证据模型：先把 Tool 执行边界发生的允许、阻断、业务失败、
 * 异常等事实标准化，再逐步替换内存 recorder 为数据库/日志/事件流持久化实现。</p>
 */
public record AgentAuditEvent(
    String auditId,
    Instant occurredAt,
    String traceId,
    String conversationId,
    String userId,
    String organizationId,
    String intentId,
    String toolName,
    SafeToolExecutionSource source,
    String httpMethod,
    List<String> apiEndpoints,
    AtlasToolMapping.OperationType operationType,
    boolean requiresConfirmation,
    AgentAuditOutcome outcome,
    boolean executed,
    boolean success,
    String reason,
    Map<String, Object> parameterSummary
) {
}
