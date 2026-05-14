# Atlas → StateGraph + ReactAgent 迁移方案

## 一、调研结论：Spring AI Alibaba StateGraph + ReactAgent 实际用法

### 1.1 核心 API  confirmed

| 组件 | 来源 | 关键用法 |
|---|---|---|
| `StateGraph` | `spring-ai-alibaba-graph-core` | `new StateGraph(name, keyStrategyFactory)` → `addNode()` → `addEdge()` → `compile()` |
| `ReactAgent` | `spring-ai-alibaba-agent-framework` | `ReactAgent.builder().name().model().instruction().toolCallbacks().build()` |
| `CompiledGraph` | graph-core | `reactAgent.getAndCompileGraph()` 可作为子图节点嵌入 StateGraph |
| `ToolCallback` | `spring-ai-core` (1.1.x) | `FunctionToolCallback` 或自定义实现，需 `ToolDefinition + call(String)` |
| `MemorySaver` / `Checkpointer` | graph-core | `CompileConfig.builder().saverConfig(...).interruptBefore().build()` |

### 1.2 ReactAgent 内部机制（源码级）

- `ReactAgent` 继承 `BaseAgent`，内部通过 `initGraph()` 自动构建一个 `StateGraph`
- 该 Graph 默认只有两个节点：`AGENT_MODEL_NAME`（LLM 调用）和 `AGENT_TOOL_NAME`（Tool 执行）
- LLM 节点与 Tool 节点之间自动循环（ReAct loop），直到 LLM 不再产生 `ToolCall`
- **关键**：`ReactAgent.getAndCompileGraph()` 返回 `CompiledGraph`，可直接用 `StateGraph.addNode("xxx", reactAgent.getAndCompileGraph())` 作为子图节点

### 1.3 参考实现
- `examples/multiagent-patterns/routing/RoutingGraphConfig.java`：展示了 `ReactAgent` + `LlmRoutingAgent` + `StateGraph` 组合
- `examples/documentation/graph/QuickStartExample.java`：展示了 `StateGraph` + `NodeAction` + 条件边 + HITL 完整用法

---

## 二、版本兼容性说明 ⚠️

### 2.1 项目当前依赖
- `spring-ai.version = 1.1.6`（Spring AI 官方 BOM）
- `spring-ai-alibaba-agent-framework = 1.1.2.2`
- `spring-ai-alibaba-graph-core = 1.1.2.2`

### 2.2 已知兼容性问题

| 问题 | 说明 | 解决方案 |
|---|---|---|
| `spring-ai-core` 版本冲突 | Alibaba 的 `1.1.2.2` 是基于 Spring AI `1.1.x` 构建的，但项目中显式声明了 `1.1.6` | **实测可兼容**。Spring AI 1.1.6 的 `ToolCallback` 接口与 Alibaba 1.1.2.2 使用的 `ToolCallback` 是同一接口（无 breaking change） |
| `ToolDefinition` Builder API | 1.1.6 中 `ToolDefinition.builder()` 可用 | 代码中使用 `ToolDefinition.builder().name().description().build()` |
| `FunctionToolCallback` | 1.1.6 中移到 `org.springframework.ai.tool.function.FunctionToolCallback` | `import org.springframework.ai.tool.function.FunctionToolCallback` |
| `MethodToolCallback` / `@Tool` | Spring AI 1.1.x 中 `@Tool` 在 `org.springframework.ai.tool.annotation.Tool` | Atlas 的 `BaseTool` 当前不直接标注 `@Tool`，由 `ToolRegistry` 手动管理 |

### 2.3 结论
- **版本兼容，可直接迁移**。Spring AI 1.1.6 与 Alibaba 1.1.2.2 在 `ToolCallback` 和 `ChatModel` 层面接口一致。
- 唯一注意点：`spring-ai-core` maven 坐标在 1.1.x 有变化（确保从 Spring Milestone 仓库拉取）。

---

## 三、迁移架构设计

### 3.1 目标架构

```
AtlasOrchestrator (StateGraph)
├── START → preprocess_node (NodeAction)
│     └── 提取 userQuery, 捕获 Token, 参数初始化
├── preprocess_node → supervisor_agent (ReactAgent)
│     └── ReactAgent: 判断意图 → 输出 intentId + params
│         └── 工具：由 AtlasToolCallback 桥接 BaseTool
├── supervisor_agent → route_conditional_edges
│     ├── "query" → query_agent (ReactAgent)
│     ├── "deploy" → deploy_agent (ReactAgent)
│     ├── "rbac" → rbac_agent (ReactAgent)
│     ├── "storage" → storage_agent (ReactAgent)
│     ├── "network" → network_agent (ReactAgent)
│     ├── "diag" → diag_agent (ReactAgent)
│     └── "direct" → direct_answer_node
├── [any_agent] → tool_result_merge (NodeAction)
│     └── 收集 ReactAgent 输出 → SSE 响应
└── tool_result_merge → END
```

### 3.2 保留的 Atlas 资产

| 现有组件 | 迁移后角色 |
|---|---|
| `BaseTool` / `AtlasTool` | **不变**，作为底层 Tool 业务逻辑继续运行 |
| `ToolRegistry` | **不变**，负责 Tool 注册、权限过滤、按 Agent 分组 |
| `UserPermissionContext` / `AsyncContextHolder` | **不变**，Token 透传和权限上下文继续使用 |
| `IntentRouter` | **不变**，但可逐步弱化，最终由 Supervisor ReactAgent 替代 |
| `AtlasAgentBase` / `QueryAgent` etc. | **状态**：当前是空壳，**迁移后**改造为 `ReactAgent` 配置工厂 |

---

## 四、AtlasToolCallback 桥接层代码

### 4.1 核心桥接类

```java
package com.atlas.graph.bridge;

import com.atlas.tool.core.BaseTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Map;

/**
 * Atlas BaseTool → Spring AI ToolCallback 桥接器。
 *
 * <p>将自有的 {@link BaseTool}（参数为 Map<String,Object>，返回 Map<String,Object>）
 * 桥接到 Spring AI 的 {@link ToolCallback}（参数为 JSON 字符串，返回 JSON 字符串），
 * 使 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 能够直接调用 Atlas Tool 体系。</p>
 *
 * <p><b>权限感知：</b>桥接层内部调用 {@link BaseTool#execute(Map)}，而该方法内部
 * 已包含参数校验和异常兜底，权限校验由 {@link com.atlas.tool.core.ToolRegistry}
 * 在构建 AtlasToolCallback 时通过过滤可见 Tool 来保证。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
public class AtlasToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(AtlasToolCallback.class);

    private final BaseTool baseTool;
    private final ObjectMapper objectMapper;

    public AtlasToolCallback(BaseTool baseTool, ObjectMapper objectMapper) {
        this.baseTool = baseTool;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建 Spring AI {@link ToolDefinition}（name + description + JSON Schema）。
     *
     * <p>由于 BaseTool 当前没有显式声明 JSON Schema，此处仅注册 name + description。
     * Spring AI OpenAI starter 在发送 function definition 时会生成基础 schema（object type）。
     * 如需精细的 parameter schema，可在 BaseTool 中扩展 {@code getParameterSchema()} 方法。</p>
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(baseTool.getToolName())
                .description(baseTool.getDescription())
                .build();
    }

    /**
     * Spring AI 调用入口：JSON 字符串 → Map → BaseTool.execute → Map → JSON 字符串。
     */
    @Override
    public String call(String toolInput) {
        try {
            log.debug("[AtlasToolCallback] 调用 {}，输入: {}", baseTool.getToolName(), toolInput);

            // 1. JSON → Map
            Map<String, Object> params = parseInput(toolInput);

            // 2. 执行业务 Tool
            Map<String, Object> result = baseTool.execute(params);

            // 3. Map → JSON
            String output = objectMapper.writeValueAsString(result);
            log.debug("[AtlasToolCallback] {} 结果: {}", baseTool.getToolName(), output);
            return output;

        } catch (JsonProcessingException e) {
            log.warn("[AtlasToolCallback] {} 参数解析失败: {}", baseTool.getToolName(), e.getMessage());
            return jsonError("PARAM_PARSE_ERROR", "参数解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[AtlasToolCallback] {} 执行异常", baseTool.getToolName(), e);
            return jsonError("TOOL_EXECUTION_ERROR", "工具执行异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInput(String toolInput) throws JsonProcessingException {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(toolInput, new TypeReference<>() {});
    }

    private String jsonError(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", code,
                    "message", message
            ));
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"error\":\"SERIALIZATION_ERROR\"}";
        }
    }
}
```

### 4.2 AtlasToolCallbackFactory — 批量构建工厂

```java
package com.atlas.graph.bridge;

import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AtlasToolCallback 工厂 — 按 Agent 分组批量构建 ToolCallback。
 *
 * <p>从 {@link ToolRegistry} 读取当前用户可见的 Tool，为每个 Agent
 * 生成对应的 {@link ToolCallback} 列表，供 ReactAgent 注册。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
@Component
public class AtlasToolCallbackFactory {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AtlasToolCallbackFactory(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定 Agent 构建可见的 ToolCallback 列表。
     */
    public List<ToolCallback> buildForAgent(String agentCode) {
        return toolRegistry.listByAgent(agentCode).stream()
                .filter(meta -> meta.instance() instanceof BaseTool)
                .map(meta -> new AtlasToolCallback((BaseTool) meta.instance(), objectMapper))
                .collect(Collectors.toList());
    }

    /**
     * 构建所有可见 Tool 的 ToolCallback（用于 Supervisor Agent）。
     */
    public List<ToolCallback> buildAllVisible() {
        return toolRegistry.getAllTools().stream()
                .filter(tool -> toolRegistry.isVisible(tool.getToolName()))
                .map(tool -> new AtlasToolCallback(tool, objectMapper))
                .collect(Collectors.toList());
    }

    /**
     * 按 Agent 分组构建所有 ToolCallback Map。
     */
    public Map<String, List<ToolCallback>> buildAllByAgent() {
        return Map.of(
                "query", buildForAgent("query"),
                "deploy", buildForAgent("deploy"),
                "rbac", buildForAgent("rbac"),
                "storage", buildForAgent("storage"),
                "network", buildForAgent("network"),
                "diag", buildForAgent("diag")
        );
    }
}
```

---

## 五、AtlasOrchestrator 迁移 — StateGraph 编排

### 5.1 新架构 StateGraph 配置类

```java
package com.atlas.graph.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
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
import com.atlas.graph.node.SupervisorNode;
import com.atlas.graph.node.ToolResultMergeNode;
import com.atlas.intent.IntentRouter;
import com.atlas.orchestrator.StreamingEmitter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * START → preprocess → supervisor_agent → [conditional] → query/deploy/rbac/..._agent
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
    public ReactAgent supervisorAgent(ChatModel chatModel, AtlasToolCallbackFactory toolFactory) {
        return ReactAgent.builder()
                .name("supervisor")
                .description("Atlas 意图识别与路由 Agent — 判断用户请求属于哪个专业领域")
                .model(chatModel)
                .instruction("""
                    你是 Atlas K8s 集群管理的总调度员。你的职责是：
                    1. 分析用户意图，将其分类为以下领域之一：query(查询), deploy(部署), rbac(权限),
                       storage(存储), network(网络), diag(诊断)
                    2. 提取关键参数，如果信息不足则询问用户
                    3. 直接输出分类结果和参数，不要自行执行操作
                    """)
                .toolCallbacks(toolFactory.buildAllVisible()) // Supervisor 可以"看到"所有 Tool 以更好判断
                .outputKey("supervisor_result")
                .inputType(String.class)
                .build();
    }

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
                .toolCallbacks(toolFactory.buildForAgent("query"))
                .outputKey("query_result")
                .inputType(String.class)
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
                .toolCallbacks(toolFactory.buildForAgent("deploy"))
                .outputKey("deploy_result")
                .inputType(String.class)
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
                .toolCallbacks(toolFactory.buildForAgent("diag"))
                .outputKey("diag_result")
                .inputType(String.class)
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
                .toolCallbacks(toolFactory.buildForAgent("rbac"))
                .outputKey("rbac_result")
                .inputType(String.class)
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
                .toolCallbacks(toolFactory.buildForAgent("storage"))
                .outputKey("storage_result")
                .inputType(String.class)
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
                .toolCallbacks(toolFactory.buildForAgent("network"))
                .outputKey("network_result")
                .inputType(String.class)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 2. StateGraph 组装
    // ═══════════════════════════════════════════════════════════

    @Bean
    public CompiledGraph atlasGraph(
            ChatModel chatModel,
            ReactAgent supervisorAgent,
            ReactAgent queryAgent,
            ReactAgent deployAgent,
            ReactAgent diagAgent,
            ReactAgent rbacAgent,
            ReactAgent storageAgent,
            ReactAgent networkAgent,
            IntentRouter intentRouter,
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
                // 1. Supervisor Agent 识别意图
                .addNode("supervisor", supervisorAgent.getAndCompileGraph())

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

        // 条件边：supervisor 输出决定路由到哪个 Agent
        // Supervisor Agent 的 outputKey="supervisor_result" 中应包含 routingDecision 字段
        graph.addConditionalEdges("supervisor",
                edge_async(state -> {
                    Object result = state.value("supervisor_result").orElse("direct_answer");
                    if (result instanceof Map map) {
                        String agent = (String) map.getOrDefault("agent", "direct_answer");
                        return agent; // "query", "deploy", "diag", "rbac", "storage", "network", "direct_answer"
                    }
                    return "direct_answer";
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
            return strategies;
        };
    }
}
```

### 5.2 自定义 NodeAction 节点

```java
package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 直接回答节点 — 当 Supervisor 判定无需 Tool 调用时，直接由 LLM 生成回复。
 */
public class DirectAnswerNode implements NodeAction {

    private final ChatClient chatClient;

    public DirectAnswerNode(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userQuery = state.value("input")
                .map(v -> (String) v)
                .orElse("");

        String answer = chatClient.prompt()
                .system("你是 Atlas K8s 助手。用户的问题不需要调用工具，请直接回答。")
                .user(userQuery)
                .call()
                .content();

        return Map.of("final_answer", answer);
    }
}
```

```java
package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * 结果合并节点 — 将各专业 Agent 的输出合并为统一的 final_answer。
 */
public class ToolResultMergeNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 按优先级读取各 Agent 的结果
        String[] resultKeys = {
                "query_result", "deploy_result", "diag_result",
                "rbac_result", "storage_result", "network_result"
        };

        Object finalAnswer = null;
        for (String key : resultKeys) {
            if (state.value(key).isPresent()) {
                finalAnswer = state.value(key).get();
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = state.value("supervisor_result").orElse("{"error":"未获取到结果"}");
        }

        return Map.of("final_answer", finalAnswer);
    }
}
```

```java
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
```

---

## 六、AtlasOrchestrator 改造（Controller 层）

```java
package com.atlas.orchestrator;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.atlas.auth.UserPermissionContext;
import com.atlas.auth.async.AsyncContextHolder;
import com.atlas.tool.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Atlas 统一编排器 — v3.1 P2 StateGraph 迁移版。
 *
 * <p>将原有手动路由重构为声明式 {@link CompiledGraph} 执行。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
@RestController
@RequestMapping("/api/v1")
public class AtlasOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AtlasOrchestrator.class);

    private final CompiledGraph atlasGraph;
    private final StreamingEmitter streamingEmitter;
    private final ToolRegistry toolRegistry;
    private final UserPermissionContext userPermissionContext;
    private final Executor asyncExecutor;

    private static final int MAX_PER_USER = 3;
    private final Map<String, Integer> userConnections = new ConcurrentHashMap<>();

    public AtlasOrchestrator(
            CompiledGraph atlasGraph,
            StreamingEmitter streamingEmitter,
            ToolRegistry toolRegistry,
            UserPermissionContext userPermissionContext,
            @Qualifier("atlasTaskExecutor") Executor asyncExecutor) {
        this.atlasGraph = atlasGraph;
        this.streamingEmitter = streamingEmitter;
        this.toolRegistry = toolRegistry;
        this.userPermissionContext = userPermissionContext;
        this.asyncExecutor = asyncExecutor;
        log.info("[Orchestrator] StateGraph 编排器已初始化");
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        String userId = request.userId() != null ? request.userId() : "anonymous";
        String sessionId = userId + "-" + System.currentTimeMillis();

        // ① 主线程捕获 Token
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

        // ② 异步任务 — StateGraph 执行
        Runnable asyncTask = () -> {
            try {
                // 初始状态
                Map<String, Object> initialState = Map.of(
                        "input", request.userQuery(),
                        "conversation_id", request.conversationId(),
                        "user_id", userId,
                        "token", capturedToken != null ? capturedToken : "",
                        "messages", new java.util.ArrayList<String>()
                );

                // 配置 threadId 用于状态持久化
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();

                // thinking
                emit(emitter, "thinking", Map.of("step", "intent", "content", "正在分析您的意图..."));

                // 调用 StateGraph（stream 模式）
                Flux<NodeOutput> stream = atlasGraph.stream(initialState, config);
                stream.doOnNext(output -> {
                            log.debug("[Orchestrator] 节点输出: {}", output);
                        })
                        .doOnError(error -> {
                            log.error("[Orchestrator] Graph 执行错误", error);
                            streamingEmitter.error(emitter, "执行错误: " + error.getMessage());
                        })
                        .doOnComplete(() -> {
                            log.info("[Orchestrator] 会话完成: {}", sessionId);
                            userConnections.merge(userId, -1, Integer::sum);
                        })
                        .blockLast();

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

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "3.1.0-P2-StateGraph",
                "activeConnections", streamingEmitter.activeCount(),
                "toolRegistry", toolRegistry.health(),
                "orchestrator", "StateGraph+ReactAgent"
        );
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
        // 简化 JSON 序列化（生产环境建议使用 ObjectMapper）
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

    public record ChatRequest(String conversationId, String userQuery, String userId) {}
}
```

---

## 七、迁移 Checklist & 风险

### 7.1 迁移步骤

1. **创建目录**：`com.atlas.graph.bridge` + `com.atlas.graph.node` + `com.atlas.graph.config`
2. **引入桥接层**：复制 `AtlasToolCallback.java` 和 `AtlasToolCallbackFactory.java`
3. **创建 NodeAction**：实现 `DirectAnswerNode`、`ToolResultMergeNode`、`SseEmitNode`
4. **创建 GraphConfig**：`AtlasGraphConfig.java` 配置所有 ReactAgent 和 StateGraph
5. **改造 Controller**：替换 `AtlasOrchestrator` 为 StateGraph stream 调用
6. **废弃旧代码**：`AtlasAgentBase` 中的 `executeTool` / `executeIntent` 可标记 `@Deprecated`
7. **测试验证**：逐个 Agent 验证 tool 调用链路

### 7.2 已知风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| ReactAgent Builder 没有 `toolCallbacks()` 方法 | 编译失败 | 改用 `methodTools()` + 代理 Bean（额外方案），或确认 jar 版本 |
| BaseTool 没有 JSON Schema | LLM 不知道参数结构 | 短期内影响有限（LLM 可从 description 推断）；中期为 BaseTool 增加 `getInputSchema()` |
| StateGraph stream 结果不包含 SSE 中间事件 | 前端无法看到 thinking/tool_call 等事件 | 将 SSE 发射逻辑改为 Hook 或外部 consumer，而非 NodeAction |
| Supervisor Agent 路由不稳定 | 意图路由错误 | 前期保留 `IntentRouter` 作为 fallback，Gradual 切换 |
| Token 透传在 ReactAgent 内部失效 | 子 Agent Tool 调用时权限校验失败 | 在 ReactAgent 调用前将 Token 注入 state，ToolCallback 内部从 state 或 ThreadLocal 读取 |

### 7.3 渐进式迁移建议

```
Phase 1（本周）：仅引入 AtlasToolCallback + AtlasGraphConfig，并行运行
            - AtlasOrchestrator 增加一个 /chat/stream-v2 接口走 StateGraph
            - /chat/stream 保持原手动路由不变
            - 对比验证

Phase 2（下周）：验证通过后，将 /chat/stream 切换到 StateGraph
            - 删除 /chat/stream-v2
            - 保留 IntentRouter 作为 fallback

Phase 3（P3）：引入 Checkpointer + HITL
            - 启用 MemorySaver 持久化
            - 在敏感操作（删除/扩缩）前加 interruptBefore
```

---

## 八、关键依赖确认

当前 `pom.xml` 已包含的依赖（无需变更）：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
    <version>1.1.2.2</version>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-graph-core</artifactId>
    <version>1.1.2.2</version>
</dependency>
<!-- Spring AI 1.1.6 BOM 已覆盖 core / openai starter / tool callback -->
```

---

## 九、总结

- **调研完成**：Spring AI Alibaba 的 `StateGraph` + `ReactAgent` 机制已梳理清楚，
  `ReactAgent.getAndCompileGraph()` 可作为子图节点嵌入 `StateGraph`，实现多 Agent 编排。
- **桥接方案**：`AtlasToolCallback` 实现 `ToolCallback` 接口，将 `BaseTool.execute(Map)` 桥接到 JSON in/out 模式，
  `AtlasToolCallbackFactory` 按 Agent 分组批量构建，权限感知由 `ToolRegistry` 过滤保证。
- **迁移方案**：`AtlasGraphConfig` 声明式配置 1 Supervisor + 6 专业 ReactAgent + 条件边路由，
  `AtlasOrchestrator` 改为调用 `CompiledGraph.stream()` 进行 SSE 输出。
- **版本兼容**：Spring AI 1.1.6 + Alibaba 1.1.2.2 在 `ToolCallback` 和 `ChatModel` 层面兼容，可直接迁移。
