package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.atlas.orchestrator.SseEvent;
import com.atlas.orchestrator.StreamingEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE 输出节点 — 将 final_answer 通过 SSE 推送给前端。
 *
 * <p>中文说明：这是旧 atlasGraph 的展示出口，把 Graph State 中已经形成的 final_answer
 * 转成前端能接收的 SSE content 事件。它让学习者看到 Graph 最后一跳如何连接到 UI。</p>
 *
 * <p>安全边界：SSE 只负责传输展示文本，不代表 Tool 执行、HITL 确认、审计落盘或 release gate 通过。
 * 新增事件时必须避免把 token、orgId、内部 endpoint、audit receipt 原文等敏感字段直接推给前端。</p>
 *
 * <p>注意：SSE 是副作用操作，StateGraph 节点不应做纯副作用，
 * 此处作为演示，实际生产建议将 SSE 发射移出 Graph 或作为 Hook 实现。</p>
 */
public class SseEmitNode implements NodeAction {

    private final StreamingEmitter streamingEmitter;

    public SseEmitNode(StreamingEmitter streamingEmitter) {
        this.streamingEmitter = streamingEmitter;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object finalAnswer = state.value("final_answer").orElse("");

        // 获取 SSE emitter（需要在状态中传递）
        SseEmitter emitter = state.value("emitter")
                .map(v -> (SseEmitter) v)
                .orElse(null);

        if (emitter != null) {
            String json = finalAnswer instanceof String s ? s : finalAnswer.toString();
            streamingEmitter.send(emitter, new SseEvent("content", json));
            streamingEmitter.complete(emitter);
        }

        return Map.of("emitted", true);
    }
}
