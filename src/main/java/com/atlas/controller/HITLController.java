package com.atlas.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.atlas.brain.BrainDecision;
import com.atlas.auth.UserPermissionContext;
import com.atlas.auth.async.AsyncContextHolder;
import com.atlas.hitl.HitlConfirmation;
import com.atlas.orchestrator.AtlasOrchestrator;
import com.atlas.orchestrator.StreamingEmitter;
import com.atlas.orchestrator.SseEvent;
import com.atlas.orchestrator.TimedDecisionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HITL (Human-in-the-Loop) 交互控制器 — v3.1 M1.5。
 *
 * <p>高危操作人工确认 + 意图澄清的 REST/SSE 接口：</p>
 * <ul>
 *   <li>{@code POST /api/v1/hitl/confirm} — 人工确认后恢复 Graph 执行（带 Token 校验 + 幂等性）</li>
 *   <li>{@code POST /api/v1/hitl/clarify} — 用户提供补充信息后重新执行</li>
 * </ul>
 *
 * <p><b>安全设计：</b></p>
 * <ul>
 *   <li>confirmToken：每个 HITL 决策生成唯一 SHA-256 Token，前端必须原样带回</li>
 *   <li>幂等性：已处理的会话 ID 在 10 分钟内不可重复 confirm</li>
 *   <li>TTL：决策 5 分钟未确认自动过期，前端收到 410 Gone</li>
 * </ul>
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>AtlasOrchestrator 检测到 HITL_CONFIRM → decisionCache.put() 生成 token → SSE hitl_request</li>
 *   <li>前端收到 SSE → 展示"命令式确认"弹窗（用户必须输入指定文字）</li>
 *   <li>用户确认 → POST /confirm {threadId, confirmToken} → 校验 → resumeGraph</li>
 *   <li>AtlasBrain resume 检测复用新决策 → 继续执行</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-M1.5
 */
@RestController
@RequestMapping("/api/agent/hitl")
public class HITLController {
    private static final Logger log = LoggerFactory.getLogger(HITLController.class);

    private final CompiledGraph compiledGraph;
    private final AtlasOrchestrator orchestrator;
    private final StreamingEmitter streamingEmitter;
    private final TimedDecisionCache decisionCache;
    private final Executor asyncExecutor;

    public HITLController(
            @Qualifier("supervisorGraph") CompiledGraph compiledGraph,
            AtlasOrchestrator orchestrator,
            StreamingEmitter streamingEmitter,
            TimedDecisionCache decisionCache,
            @Qualifier("atlasTaskExecutor") Executor asyncExecutor) {
        this.compiledGraph = compiledGraph;
        this.orchestrator = orchestrator;
        this.streamingEmitter = streamingEmitter;
        this.decisionCache = decisionCache;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 高危操作人工确认 — 流式恢复执行。
     *
     * <p>将 AtlasBrain 产出的 HITL_CONFIRM 决策转换为 CALL_TOOL，
     * 并重新注入 Graph 执行（AtlasBrain resume 检测会直接复用）。</p>
     *
     * @param request 确认请求（包含原会话 threadId + confirmToken）
     * @return SSE 流，包含 Tool 执行结果
     */
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirmAndResume(@RequestBody ConfirmRequest request) {
        String threadId = request.threadId();
        String confirmToken = request.confirmToken();
        SseEmitter emitter = streamingEmitter.createEmitter("hitl-" + threadId);

        // 安全校验：Token 匹配 + 幂等性检查
        BrainDecision original = decisionCache.remove(threadId, confirmToken);
        if (original == null) {
            CompletableFuture.runAsync(
                () -> streamingEmitter.error(emitter,
                    "会话已过期、不存在或已被确认（Token 无效/过期/重复使用）"),
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

        HitlConfirmation confirmation = HitlConfirmation.human(threadId, original.target());
        log.info("[HITL] 用户确认执行: threadId={}, target={}", threadId, original.target());
        runResumeWithCheckpointContext(threadId, confirmed, confirmation, emitter);
        return emitter;
    }

    /**
     * 意图澄清 — 用户提供补充信息后重新执行。
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

        // 清理旧决策（无需 Token 校验，因为是用户主动补充）
        decisionCache.removeForClarify(threadId);

        // 构建新决策：携带用户补充信息
        BrainDecision clarified = new BrainDecision(
            BrainDecision.ActionType.ASK_CLARIFY,
            "",
            Map.of("clarified_input", String.valueOf(request.reply())),
            "用户补充: " + request.reply(),
            0.5,
            List.of()
        );

        log.info("[HITL] 用户澄清: threadId={}, reply={}", threadId, request.reply());
        runResumeWithCheckpointContext(threadId, clarified, null, emitter);
        return emitter;
    }

    /**
     * M5.6：HITL 恢复执行前先从 checkpoint 捕获 token + orgId 原子安全上下文。
     *
     * <p>HITL confirm/clarify 是 Graph 暂停后的异步恢复入口，如果只恢复 token、不恢复 orgId，
     * 后续 Tool 层会失去可信租户边界。这里提前读取 checkpoint 并使用 AsyncContextHolder 包装，
     * 保证异步线程中 ThreadLocal 的 token/orgId 与主流程一致；缺失 orgId 时 fail-safe，不继续执行。</p>
     */
    private void runResumeWithCheckpointContext(String threadId,
                                                BrainDecision decision,
                                                HitlConfirmation confirmation,
                                                SseEmitter emitter) {
        CheckpointContext context = loadCheckpointContext(threadId);
        if (context.orgId().isBlank()) {
            CompletableFuture.runAsync(
                () -> streamingEmitter.error(emitter, "安全上下文缺失：无法确定当前用户所属组织，请重新登录后再试。"),
                asyncExecutor
            );
            return;
        }
        CompletableFuture.runAsync(
            AsyncContextHolder.wrap(() -> resumeGraph(threadId, decision, confirmation, emitter), context.token(), context.orgId()),
            asyncExecutor
        );
    }

    /** 从 checkpoint 中提取恢复执行所需的 token + orgId 原子上下文。 */
    private CheckpointContext loadCheckpointContext(String threadId) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        try {
            Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(config);
            if (snapshotOpt.isPresent() && snapshotOpt.get().state() != null) {
                OverAllState oldState = snapshotOpt.get().state();
                String token = oldState.value("token").map(Object::toString).orElse("");
                String orgId = oldState.value("orgId")
                    .or(() -> oldState.value("organizationId"))
                    .map(Object::toString)
                    .orElse("");
                return new CheckpointContext(token, orgId);
            }
        } catch (Exception e) {
            log.warn("[HITL] checkpoint 安全上下文读取失败: {}", e.getMessage());
        }
        return new CheckpointContext("", "");
    }

    /**
     * 核心恢复逻辑：从 checkpoint 读取状态，注入新决策，流式执行 Graph。
     *
     * <p>关键：resume 时必须复用原会话的 Token/上下文，否则后端 Tool 无权限执行。</p>
     */
    private void resumeGraph(String threadId,
                             BrainDecision newDecision,
                             HitlConfirmation confirmation,
                             SseEmitter emitter) {
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            // 1. 构建输入：新决策 + 澄清输入
            Map<String, Object> inputs = new HashMap<>();
        inputs.put("brain_decision", newDecision);
        if (confirmation != null) {
            inputs.put("hitl_confirmation", confirmation);
        } else {
            // M5.13 fail-closed 修复：clarify/resume 不是人工确认，必须显式清空旧确认 marker，
            // 防止同一 thread/checkpoint 中历史确认被后续恢复流程继承并绕过新的 HITL。
            inputs.put("hitl_confirmation", null);
        }
            inputs.put("input", String.valueOf(
                newDecision.parameters().getOrDefault("clarified_input", "")
            ));

            // 2. 尝试从 checkpoint 恢复上下文（token, user_id, conversation_id）
            try {
                Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(config);
                if (snapshotOpt.isPresent() && snapshotOpt.get().state() != null) {
                    OverAllState oldState = snapshotOpt.get().state();
                    oldState.value("user_id").ifPresent(v -> inputs.put("user_id", v));
                    oldState.value("token").ifPresent(v -> inputs.put("token", v));
                    oldState.value("orgId").ifPresent(v -> {
                        inputs.put("orgId", v);
                        inputs.put("organizationId", v);
                    });
                    oldState.value("organizationId").ifPresent(v -> inputs.putIfAbsent("organizationId", v));
                    oldState.value("conversation_id").ifPresent(v -> inputs.put("conversation_id", v));
                    oldState.value("messages").ifPresent(v -> inputs.put("messages", v));
                    log.debug("[HITL] 从 checkpoint 恢复上下文: threadId={}", threadId);
                }
            } catch (Exception e) {
                log.warn("[HITL] checkpoint 读取失败，将新建状态: {}", e.getMessage());
            }

            log.info("[HITL] 恢复会话 {}, actionType={}", threadId, newDecision.actionType());

            // 3. 流式执行 Graph — SSE 事件格式与主流程完全一致
            Set<String> emittedStructuredClarifications = java.util.concurrent.ConcurrentHashMap.newKeySet();
            compiledGraph.stream(inputs, config)
                .subscribe(
                    nodeOutput -> {
                        String node = nodeOutput.node();
                        var state = nodeOutput.state();

                        log.debug("[HITL] 节点 {} 输出", node);

                        // 使用与 AtlasOrchestrator 完全一致的 SSE 事件格式
                        state.value("thinking").ifPresent(content ->
                            emitSse(emitter, "thinking", Map.of("step", node, "content", content))
                        );

                        state.value(node + "_result").ifPresent(result ->
                            emitSse(emitter, "content", Map.of("node", node, "result", result.toString()))
                        );

                        state.value("answer").ifPresent(ans ->
                            emitSse(emitter, "content", Map.of("answer", ans.toString()))
                        );

                        if ("tool_call".equals(node) || "execute_node".equals(node)) {
                            emitStructuredClarificationIfPresent(
                                emitter,
                                threadId,
                                node,
                                state::value,
                                emittedStructuredClarifications
                            );
                        }
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

    /** 统一 SSE 事件发送（与 AtlasOrchestrator.emit() 格式完全一致） */
    private void emitSse(SseEmitter emitter, String event, Map<String, Object> payload) {
        try {
            String json = toJson(payload);
            streamingEmitter.send(emitter, new SseEvent(event, json));
        } catch (Exception e) {
            log.warn("[HITL] SSE 发送失败: {}", e.getMessage());
        }
    }

    /**
     * 恢复流中的 Tool 结构化补参信号转发。
     *
     * <p>confirm/clarify resume 也可能再次触发 Tool 级别的缺参或歧义，例如用户补充 GPU 型号后仍缺少
     * 明确 {@code gpuSpec}。这里与 AtlasOrchestrator 的主流式入口保持同一事件语义，继续发 clarify。</p>
     */
    private void emitStructuredClarificationIfPresent(SseEmitter emitter,
                                                      String threadId,
                                                      String node,
                                                      java.util.function.Function<String, Optional<Object>> stateValue,
                                                      Set<String> emittedKeys) {
        boolean requiresClarification = stateValue.apply("requires_clarification")
            .map(this::isTruthy)
            .orElse(false);
        if (!requiresClarification) {
            return;
        }

        Object errorCode = stateValue.apply("tool_error_code").orElse(null);
        Object suggestions = stateValue.apply("tool_suggestions").orElse(null);
        Object toolResult = stateValue.apply("tool_result").orElse(null);
        if (toolResult instanceof Map<?, ?> tr) {
            if (errorCode == null) {
                errorCode = tr.get("errorCode");
            }
            if (suggestions == null) {
                suggestions = tr.get("suggestions");
            }
        }

        String dedupeKey = node + "|" + String.valueOf(errorCode) + "|" + String.valueOf(suggestions);
        if (emittedKeys != null && !emittedKeys.add(dedupeKey)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("threadId", threadId);
        payload.put("node", node);
        payload.put("source", "tool_result");
        payload.put("errorCode", errorCode != null ? errorCode : "TOOL_REQUIRES_CLARIFICATION");
        payload.put("suggestions", suggestions != null ? suggestions : List.of());
        payload.put("requiredContext", suggestions != null ? suggestions : List.of());
        payload.put("content", buildClarificationContent(errorCode, suggestions));
        emitSse(emitter, "clarify", payload);
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private String buildClarificationContent(Object errorCode, Object suggestions) {
        String codeText = errorCode != null ? errorCode.toString() : "TOOL_REQUIRES_CLARIFICATION";
        return "需要补充信息后才能继续执行（" + codeText + "）"
            + (suggestions != null ? "：" + suggestions : "");
    }

    /** 简易 JSON 序列化（与 AtlasOrchestrator.toJson 保持一致） */
    @SuppressWarnings("unchecked")
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
            } else if (v instanceof Map) {
                sb.append(toJson((Map<String, Object>) v));
            } else if (v instanceof List) {
                sb.append(toJsonList((List<?>) v));
            } else {
                sb.append("\"").append(v.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            if (item instanceof String s) {
                sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (item instanceof Number || item instanceof Boolean) {
                sb.append(item);
            } else {
                sb.append("\"").append(item.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private record CheckpointContext(String token, String orgId) {
    }

    // ── 请求 DTO ────────────────────────────────────

    /**
     * 确认请求体 — M1.5 强化版（含 confirmToken 安全校验）。
     */
    public record ConfirmRequest(String threadId, String confirmToken) {}

    /**
     * 澄清请求体。
     */
    public record ClarifyRequest(String threadId, String reply) {}
}
