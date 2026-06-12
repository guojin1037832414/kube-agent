package com.atlas.tool.execution;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全工具执行结果。
 *
 * <p>中文说明：该结果是 SafeToolExecutor 对 Graph、ReAct、ToolCallback 和前端展示层输出的统一执行回执。
 * 它把“是否真的执行过 Tool”“Tool 业务是否成功”“面向用户的摘要”“结构化结果”“是否需要澄清”
 * 放在一个不可变 record 里，避免不同编排入口各自发明一套成功/失败语义。</p>
 *
 * <p>安全边界：该对象只是执行后的回执，不是新的权限来源。{@code success=true} 只能表示某个 Tool
 * 在 SafeToolExecutor 已允许的前提下返回业务成功，不能反向证明 HITL、durable audit、release gate、
 * kube-manager 权限、Memory/RAG 证据或 MCP runtime 已经具备。{@code answer} 面向用户展示，不能承载
 * token、raw endpoint、raw principal、未脱敏参数或可被前端再次当作授权事实的隐藏字段。</p>
 *
 * <p>该结果保持与 Graph 既有 {@code tool_call} 节点返回结构兼容：上层可直接调用
 * {@link #toGraphUpdates()} 写回 State。失败场景只写 {@code answer}，成功场景同时写
 * {@code answer} 与 {@code tool_result}。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
public record SafeToolExecutionResult(
    /** 是否进入过真实 Tool 执行；false 通常代表权限、HITL、参数或审计门在执行前 fail-closed。 */
    boolean executed,
    /** Tool 业务返回是否成功；它不是权限事实，也不等于 release/eval 通过。 */
    boolean success,
    /** 面向用户和 SSE 的摘要文本；必须保持可展示、可脱敏，不能泄露 token 或 raw 参数。 */
    String answer,
    /** Tool 结构化结果；由上层 Graph/前端读取，但不能跳过审计或二次权限校验。 */
    Map<String, Object> toolResult,
    /** 结构化错误码，主要用于澄清/补参 UI 和测试断言，不是异常栈或内部实现泄露通道。 */
    String errorCode,
    /** 可选建议，例如补参选项；它是用户交互候选，不是服务端自动执行授权。 */
    Object suggestions,
    /** 是否建议前端进入澄清流程；clarify 只补上下文，不等于 HITL confirm 或写操作许可。 */
    boolean requiresClarification,
    /** 服务端 traceId，用于审计、日志和前端关联展示；必须来自 AgentTraceContext 的安全候选或生成值。 */
    String traceId
) {

    /**
     * 构造未执行或执行失败的安全响应。
     *
     * @param answer 面向用户的安全提示
     * @return 执行结果
     */
    public static SafeToolExecutionResult notExecuted(String answer) {
        return notExecuted(answer, "");
    }

    /**
     * 构造未进入真实 Tool 的响应。
     *
     * <p>中文说明：权限拒绝、参数缺失、HITL 未确认、durable audit 未就绪等前置门禁失败时，
     * 应使用 {@code executed=false} 明确告诉 Graph“没有触达 kube-manager / 外部系统”。</p>
     */
    public static SafeToolExecutionResult notExecuted(String answer, String traceId) {
        return new SafeToolExecutionResult(false, false, answer, null, null, null, false, traceId);
    }

    /**
     * 构造已执行响应。
     *
     * @param success Tool 业务执行是否成功
     * @param answer 面向用户的摘要
     * @param toolResult 结构化 Tool 结果
     * @return 执行结果
     */
    public static SafeToolExecutionResult executed(boolean success,
                                                   String answer,
                                                   Map<String, Object> toolResult) {
        return executed(success, answer, toolResult, "");
    }

    /**
     * 构造已进入 Tool 的响应，并从结构化结果里提取澄清信号。
     *
     * <p>安全边界：只有 SafeToolExecutor 已经完成来源、权限、受保护字段、HITL 和审计门禁后，
     * 才应该调用此方法。这里不会再次授权，也不会替调用方补齐 token/orgId。</p>
     */
    public static SafeToolExecutionResult executed(boolean success,
                                                   String answer,
                                                   Map<String, Object> toolResult,
                                                   String traceId) {
        String errorCode = toolResult != null && toolResult.get("errorCode") != null
            ? toolResult.get("errorCode").toString() : null;
        Object suggestions = toolResult != null ? toolResult.get("suggestions") : null;
        // Tool 已执行但返回结构化失败建议时，上层应把它当成“需要澄清/补参”的强信号，
        // 而不是普通文本失败。比如 GPU 创建缺少 gpuSpec 时，前端可以直接渲染澄清选项。
        boolean requiresClarification = !success && (errorCode != null || suggestions != null);
        return new SafeToolExecutionResult(true, success, answer, toolResult, errorCode, suggestions, requiresClarification, traceId);
    }

    /**
     * 转换为 Graph State updates，保持与旧 tool_call 节点兼容。
     *
     * <p>中文说明：Graph State 只接收前端展示、后续节点路由和审计关联所需的最小字段。
     * 这里不会写入 token、orgId、userId、HITL confirmToken、durable audit receipt 或 release
     * 结果，避免后续节点把展示态误当作控制面事实。</p>
     *
     * @return 可直接返回给 StateGraph 节点的更新 Map
     */
    public Map<String, Object> toGraphUpdates() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("answer", answer != null ? answer : "");
        if (toolResult != null) {
            updates.put("tool_result", toolResult);
        }
        if (errorCode != null) {
            updates.put("tool_error_code", errorCode);
        }
        if (suggestions != null) {
            updates.put("tool_suggestions", suggestions);
        }
        if (requiresClarification) {
            updates.put("requires_clarification", true);
        }
        if (traceId != null && !traceId.isBlank()) {
            updates.put("traceId", traceId);
        }
        return updates;
    }
}
