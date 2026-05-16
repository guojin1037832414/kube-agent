package com.atlas.graph.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.atlas.graph.bridge.AtlasToolCallbackFactory;
import com.atlas.graph.node.DirectAnswerNode;
import com.atlas.graph.node.SseEmitNode;
import com.atlas.graph.node.ToolResultMergeNode;
import com.atlas.orchestrator.StreamingEmitter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.atlas.brain.AtlasBrain;
import com.atlas.brain.BrainDecision;
import com.atlas.brain.ExecutionContext;
import com.atlas.tool.core.ToolRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Atlas StateGraph 编排配置 — v3.1 P2。
 *
 * <p>将原有手动 if-else 路由的 {@link com.atlas.orchestrator.AtlasOrchestrator}
 * 迁移为 Spring AI Alibaba {@link StateGraph} + {@link ReactAgent} 的声明式编排：</p>
 *
 * <pre>
 * START → supervisor_agent → [conditional] → query/deploy/rbac/..._agent
 *                                                          → direct_answer
 * [any_agent] → merge_result → emit_sse → END
 * </pre>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
@Configuration
public class AtlasGraphConfig {

    // ═══════════════════════════════════════════════════════════
    // 1. ReactAgent 定义（每个专业 Agent）
    // ═══════════════════════════════════════════════════════════

    @Bean
    public ReactAgent queryAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("query")
                .description("Atlas 查询 Agent — 节点/GPU/镜像/监控/概览")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 查询专家。可用工具：节点查询、GPU查询、镜像查询、Ingress查询、
                    集群概览等。请根据用户问题选择合适的工具并提取参数。
                    """)
                .tools(toolFactory.buildForAgent("query"))
                .outputKey("query_result")
                
                .build();
    }

    @Bean
    public ReactAgent deployAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("deploy")
                .description("Atlas 部署 Agent — 实例/NIM/分布式创建、扩缩、删除")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 部署专家。可用工具：创建实例、创建NIM、扩缩容、删除部署、重启等。
                    涉及资源变更的操作请谨慎确认必填参数。
                    """)
                .tools(toolFactory.buildForAgent("deploy"))
                .outputKey("deploy_result")
                
                .build();
    }

    @Bean
    public ReactAgent diagAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("diag")
                .description("Atlas 诊断 Agent — Pod 故障/日志分析")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 诊断专家。可用工具：Pod诊断、日志查询等。
                    请先收集 Pod 名称、命名空间等关键信息再调用工具。
                    """)
                .tools(toolFactory.buildForAgent("diag"))
                .outputKey("diag_result")
                
                .build();
    }

    @Bean
    public ReactAgent rbacAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("rbac")
                .description("Atlas 权限 Agent — 用户/角色/权限管理")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 权限管理专家。可用工具：用户查询、角色查询、用户创建/删除等。
                    注意：涉及用户变更的操作需要管理员权限，权限不足时请拒绝。
                    """)
                .tools(toolFactory.buildForAgent("rbac"))
                .outputKey("rbac_result")
                
                .build();
    }

    @Bean
    public ReactAgent storageAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("storage")
                .description("Atlas 存储 Agent — PVC/存储卷管理")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 存储管理专家。可用工具：存储查询、存储创建、存储删除等。
                    """)
                .tools(toolFactory.buildForAgent("storage"))
                .outputKey("storage_result")
                
                .build();
    }

    @Bean
    public ReactAgent networkAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("network")
                .description("Atlas 网络 Agent — 带宽/Ingress/域名配置")
                .model(chatModel)
                .instruction("""
                    你是 Atlas 网络管理专家。可用工具：网络查询、Ingress查询等。
                    """)
                .tools(toolFactory.buildForAgent("network"))
                .outputKey("network_result")
                
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 2. StateGraph 组装 (主图)
    // ═══════════════════════════════════════════════════════════

    @Bean
    @Primary
    public CompiledGraph atlasGraph(
            ChatModel chatModel,
            AtlasBrain atlasBrain,
            ToolRegistry toolRegistry,
            ReactAgent queryAgent,
            ReactAgent deployAgent,
            ReactAgent diagAgent,
            ReactAgent rbacAgent,
            ReactAgent storageAgent,
            ReactAgent networkAgent,
            StreamingEmitter streamingEmitter
    ) throws GraphStateException {

        KeyStrategyFactory keyFactory = buildKeyStrategyFactory();

        // 自定义节点：直接回答（无需 Tool 调用）
        var directAnswerNode = node_async(new DirectAnswerNode(chatModel));

        // 自定义节点：合并结果并准备 SSE 输出
        var mergeResultNode = node_async(new ToolResultMergeNode());

        // 自定义节点：SSE 输出
        var emitSseNode = node_async(new SseEmitNode(streamingEmitter));

        StateGraph graph = new StateGraph("atlas_orchestrator", keyFactory)
                // 1. AtlasBrain 认知决策节点 — 替代旧 supervisor Agent
                .addNode("supervisor", node_async(state -> {
                    // 读取用户输入与上下文
                    String input = state.value("input").map(Object::toString).orElse("");
                    String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
                    String token = state.value("token").map(Object::toString).orElse("");

                    // 构建 ExecutionContext
                    ExecutionContext ctx = new ExecutionContext(
                        UUID.randomUUID().toString(),
                        userId,
                        input,
                        List.of(),
                        Map.of("token", token),
                        UUID.randomUUID().toString(),
                        Instant.now()
                    );

                    // AtlasBrain 决策（resume 场景：已有非中断决策则复用，避免重复调用 LLM）
                    BrainDecision decision = state.value("brain_decision")
                            .filter(BrainDecision.class::isInstance)
                            .map(BrainDecision.class::cast)
                            .filter(d -> d.actionType() != BrainDecision.ActionType.HITL_CONFIRM
                                     && d.actionType() != BrainDecision.ActionType.ASK_CLARIFY)
                            .orElseGet(() -> atlasBrain.decide(ctx));

                    // 将决策存入 State（供 AtlasOrchestrator SSE 读取）
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("brain_decision", decision);
                    updates.put("supervisor_result", decision); // 兼容旧 key

                    // 返回路由键 — 复用现有条件边目标
                    return updates;
                }))

                // 2. 各专业 Agent（作为子图节点）
                .addNode("query", queryAgent.getAndCompileGraph())
                .addNode("deploy", deployAgent.getAndCompileGraph())
                .addNode("diag", diagAgent.getAndCompileGraph())
                .addNode("rbac", rbacAgent.getAndCompileGraph())
                .addNode("storage", storageAgent.getAndCompileGraph())
                .addNode("network", networkAgent.getAndCompileGraph())

                // 3. 辅助节点
                .addNode("direct_answer", directAnswerNode)
                .addNode("merge_result", mergeResultNode)
                .addNode("emit_sse", emitSseNode);

        // 边：START → supervisor
        graph.addEdge(START, "supervisor");

        // 条件边：supervisor 节点输出决定路由到哪个 Agent
        // AtlasBrain 的 BrainDecision.actionType() 映射为路由键
        graph.addConditionalEdges("supervisor",
                edge_async(state -> {
                    BrainDecision decision = state.value("brain_decision")
                            .filter(BrainDecision.class::isInstance)
                            .map(BrainDecision.class::cast)
                            .orElse(null);

                    if (decision == null) {
                        // 无决策 — 回退到直接回答
                        return "direct_answer";
                    }

                    switch (decision.actionType()) {
                        case DELEGATE_AGENT:
                            // 专业 Agent 路由：target 应为 agent 名称
                            String agent = decision.target() != null
                                    ? decision.target().toLowerCase()
                                    : "direct_answer";
                            return List.of("query", "deploy", "diag", "rbac", "storage", "network")
                                    .contains(agent) ? agent : "direct_answer";

                        case CALL_TOOL:
                            // 单工具调用 — 通过 ToolRegistry 映射到对应 Agent
                            if (decision.target() != null) {
                                var metaOpt = toolRegistry.listByAgent("query").stream()
                                        .filter(m -> m.name().equals(decision.target()))
                                        .findFirst();
                                if (metaOpt.isPresent()) {
                                    String agentCode = metaOpt.get().agent();
                                    if (List.of("query", "deploy", "diag", "rbac", "storage", "network")
                                            .contains(agentCode)) {
                                        return agentCode;
                                    }
                                }
                                // 简单 fallback：target 名匹配
                                String target = decision.target().toLowerCase();
                                if (target.contains("deploy") || target.contains("创建")) return "deploy";
                                if (target.contains("diag") || target.contains("诊断")) return "diag";
                                if (target.contains("rbac") || target.contains("用户")) return "rbac";
                                if (target.contains("storage") || target.contains("存储")) return "storage";
                                if (target.contains("network") || target.contains("网络")) return "network";
                            }
                            return "query"; // 默认查询

                        case DIRECT_ANSWER:
                        case ASK_CLARIFY:
                        case HITL_CONFIRM:
                        default:
                            // 直接回答、澄清、HITL 均路由到 direct_answer
                            return "direct_answer";
                    }
                }),
                Map.of(
                        "query", "query",
                        "deploy", "deploy",
                        "diag", "diag",
                        "rbac", "rbac",
                        "storage", "storage",
                        "network", "network",
                        "direct_answer", "direct_answer"
                )
        );

        // 所有专业 Agent 的执行结果都汇聚到 merge_result
        graph.addEdge("query", "merge_result");
        graph.addEdge("deploy", "merge_result");
        graph.addEdge("diag", "merge_result");
        graph.addEdge("rbac", "merge_result");
        graph.addEdge("storage", "merge_result");
        graph.addEdge("network", "merge_result");
        graph.addEdge("direct_answer", "merge_result");

        // merge_result → emit_sse
        graph.addEdge("merge_result", "emit_sse");

        // emit_sse → END
        graph.addEdge("emit_sse", END);

        // 持久化 + HITL 配置（可选）
        var memory = new MemorySaver();
        var compileConfig = com.alibaba.cloud.ai.graph.CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(memory).build())
                // .interruptBefore("merge_result") // 如需在合并前人工确认，取消注释
                .build();

        return graph.compile(compileConfig);
    }

    private KeyStrategyFactory buildKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("input", new ReplaceStrategy());          // 用户原始输入
            strategies.put("messages", new AppendStrategy(false));   // 消息历史
            strategies.put("supervisor_result", new ReplaceStrategy());
            strategies.put("brain_decision", new ReplaceStrategy());   // AtlasBrain 决策结果
            strategies.put("query_result", new ReplaceStrategy());
            strategies.put("deploy_result", new ReplaceStrategy());
            strategies.put("diag_result", new ReplaceStrategy());
            strategies.put("rbac_result", new ReplaceStrategy());
            strategies.put("storage_result", new ReplaceStrategy());
            strategies.put("network_result", new ReplaceStrategy());
            strategies.put("final_answer", new ReplaceStrategy());   // 最终 SSE 输出
            strategies.put("conversation_id", new ReplaceStrategy());
            strategies.put("user_id", new ReplaceStrategy());
            strategies.put("token", new ReplaceStrategy());          // 透传的 Token
            strategies.put("answer", new ReplaceStrategy());         // 直接回答 / tool_call 返回
            strategies.put("tool_result", new ReplaceStrategy());      // Tool 结构化结果
            return strategies;
        };
    }
    /**
     * Supervisor 图 — AtlasBrain 决策节点 + 条件路由。
     * START → supervisor → [conditional] → {direct_answer, ask_clarify, tool_call, delegate} → END
     */
    @Bean
    public CompiledGraph supervisorGraph(
            AtlasBrain atlasBrain, ToolRegistry toolRegistry,
            com.atlas.http.KubeManagerHttpClient kubeManagerClient,
            ReactAgent queryAgent,
            ReactAgent deployAgent,
            ReactAgent diagAgent,
            ReactAgent rbacAgent,
            ReactAgent storageAgent,
            ReactAgent networkAgent
    ) throws GraphStateException {
        StateGraph graph = new StateGraph("supervisor", buildKeyStrategyFactory());

        // ═══════════════════════════════════════════════════════════
        // Agent 映射表 — 供 delegate 节点根据 BrainDecision.target 路由
        // ═══════════════════════════════════════════════════════════
        final java.util.Map<String, ReactAgent> agentMap = java.util.Map.of(
            "query", queryAgent,
            "deploy", deployAgent,
            "diag", diagAgent,
            "rbac", rbacAgent,
            "storage", storageAgent,
            "network", networkAgent
        );

        // 1. Supervisor 节点：调用 AtlasBrain 做决策
        graph.addNode("supervisor", node_async((OverAllState state) -> {
            String input = state.value("input").map(Object::toString).orElse("");
            String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
            String token = state.value("token").map(Object::toString).orElse("");

            ExecutionContext ctx = new ExecutionContext(
                UUID.randomUUID().toString(), userId, input,
                java.util.Collections.emptyList(),
                java.util.Map.of("token", token),
                UUID.randomUUID().toString(), Instant.now()
            );

            BrainDecision decision = atlasBrain.decide(ctx);

            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("brain_decision", decision);
            updates.put("reasoning", decision.reasoning());
            return updates;
        }));

        // 2. 条件边：根据 BrainDecision.actionType 路由
        graph.addConditionalEdges("supervisor",
            edge_async((OverAllState state) -> {
                BrainDecision decision = state.value("brain_decision")
                    .filter(BrainDecision.class::isInstance)
                    .map(BrainDecision.class::cast)
                    .orElse(null);
                if (decision == null) return "direct_answer";
                return switch (decision.actionType()) {
                    case DIRECT_ANSWER -> "direct_answer";
                    case ASK_CLARIFY -> "ask_clarify";
                    case CALL_TOOL -> "tool_call";
                    case DELEGATE_AGENT -> "delegate";
                    case HITL_CONFIRM -> "hitl_confirm";
                };
            }),
            java.util.Map.of(
                "direct_answer", "direct_answer",
                "ask_clarify", "ask_clarify",
                "tool_call", "tool_call",
                "delegate", "delegate",
                "hitl_confirm", "hitl_confirm"
            )
        );

        // 3. 各目标节点（最小实现）
        graph.addNode("direct_answer", node_async((OverAllState state) -> {
            BrainDecision d = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast).orElse(null);
            String answer = (d != null) ? d.reasoning() : "No reasoning available";
            return java.util.Map.of("answer", answer);
        }));

        graph.addNode("ask_clarify", node_async((OverAllState state) -> {
            BrainDecision d = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast).orElse(null);
            String answer = (d != null && d.requiredContext() != null)
                ? "请补充以下信息: " + String.join(", ", d.requiredContext())
                : "请补充更多信息";
            return java.util.Map.of("answer", answer);
        }));

        graph.addNode("tool_call", node_async((OverAllState state) -> {
            BrainDecision d = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast)
                .orElse(null);
            if (d == null) {
                return java.util.Map.of("answer", "[无决策] AtlasBrain 未产生有效决策");
            }

            String intentId = d.target();
            String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
            String token = state.value("token").map(Object::toString).orElse("");

            // ═══════════════════════════════════════════════════════════
            // Token 透传修复：Graph 异步线程中 ThreadLocal 丢失，需显式设置
            // ═══════════════════════════════════════════════════════════
            boolean tokenSet = false;
            if (!token.isBlank()) {
                com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.set(token);
                tokenSet = true;
            }

            try {
                // 1. 查找 Tool
                java.util.Optional<com.atlas.tool.core.BaseTool> toolOpt = toolRegistry.findByIntentId(intentId);
                if (toolOpt.isEmpty()) {
                    return java.util.Map.of("answer",
                        "⚠️ 意图 '" + intentId + "' 已识别，暂无对应 Tool 实现。");
                }

                com.atlas.tool.core.BaseTool tool = toolOpt.get();

                // 2. 权限检查
                if (!toolRegistry.canExecuteIntent(intentId)) {
                    return java.util.Map.of("answer",
                        "❌ 权限不足：无权执行 '" + intentId + "'");
                }

                // 3. 构建参数（注册 k8s 网络问题暂缓动态 orgId 查询，先用固定值）
                String orgId = "100001";
                // 注：后续恢复 kubeManagerClient.resolveOrgId(userId, token) 动态查询
                java.util.Map<String, Object> toolParams = new java.util.HashMap<>();
                toolParams.put("userId", userId);
                toolParams.put("organizationId", orgId);
                if (d.parameters() != null) {
                    toolParams.putAll(d.parameters());
                }

                // 4. 执行 Tool
                try {
                    java.util.Map<String, Object> toolResult = tool.execute(toolParams);
                    boolean success = Boolean.TRUE.equals(toolResult.get("success"));
                    String message = toolResult.get("message") != null
                        ? toolResult.get("message").toString() : "";
                    Object data = toolResult.get("data");

                    // 5. 生成简洁回答
                    String summary = data instanceof java.util.List
                        ? String.format("✅ %s（共 %d 条数据）", message, ((java.util.List<?>) data).size())
                        : "✅ " + message;

                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    updates.put("answer", summary);
                    updates.put("tool_result", java.util.Map.of(
                        "success", success,
                        "message", message,
                        "tool", intentId,
                        "data", data != null ? data : java.util.Map.of()
                    ));
                    return updates;
                } catch (Exception e) {
                    return java.util.Map.of("answer",
                        "❌ Tool 执行异常: " + e.getMessage());
                }
            } finally {
                // 清理 ThreadLocal，防止线程池复用导致 Token 泄漏
                if (tokenSet) {
                    com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.remove();
                }
            }
        }));

        graph.addNode("delegate", node_async((OverAllState state) -> {
            BrainDecision d = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast).orElse(null);
            if (d == null || d.target() == null) {
                return Map.of("answer", "[DELEGATE] 无目标 Agent");
            }
            String agentName = d.target().toLowerCase();
            ReactAgent agent = agentMap.get(agentName);
            if (agent == null) {
                return Map.of("answer", "[DELEGATE] 未知 Agent: " + agentName);
            }
            try {
                // ==============================
                // 1. 构建子图输入：复用父图 state 中的 key
                // ==============================
                Map<String, Object> subInputs = new HashMap<>();
                state.value("input").ifPresent(v -> subInputs.put("input", v));
                state.value("user_id").ifPresent(v -> subInputs.put("user_id", v));
                state.value("token").ifPresent(v -> subInputs.put("token", v));
                state.value("messages").ifPresent(v -> subInputs.put("messages", v));

                // ==============================
                // 2. Token 透传：在子图执行前显式设置 ThreadLocal
                // ==============================
                String token = state.value("token").map(Object::toString).orElse("");
                boolean tokenSet = false;
                if (!token.isBlank()) {
                    com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.set(token);
                    tokenSet = true;
                }

                // ==============================
                // 3. 执行子图（在 Graph 异步线程中同步等待结果）
                // ==============================
                CompiledGraph subGraph = agent.getAndCompileGraph();

                String threadId = state.value("_graph_execution_id_")
                    .map(Object::toString).orElse(UUID.randomUUID().toString());
                com.alibaba.cloud.ai.graph.RunnableConfig subConfig =
                    com.alibaba.cloud.ai.graph.RunnableConfig.builder()
                        .threadId(threadId + "-" + agentName)
                        .build();

                String outputKey = agentName + "_result"; // query_result, deploy_result, ...
                try {
                    java.util.Map<String, Object> result = subGraph.stream(subInputs, subConfig)
                        .filter(no -> no.isEND())
                        .map(no -> {
                            // END 节点的 state 包含所有节点产生的数据
                            com.alibaba.cloud.ai.graph.OverAllState endState = no.state();
                            Map<String, Object> endResult = new HashMap<>();
                            // 提取子图 outputKey 对应的结果
                            endState.value(outputKey).ifPresent(v ->
                                endResult.put(outputKey, v));
                            // fallback：提取 answer
                            endState.value("answer").ifPresent(v ->
                                endResult.put("answer", v));
                            // 提取 messages（子图的对话记录）
                            endState.value("messages").ifPresent(v ->
                                endResult.put("messages", v));
                            return endResult;
                        })
                        .blockFirst();

                    if (result == null) {
                        return Map.of("answer", "[Agent " + agentName + " 执行超时]");
                    }

                    // 构造最终 state 更新
                    Map<String, Object> updates = new HashMap<>();
                    Object agentOutput = result.get(outputKey);
                    if (agentOutput == null) {
                        agentOutput = result.get("answer"); // fallback
                    }
                    Object messages = result.get("messages");

                    String summary = "[Agent " + agentName + " 执行完成]";
                    if (agentOutput instanceof String s) {
                        summary = s;
                    } else if (agentOutput != null) {
                        summary = agentOutput.toString();
                    }

                    updates.put("answer", summary);
                    updates.put(outputKey, agentOutput != null ? agentOutput : Map.of());
                    if (messages != null) {
                        updates.put("messages", messages);
                    }
                    updates.put("agent_executed", agentName);
                    return updates;
                } finally {
                    // 清理 ThreadLocal，防止泄漏
                    if (tokenSet) {
                        com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.remove();
                    }
                }
            } catch (Exception e) {
                return Map.of("answer",
                    "[DELEGATE] Agent " + agentName + " 执行异常: " + e.getMessage());
            }
        }));

        graph.addNode("hitl_confirm", node_async((OverAllState state) -> {
            return java.util.Map.of("answer", "[HITL_CONFIRM] 请人工确认此操作");
        }));

        // 4. 连接边
        graph.addEdge(START, "supervisor");
        graph.addEdge("direct_answer", END);
        graph.addEdge("ask_clarify", END);
        graph.addEdge("tool_call", END);
        graph.addEdge("delegate", END);
        graph.addEdge("hitl_confirm", END);

        return graph.compile();
    }
}
