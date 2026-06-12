package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * 结果合并节点 — 将各专业 Agent 的输出合并为统一的 final_answer。
 *
 * <p>中文说明：这是旧 atlasGraph 的收束节点，负责从 query/deploy/diag/rbac/storage/network
 * 等子图结果中选择一个最终输出，写入前端统一消费的 final_answer。</p>
 *
 * <p>安全边界：本节点只做展示结果合并，不执行 Tool、不调用 LLM、不访问 kube-manager，
 * 也不把某个 Agent 的自然语言输出解释为 HITL、audit、release 或写入成功证据。</p>
 */
public class ToolResultMergeNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 按优先级读取各 Agent 的结果
        String[] resultKeys = {
                "query_result", "deploy_result", "diag_result",
                "rbac_result", "storage_result", "network_result"
        };

        Object finalAnswer = null;
        for (String key : resultKeys) {
            if (state.value(key).isPresent()) {
                finalAnswer = state.value(key).get();
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = state.value("supervisor_result").orElse("{\"error\":\"未获取到结果\"}");
        }

        return Map.of("final_answer", finalAnswer);
    }
}
