package com.atlas.orchestrator;

import com.atlas.agent.AtlasAgentBase;
import com.atlas.agent.QueryAgent;
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
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Atlas 统一编排器 — v3.1 P1.4 权限感知 + Token 异步透传版。
 *
 * <p><b>端到端流程（实验样本）：</b></p>
 * <ol>
 *   <li>接收 SSE 流式请求</li>
 *   <li>{@code thinking} — 正在分析意图</li>
 *   <li>{@link IntentRouter#route} 分类意图</li>
 *   <li>{@code content} — 返回意图分类结果</li>
 *   <li>{@code tool_call} — 调用对应 Agent → Tool（权限预检）</li>
 *   <li>{@code tool_result} — 返回 Tool 执行结果</li>
 *   <li>{@code done} — 完成</li>
 * </ol>
 *
 * <p><b>P1.4 重大变更：</b></p>
 * <ul>
 *   <li>Token 异步透传：{@code CompletableFuture.runAsync()} 调用前显式捕获主线程 Token，
 *       通过 {@link AsyncContextHolder#wrap(Runnable, String)} 包装任务，确保子线程中
 *       {@link UserPermissionContext#current()} 正常返回</li>
 *   <li>权限感知：Tool 执行前通过 {@code toolRegistry.isVisible()} 双重校验</li>
 *   <li>线程池隔离：使用 {@code @Qualifier("atlasTaskExecutor")} 的专用线程池，
 *       避免与系统 ForkJoinPool 竞争</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P1.4
 */
@RestController
@RequestMapping("/api/v1")
public class AtlasOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AtlasOrchestrator.class);

    private final IntentRouter intentRouter;
    private final StreamingEmitter streamingEmitter;
    private final ToolRegistry toolRegistry;
    private final Map<String, AtlasAgentBase> agentMap;
    private final UserPermissionContext userPermissionContext;
    private final Executor asyncExecutor;

    /**
     * (可选) Spring AI Alibaba StateGraph 编译后的执行图。
     * P2 阶段新增：实验性接口 /chat/graph 使用。
     * 如果未启用 Graph 模式（null），则回退到旧的 IntentRouter 路由。
     */
    private final com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph;

    /** 每用户最大并发连接数 */
    private static final int MAX_PER_USER = 3;
    private final Map<String, Integer> userConnections = new ConcurrentHashMap<>();

    /**
     * 旧版构造方法 — 保留向后兼容。
     */
    public AtlasOrchestrator(IntentRouter intentRouter,
                             StreamingEmitter streamingEmitter,
                             ToolRegistry toolRegistry,
                             List<AtlasAgentBase> agents,
                             UserPermissionContext userPermissionContext,
                             @Qualifier("atlasTaskExecutor") Executor asyncExecutor) {
        this(intentRouter, streamingEmitter, toolRegistry, agents,
             userPermissionContext, asyncExecutor, null);
    }

    /**
     * P2 版构造方法 — 支持注入 CompiledGraph（可选）。
     */
    @Autowired
    public AtlasOrchestrator(IntentRouter intentRouter,
                             StreamingEmitter streamingEmitter,
                             ToolRegistry toolRegistry,
                             List<AtlasAgentBase> agents,
                             UserPermissionContext userPermissionContext,
                             @Qualifier("atlasTaskExecutor") Executor asyncExecutor,
                             com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph) {
        this.intentRouter = intentRouter;
        this.streamingEmitter = streamingEmitter;
        this.toolRegistry = toolRegistry;
        this.userPermissionContext = userPermissionContext;
        this.asyncExecutor = asyncExecutor;
        this.agentMap = agents.stream()
            .collect(Collectors.toMap(AtlasAgentBase::getAgentType, a -> a));
        this.compiledGraph = compiledGraph;
        log.info("[Orchestrator] 已加载 {} 个Agent: {} | Graph模式: {}",
            agentMap.size(), agentMap.keySet(),
            compiledGraph != null ? "已启用 ✅" : "未启用 ⚠️");
    }

    /**
     * SSE 流式对话接口 — P1.4 完整版（权限感知 + Token 透传）。
     *
     * <p><b>Token 透传实现：</b></p>
     * <ol>
     *   <li>主线程（Tomcat HTTP 工作线程）中通过 {@code userPermissionContext.getCurrentToken()} 捕获 Token</li>
     *   <li>使用 {@link AsyncContextHolder#wrap(Runnable, String)} 包装异步任务</li>
     *   <li>子线程中 {@code UserPermissionContext.CURRENT_TOKEN} 已绑定 → 权限校验和 HTTP 调用正常</li>
     *   <li>任务结束后 finally 中自动 unbind，防止线程池复用泄漏</li>
     * </ol>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        String userId = request.userId() != null ? request.userId() : "anonymous";
        String sessionId = userId + "-" + System.currentTimeMillis();

        // ═══ ① 主线程捕获 Token（必须在 runAsync 之前！）═══
        String capturedToken = userPermissionContext.getCurrentToken();
        if (capturedToken == null || capturedToken.isBlank()) {
            log.warn("[Orchestrator] 请求未携带 Token（匿名用户），后续权限校验将全部失败");
        } else {
            log.debug("[Orchestrator] 主线程已捕获 Token: {}",
                capturedToken.substring(0, Math.min(8, capturedToken.length())) + "...");
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

        // ═══ ② 异步任务包装 — Token 显式透传 ═══
        Runnable asyncTask = () -> {
            try {
                // ── 1. thinking ─────────────────────────
                emit(emitter, "thinking", Map.of("step", "intent", "content", "正在分析您的意图..."));

                // ── 2. 意图路由 ─────────────────────────
                IntentResult result = intentRouter.route(request.userQuery());
                log.info("[Orchestrator] {} → {} ({} norm={:.3f})",
                    sessionId, result.intentId(), result.matchedLevel(), result.confidence());

                // ── 3. content — 返回分类结果 ──────────
                emit(emitter, "content", Map.of(
                    "intentId", result.intentId(),
                    "description", result.description(),
                    "confidence", result.confidence(),
                    "matchedLevel", result.matchedLevel(),
                    "agent", result.agent()
                ));

                // ── 4. Tool 执行 — 动态路由到对应Agent ──
                AtlasAgentBase agent = agentMap.get(result.agent());
                if (agent != null) {
                    emit(emitter, "tool_call", Map.of("tool", result.intentId(), "params", Map.of()));

                    // P1.4 权限感知：Agent 层已做二次校验，但此处再做一次兜底
                    if (!toolRegistry.canExecuteIntent(result.intentId())) {
                        log.warn("[Orchestrator] 用户 {} 越权尝试执行意图 '{}'", userId, result.intentId());
                        emit(emitter, "tool_result", Map.of(
                            "success", false,
                            "error", "权限不足",
                            "message", "当前用户无权执行此操作",
                            "deniedIntent", result.intentId()
                        ));
                    } else {
                        Map<String, Object> toolResult = agent.executeIntent(
                            result.intentId(), Map.of()
                        );
                        emit(emitter, "tool_result", toolResult);
                    }
                } else {
                    emit(emitter, "content", Map.of(
                        "type", "notice",
                        "content", "意图 '" + result.intentId() + "' 已识别（Agent: " + result.agent() + "），但该Agent暂未加载。"
                    ));
                }

                // ── 5. done ──────────────────────────
                streamingEmitter.complete(emitter);

            } catch (Exception e) {
                log.error("[Orchestrator] 会话异常: {}", sessionId, e);
                streamingEmitter.error(emitter, e.getMessage());
            } finally {
                userConnections.merge(userId, -1, Integer::sum);
            }
        };

        // 使用 AsyncContextHolder 包装 + 专用线程池执行
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
            "version", "3.1.0-P1.4",
            "activeConnections", streamingEmitter.activeCount(),
            "toolRegistry", toolRegistry.health()
        );
    }

    // ═══════════════════════════════════════════════════════════
    // P2 实验: StateGraph 流式对话接口
    // ═══════════════════════════════════════════════════════════

    /**
     * (实验) StateGraph 智能编排对话接口 — P2 引入。
     *
     * <p>直接调用编译好的 {@code CompiledGraph}，利用 ReactAgent 内部完成
     * 意图识别 → 工具调用 → 结果合并 的全链路循环。无需手动维护
     * IntentRouter + agentMap 路由。</p>
     *
     * <p><b>流式事件序列：</b></p>
     * <pre>
     *   thinking → content(推理过程) → tool_call → tool_result → done
     * </pre>
     */
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
                // Graph 输入状态
                Map<String, Object> inputs = Map.of(
                    "input", request.userQuery(),
                    "conversation_id", Optional.ofNullable(request.conversationId()).orElse(""),
                    "user_id", userId,
                    "token", Optional.ofNullable(capturedToken).orElse("")
                );

                // 使用 graphResponseStream 获取完整响应（含 metadata）
                var config = com.alibaba.cloud.ai.graph.RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

                log.info("[Graph] 启动会话 {}, 输入: {}", sessionId, request.userQuery());

                // 订阅 StateGraph 流
                compiledGraph.stream(inputs, config)
                    .subscribe(
                        nodeOutput -> {
                            String node = nodeOutput.node();
                            var state = nodeOutput.state();

                            log.debug("[Graph] 节点 {} 输出，state keys: {}",
                                node, state.data().keySet());

                            // 发送 thinking 事件（节点开始执行）
                            emit(emitter, "thinking", Map.of(
                                "step", node,
                                "content", "节点 " + node + " 正在执行..."
                            ));

                            // 提取该节点的关键输出并发送 content
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
