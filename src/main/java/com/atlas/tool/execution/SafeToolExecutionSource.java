package com.atlas.tool.execution;

/**
 * 安全工具执行来源枚举。
 *
 * <p>该枚举用于审计和治理：同一个 {@link SafeToolExecutor} 会被 Graph tool_call、
 * 后续 execute_node、ReAct/ToolCallback 等不同入口复用。执行来源只用于记录和策略扩展，
 * 绝不能作为绕过 HITL 或权限校验的依据。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
public enum SafeToolExecutionSource {

    /** Graph 中既有的 CALL_TOOL → tool_call 执行入口。 */
    GRAPH_TOOL_CALL,

    /** Plan-and-Execute 后续 execute_node 执行入口。 */
    PLAN_EXECUTE_NODE,

    /** 手写 ReAct 引擎执行入口，后续可逐步迁移复用。 */
    REACT_ENGINE,

    /** Spring AI ToolCallback 桥接入口，后续可逐步迁移复用。 */
    TOOL_CALLBACK
}
