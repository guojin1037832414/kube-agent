# StateGraph + ReactAgent + AtlasBrain 集成调研报告 — kube-agent v3.1 Layer 3

> **调研目标**: 为 kube-agent v3.1 Layer 3 编排层提供从 IntentRouter 单步路由 → StateGraph 图编排 + AtlasBrain 决策 + ReactAgent 多步推理的精确可执行方案。
> **调研路径**: alibaba/spring-ai-alibaba GitHub 源码第一手分析（StateGraph.java, ReactAgent.java, CompiledGraph.java, GraphRunner.java, MainGraphExecutor.java）
> **日期**: 2026-05-15

---

## 1. 结论总览

| 问题 | 代码级结论 |
|------|-----------|
| StateGraph如何集成主链路 | `CompiledGraph.stream(inputs, config)` 返回 `Flux<NodeOutput>`，直接衔接现有 SSE emitter，无阻塞 |
| AtlasBrain作为Node注册 | 以 `node_async(lambda)` 注册到 StateGraph，`addConditionalEdges("supervisor", edge_async(...), mappings)` 映射 `BrainDecision.actionType` → 目标节点 |
| ReactAgent与StateGraph共存 | ReactAgent 作为 `CompiledGraph` 子图节点 `graph.addNode("query", queryAgent.getAndCompileGraph())`，其内部 ReAct 循环对父图透明 |
| 自循环限制 | **图层面不支持自循环**；循环需求由 ReactAgent 内部条件边实现，或移至 Orchestrator 外层循环 |
| 渐进迁移 | 保留 `/chat/stream` 接口，通过策略模式在 IntentRouter 和 CompiledGraph 之间切换，逐步下线旧路由 |

---

## 2. CompiledGraph 与主链路集成（源码级分析）

### 2.1 CompiledGraph 流式执行入口

```java
// CompiledGraph.java L570-L602
public Flux<NodeOutput> stream(Map<String, Object> inputs, RunnableConfig config) {
    return streamFromInitialNode(stateCreate(inputs), config);
}

public Flux<NodeOutput> streamFromInitialNode(OverAllState overAllState, RunnableConfig config) {
    Objects.requireNonNull(config, "config cannot be null");
    try {
        GraphRunner runner = new GraphRunner(this, config);
        return runner.run(overAllState).flatMap(data -> {
            if (data.isDone()) {
                if (data.resultValue().isPresent() && data.resultValue().get() instanceof NodeOutput) {
                    return Flux.just((NodeOutput) data.resultValue().get());
                } else {
                    return Flux.empty();
                }
            }
            if (data.isError()) {
                return Mono.fromFuture(data.getOutput()).onErrorMap(throwable -> throwable).flux();
            }
            return Mono.fromFuture(data.getOutput()).flux();
        });
    } catch (Exception e) {
        return Flux.error(e);
    }
}
```

**关键发现**：
- `CompiledGraph.stream()` 返回的是 `Flux<NodeOutput>`，天然适配 SSE 流式输出
- 内部使用 `GraphRunner` + `MainGraphExecutor.execute()` 递归执行，基于 Project Reactor
- 每个节点执行完成后会 emit 一个 `NodeOutput`（含 node名称 + 当前 state snapshot）

### 2.2 AtlasOrchestrator 集成方式

```java
// 修改 AtlasOrchestrator.streamChat() 主链路
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestBody ChatRequest request) {
    // ... (限流、Token捕获等保持不变)
    
    Runnable asyncTask = () -> {
        try {
            // 构建 Graph 输入
            Map<String, Object> inputs = Map.of(
                "input", request.userQuery(),
                "user_id", userId,
                "token", capturedToken,
                "conversation_id", request.conversationId()
            );
            
            RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();
            
            // 核心：CompiledGraph 流式订阅 → SSE emit
            compiledGraph.stream(inputs, config)
                .subscribe(
                    nodeOutput -> {
                        // 每个节点完成时推送
                        String node = nodeOutput.node();
                        OverAllState state = nodeOutput.state();
                        
                        emit(emitter, "thinking", Map.of("step", node));
                        
                        // 读取节点输出 key（如 query_result, brain_decision 等）
                        state.value(node + "_result").ifPresent(result ->
                            emit(emitter, "content", Map.of("node", node, "result", result))
                        );
                    },
                    err -> streamingEmitter.error(emitter, err.getMessage()),
                    () -> streamingEmitter.complete(emitter)
                );
        } catch (Exception e) { /* ... */ }
    };
    // ...
}
```

**为什么可以直接替换 IntentRouter？**
1. `compiledGraph.stream()` 是**非阻塞、异步**的 Flux，与现有 `CompletableFuture.runAsync` 兼容
2. `NodeOutput` 包含完整的 `OverAllState`，可以读取任何 state key（如 `brain_decision`）
3. 无需改动 SSE 基础设施，`emit()` 方法复用

---

## 3. AtlasBrain 作为 StateGraph Node 的精确注册方式

### 3.1 注册方式：node_async(lambda)

AtlasBrain 本身不是 ReactAgent，不能直接 `asNode()`。正确方式是注册为普通 Node：

```java
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

StateGraph graph = new StateGraph("atlas_orchestrator", keyFactory)
    .addNode("supervisor", node_async((OverAllState state) -> {
        // 1. 从 state 读取输入
        String input = state.value("input").map(Object::toString).orElse("");
        String userId = state.value("user_id").map(Object::toString).orElse("anonymous");
        String token = state.value("token").map(Object::toString).orElse("");
        String sessionId = state.value("session_id").map(Object::toString).orElse(UUID.randomUUID().toString());
        
        // 2. 构建 ExecutionContext
        ExecutionContext ctx = new ExecutionContext(
            sessionId, userId, input,
            List.of(),            // history（Brain暂不使用完整Message历史）
            Map.of("token", token),// env
            sessionId, Instant.now()
        );
        
        // 3. 调用 AtlasBrain（同步调用，因为 NodeAction.apply 返回 Map）
        BrainDecision decision = atlasBrain.decide(ctx);
        
        // 4. 写入 state，返回 update map
        Map<String, Object> updates = new HashMap<>();
        updates.put("brain_decision", decision);
        updates.put("supervisor_result", decision);  // 兼容旧 key
        return updates;
    }));
```

**关键约束**：
- `NodeAction.apply(OverAllState)` 返回 `Map<String, Object>`（state 更新），**不能返回 Flux**
- 这意味着 Brain 节点内部必须是同步调用。如果 Brain 需要流式输出 thinking，应在调用 Brain 之前通过 SSE 推送

### 3.2 条件边路由：BrainDecision → 下游节点

```java
// 条件边：根据 brain_decision.actionType 路由到不同节点
graph.addConditionalEdges("supervisor",
    edge_async((OverAllState state) -> {
        BrainDecision decision = state.value("brain_decision")
            .filter(BrainDecision.class::isInstance)
            .map(BrainDecision.class::cast)
            .orElse(null);
        
        if (decision == null) return "direct_answer";
        
        return switch (decision.actionType()) {
            case DELEGATE_AGENT -> 
                List.of("query","deploy","diag","rbac","storage","network").contains(decision.target())
                    ? decision.target() : "direct_answer";
            case CALL_TOOL -> resolveToolToAgent(decision.target(), toolRegistry);
            case DIRECT_ANSWER, ASK_CLARIFY, HITL_CONFIRM -> "direct_answer";
        };
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
```

**映射说明**：
- `edge_async(...)` 的返回值必须是 mappings 中的 key 之一
- 若返回的 key 不在 mappings 中，`CompiledGraph.nextNodeId()` 会抛 `missingNodeInEdgeMapping` 异常（见 CompiledGraph.java L393-L395）

---

## 4. ReactAgent 与 StateGraph 共存机制

### 4.1 ReactAgent 作为子图节点的注册

```java
// ReactAgent.java L298-L308
public CompiledGraph getCompiledGraph() {
    return compiledGraph;  // 内部已编译
}

@Override
public Node asNode(boolean includeContents, boolean returnReasoningContents) {
    if (this.compiledGraph == null) {
        this.compiledGraph = getAndCompileGraph();
    }
    return new AgentSubGraphNode(this.name, includeContents, returnReasoningContents, this.compiledGraph, this.instruction);
}
```

在 StateGraph 中的注册方式（**已有代码实测通过**）：

```java
// StateGraph.addNode(String id, CompiledGraph subGraph) — StateGraph.java L320-L333
graph.addNode("query", queryAgent.getAndCompileGraph())   // ✅ 将 CompiledGraph 作为子图节点
     .addNode("deploy", deployAgent.getAndCompileGraph())
     // ...
```

`StateGraph.addNode("query", queryAgent.getAndCompileGraph())` 内部创建 `SubCompiledGraphNode`，执行时作为子图运行，**共享父图的 OverAllState**。

### 4.2 ReactAgent 内部循环的源码机制

ReactAgent 的循环**不是图层面的自循环**，而是通过 `model → tool → model` 的条件边链实现：

```java
// ReactAgent.java L714-L725（源码关键边）
private static void setupToolRouting(
        StateGraph graph, String loopExitNode, String loopEntryNode,
        String exitNode, ReactAgent agentInstance) throws GraphStateException {
    // Model → Tools 条件边（如果 model 产出了 tool_calls）
    graph.addConditionalEdges(loopExitNode,
        edge_async(agentInstance.makeModelToTools(loopEntryNode, exitNode)),
        Map.of(AGENT_TOOL_NAME, AGENT_TOOL_NAME, exitNode, exitNode, loopEntryNode, loopEntryNode));
    
    // Tools → Model 条件边（执行完 tool 后回到 model）
    graph.addConditionalEdges(AGENT_TOOL_NAME,
        edge_async(agentInstance.makeToolsToModelEdge(loopEntryNode, exitNode)),
        Map.of(loopEntryNode, loopEntryNode, exitNode, exitNode));
}
```

**循环判断逻辑**（`makeModelToTools` 源码 L762-L832）：
1. 读取 `state.messages` 最后一条消息
2. 如果是 `AssistantMessage` 且 `hasToolCalls()` → 路由到 `AGENT_TOOL_NAME`
3. 如果是 `AssistantMessage` 且无 tool calls → 路由到 `exitNode`
4. 如果是 `ToolResponseMessage` → 检查是否所有请求的工具都已响应，决定回 model 还是 end

### 4.3 对父图的影响

- ReactAgent 子图执行完毕后，其 `outputKey`（如 `"query_result"`）通过子图的 state 合并写回父图 state
- 父图看到的只是 `NodeOutput(node="query", state=...)`，对内部循环完全无感知
- **结论**：ReactAgent 内部循环与 StateGraph DAG 不冲突，可以共存

---

## 5. 最小可行路径：渐进迁移方案

### 5.1 现状分析

- `/chat/stream` → IntentRouter 单步路由（主链路，全量流量）
- `/chat/graph` → CompiledGraph 实验接口（仅测试，Brain感知不完整）
- 109个Tool已注册，`AtlasGraphConfig` 已配置6个专业Agent + Supervisor节点

### 5.2 三阶段迁移

#### Phase 1：Brain 决策 + 直接Tool调用（1周，风险最低）

**目标**：将 `/chat/stream` 的 IntentRouter 替换为 StateGraph，但 Brain 决策 result 为 `CALL_TOOL` 时直接走 ToolRegistry，不进入 ReactAgent。

```
/chat/stream
  → compiledGraph.stream()
    → supervisor 节点 (AtlasBrain.decide)
      → CALL_TOOL → tool_registry 直接执行 → merge_result → emit_sse → END
      → DIRECT_ANSWER → direct_answer → merge_result → emit_sse → END
      → DELEGATE_AGENT → （Phase 1 fallback 到 ToolRegistry直接调用）
```

**需要改动**：
1. `AtlasOrchestrator` 中 `streamChat()` 改为调用 `compiledGraph.stream()`
2. 修改 `AtlasGraphConfig` 中的条件边：`CALL_TOOL` 不路由到 Agent 子图，而是路由到 `tool_registry` 节点
3. 新增 `tool_registry` 节点：从 state 读取 `brain_decision`，直接调用 `BaseTool.execute()`

#### Phase 2：Brain + ReactAgent 子图（1-2周）

**目标**：`DELEGATE_AGENT` 真正路由到 ReactAgent 子图。

```
supervisor 节点
  → DELEGATE_AGENT → query/deploy/diag Agent (ReactAgent 子图) → merge_result → emit_sse → END
  → CALL_TOOL → tool_registry 直接调用 → merge_result → emit_sse → END
  → DIRECT_ANSWER → direct_answer → merge_result → emit_sse → END
```

**需要改动**：
1. Supervisor 条件边映射增加 DELEGATE_AGENT → agent 子图
2. 确保 `AtlasToolCallback` 参数 schema 准确（Agent 内部 tool calling 需要正确的 JSON Schema）
3. 测试 ReactAgent 子图执行后的 state merge（`outputKey` 是否成功写回父图）

#### Phase 3：完全下线 IntentRouter（1周）

- 删除 `IntentRouter` bean 依赖
- 清理 `/chat/graph` 实验接口（已与 `/chat/stream` 合并）
- 日志和 metrics 确认 StateGraph 主链路稳定性

### 5.3 回滚策略

在 `AtlasOrchestrator` 中保留策略开关：

```java
@Component
public class OrchestratorStrategy {
    @Value("${atlas.orchestrator.strategy:graph}")  // "graph" | "intent"
    private String strategy;
    
    public boolean useGraph() { return "graph".equals(strategy); }
}
```

Phase 1-2 期间，通过配置 `atlas.orchestrator.strategy=intent` 可秒级回滚到 IntentRouter。

---

## 6. 风险点与精确应对

### 6.1 风险1：StateGraph 不支持自循环 → Brain 多轮决策如何实现？

**源码确认**：

```java
// CompiledGraph.nextNodeId(String nodeId, Map state, RunnableConfig config) L413-L416
private Command nextNodeId(String nodeId, Map<String, Object> state, RunnableConfig config) throws Exception {
    return nextNodeId(edges.get(nodeId), state, nodeId, config);
}
```

`nextNodeId` 从 `edges.get(nodeId)` 读取目标节点。如果 `sourceId == targetId`，会死循环。

**应对方案**：
- **方案A（推荐）**：Brain 不做循环。每轮决策只执行一次，如果需要"观察结果再决策"，由前端驱动新请求
- **方案B**：如果需要内循环，使用 `LoopAgent`（将子 Agent 放入循环体，通过 `LoopStrategy` 控制终止）
- **方案C**：如果实在需要图级循环，构造 `A → B → A` 的节点间循环，但设置 `CompileConfig.recursionLimit()` 严格兜底

### 6.2 风险2：ReactAgent 内部循环的 maxIterations

```java
// ReactAgent.java L97-L162
// CompileConfig 默认 recursionLimit = 25
// CompiledGraph.java L89: private int maxIterations = 25;
```

**应对**：为复杂 Agent（如 diag）单独配置 recursionLimit：

```java
ReactAgent diagAgent = ReactAgent.builder()
    .name("diag")
    .model(chatModel)
    .tools(toolFactory.buildForAgent("diag"))
    .compileConfig(CompileConfig.builder().recursionLimit(50).build())  // 增加上限
    .build();
```

### 6.3 风险3：子图 state merge 丢失 BrainDecision

```java
// ReactAgent.AgentToSubCompiledGraphNodeAdapter.apply() L1003-L1032
public Map<String, Object> apply(OverAllState parentState, RunnableConfig config) throws Exception {
    Map<String, Object> stateForChild = new HashMap<>(parentState.data());
    // ... 子图执行 ...
    String outputKeyToParent = StringUtils.hasLength(ReactAgent.this.outputKey) 
        ? ReactAgent.this.outputKey : "messages";
    result.put(outputKeyToParent, getGraphResponseFlux(...));
    return result;
}
```

**确认**：子图只写回 `outputKey` 或 `messages`，**不会覆盖 `brain_decision`**。父图 state 中的 `brain_decision` 在子图执行前后保持不变。

### 6.4 风险4：ThreadLocal Token 在 Graph 异步流中丢失

**确认**：`GraphRunner.run(OverAllState)` 使用 Project Reactor Flux，可能在线程池切换中丢失 ThreadLocal。

**应对**：在 `AtlasOrchestrator.streamChat()` 中，在调用 `compiledGraph.stream()` **之前**将 Token 写入 state：

```java
Map<String, Object> inputs = Map.of(
    "input", request.userQuery(),
    "token", capturedToken   // 写入 state，节点内部从 state 读取
);
```

子图内的 Agent 节点如果需要 Token，应在 Agent 的 instruction 或工具参数中从 state 读取，而不是依赖 ThreadLocal。

---

## 7. 代码级行动清单

| # | 行动项 | 文件 | 优先级 | 备注 |
|---|--------|------|--------|------|
| 1 | 修改 `streamChat()` 使用 `compiledGraph.stream()` | `AtlasOrchestrator.java` | P0 | Phase 1 核心 |
| 2 | 完善 `supervisor` Node 中 BrainDecision → state 的写入 | `AtlasGraphConfig.java` | P0 | 确保 `ASK_CLARIFY` / `HITL_CONFIRM` 状态可读 |
| 3 | 新增 `tool_registry` Node（Brain CALL_TOOL 直接执行） | `AtlasGraphConfig.java` | P1 | Phase 1 降低风险 |
| 4 | 测试 ReactAgent 子图在父图中的 `outputKey` merge | `AtlasGraphConfig.java` + 测试 | P1 | 验证 state 传递 |
| 5 | 为 diag Agent 配置更高的 `recursionLimit` | `AtlasGraphConfig.java` | P2 | 防止长诊断链超限 |
| 6 | 配置 `OrchestratorStrategy` 开关 | 新增类 | P1 | 支持回滚 |
| 7 | 删除 `/chat/graph` 实验接口，合并到 `/chat/stream` | `AtlasOrchestrator.java` | P2 | Phase 3 清理 |
| 8 | AtlasToolCallback 补充 inputSchema | `AtlasToolCallback.java` | P2 | 提升 LLM 传参准确率 |

---

## 8. 参考源码索引

| 文件路径 | 关键行号/内容 | 调研结论 |
|---------|--------------|---------|
| `spring-ai-alibaba-graph-core/.../StateGraph.java` | L239-278 `addNode()`; L371-389 `addEdge()`; L420-438 `addConditionalEdges()` | 节点/边/条件边的注册 API |
| `spring-ai-alibaba-graph-core/.../CompiledGraph.java` | L98-143 构造函数; L369-416 `nextNodeId()`; L570-602 `stream()` | DAG 遍历执行，stream 返回 Flux<NodeOutput> |
| `spring-ai-alibaba-graph-core/.../GraphRunner.java` | L48-58 `run()` | Flux.defer 包装 MainGraphExecutor |
| `spring-ai-alibaba-graph-core/.../MainGraphExecutor.java` | L57-116 `execute()`; L123-140 `handleStartNode()` | 递归执行节点，START/END 特殊处理 |
| `spring-ai-alibaba-graph-core/.../GraphRunnerContext.java` | L65 `AtomicInteger iteration`; L157 `isMaxIterationsReached()` | 迭代计数上限检查 |
| `spring-ai-alibaba-agent-framework/.../ReactAgent.java` | L311-406 `initGraph()`; L714-725 `setupToolRouting()`; L762-832 `makeModelToTools()` | 内部 ReAct 循环通过条件边 model↔tool 实现 |
| `spring-ai-alibaba-agent-framework/.../LoopAgent.java` | L58-73 `LoopAgent` | 图层面循环由 LoopStrategy 控制，图内无循环边 |
| `kube-agent/.../AtlasGraphConfig.java` | L182-322 `atlasGraph()` | 现有 StateGraph + ReactAgent 配置参考 |
| `kube-agent/.../AtlasOrchestrator.java` | L244-374 `streamChatGraph()` | 现有 Graph 流式订阅参考 |
