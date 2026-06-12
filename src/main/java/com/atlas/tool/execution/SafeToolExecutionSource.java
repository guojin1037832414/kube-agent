package com.atlas.tool.execution;

/**
 * 安全工具执行来源枚举。
 *
 * <p>中文说明：该枚举用于审计和治理：同一个 {@link SafeToolExecutor} 会被 Graph tool_call、
 * Plan execute_node、ReAct 引擎、Spring AI ToolCallback 和旧 IntentRouter fallback 等入口复用。
 * 显式记录来源后，日志、审计、Eval 和前端回放可以解释“这次 Tool 候选是从哪条编排路径来的”。</p>
 *
 * <p>安全边界：执行来源只用于记录、诊断和未来策略收敛，绝不能作为绕过 SafeToolExecutor、
 * HITL、ToolPermission、durable audit、受保护参数过滤、kube-manager 权限或 release gate 的依据。
 * 即使来源是 PLAN_EXECUTE_NODE 或 TOOL_CALLBACK，也必须按同一套服务端证据链校验。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
public enum SafeToolExecutionSource {

    /** Graph 中既有的 CALL_TOOL -> tool_call 执行入口；来源于图路由，不代表 Tool 已授权。 */
    GRAPH_TOOL_CALL,

    /** Plan-and-Execute 后续 execute_node 执行入口；PlanStep 只是计划证据，不是执行许可。 */
    PLAN_EXECUTE_NODE,

    /** 手写 ReAct 引擎执行入口；LLM Action JSON 仍只是候选业务参数。 */
    REACT_ENGINE,

    /** Spring AI ToolCallback 桥接入口；第三方框架回调也必须回到 kube-agent 服务端边界。 */
    TOOL_CALLBACK,

    /** 传统 IntentRouter fallback 执行入口；仅用于兼容旧路径，不降低权限或审计要求。 */
    ORCHESTRATOR_FALLBACK
}
