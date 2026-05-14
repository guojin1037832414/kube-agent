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
