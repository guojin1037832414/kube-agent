package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolResultMergeNode 结果合并行为测试。
 *
 * <p>中文说明：旧 atlasGraph 会把 direct_answer、专业 Agent、ReAct 和 Graph fail-closed 结果
 * 汇聚到 merge_result。本测试锁定合并优先级，避免用户已经可以看到的回答或安全停止原因在最后一跳
 * 被 supervisor_result 覆盖。</p>
 *
 * <p>安全边界：本测试只执行纯内存节点，不调用 LLM、不执行 Tool、不访问 kube-manager、
 * 不创建 HITL/audit/release 证据。{@code final_answer} 只是 SSE 展示文本，不代表 Tool 成功。</p>
 */
class ToolResultMergeNodeTest {

    private final ToolResultMergeNode node = new ToolResultMergeNode();

    @Test
    void apply_shouldKeepExistingFinalAnswerFromDirectAnswerNode() throws Exception {
        Map<String, Object> updates = node.apply(new OverAllState(Map.of(
            "final_answer", "这是 direct_answer 已生成的最终回答",
            "supervisor_result", "BrainDecision(reasoning=直接回答)"
        )));

        assertThat(updates).containsEntry("final_answer", "这是 direct_answer 已生成的最终回答");
    }

    @Test
    void apply_shouldPreferReactNodeResultBeforeGenericAnswerAndSupervisor() throws Exception {
        Map<String, Object> updates = node.apply(new OverAllState(Map.of(
            "react_node_result", "ReAct 诊断总结",
            "answer", "通用 answer",
            "supervisor_result", "BrainDecision(reasoning=走 ReAct)"
        )));

        assertThat(updates).containsEntry("final_answer", "ReAct 诊断总结");
    }

    @Test
    void apply_shouldExposeFailClosedAnswerWhenNoDedicatedResultExists() throws Exception {
        Map<String, Object> updates = node.apply(new OverAllState(Map.of(
            "answer", "⛔ Graph 已停止：缺失可信组织上下文",
            "tool_error_code", "GRAPH_TRUSTED_ORG_MISSING",
            "supervisor_result", "BrainDecision(reasoning=CALL_TOOL)"
        )));

        assertThat(updates)
            .as("安全停止原因必须能进入 final_answer，但仍只是展示文本，不代表 Tool 已执行")
            .containsEntry("final_answer", "⛔ Graph 已停止：缺失可信组织上下文");
    }

    @Test
    void apply_shouldFallBackToExplicitErrorWhenNoRenderableResultExists() throws Exception {
        Map<String, Object> updates = node.apply(new OverAllState(Map.of()));

        assertThat(updates.get("final_answer").toString()).contains("未获取到结果");
    }
}
