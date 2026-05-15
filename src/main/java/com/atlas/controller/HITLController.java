package com.atlas.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.atlas.brain.BrainDecision;
import com.atlas.orchestrator.AtlasOrchestrator;
import com.atlas.orchestrator.StreamingEmitter;
import com.atlas.orchestrator.SseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HITL (Human-in-the-Loop) 交互控制器 — v3.1 HITL 闭环。
 *
 * <p>提供高危操作确认和意图澄清的 REST/SSE 接口：</p>
 * <ul>
 *   <li>{@code POST /api/v1/hitl/confirm} — 人工确认后恢复 Graph 执行</li>
 *   <li>{@code POST /api/v1/hitl/clarify} — 用户提供补充信息后重新执行</li>
 * </ul>
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>AtlasOrchestrator 检测到 HITL_CONFIRM/ASK_CLARIFY → pendingDecisions 保存</li>
 *   <li>前端收到 SSE 事件 → 展示确认/追问 UI</li>
 *   <li>用户交互 → POST 本接口</li>
 *   <li>本接口构建新 BrainDecision（HITL_CONFIRM → CALL_TOOL，ASK_CLARIFY → 携带补充input）
 *   <li>调用 CompiledGraph.stream() 恢复执行，AtlasBrain resume 检测复用新决策</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-HITL
 */
@RestController
@RequestMapping("/api/v1/hitl")
public class HITLController {
    private static final Logger log = LoggerFactory.getLogger(HITLController.class);

    private final CompiledGraph compiledGraph;
    private final AtlasOrchestrator orchestrator;
    private final StreamingEmitter streamingEmitter;
    private final Executor asyncExecutor;

    public HITLController(
            CompiledGraph compiledGraph,
            AtlasOrchestrator orchestrator,
            StreamingEmitter streamingEmitter,
            @Qualifier("atlasTaskExecutor") Executor asyncExecutor) {
        this.compiledGraph = compiledGraph;
        this.orchestrator = orchestrator;
        this.streamingEmitter = streamingEmitter;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 高危操作人工确认 — 流式恢复执行。
     *
     * <p>将 AtlasBrain 产出的 HITL_CONFIRM 决策转换为 CALL_TOOL，
     * 并重新注入 Graph 执行（AtlasBrain resume 检测会直接复用）。</p>
     *
     * @param request 确认请求（包含原会话 threadId）
     * @return SSE 流，包含 Tool 执行结果
     */
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirmAndResume(@RequestBody ConfirmRequest request) {
        String threadId = request.threadId();
        SseEmitter emitter = streamingEmitter.createEmitter("hitl-" + threadId);

        BrainDecision original = orchestrator.removePendingDecision(threadId);
        if (original == null) {
            CompletableFuture.runAsync(
                () -> streamingEmitter.error(emitter, "会话已过期或不存在待确认操作，请重新发起请求"),
                asyncExecutor
            );
            return emitter;
        }

        // 构建新的决策：HITL_CONFIRM → CALL_TOOL（用户已确认执行）
        BrainDecision confirmed = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            original.target(),
            original.parameters(),
            "用户已确认执行: " + original.reasoning(),
            original.confidence(),
            original.requiredContext()
        );

        log.info("[HITL] 用户确认执行: threadId={}, target={}", threadId, original.target());
        CompletableFuture.runAsync(() -> resumeGraph(threadId, confirmed, emitter), asyncExecutor);
        return emitter;
    }

    /**
     * 意图澄清 — 用户使用补充信息后重新执行。
     *
     * <p>将用户回复作为新 input，重新触发 AtlasBrain 决策（会带上补充上下文），
     * 期望 Brain 这次能产出明确的 CALL_TOOL 或 DIRECT_ANSWER。</p>
     *
     * @param request 澄清请求（包含原会话 threadId + 用户回复）
     * @return SSE 流
     */
    @PostMapping(value = "/clarify", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter clarifyAndResume(@RequestBody ClarifyRequest request) {
        String threadId = request.threadId();
        SseEmitter emitter = streamingEmitter.createEmitter("clarify-" + threadId);

        // 清理旧决策
        orchestrator.removePendingDecision(threadId);

        // 构建新决策：携带用户补充信息，让 AtlasBrain 重新决策
        // resume 时 AtlasBrain 检测到已有非中断决策会复用 — 所以这里用 ASK_CLARIFY
        // 并携带 clarified_input，让 Graph supervisor 节点读取并调用 AtlasBrain
        BrainDecision clarified = new BrainDecision(
            BrainDecision.ActionType.ASK_CLARIFY,
            "",
            Map.of("clarified_input", String.valueOf(request.reply())),
            "用户补充: " + request.reply(),
            0.5,
            List.of()
        );

        log.info("[HITL] 用户澄清: threadId={}, reply={}", threadId, request.reply());
        CompletableFuture.runAsync(() -> resumeGraph(threadId, clarified, emitter), asyncExecutor);
        return emitter;
    }

    /**
     * 核心恢复逻辑：从 checkpoint 读取状态，注入新决策，流式执行 Graph。
     */
    private void resumeGraph(String threadId, BrainDecision newDecision, SseEmitter emitter) {
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            // 1. 尝试从 checkpoint 恢复上下文（token, user_id, conversation_id）
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("brain_decision", newDecision);
            inputs.put("input", String.valueOf(
                newDecision.parameters().getOrDefault("clarified_input", "")
            ));

            try {
                Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(config);
                if (snapshotOpt.isPresent() && snapshotOpt.get().state() != null) {
                    OverAllState oldState = snapshotOpt.get().state();
                    oldState.value("user_id").ifPresent(v -> inputs.put("user_id", v));
                    oldState.value("token").ifPresent(v -> inputs.put("token", v));
                    oldState.value("conversation_id").ifPresent(v -> inputs.put("conversation_id", v));
                    oldState.value("messages").ifPresent(v -> inputs.put("messages", v));
                    log.debug("[HITL] 从 checkpoint 恢复上下文: threadId={}", threadId);
                }
            } catch (Exception e) {
                log.warn("[HITL] checkpoint 读取失败，将新建状态: {}", e.getMessage());
            }

            log.info("[HITL] 恢复会话 {}, actionType={}", threadId, newDecision.actionType());

            // 2. 流式执行 Graph
            compiledGraph.stream(inputs, config)
                .subscribe(
                    nodeOutput -> {
                        String node = nodeOutput.node();
                        var state = nodeOutput.state();

                        log.debug("[HITL] 节点 {} 输出", node);
                        emit(emitter, "thinking", Map.of("step", node, "content", "节点 " + node + " 正在执行..."));

                        state.value(node + "_result").ifPresent(result ->
                            emit(emitter, "content", Map.of("node", node, "result", result.toString()))
                        );
                    },
                    err -> {
                        log.error("[HITL] 会话 {} 恢复执行错误", threadId, err);
                        streamingEmitter.error(emitter, err.getMessage());
                    },
                    () -> {
                        log.info("[HITL] 会话 {} 恢复执行完成", threadId);
                        streamingEmitter.complete(emitter);
                    }
                );

        } catch (Exception e) {
            log.error("[HITL] 恢复会话 {} 异常", threadId, e);
            streamingEmitter.error(emitter, "恢复执行失败: " + e.getMessage());
        }
    }

    /** SSE 事件发送 */
    private void emit(SseEmitter emitter, String event, Map<String, Object> payload) {
        try {
            String json = toJson(payload);
            streamingEmitter.send(emitter, new SseEvent(event, json));
        } catch (Exception e) {
            log.warn("[HITL] SSE 发送失败: {}", e.getMessage());
        }
    }

    /** 简易 JSON 序列化 */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String s) {
                sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(v.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ── 请求 DTO ────────────────────────────────────

    /**
     * 确认请求体
     */
    public record ConfirmRequest(String threadId, String action) {}

    /**
     * 澄清请求体
     */
    public record ClarifyRequest(String threadId, String reply) {}
}
