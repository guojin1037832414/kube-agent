package com.atlas.tool.execution;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全工具执行结果。
 *
 * <p>该结果保持与 Graph 既有 {@code tool_call} 节点返回结构兼容：上层可直接调用
 * {@link #toGraphUpdates()} 写回 State。失败场景只写 {@code answer}，成功场景同时写
 * {@code answer} 与 {@code tool_result}。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
public record SafeToolExecutionResult(
    boolean executed,
    boolean success,
    String answer,
    Map<String, Object> toolResult
) {

    /**
     * 构造未执行或执行失败的安全响应。
     *
     * @param answer 面向用户的安全提示
     * @return 执行结果
     */
    public static SafeToolExecutionResult notExecuted(String answer) {
        return new SafeToolExecutionResult(false, false, answer, null);
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
        return new SafeToolExecutionResult(true, success, answer, toolResult);
    }

    /**
     * 转换为 Graph State updates，保持与旧 tool_call 节点兼容。
     *
     * @return 可直接返回给 StateGraph 节点的更新 Map
     */
    public Map<String, Object> toGraphUpdates() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("answer", answer != null ? answer : "");
        if (toolResult != null) {
            updates.put("tool_result", toolResult);
        }
        return updates;
    }
}
