package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * 结果合并节点 — 将各专业 Agent 的输出合并为统一的 final_answer。
 *
 * <p>中文说明：这是旧 atlasGraph 的收束节点，负责把前面节点已经形成的展示结果投影成
 * 前端统一消费的 {@code final_answer}。它不“再判断一次业务是否成功”，只按明确优先级选择
 * 最适合展示的文本，避免 direct_answer、ReAct 结果或 Graph 入口守卫的 fail-closed 原因在
 * merge_result 阶段丢失。</p>
 *
 * <p>安全边界：本节点只做展示结果合并，不执行 Tool、不调用 LLM、不访问 kube-manager，
 * 也不把某个 Agent 的自然语言输出解释为 HITL、audit、release 或写入成功证据。即使
 * {@code answer} 来自安全停止原因，它也只是用户可见说明，不代表 Tool 成功，
 * 更不会被转成权限事实。</p>
 */
public class ToolResultMergeNode implements NodeAction {

    private static final String[] RESULT_KEYS = {
            // 先保留上游已经明确写好的最终答案，避免 direct_answer 的 final_answer 被 supervisor_result 覆盖。
            "final_answer",
            // ReAct 节点会同时写 answer/react_node_result；优先使用专用 key 便于学习和调试。
            "react_node_result",
            // 专业 Agent 子图结果。
            "query_result", "deploy_result", "diag_result",
            "rbac_result", "storage_result", "network_result",
            // Graph tool_call / execute_node / react_node fail-closed 时常只写 answer，必须能展示给前端。
            "answer",
            // 最后才回落到 supervisor_result；它通常是 BrainDecision 或推理摘要，不一定是最终用户回答。
            "supervisor_result"
    };

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object finalAnswer = null;
        for (String key : RESULT_KEYS) {
            if (state.value(key).isPresent()) {
                finalAnswer = state.value(key).get();
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "{\"error\":\"未获取到结果\"}";
        }

        return Map.of("final_answer", finalAnswer);
    }
}
