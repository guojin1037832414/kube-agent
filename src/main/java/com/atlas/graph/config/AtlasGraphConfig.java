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
import com.atlas.react.ReActEngine;
import com.atlas.react.ReActResult;
import com.atlas.react.ReActEventSink;
import com.atlas.react.ReActEventSinkRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.atlas.brain.AtlasBrain;
import com.atlas.brain.BrainDecision;
import com.atlas.brain.ExecutionContext;
import com.atlas.hitl.HitlConfirmation;
import com.atlas.hitl.HitlGuard;
import com.atlas.plan.PlanEngine;
import com.atlas.plan.PlanResult;
import com.atlas.plan.PlanStep;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionResult;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.atlas.tool.execution.SafeToolExecutor;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 会话/认证上下文字段白名单。
     *
     * <p>M5.5 多租户安全治理：这些字段只能由系统上下文写入，不能由
     * AtlasBrain/LLM 输出的 parameters 覆盖，避免跨租户 path 污染。</p>
     */
    private static final Set<String> PROTECTED_CONTEXT_PARAMS = Set.of(
        "token",
        "organizationId",
        "orgId",
        "conversationId",
        "conversation_id",
        "userId",
        "user_id"
    );

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
            ReActEngine reactEngine,
            ReactAgent queryAgent,
            ReactAgent deployAgent,
            ReactAgent diagAgent,
            ReactAgent rbacAgent,
            ReactAgent storageAgent,
            ReactAgent networkAgent,
            StreamingEmitter streamingEmitter,
            ReActEventSinkRegistry reactEventSinkRegistry
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

                // 3. ReAct 节点（手写推理引擎，M3.2 第二批接入）
                .addNode("react_node", buildReActNode(reactEngine, reactEventSinkRegistry))

                // 4. 辅助节点
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

                        case DELEGATE_REACT:
                            // M3.2: 诊断类查询路由到手写 ReAct 引擎
                            return "react_node";

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
                        "react_node", "react_node",
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
        graph.addEdge("react_node", "merge_result");
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

    private boolean isProtectedContextParam(String key) {
        return key != null && PROTECTED_CONTEXT_PARAMS.contains(key);
    }

    /**
     * 判断 Tool 是否必须经过 HITL 人工确认。
     *
     * <p>M5.13 采用 fail-closed 策略：只有明确声明为 READ 且未要求确认的 Tool 可以直接执行。
     * CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER/UNKNOWN 以及元数据缺失，都视为需要确认。
     * 这样即使某个历史 Tool 尚未补齐风险注解，也不会被默认放行。</p>
     */
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
            strategies.put("react_node_result", new ReplaceStrategy()); // ReAct 节点最终答案
            strategies.put("react_result", new ReplaceStrategy());      // ReAct 完整结果对象
            strategies.put("react_steps", new ReplaceStrategy());       // ReAct 步骤列表
            strategies.put("plan_node_result", new ReplaceStrategy());  // Plan 节点最终答案
            strategies.put("plan_result", new ReplaceStrategy());       // PlanEngine 结构化结果对象
            strategies.put("plan_steps", new ReplaceStrategy());        // PlanEngine 结构化步骤列表
            strategies.put("execute_node_result", new ReplaceStrategy()); // M4-PX execute_node 最终答案
            strategies.put("execute_result", new ReplaceStrategy());      // M4-PX execute_node 结构化执行结果
            strategies.put("execute_steps", new ReplaceStrategy());       // M4-PX execute_node 步骤执行状态
            strategies.put("react_event_session_id", new ReplaceStrategy()); // ReAct 过程事件会话ID（纯字符串，可序列化）
            strategies.put("final_answer", new ReplaceStrategy());   // 最终 SSE 输出
            strategies.put("conversation_id", new ReplaceStrategy());
            strategies.put("user_id", new ReplaceStrategy());
            strategies.put("token", new ReplaceStrategy());          // 透传的 Token
            strategies.put("orgId", new ReplaceStrategy());          // 可信组织上下文
            strategies.put("organizationId", new ReplaceStrategy()); // 兼容旧 key，仅系统写入
            strategies.put("answer", new ReplaceStrategy());         // 直接回答 / tool_call 返回
            strategies.put("tool_result", new ReplaceStrategy());      // Tool 结构化结果
            strategies.put("hitl_confirmation", new ReplaceStrategy()); // M5.13 服务端确认凭证：只由 HITLController 写入
            return strategies;
        };
    }

    /**
     * 构建 Plan 节点异步动作。
     *
     * <p>M4.2 最小 POC 中，plan_node 只调用 {@link PlanEngine} 生成结构化计划和单次
     * Reflection 自检结果，绝不调用 Tool、绝不写入 hitl_confirmation、绝不创建
     * HitlConfirmation。所有真实执行仍必须走 tool_call / ReAct / 后续 execute_node，
     * 并继续受 HitlGuard fail-closed 保护。</p>
     *
     * @param planEngine 计划生成引擎
     * @return 异步节点动作
     */
    private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildPlanNode(PlanEngine planEngine) {
        return node_async((OverAllState state) -> {
            String input = state.value("input").map(Object::toString).orElse("");
            String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
            String orgId = state.value("orgId").map(Object::toString).orElse("");
            String conversationId = state.value("conversation_id").map(Object::toString).orElse("");
            BrainDecision decision = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast)
                .orElse(null);

            Map<String, Object> context = new HashMap<>();
            context.put("userId", userId);
            context.put("organizationId", orgId);
            context.put("conversationId", conversationId);

            PlanResult result = planEngine.plan(input, decision, context);
            Map<String, Object> updates = new HashMap<>();
            updates.put("answer", result.finalAnswer());
            updates.put("plan_node_result", result.finalAnswer());
            updates.put("plan_result", result);
            updates.put("plan_steps", result.steps() != null ? result.steps() : List.of());
            return updates;
        });
    }

    /**
     * 构建 Execute 节点异步动作。
     *
     * <p>M4-PX.4 将 execute_node 从“完全不执行”的占位节点，收口升级为
     * “单步 READ 候选执行”节点：它仍然不信任 PlanResult / PlanStep 自带的风险字段，
     * 只把第一条候选 {@link PlanStep#suggestedTool()} 作为待执行 intentId，再统一委托
     * {@link SafeToolExecutor} 重新做 ToolRegistry 解析、权限校验、HITL fail-closed、
     * 可信租户上下文覆盖和 ThreadLocal 恢复。</p>
     *
     * <p>当前小样本故意只开放“恰好一个步骤、风险展示为 READ、无需确认、suggestedTool 非空”
     * 的计划；多步计划、非 READ 步骤、声明需要确认的步骤、空工具名都直接 fail-closed。
     * 这样可以先验证统一执行层接线，不会让 LLM/Plan 输出绕过 HITL 或直接触碰真实 Tool。</p>
     *
     * @param safeToolExecutor 统一安全工具执行器；execute_node 不允许直接调用 BaseTool#execute
     * @return 异步节点动作
     */
    private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildExecuteNode(
            SafeToolExecutor safeToolExecutor) {
        return node_async((OverAllState state) -> {
            PlanResult planResult = state.value("plan_result")
                .filter(PlanResult.class::isInstance)
                .map(PlanResult.class::cast)
                .orElse(null);
            List<?> planSteps = state.value("plan_steps")
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .orElse(List.of());

            Map<String, Object> executeResult = new HashMap<>();
            executeResult.put("executed", false);
            executeResult.put("planExecutable", planResult != null && planResult.executable());
            executeResult.put("stepCount", planSteps.size());

            String answer;
            if (planResult == null) {
                answer = "⛔ execute_node 已停止：未找到 plan_result，无法确认计划来源和风险边界。";
                executeResult.put("code", "PLAN_RESULT_MISSING");
                executeResult.put("reason", "缺失结构化计划结果，按 fail-closed 策略停止。");
            } else if (!planResult.executable()) {
                answer = "🧭 计划已生成，但当前阶段不会自动执行。原因：PlanResult.executable=false，系统按 fail-closed 策略停止在 execute_node。";
                executeResult.put("code", "PLAN_NOT_EXECUTABLE");
                executeResult.put("reason", "计划未声明可执行，execute_node 不会推测执行意图。");
            } else if (planSteps.size() != 1 || !(planSteps.get(0) instanceof PlanStep step)) {
                answer = "⛔ execute_node 已停止：M4-PX.4 仅开放单步 READ 计划执行，多步或非结构化计划暂不自动执行。";
                executeResult.put("code", "EXECUTE_STEP_UNSUPPORTED");
                executeResult.put("reason", "当前只允许恰好一个结构化 PlanStep 进入安全执行层。");
            } else if (!"READ".equalsIgnoreCase(String.valueOf(step.riskLevel()))) {
                answer = "⛔ execute_node 已停止：计划步骤不是 READ 风险等级，必须走 HITL 或 ReAct/ToolCall 安全链路。";
                executeResult.put("code", "EXECUTE_STEP_NOT_READ_ONLY");
                executeResult.put("stepId", step.id());
                executeResult.put("riskLevel", step.riskLevel());
            } else if (step.requiresConfirmation()) {
                answer = "⛔ execute_node 已停止：计划步骤声明需要人工确认，不能由 execute_node 自动执行。";
                executeResult.put("code", "EXECUTE_STEP_REQUIRES_CONFIRMATION");
                executeResult.put("stepId", step.id());
            } else if (step.suggestedTool() == null || step.suggestedTool().isBlank()) {
                answer = "⛔ execute_node 已停止：READ 步骤缺少 suggestedTool，无法映射到受控 ToolRegistry。";
                executeResult.put("code", "EXECUTE_STEP_UNSUPPORTED");
                executeResult.put("reason", "缺少候选 intentId。");
            } else {
                Map<String, Object> stepParameters = step.parameters() == null ? Map.of() : step.parameters();
                if (containsProtectedContextParam(stepParameters)) {
                    answer = "⛔ execute_node 已停止：PlanStep.parameters 包含受保护的系统上下文字段，不能由计划结果覆盖身份、租户或会话信息。";
                    executeResult.put("code", "PROTECTED_PLAN_PARAMETER");
                    executeResult.put("stepId", step.id());
                    executeResult.put("reason", "Plan 参数属于不可信输入，出现 token/orgId/userId/conversationId 等字段时按 fail-closed 策略停止。");
                } else {
                    // 【关键安全边界】execute_node 不直接执行任何 Tool，只构造服务端可信请求，
                    // 然后交给 SafeToolExecutor 统一校验 Tool 元数据、权限、HITL、租户上下文和异常恢复。
                    SafeToolExecutionRequest request = new SafeToolExecutionRequest(
                        step.suggestedTool(),
                        stepParameters,
                        state.value("user_id").map(Object::toString).orElse("anonymous"),
                        state.value("token").map(Object::toString).orElse(""),
                        state.value("orgId").map(Object::toString).orElse(""),
                        state.value("conversation_id").map(Object::toString).orElse(""),
                        null,
                        SafeToolExecutionSource.PLAN_EXECUTE_NODE
                    );
                    SafeToolExecutionResult result = safeToolExecutor.executeIntent(request);
                    Map<String, Object> updates = result.toGraphUpdates();
                    Map<String, Object> executedResult = new HashMap<>();
                    executedResult.put("executed", result.executed());
                    executedResult.put("success", result.success());
                    executedResult.put("code", result.executed() ? "EXECUTE_STEP_DELEGATED" : "EXECUTE_STEP_BLOCKED_BY_SAFE_EXECUTOR");
                    executedResult.put("intentId", step.suggestedTool());
                    executedResult.put("source", SafeToolExecutionSource.PLAN_EXECUTE_NODE.name());
                    executedResult.put("stepCount", planSteps.size());
                    updates.put("execute_node_result", result.answer());
                    updates.put("execute_result", executedResult);
                    updates.put("execute_steps", planSteps);
                    return updates;
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("answer", answer);
            updates.put("execute_node_result", answer);
            updates.put("execute_result", executeResult);
            updates.put("execute_steps", planSteps);
            return updates;
        });
    }

    /**
     * 构建 ReAct 节点异步动作。
     * <p>从 Graph State 中读取用户查询、token、user_id、orgId，
     * 构造 initialParams 后调用 {@link ReActEngine#run} 同步执行手写 ReAct 推理循环。
     * 执行结果写入 State 的 answer、react_node_result、react_result、react_steps 等 key，
     * 供下游 SSE 节点消费。</p>
     *
     * <p>M3.2 批次：同步执行，暂不接入 SSE 流式推送（TODO 第三批）。</p>
     *
     * @param engine 手写 ReAct 引擎（由 Spring 容器注入）
     * @param sinkRegistry ReAct 过程事件接收器运行期注册表
     * @return 异步节点动作
     */
    private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildReActNode(
            ReActEngine engine,
            ReActEventSinkRegistry sinkRegistry) {
        return node_async((OverAllState state) -> {
            // 1. 从 State 读取必要上下文
            String input = state.value("input").map(Object::toString).orElse("");
            String token = state.value("token").map(Object::toString).orElse("");
            String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
            String orgId = state.value("orgId").map(Object::toString).orElse("");
            String conversationId = state.value("conversation_id").map(Object::toString).orElse("");

            // 2. 构造 initialParams，透传身份、租户和会话信息供工具调用使用
            //    ReActEngine.run 会将 initialParams 与每轮 Action params 合并后透传至工具执行层
            Map<String, Object> initialParams = new HashMap<>();
            initialParams.put("userId", userId);
            initialParams.put("token", token);
            initialParams.put("organizationId", orgId);
            initialParams.put("conversationId", conversationId);

            // 3. 执行同步 ReAct 推理循环。
            //    Graph State 只允许保存纯数据，不能保存 Lambda/SseEmitter 等运行期对象。
            //    因此 State 中只读取 react_event_session_id 字符串，再通过运行期 registry 查找 sink。
            String reactEventSessionId = state.value("react_event_session_id")
                .map(Object::toString)
                .orElse("");
            ReActEventSink eventSink = reactEventSessionId.isBlank()
                ? ReActEventSink.NOOP
                : event -> sinkRegistry.publish(reactEventSessionId, event);
            ReActResult result = engine.runWithEvents(input, initialParams, eventSink);

            // 4. 组装 State 更新：answer 作为通用最终答案 key，
            //    react_node_result / react_result / react_steps 供精细化展示和调试
            String finalAnswer = result.finalAnswer() != null ? result.finalAnswer() : "";
            Map<String, Object> updates = new HashMap<>();
            updates.put("answer", finalAnswer);
            updates.put("react_node_result", finalAnswer);
            updates.put("react_result", result);
            if (result.steps() != null) {
                updates.put("react_steps", result.steps());
            }
            return updates;
        });
    }
    /**
     * Supervisor 图 — AtlasBrain 决策节点 + 条件路由。
     * START → supervisor → [conditional] → {direct_answer, ask_clarify, tool_call, delegate, react_node} → END
     */
    @Bean
    public CompiledGraph supervisorGraph(
            AtlasBrain atlasBrain, ToolRegistry toolRegistry,
            HitlGuard hitlGuard,
            ReActEngine reactEngine,
            ReActEventSinkRegistry reactEventSinkRegistry,
            PlanEngine planEngine,
            SafeToolExecutor safeToolExecutor,
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

            BrainDecision existingDecision = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast)
                .orElse(null);
            HitlConfirmation existingConfirmation = state.value("hitl_confirmation")
                .filter(HitlConfirmation.class::isInstance)
                .map(HitlConfirmation.class::cast)
                .orElse(null);
            if (existingDecision != null
                && existingDecision.actionType() == BrainDecision.ActionType.CALL_TOOL
                && existingConfirmation != null) {
                // M5.13/M5.21 HITL 恢复闭环：
                // confirm 注入的是已通过服务端 token 校验的 CALL_TOOL 决策，必须优先复用，
                // 否则再次调用 AtlasBrain 会覆盖目标 Tool，导致确认后仍无法进入 tool_call。
                // clarify 注入的是用户补充输入，不是人工确认；必须重新进入 AtlasBrain 决策，
                // 避免复用 ASK_CLARIFY 后继续路由到 ask_clarify，形成补参后的原地打转。
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("brain_decision", existingDecision);
                updates.put("reasoning", existingDecision.reasoning());
                return updates;
            }

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
                    case DELEGATE_REACT -> "react_node"; // M3.2 第二批：接入手写 ReAct 引擎
                    case PLAN -> "plan_node"; // M4 Plan-and-Execute：进入只规划不执行的 plan_node
                    case HITL_CONFIRM -> "hitl_confirm";
                };
            }),
            java.util.Map.of(
                "direct_answer", "direct_answer",
                "ask_clarify", "ask_clarify",
                "tool_call", "tool_call",
                "delegate", "delegate",
                "react_node", "react_node",
                "plan_node", "plan_node",
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

        graph.addNode("plan_node", buildPlanNode(planEngine));
        graph.addNode("execute_node", buildExecuteNode(safeToolExecutor));

        graph.addNode("tool_call", node_async((OverAllState state) -> {
            BrainDecision d = state.value("brain_decision")
                .filter(BrainDecision.class::isInstance)
                .map(BrainDecision.class::cast)
                .orElse(null);
            if (d == null) {
                return java.util.Map.of("answer", "[无决策] AtlasBrain 未产生有效决策");
            }

            String orgId = state.value("orgId").map(Object::toString).orElse("");
            HitlConfirmation confirmation = state.value("hitl_confirmation")
                .filter(HitlConfirmation.class::isInstance)
                .map(HitlConfirmation.class::cast)
                .orElse(null);

            // M4-PX.3-A：Graph tool_call 不再内联 Tool 执行链，而是统一委托 SafeToolExecutor。
            // 这样 tool_call 与后续 execute_node 能共享同一套权限、租户上下文、HITL fail-closed、
            // ThreadLocal 绑定/恢复和受保护参数过滤逻辑，避免多入口安全漂移。
            SafeToolExecutionRequest request = new SafeToolExecutionRequest(
                d.target(),
                d.parameters(),
                state.value("user_id").map(Object::toString).orElse("anonymous"),
                state.value("token").map(Object::toString).orElse(""),
                orgId,
                state.value("conversation_id").map(Object::toString).orElse(""),
                confirmation,
                SafeToolExecutionSource.GRAPH_TOOL_CALL
            );
            return safeToolExecutor.executeIntent(request).toGraphUpdates();
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
                state.value("orgId").ifPresent(v -> {
                    subInputs.put("orgId", v);
                    subInputs.put("organizationId", v);
                });
                state.value("messages").ifPresent(v -> subInputs.put("messages", v));

                // ==============================
                // 2. Token + OrgId 透传：在子图执行前显式设置 ThreadLocal
                // ==============================
                String token = state.value("token").map(Object::toString).orElse("");
                String orgId = state.value("orgId").map(Object::toString).orElse("");
                if (orgId.isBlank()) {
                    orgId = com.atlas.auth.UserPermissionContext.getCurrentOrgId();
                }
                if (orgId == null || orgId.isBlank()) {
                    return Map.of("answer", "❌ 安全上下文缺失：无法确定当前用户所属组织，请重新登录后再试。");
                }
                String previousToken = com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.get();
                String previousOrgId = com.atlas.auth.UserPermissionContext.CURRENT_ORG_ID.get();
                if (!token.isBlank()) {
                    com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.set(token);
                } else {
                    com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.remove();
                }
                if (orgId != null && !orgId.isBlank()) {
                    com.atlas.auth.UserPermissionContext.CURRENT_ORG_ID.set(orgId);
                    subInputs.put("orgId", orgId);
                    subInputs.put("organizationId", orgId);
                } else {
                    com.atlas.auth.UserPermissionContext.CURRENT_ORG_ID.remove();
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
                    // 恢复 ThreadLocal，防止泄漏或误删外层上下文
                    if (previousToken != null) {
                        com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.set(previousToken);
                    } else {
                        com.atlas.auth.UserPermissionContext.CURRENT_TOKEN.remove();
                    }
                    if (previousOrgId != null) {
                        com.atlas.auth.UserPermissionContext.CURRENT_ORG_ID.set(previousOrgId);
                    } else {
                        com.atlas.auth.UserPermissionContext.CURRENT_ORG_ID.remove();
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

        // 5. ReAct 节点（手写推理引擎）— M3.2 第二批接入
        //    复用 buildReActNode 工厂方法，保持与 atlasGraph 一致的节点行为
        graph.addNode("react_node", buildReActNode(reactEngine, reactEventSinkRegistry));

        // 4. 连接边
        graph.addEdge(START, "supervisor");
        graph.addEdge("direct_answer", END);
        graph.addEdge("ask_clarify", END);
        graph.addEdge("tool_call", END);
        graph.addEdge("delegate", END);
        graph.addEdge("react_node", END);
        graph.addEdge("plan_node", "execute_node");
        graph.addEdge("execute_node", END);
        graph.addEdge("hitl_confirm", END);

        return graph.compile();
    }

    /**
     * 递归检查 Plan 参数中是否包含受保护的系统上下文字段。
     *
     * <p>PlanStep.parameters 来自规划层，可能由 LLM 或上游状态间接生成，不能信任。
     * execute_node 在把参数交给 SafeToolExecutor 之前先做一层快速 fail-closed：只要
     * 顶层或嵌套对象中出现 token/orgId/userId/conversationId 等字段，就认为该计划尝试
     * 覆盖服务端可信上下文，直接停止执行。SafeToolExecutor 仍保留最终兜底过滤，形成双层防线。</p>
     *
     * @param value 任意 Plan 参数值，可能是 Map、List 或普通标量
     * @return true 表示发现受保护上下文字段，应停止 execute_node 自动执行
     */
    private static boolean containsProtectedContextParam(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isProtectedContextKey(key) || containsProtectedContextParam(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsProtectedContextParam(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断字段名是否属于 execute_node 禁止从 Plan 参数接收的服务端上下文字段。
     *
     * <p>这里采用大小写不敏感匹配，并兼容 snake_case/camelCase 的历史字段名。该集合
     * 与 SafeToolExecutor 的 protected 参数保持语义一致，但 execute_node 选择 fail-closed，
     * 避免静默删除恶意字段后继续执行。</p>
     */
    private static boolean isProtectedContextKey(String key) {
        if (key == null) {
            return false;
        }
        Set<String> protectedKeys = Set.of(
            "token",
            "authorization",
            "cookie",
            "accesstoken",
            "access_token",
            "authtoken",
            "auth_token",
            "organizationid",
            "organization_id",
            "orgid",
            "org_id",
            "tenantid",
            "tenant_id",
            "conversationid",
            "conversation_id",
            "userid",
            "user_id"
        );
        return protectedKeys.contains(key.toLowerCase(java.util.Locale.ROOT));
    }
}
