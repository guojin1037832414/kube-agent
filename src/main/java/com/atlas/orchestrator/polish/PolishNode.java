package com.atlas.orchestrator.polish;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * Graph 模式下的润色节点 — v3.1 B方案扩展。
 *
 * <p><b>重要限制：</b>当前 StateGraph 节点为同步执行（返回 Map），
 * 无法直接支持 Flux 流式输出到 SSE。因此本节点采用 {@link ToolResultPolishingService#polishSync()}
 * 同步润色，润色结果写入 state 的 "answer" key。</p>
 *
 * <p><b>流式润色方案：</b>如需在 Graph 模式下实现流式输出，
 * 建议在 {@link com.atlas.orchestrator.AtlasOrchestrator#runSupervisorGraph()}
 * 的 {@code .subscribe()} 回调中，读取 "tool_result" state 后，
 * 在 Graph 外部调用 {@link ToolResultPolishingService#polishStream()} 并直接 emit SSE。</p>
 *
 * <p><b>节点输入（state keys）：</b></p>
 * <ul>
 *   <li>{@code tool_result}: Tool 执行返回的 Map</li>
 *   <li>{@code input}: 用户原始 query</li>
 * </ul>
 *
 * <p><b>节点输出（state keys）：</b></p>
 * <ul>
 *   <li>{@code answer}: 润色后的自然语言文本</li>
 *   <li>{@code polish_node_status}: "ok" / "fallback"</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P3
 */
public class PolishNode implements NodeAction {

    private final ToolResultPolishingService polishingService;

    public PolishNode(ToolResultPolishingService polishingService) {
        this.polishingService = polishingService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userQuery = state.value("input").map(Object::toString).orElse("");

        Object toolResultObj = state.value("tool_result").orElse(null);
        if (!(toolResultObj instanceof Map)) {
            return Map.of(
                "answer", "⚠️ 无法获取工具执行结果",
                "polish_node_status", "no_data"
            );
        }

        Map<String, Object> toolResult = (Map<String, Object>) toolResultObj;

        try {
            // Graph 模式下使用同步润色
            String polished = polishingService.polishSync(toolResult, userQuery);
            return Map.of(
                "answer", polished,
                "polish_node_status", "ok"
            );
        } catch (Exception e) {
            // fallback：原始结果兜底
            boolean success = Boolean.TRUE.equals(toolResult.get("success"));
            String message = toolResult.get("message") != null
                ? toolResult.get("message").toString() : "";
            return Map.of(
                "answer", (success ? "✅ " : "❌ ") + message,
                "polish_node_status", "fallback"
            );
        }
    }
}
