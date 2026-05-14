package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * 结果合并节点 — 将各专业 Agent 的输出合并为统一的 final_answer。
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
