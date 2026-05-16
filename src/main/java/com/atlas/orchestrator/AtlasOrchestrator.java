package com.atlas.orchestrator;

import com.atlas.auth.UserPermissionContext;
import com.atlas.auth.async.AsyncContextHolder;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.intent.IntentRouter;
import com.atlas.intent.core.IntentResult;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.atlas.brain.BrainDecision;
import java.util.List;
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
    private final KubeManagerHttpClient kubeManagerClient;

    /**
     * (Phase1) Supervisor Graph compiled from AtlasBrain decision + conditional routing.
     * Injected from AtlasGraphConfig.supervisorGraph().
     * When non-null, /chat/stream routes through this graph instead of IntentRouter.
     */
    private final com.alibaba.cloud.ai.graph.CompiledGraph supervisorGraph;

    /**
     * (可选) Spring AI Alibaba StateGraph 编译后的执行图。
     * P2 阶段新增：实验性接口 /chat/graph 使用。
     * 如果未启用 Graph 模式（null），则回退到 IntentRouter 路由。
     */
    private final com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph;

    /** 每用户最大并发连接数 */
    private static final int MAX_PER_USER = 3;
    private final Map<String, Integer> userConnections = new ConcurrentHashMap<>();

    /** HITL 待确认决策：threadId → BrainDecision（供 HITLController 读取） */
    private final Map<String, BrainDecision> pendingDecisions = new ConcurrentHashMap<>();

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
                             KubeManagerHttpClient kubeManagerClient,
                             @Qualifier("atlasTaskExecutor") Executor asyncExecutor,
                             @Autowired(required = false)
                             @Qualifier("supervisorGraph")
                             com.alibaba.cloud.ai.graph.CompiledGraph supervisorGraph,
                             @Autowired(required = false)
                             @Qualifier("atlasGraph")
                             com.alibaba.cloud.ai.graph.CompiledGraph compiledGraph) {
        this.intentRouter = intentRouter;
        this.streamingEmitter = streamingEmitter;
        this.toolRegistry = toolRegistry;
        this.userPermissionContext = userPermissionContext;
        this.kubeManagerClient = kubeManagerClient;
        this.asyncExecutor = asyncExecutor;
        this.supervisorGraph = supervisorGraph;
        this.compiledGraph = compiledGraph;
        log.info("[Orchestrator] 初始化完成 | SupervisorGraph: {} | AtlasGraph: {}",
            supervisorGraph != null ? "已启用 ✅" : "未启用 ⚠️",
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
                // Phase 1: Supervisor Graph 优先路由（AtlasBrain 决策 + 条件边）
                if (supervisorGraph != null) {
                    runSupervisorGraph(request, emitter, userId, sessionId, capturedToken);
                    return;
                }

                // Fallback: 传统 IntentRouter（保留完整 P0 逻辑）

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
                Optional<BaseTool> toolOpt = toolRegistry.findByIntentId(result.intentId());

                if (toolOpt.isPresent()) {
                    BaseTool tool = toolOpt.get();
                    // 权限预检（兜底）
                    if (toolRegistry.canExecuteIntent(result.intentId())) {
                        emit(emitter, "tool_call", Map.of("tool", result.intentId()));

                        try {
                            // P1.4 修复：按用户名解析组织ID，Tool 内部据此路由到正确的后端 API
                            String orgId = kubeManagerClient.resolveOrgId(userId, capturedToken);
                            if ("sysadmin".equals(orgId)) {
                                // 超管穿透：使用系统专用组织ID（kube-manager 超管可跨组织查询）
                                orgId = "100001";
                            }
                            Map<String, Object> toolParams = new java.util.HashMap<>();
                            toolParams.put("userId", userId);
                            toolParams.put("organizationId", orgId);

                            Map<String, Object> toolResult = tool.execute(toolParams);
                            boolean success = Boolean.TRUE.equals(toolResult.get("success"));
                            String message = toolResult.get("message") != null
                                ? toolResult.get("message").toString() : "";
                            Object data = toolResult.get("data");

                            emit(emitter, "tool_result", Map.of(
                                "success", success,
                                "message", message,
                                "tool", result.intentId(),
                                "data", data != null ? data : Map.of()
                            ));
                        } catch (Exception e) {
                            log.error("[Orchestrator] Tool '{}' 执行异常", result.intentId(), e);
                            emit(emitter, "tool_result", Map.of(
                                "success", false,
                                "message", "Tool 执行异常: " + e.getMessage(),
                                "tool", result.intentId()
                            ));
                        }
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
            "graphEnabled", compiledGraph != null,
            "supervisorGraphEnabled", supervisorGraph != null
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
                    "input", Optional.ofNullable(request.userQuery()).orElse(""),
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

                        // 节点输出处理 — 增加 AtlasBrain 决策感知（HITL/Clarify）
                        // 检查当前是否为 supervisor 节点并读取 brain_decision
                        if ("supervisor".equals(node)) {
                            state.value("brain_decision")
                                .filter(BrainDecision.class::isInstance)
                                .map(BrainDecision.class::cast)
                                .ifPresent(decision -> {
                                    if (decision.actionType() == BrainDecision.ActionType.ASK_CLARIFY) {
                                        // 保存待澄清决策，供 /hitl/clarify 接口读取
                                        pendingDecisions.put(sessionId, decision);
                                        emit(emitter, "clarify", Map.of(
                                            "reasoning", decision.reasoning(),
                                            "confidence", decision.confidence(),
                                            "requiredContext", decision.requiredContext() != null
                                                ? decision.requiredContext() : List.of()
                                        ));
                                        log.info("[Graph] 会话 {} 触发 clarify: {}",
                                            sessionId, decision.reasoning());
                                    } else if (decision.actionType() == BrainDecision.ActionType.HITL_CONFIRM) {
                                        // 保存待确认决策，供 /hitl/confirm 接口读取
                                        pendingDecisions.put(sessionId, decision);
                                        emit(emitter, "hitl_request", Map.of(
                                            "target", decision.target(),
                                            "reasoning", decision.reasoning(),
                                            "confidence", decision.confidence(),
                                            "parameters", decision.parameters() != null
                                                ? decision.parameters() : Map.of()
                                        ));
                                        log.info("[Graph] 会话 {} 触发 hitl_request: target={}",
                                            sessionId, decision.target());
                                    }
                                });
                        }

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

    /**
     * Phase 1: Supervisor Graph 流式执行。
     * 使用 supervisorGraph Bean（START → supervisor → conditional edges → END），
     * 保持与 /chat/graph 相同的 SSE 事件约定。
     */
    private void runSupervisorGraph(ChatRequest request, SseEmitter emitter,
                                     String userId, String sessionId, String token) {
        try {
            Map<String, Object> inputs = Map.of(
                "input", Optional.ofNullable(request.userQuery()).orElse(""),
                "conversation_id", Optional.ofNullable(request.conversationId()).orElse(""),
                "user_id", userId,
                "token", Optional.ofNullable(token).orElse("")
            );

            var config = com.alibaba.cloud.ai.graph.RunnableConfig.builder()
                .threadId(sessionId)
                .build();

            log.info("[Supervisor] 启动会话 {}, 输入: {}", sessionId, request.userQuery());

            supervisorGraph.stream(inputs, config)
                .subscribe(
                    nodeOutput -> {
                        String node = nodeOutput.node();
                        var state = nodeOutput.state();

                        log.debug("[Supervisor] 节点 {} 输出, state keys: {}",
                            node, state.data().keySet());

                        if ("supervisor".equals(node)) {
                            state.value("brain_decision")
                                .filter(BrainDecision.class::isInstance)
                                .map(BrainDecision.class::cast)
                                .ifPresent(decision -> {
                                    if (decision.actionType() == BrainDecision.ActionType.ASK_CLARIFY) {
                                        pendingDecisions.put(sessionId, decision);
                                        emit(emitter, "clarify", Map.of(
                                            "reasoning", decision.reasoning(),
                                            "confidence", decision.confidence(),
                                            "requiredContext", decision.requiredContext() != null
                                                ? decision.requiredContext() : List.of()
                                        ));
                                    } else if (decision.actionType() == BrainDecision.ActionType.HITL_CONFIRM) {
                                        pendingDecisions.put(sessionId, decision);
                                        emit(emitter, "hitl_request", Map.of(
                                            "target", decision.target(),
                                            "reasoning", decision.reasoning(),
                                            "confidence", decision.confidence(),
                                            "parameters", decision.parameters() != null
                                                ? decision.parameters() : Map.of()
                                        ));
                                    }
                                });
                        }

                        emit(emitter, "thinking", Map.of(
                            "step", node,
                            "content", "节点 " + node + " 正在执行..."
                        ));

                        // 输出 answer（direct_answer/tool_call 等节点写入的 key）
                        state.value("answer").ifPresent(ans ->
                            emit(emitter, "content", Map.of(
                                "node", node,
                                "result", ans.toString()
                            ))
                        );
                    },
                    err -> {
                        log.error("[Supervisor] 会话 {} 流式错误", sessionId, err);
                        streamingEmitter.error(emitter, err.getMessage());
                        userConnections.merge(userId, -1, Integer::sum);
                    },
                    () -> {
                        log.info("[Supervisor] 会话 {} 完成", sessionId);
                        streamingEmitter.complete(emitter);
                        userConnections.merge(userId, -1, Integer::sum);
                    }
                );
        } catch (Exception e) {
            log.error("[Supervisor] 会话 {} 异常", sessionId, e);
            streamingEmitter.error(emitter, e.getMessage());
            userConnections.merge(userId, -1, Integer::sum);
        }
    }

    /**
     * 获取待确认的 HITL 决策（供 HITLController 使用）。
     */
    public BrainDecision getPendingDecision(String sessionId) {
        return pendingDecisions.get(sessionId);
    }

    /**
     * 移除已处理的 HITL 决策。
     */
    public BrainDecision removePendingDecision(String sessionId) {
        return pendingDecisions.remove(sessionId);
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
