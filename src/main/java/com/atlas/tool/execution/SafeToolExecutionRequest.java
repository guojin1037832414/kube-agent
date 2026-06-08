package com.atlas.tool.execution;

import com.atlas.hitl.HitlConfirmation;

import java.util.Map;

/**
 * 安全工具执行请求。
 *
 * <p>该请求对象只承载执行所需的服务端上下文和业务参数。注意：{@code parameters}
 * 可能来自 LLM、Plan 或前端输入，属于不可信输入；执行器内部必须过滤 token/orgId/userId
 * 等受保护字段，并以后端可信上下文覆盖。</p>
 *
 * @param intentId AtlasBrain / Plan 明确选中的意图 ID
 * @param parameters LLM 或计划生成的业务参数，不可信
 * @param userId 当前用户 ID
 * @param token 当前会话 token，仅用于 ThreadLocal 透传，不写入返回结果
 * @param orgId 可信组织 ID，缺失时必须 fail-closed
 * @param conversationId 会话 ID，用于后续审计扩展
 * @param traceId Agent 全链路 traceId，用于日志、SSE、审计和未来 OpenTelemetry Span 关联
 * @param confirmation HITLController 注入的服务端确认 marker，可以为空
 * @param source 执行来源，用于审计和策略扩展
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
public record SafeToolExecutionRequest(
    String intentId,
    Map<String, Object> parameters,
    String userId,
    String token,
    String orgId,
    String conversationId,
    String traceId,
    HitlConfirmation confirmation,
    SafeToolExecutionSource source
) {
    public SafeToolExecutionRequest(String intentId,
                                    Map<String, Object> parameters,
                                    String userId,
                                    String token,
                                    String orgId,
                                    String conversationId,
                                    HitlConfirmation confirmation,
                                    SafeToolExecutionSource source) {
        this(intentId, parameters, userId, token, orgId, conversationId, "", confirmation, source);
    }
}
