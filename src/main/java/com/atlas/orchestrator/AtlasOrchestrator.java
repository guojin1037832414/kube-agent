package com.atlas.orchestrator;

import com.atlas.auth.UserPermissionContext;
import com.atlas.auth.async.AsyncContextHolder;
import com.atlas.intent.IntentRouter;
import com.atlas.intent.core.IntentResult;
import com.atlas.tool.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Atlas 统一编排器 — v3.1 P0 清场版。
 *
 * <p><b>Phase 0 变更：</b></p>
 * <ul>
 *   <li>删除旧 AtlasAgentBase 及 6 个子类依赖</li>
 *   <li>Tool 执行直接走 ToolRegistry（P1 基础设施，无需 Agent 包装）</li>
 *   <li>保留 SSE 流式输出、Token 透传、权限感知等成熟能力</li>
 *   <li>Graph 模式为可选实验功能（CompiledGraph 未注入时自动降级）</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P0
 */
@RestController
@RequestMapping("/api/v1")
public class AtlasOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AtlasOrchestrator.class);

    private final IntentRouter intentRouter;
    private final StreamingEmitter streamingEmitter;
    private final ToolRegistry toolRegistry;
    private final UserPermissionContext userPermissionContext;
    private final Executor asyncExecutor;

    /**
     * (可选) Spring AI Alibaba StateGraph 编译后的执行图。
     * P2 阶段新增：实验性接口 /chat/graph 使用。
     * 如果未启用 Graph 模式（null），则回退到 IntentRouter 路由。
     */
    private final com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph;

    /** 每用户最大并发连接数 */
    private static final int MAX_PER_USER = 3;
    private final Map<String, Integer> userConnections = new ConcurrentHashMap<>();

    /**
     * 构造方法 — P0 版。
     *
     * <p>删除了旧 {@code List<AtlasAgentBase>} 参数，Tool 执行直接通过
     * {@link ToolRegistry} 完成，无需 Agent 包装层。</p>
     */
    @Autowired
    public AtlasOrchestrator(IntentRouter intentRouter,
                             StreamingEmitter streamingEmitter,
                             ToolRegistry toolRegistry,
                             UserPermissionContext userPermissionContext,
                             @Qualifier("atlasTaskExecutor") Executor asyncExecutor,
                             @Autowired(required = false)
                             com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph) {
        this.intentRouter = intentRouter;
        this.streamingEmitter = streamingEmitter;
        this.toolRegistry = toolRegistry;
        this.userPermissionContext = userPermissionContext;
        this.asyncExecutor = asyncExecutor;
        this.compiledGraph = compiledGraph;
        log.info("[Orchestrator] 初始化完成 | Graph模式: {}",
            compiledGraph != null ? "已启用 ✅" : "未启用 ⚠️");
    }

    /**
     * SSE 流式对话接口 — P0 清场版。
     *
     * <p>端到端流程：</p>
     * <ol>
     *   <li>thinking — 正在分析意图</li>
     *   <li>{@link IntentRouter#route} 分类意图</li>
     *   <li>content — 返回意图分类结果</li>
     *   <li>tool_call / tool_result — 直接调用 ToolRegistry 执行</li>
     *   <li>done — 完成</li>
     * </ol>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        String userId = request.userId() != null ? request.userId() : "anonymous";
        String sessionId = userId + "-" + System.currentTimeMillis();

        // ① 主线程捕获 Token（必须在 runAsync 之前！）
        String capturedToken = userPermissionContext.getCurrentToken();
        if (capturedToken == null || capturedToken.isBlank()) {
            log.warn("[Orchestrator] 请求未携带 Token（匿名用户）");
        }

        // 连接限流
        if (userConnections.getOrDefault(userId, 0) >= MAX_PER_USER) {
            SseEmitter errEmitter = new SseEmitter(0L);
            CompletableFuture.runAsync(
                AsyncContextHolder.wrap(() -> streamingEmitter.error(errEmitter,
                    "超过最大并发连接数: " + MAX_PER_USER), capturedToken),
                asyncExecutor
            );
            return errEmitter;
        }

        userConnections.merge(userId, 1, Integer::sum);
        SseEmitter emitter = streamingEmitter.createEmitter(sessionId);

        // ② 异步任务 — Token 显式透传
        Runnable asyncTask = () -> {
            try {
                // 1. thinking
                emit(emitter, "thinking", Map.of("step", "intent", "content", "正在分析您的意图..."));

                // 2. 意图路由
                IntentResult result = intentRouter.route(request.userQuery());
                log.info("[Orchestrator] {} → {} ({}, norm={:.3f})",
                    sessionId, result.intentId(), result.matchedLevel(), result.confidence());

                // 3. content — 返回分类结果
                emit(emitter, "content", Map.of(
                    "intentId", result.intentId(),
                    "description", result.description(),
                    "confidence", result.confidence(),
                    "matchedLevel", result.matchedLevel(),
                    "agent", result.agent()
                ));

                // 4. Tool 执行 — 直接通过 ToolRegistry（P0 删除 Agent 包装层）
                Optional<ToolRegistry.ToolMetadata> toolOpt = toolRegistry.findByIntentId(result.intentId())
                    .map(tool -> {
                        try {
                            return toolRegistry.resolve(tool.getToolName());
                        } catch (Exception e) {
                            log.warn("[Orchestrator] Tool '{}' 权限校验失败: {}", tool.getToolName(), e.getMessage());
                            return null;
                        }
                    });

                if (toolOpt.isPresent()) {
                    ToolRegistry.ToolMetadata meta = toolOpt.get();
                    emit(emitter, "tool_call", Map.of("tool", result.intentId(), "agent", meta.agent()));

                    // 权限预检（兜底）
                    if (toolRegistry.canExecuteIntent(result.intentId())) {
                        // P0 暂不执行真实 Tool 调用（避免参数提取不完整导致下游异常）
                        // 仅返回 Tool 元数据作为演示，Phase 1 接入 ReActEngine 后恢复完整调用
                        emit(emitter, "tool_result", Map.of(
                            "success", true,
                            "message", "意图识别成功，Tool '" + result.intentId() + "' 已定位（Agent: " + meta.agent() + "）",
                            "tool", result.intentId(),
                            "agent", meta.agent(),
                            "description", meta.description()
                        ));
                    } else {
                        log.warn("[Orchestrator] 用户 {} 越权尝试执行意图 '{}'", userId, result.intentId());
                        emit(emitter, "tool_result", Map.of(
                            "success", false,
                            "error", "权限不足",
                            "message", "当前用户无权执行此操作",
                            "deniedIntent", result.intentId()
                        ));
                    }
                } else {
                    emit(emitter, "content", Map.of(
                        "type", "notice",
                        "content", "意图 '" + result.intentId() + "' 已识别（Agent: " + result.agent() + "），暂无对应 Tool 实现。"
                    ));
                }

                // 5. done
                streamingEmitter.complete(emitter);

            } catch (Exception e) {
                log.error("[Orchestrator] 会话异常: {}", sessionId, e);
                streamingEmitter.error(emitter, e.getMessage());
            } finally {
                userConnections.merge(userId, -1, Integer::sum);
            }
        };

        CompletableFuture.runAsync(
            AsyncContextHolder.wrap(asyncTask, capturedToken),
            asyncExecutor
        );

        return emitter;
    }

    /**
     * 健康检查接口。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "version", "3.1.0-P0",
            "activeConnections", streamingEmitter.activeCount(),
            "toolRegistry", toolRegistry.health(),
            "graphEnabled", compiledGraph != null
        );
    }

    // ═══════════════════════════════════════════════════════════
    // P2 实验: StateGraph 流式对话接口
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/chat/graph", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatGraph(@RequestBody ChatRequest request) {
        if (compiledGraph == null) {
            SseEmitter err = new SseEmitter(0L);
            CompletableFuture.runAsync(() -> {
                try {
                    streamingEmitter.send(err, new SseEvent("error",
                        toJson(Map.of("code","GRAPH_NOT_AVAILABLE",
                                      "message","StateGraph 未启用 (CompiledGraph bean 未注入)"))));
                    streamingEmitter.complete(err);
                } catch (Exception ignored) {}
            }, asyncExecutor);
            return err;
        }

        String userId = request.userId() != null ? request.userId() : "anonymous";
        String sessionId = userId + "-graph-" + System.currentTimeMillis();
        String capturedToken = userPermissionContext.getCurrentToken();

        // 连接限流
        if (userConnections.getOrDefault(userId, 0) >= MAX_PER_USER) {
            SseEmitter errEmitter = new SseEmitter(0L);
            CompletableFuture.runAsync(
                AsyncContextHolder.wrap(() -> streamingEmitter.error(errEmitter,
                    "超过最大并发连接数: " + MAX_PER_USER), capturedToken),
                asyncExecutor
            );
            return errEmitter;
        }

        userConnections.merge(userId, 1, Integer::sum);
        SseEmitter emitter = streamingEmitter.createEmitter(sessionId);

        // 异步执行 Graph stream
        Runnable graphTask = () -> {
            try {
                Map<String, Object> inputs = Map.of(
                    "input", request.userQuery(),
                    "conversation_id", Optional.ofNullable(request.conversationId()).orElse(""),
                    "user_id", userId,
                    "token", Optional.ofNullable(capturedToken).orElse("")
                );

                var config = com.alibaba.cloud.ai.graph.RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

                log.info("[Graph] 启动会话 {}, 输入: {}", sessionId, request.userQuery());

                compiledGraph.stream(inputs, config)
                    .subscribe(
                        nodeOutput -> {
                            String node = nodeOutput.node();
                            var state = nodeOutput.state();

                            log.debug("[Graph] 节点 {} 输出，state keys: {}",
                                node, state.data().keySet());

                            emit(emitter, "thinking", Map.of(
                                "step", node,
                                "content", "节点 " + node + " 正在执行..."
                            ));

                            Optional<Object> resultOpt = state.value(node + "_result");
                            resultOpt.ifPresent(result ->
                                emit(emitter, "content", Map.of(
                                    "node", node,
                                    "result", result.toString()
                                ))
                            );
                        },
                        err -> {
                            log.error("[Graph] 会话 {} 流式错误", sessionId, err);
                            streamingEmitter.error(emitter, err.getMessage());
                            userConnections.merge(userId, -1, Integer::sum);
                        },
                        () -> {
                            log.info("[Graph] 会话 {} 完成", sessionId);
                            streamingEmitter.complete(emitter);
                            userConnections.merge(userId, -1, Integer::sum);
                        }
                    );

            } catch (Exception e) {
                log.error("[Graph] 会话 {} 异常", sessionId, e);
                streamingEmitter.error(emitter, e.getMessage());
                userConnections.merge(userId, -1, Integer::sum);
            }
        };

        CompletableFuture.runAsync(
            AsyncContextHolder.wrap(graphTask, capturedToken),
            asyncExecutor
        );

        return emitter;
    }

    // ── 私有辅助 ────────────────────────────────────

    private void emit(SseEmitter emitter, String event, Map<String, Object> payload) {
        try {
            String json = toJson(payload);
            streamingEmitter.send(emitter, new SseEvent(event, json));
        } catch (Exception e) {
            log.warn("[Orchestrator] SSE 发送失败: {}", e.getMessage());
        }
    }

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
            } else {
                sb.append("\"").append(v.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ── 请求 DTO ────────────────────────────────────

    public record ChatRequest(String conversationId, String userQuery, String userId) {}
}
