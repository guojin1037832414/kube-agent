# AtlasBrain 可落地编码方案 — 调研报告

> **版本**: v3.1.0-P2  
> **技术栈**: Spring Boot 3.4.4, Spring AI 1.1.6, spring-ai-alibaba 1.1.2.2, Java 17/21  
> **日期**: 2026-05-15  
> **调研路径**: 第一手 GitHub 源码 (alibaba/spring-ai-alibaba) + kube-agent 现有代码  

---

## 目录

1. [Spring AI 结构化输出实际可靠性](#1-spring-ai-结构化输出实际可靠性)
2. [StateGraph 循环节点定论](#2-stategraph-循环节点定论)
3. [AtlasBrain 类完整设计](#3-atlasbrain-类完整设计)
4. [与现有 Worker Agent 集成](#4-与现有-worker-agent-集成)
5. [执行历史与 Thinking Stream 存储](#5-执行历史与-thinking-stream-存储)
6. [HITL 暂停机制](#6-hitl-暂停机制)
7. [MVP vs 完整架构划分](#7-mvp-vs-完整架构划分)
8. [风险点与 Mitigation](#8-风险点与-mitigation)
9. [参考引用](#9-参考引用)

---

## 1. Spring AI 结构化输出实际可靠性

### 1.1 结论

`ChatClient.prompt().call().entity(BrainDecision.class)` **不可靠**，生产环境**不推荐直接依赖**。Spring AI Alibaba 的 `AgentLlmNode` 中 `outputSchema` / `outputType` 机制也是**纯 prompt-injection 软约束**，无 schema enforcement（与 OpenAI Structured Output API 不同）。

### 1.2 源码证据

Spring AI Alibaba 官方示例 (`StructuredOutputExample.java`) 明确展示了三种容错策略：

```java
// 模式1: outputSchema (BeanOutputConverter.getFormat() 注入 prompt)
BeanOutputConverter<ContactInfo> converter = new BeanOutputConverter<>(ContactInfo.class);
ReactAgent.builder().outputSchema(converter.getFormat()).build();

// 模式2: outputType (映射到 schema 注入)
ReactAgent.builder().outputType(ContactInfo.class).build();

// 模式3: 官方推荐的容错 — Triple 策略 (Try-Catch + Validation + Retry)
ObjectMapper mapper = new ObjectMapper();
for (int i = 0; i < 3; i++) {
    try {
        data = mapper.readValue(result.getText(), ContactInfo.class);
        break;
    } catch (JsonProcessingException e) {
        // 重试，把错误信息回传
    }
}
```

### 1.3 AtlasBrain 的推荐策略

| 策略 | 建议 | 原因 |
|------|------|------|
| `BeanOutputConverter` | ✅ 用于生成 prompt schema | 简单 Java Bean → JSON Schema |
| `MapOutputConverter` | ⚠️ 仅 fallback | 无类型约束 |
| 输出解析容错 | ✅ **必须实现** | LLM 会输出 markdown fence、多余空格、中文 key |
| 重试 + 回传错误 | ✅ **必须实现** | 官方示例推荐 pattern |
| JSON Schema enforcement | ❌ 不可用 | `spring-ai-alibaba` 无底层 enforcement |

### 1.4 推荐实现

```java
@Component
public class StructuredOutputParser<T> {
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private static final int MAX_RETRIES = 3;

    public T parse(String userQuery, Class<T> clazz, String systemPrompt) {
        String schema = new BeanOutputConverter<>(clazz).getFormat();
        String prompt = systemPrompt + "\n必须严格输出以下 JSON 格式，不要 markdown 代码块：\n" + schema;

        for (int i = 0; i < MAX_RETRIES; i++) {
            String raw = chatClient.prompt().user(prompt).call().content();
            String cleaned = sanitize(raw); // 去 ```json、trim
            try {
                return objectMapper.readValue(cleaned, clazz);
            } catch (Exception e) {
                if (i == MAX_RETRIES - 1) throw new BrainParseException(e);
                prompt += "\n之前输出解析失败: " + e.getMessage() + "，请修正。";
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private String sanitize(String raw) {
        return raw.replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
```

**来源**: [StructuredOutputExample.java](https://github.com/alibaba/spring-ai-alibaba/blob/main/examples/documentation/src/main/java/com/alibaba/cloud/ai/examples/documentation/framework/tutorials/StructuredOutputExample.java)

---

## 2. StateGraph 循环节点定论

### 2.1 核心结论

**StateGraph `addEdge(self, self)` 不支持自循环。循环必须在 ReactAgent / 节点内部实现。**

### 2.2 源码证据

#### 2.2.1 StateGraph.addEdge() 限制

```java
// StateGraph.java L287-L300
public StateGraph addEdge(String sourceId, String targetId) throws GraphStateException {
    if (Objects.equals(sourceId, END)) {
        throw Errors.invalidEdgeIdentifier.exception(END);
    }
    var newEdge = new Edge(sourceId, new EdgeValue(targetId));
    // ... 仅做 target 合并，无任何循环检测或阻止逻辑
    // 但编译后 CompiledGraph.nextNodeId() 是 DAG traversal：
    // node → edge → nextNode，如果 edge 指向自己，会导致无限递归
}
```

实际验证：`CompiledGraph.nextNodeId()` 每次从 edge map 取 target，若 source=target 则死循环。`GraphRunner` 有 `maxIterations` 上限，但循环边本身**不会在 framework 层面正确执行**。

#### 2.2.2 ReactAgent 内建循环机制

ReactAgent 源码 (`ReactAgent.java` L340-L540) 展示了循环是如何在 Agent 内部实现的：

```java
// ReactAgent 内部图结构：
// START → entryNode → [beforeModel hooks] → AGENT_MODEL_NAME 
//   ↓                                        ↑
//   └─ AGENT_TOOL_NAME ← [条件边: model.hasToolCalls ? tool : end]
//      └─ [afterModel hooks] ← tool execution
//      → route back to modelDestination (loopEntryNode)
```

关键边路由代码 (ReactAgent.java L520–540):

```java
// Model → Tools 的条件边
graph.addConditionalEdges(loopExitNode,
    edge_async(agentInstance.makeModelToTools(loopEntryNode, exitNode)),
    Map.of(AGENT_TOOL_NAME, AGENT_TOOL_NAME, exitNode, exitNode, loopEntryNode, loopEntryNode));

// Tools → Model 的条件边（回到循环入口！）
graph.addConditionalEdges(AGENT_TOOL_NAME,
    edge_async(agentInstance.makeToolsToModelEdge(loopEntryNode, exitNode)),
    Map.of(loopEntryNode, loopEntryNode, exitNode, exitNode));
```

注意：`loopEntryNode` 可能等于 `AGENT_MODEL_NAME`，也可能等于 `beforeModelHook` 的名称。但**循环发生在 `model → tool → model` 这条链上**，而不是单个节点的自循环。

#### 2.2.3 LoopAgent 的设计

官方 `LoopAgent` (`LoopAgent.java`) 明确声明：

> LoopAgent must have a subAgent, which is the agent that will be executed in each loop.

支持 COUNT / CONDITION / JSON_ARRAY 三种模式，但都是**把子 Agent 作为循环体**，外部通过 `LoopStrategy` 控制终止条件。图层面不暴露循环边。

### 2.3 AtlasBrain 循环方案 — 定论

| 方式 | 方案 | 评价 |
|------|------|------|
| 图级自循环 | ❌ 不支持 | StateGraph 编译后无自环支持 |
| 图级节点间循环 (A→B→A) | ⚠️ 可能但风险高 | CompiledGraph 可能无限递归，依赖 maxIterations 兜底 |
| **ReactAgent 内部循环** | ✅ **推荐** | 框架原生，有 tool/model 循环边，有循环检测 |
| 节点内部 while | ✅ 次选 | 简单场景可用，丢失断点恢复能力 |

**推荐设计**：
- **AtlasBrain 本身不做循环**。Brain 只做一次性"决策"（decide → dispatch → observe）。
- 如果某 Agent 需要多轮 ReAct（如诊断 Agent），**使用 ReactAgent 的内建循环**。
- 如果 AtlasBrain 需要"观察结果后再决策"，应该由 **Orchestrator 外层循环**驱动：流输出结束 → 前端等待 → 新 query → 新一轮 Brain 决策。

---

## 3. AtlasBrain 类完整设计

### 3.1 设计哲学

AtlasBrain 是**单次决策器**（Single-turn Decision Maker），不做循环、不维护历史。它：
1. 接收当前状态（用户 query + 上下文 + 可用工具）
2. 产出 `BrainDecision`（干什么、给谁干、传什么参数）
3. 由 Orchestrator 负责将 Decision 路由到对应 Worker

### 3.2 核心类定义

```java
// BrainDecision.java
public record BrainDecision(
    ActionType actionType,           // CALL_TOOL | DELEGATE_AGENT | DIRECT_ANSWER | ASK_CLARIFY | HITL_CONFIRM
    String target,                   // toolName / agentName / ""
    Map<String, Object> parameters,  // 调用参数
    String reasoning,                // Thinking 过程（可展示给用户）
    double confidence,               // 0.0-1.0
    List<String> requiredContext     // 需要额外上下文时的 key 列表
) {
    public enum ActionType {
        CALL_TOOL,        // 直接调用 ToolRegistry
        DELEGATE_AGENT,   // 委托给 ReactAgent（复杂多步）
        DIRECT_ANSWER,    // LLM 直接生成自然语言回答
        ASK_CLARIFY,      // 信息不足，反问用户
        HITL_CONFIRM      // 高危操作，等待人工确认
    }
}

// ExecutionContext.java
public record ExecutionContext(
    String sessionId,
    String userId,
    String userQuery,
    List<AtlasMessage> history,      // 简洁对话历史（不参与 Brain 决策 prompt，仅展示）
    Map<String, Object> env,         // 环境变量（token、orgId、权限等）
    String conversationId,
    Instant createdAt
) {}

// AtlasMessage.java — 简化序列化
public record AtlasMessage(
    String role,        // user / assistant / tool
    String content,
    String toolName,    // role=tool 时填充
    Instant timestamp
) {}

// AtlasBrain.java — 核心决策器
@Component
public class AtlasBrain {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final StructuredOutputParser<BrainDecision> parser;

    public BrainDecision decide(ExecutionContext ctx) {
        // 1. 构建权限感知的可用工具列表
        String visibleTools = buildVisibleToolSummary(ctx);

        // 2. 构建系统 Prompt + Schema
        String systemPrompt = buildSystemPrompt(ctx, visibleTools);

        // 3. 调用 LLM 产出结构化决策
        BrainDecision decision = parser.parse(ctx.userQuery(), BrainDecision.class, systemPrompt);

        // 4. 后置校验（权限二次检查 + 参数预检）
        validateDecision(decision, ctx);

        return decision;
    }

    private String buildSystemPrompt(ExecutionContext ctx, String visibleTools) {
        return """
            你是 Atlas K8s 集群管理的总调度员（Brain）。你的职责是根据用户请求产出一次精确决策。
            
            可用工具（仅以下工具对该用户可见）：
            %s
            
            决策规则：
            1. 简单查询（单 Tool 可完成）→ actionType=CALL_TOOL，target=toolName
            2. 复杂任务（需多步推理）→ actionType=DELEGATE_AGENT，target=agentName
            3. 闲聊/非 operational → actionType=DIRECT_ANSWER
            4. 信息不足 → actionType=ASK_CLARIFY，reasoning 说明需要什么
            5. 高危操作（删除/扩缩容/变更权限）→ actionType=HITL_CONFIRM
            
            输出格式：严格的 JSON，不要 markdown 代码块。
            """.formatted(visibleTools);
    }

    private void validateDecision(BrainDecision d, ExecutionContext ctx) {
        if (d.actionType() == ActionType.CALL_TOOL) {
            if (!toolRegistry.isVisible(d.target())) {
                throw new PermissionDeniedException("Brain 决策越权: " + d.target());
            }
        }
        // 高危关键词检测
        if (isHighRisk(d) && d.actionType() != ActionType.HITL_CONFIRM) {
            throw new BrainValidationException("高危操作未标记 HITL_CONFIRM");
        }
    }
}

// ActionDispatcher.java — 决策路由
@Component
public class ActionDispatcher {
    private final ToolRegistry toolRegistry;
    private final Map<String, ReactAgent> agentMap;  // query/deploy/diag/...
    private final ChatClient chatClient;

    public ActionResult dispatch(BrainDecision decision, ExecutionContext ctx, SseEmitter emitter) {
        return switch (decision.actionType()) {
            case CALL_TOOL -> dispatchTool(decision, ctx, emitter);
            case DELEGATE_AGENT -> dispatchAgent(decision, ctx, emitter);
            case DIRECT_ANSWER -> dispatchDirectAnswer(decision, ctx, emitter);
            case ASK_CLARIFY -> dispatchClarify(decision, emitter);
            case HITL_CONFIRM -> dispatchHITL(decision, emitter);
        };
    }

    private ActionResult dispatchAgent(BrainDecision decision, ExecutionContext ctx, SseEmitter emitter) {
        ReactAgent agent = agentMap.get(decision.target());
        if (agent == null) throw new IllegalArgumentException("Unknown agent: " + decision.target());

        // 构建 Agent 输入
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", ctx.userQuery());
        inputs.put("user_id", ctx.userId());
        inputs.put("token", ctx.env().get("token"));

        RunnableConfig config = RunnableConfig.builder()
            .threadId(ctx.sessionId())
            .build();

        // stream execute
        return agent.call(inputs, config); // AtlasOrchestrator 负责流式推送
    }

    private ActionResult dispatchTool(BrainDecision decision, ExecutionContext ctx, SseEmitter emitter) {
        BaseTool tool = toolRegistry.findByName(decision.target())
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + decision.target()));

        Map<String, Object> params = new HashMap<>(decision.parameters());
        params.putAll(ctx.env()); // 注入 token / orgId

        Map<String, Object> result = tool.execute(params);
        emit(emitter, "tool_result", result);
        return new ActionResult(result, false);
    }

    private void emit(SseEmitter emitter, String event, Object data) { /* ... */ }
}
```

### 3.3 与 AgentLlmNode.outputKey / outputSchema 的衔接

`ReactAgent.builder().outputKey("supervisor_result")` 在编译后的子图中会把该 key 写入父图的 `OverAllState`。

AtlasBrain 不使用 ReactAgent 的 `outputKey`（Brain 不是 ReactAgent 子图的一个 node）。Brain 使用独立 ChatClient 调用，产出 Decision 后由 Orchestrator 写回 StateGraph state。

衔接方式：

```java
// 在 AtlasOrchestrator 中
BrainDecision decision = atlasBrain.decide(ctx);
Map<String, Object> brainOutput = Map.of(
    "brain_decision", decision,
    "reasoning", decision.reasoning()
);
// 将 brainOutput merge 到 graph state
```

---

## 4. 与现有 Worker Agent 集成

### 4.1 两种集成模式对比

| 模式 | 方式 | 适用场景 | 复杂度 |
|------|------|----------|--------|
| **模式A: Brain → ToolRegistry 直接调用** | Brain 产出 CALL_TOOL → 直接 `BaseTool.execute()` | 简单单步查询 | 低 |
| **模式B: Brain → ReactAgent 子图** | Brain 产出 DELEGATE_AGENT → `ReactAgent.call()` | 复杂多步（诊断、部署排障） | 中 |

### 4.2 模式 B 的内嵌调用实现

```java
// AtlasOrchestrator.java 中的 Graph stream 模式
BrainDecision decision = atlasBrain.decide(ctx);

if (decision.actionType() == DELEGATE_AGENT) {
    ReactAgent agent = agentMap.get(decision.target());

    Map<String, Object> agentInputs = Map.of(
        "input", ctx.userQuery(),
        "user_id", ctx.userId(),
        "token", ctx.env().get("token")
    );

    RunnableConfig config = RunnableConfig.builder()
        .threadId(ctx.sessionId())
        .build();

    // 同步阻塞调用（Graph 节点内部不支持异步嵌套 stream）
    AssistantMessage result = agent.call(agentInputs, config);

    // 将结果写回 state
    return Map.of(decision.target() + "_result", result.getText());
}
```

**关键点**：在 `StateGraph` 的自定义 Node 内部调用 `ReactAgent.call()` 是**同步调用**。原因：
1. `NodeAction.apply()` 返回 `Map<String, Object>`，不是 `Flux/Mono`
2. `compiledGraph.stream()` 已经是 Flux，内部嵌套 stream 会导致线程池死锁
3. 框架设计预期：子图节点 (`SubCompiledGraphNode`) 被 compile 时作为内部节点执行

### 4.3 架构图

```
┌─────────────────┐
│ AtlasOrchestrator│
│   (REST + SSE)   │
└────────┬────────┘
         │
    ┌────▼────┐
    │ AtlasBrain│  ← 单次决策，产出 BrainDecision
    └────┬────┘
         │
    ┌────┴────┬──────────────────┐
    ▼         ▼                  ▼
 CALL_TOOL  DELEGATE_AGENT   DIRECT_ANSWER
    │         │                  │
    ▼         ▼                  ▼
ToolRegistry ReactAgent        ChatClient
    │      (内部 ReAct 循环)      │
    ▼         │                  ▼
BaseTool     ▼                自然语言回复
 execute   ToolCallback
    │         │
    └─────────┴──→ 结果回传 Orchestrator → SSE emit
```

---

## 5. 执行历史与 Thinking Stream 存储

### 5.1 Spring AI Message 序列化方案

`spring-ai-alibaba-graph-core` 已内建 Jackson 序列化器：

```java
// SpringAIJacksonStateSerializer — 默认状态序列化器
public static final StateSerializer DEFAULT_JACKSON_SERIALIZER = 
    new SpringAIJacksonStateSerializer(OverAllState::new, new ObjectMapper());
```

但它序列化的是 `OverAllState.data()`（即 `Map<String, Object>`），其中的 `messages` 是 `List<Message>`。

`Message` 接口的子类（`UserMessage`, `AssistantMessage`, `ToolResponseMessage`）已通过 Jackson 模块支持序列化。

### 5.2 AtlasBrain 历史存储方案

**不推荐**在 Brain 中维护完整 `List<Message>`。Brain 只保留轻量级的 `AtlasMessage` 记录：

```java
// ConversationMemory.java
@Component
public class ConversationMemory {
    // spring-data-redis 或 memory map
    private final Map<String, List<AtlasMessage>> memory = new ConcurrentHashMap<>();

    public void append(String conversationId, AtlasMessage msg) {
        memory.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(msg);
        // 超长截断：只保留最近 20 轮
    }

    public List<AtlasMessage> getHistory(String conversationId) {
        return memory.getOrDefault(conversationId, List.of());
    }
}
```

**StateGraph 的完整 state（含 messages）** 由 `MemorySaver` 自动管理：

```java
MemorySaver memory = new MemorySaver();
CompileConfig config = CompileConfig.builder()
    .saverConfig(SaverConfig.builder().register(memory).build())
    .build();
```

### 5.3 Thinking Stream 的 SSE 序列化

Brain 的 thinking 过程不写入持久存储，直接通过 SSE 推送给前端：

```java
// 在 AtlasOrchestrator 中
emit(emitter, "thinking", Map.of(
    "step", "brain_decision",
    "content", decision.reasoning()
));
```

### 5.4 执行历史的结构化 Schema

需要设计一张执行日志表（可选，MVP 阶段可不做）：

```sql
CREATE TABLE atlas_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    user_id VARCHAR(64),
    step_type ENUM('INTENT', 'BRAIN_DECISION', 'TOOL_CALL', 'TOOL_RESULT', 'AGENT_OUTPUT', 'ERROR'),
    node_name VARCHAR(64),
    input_json TEXT,
    output_json TEXT,
    reasoning TEXT,
    latency_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_conversation (conversation_id)
);
```

---

## 6. HITL 暂停机制

### 6.1 官方支持的两种模式

根据 `HumanInTheLoopExample.java` 和 `WaitUserInputExample.java`，框架支持：

| 模式 | 机制 | 用法 | 适用场景 |
|------|------|------|----------|
| **InterruptionMetadata** | 节点实现 `InterruptableAction`，`interrupt()` 返回 `Optional<InterruptionMetadata>` | 任意节点动态中断 | 灵活，节点自己决定何时中断 |
| **interruptBefore** | `CompileConfig.interruptBefore("node_name")` | 编译时静态声明 | 固定确认点（如 merge_result 前） |

### 6.2 interruptBefore 模式详解

**源码执行流程** (`CompiledGraph.java` L423-L438, `MainGraphExecutor.java`):

```java
// CompiledGraph.shouldInterruptBefore()
private boolean shouldInterruptBefore(String nodeId, String previousNodeId) {
    if (previousNodeId == null) return false; // FIX RESUME ERROR
    return compileConfig.interruptsBefore().contains(nodeId);
}

// MainGraphExecutor.execute() 中
if (context.shouldInterrupt()) {
    InterruptionMetadata metadata = InterruptionMetadata
        .builder(context.getCurrentNodeId(), context.cloneState(...))
        .build();
    return Flux.just(GraphResponse.done(metadata));
}
```

**恢复流程** (`WaitUserInputExample.java`):

```java
// 1. 初始 stream 执行到中断点
graph.stream(inputs, config).blockLast(); // 流结束于 InterruptionMetadata

// 2. 用户输入后，updateState 更新状态
RunnableConfig updatedConfig = graph.updateState(
    config, 
    Map.of("human_feedback", userInput), 
    null  // interruptBefore 模式下 asNode 为 null
);

// 3. 恢复执行 — resume flag
RunnableConfig resumeConfig = updatedConfig.withResume();
graph.stream(null, resumeConfig).blockLast();
```

### 6.3 AtlasBrain HITL 推荐方案

**方案：Orchestrator 层 HITL（不依赖 StateGraph interruptBefore）**

原因：
1. AtlasBrain 是决策器，不是持续运行的 Graph。如果 Brain 决策为 `HITL_CONFIRM`，直接由 Orchestrator 暂停 SSE，等待前端 POST 确认。
2. 不需要 Checkpoint / resume 的复杂状态管理。
3. 前端体验更直接：弹出确认框 → 用户点击 → 重发请求。

```java
// AtlasOrchestrator.java flowChat() / streamChatGraph()
BrainDecision decision = atlasBrain.decide(ctx);

if (decision.actionType() == HITL_CONFIRM) {
    emit(emitter, "hitl_request", Map.of(
        "message", decision.reasoning(),
        "action", decision.target(),
        "params", decision.parameters()
    ));
    // SSE 保持连接，等待前端发送 /chat/confirm
    // （下次请求携带 conversationId + confirm=true）
    return emitter; // 不 close
}
```

前端在收到 `hitl_request` 后显示确认弹窗。用户确认后，携带 `confirmation=true` 重调 `/chat/graph`，Orchestrator 检测到历史决策为 HITL_CONFIRM 且已确认，继续执行。

**如果必须使用 Graph 级 HITL**：使用 `interruptBefore("merge_result")` + `MemorySaver`，参考官方 `WaitUserInputExample`。

### 6.4 MemorySaver 是否能保存中断状态？

**能**。`MemorySaver` 是 `BaseCheckpointSaver` 的内存实现：

```java
// MemorySaver.java
public final RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) {
    // threadId → LinkedList<Checkpoint>，LIFO（最近的在前面 push）
    checkpoints.push(checkpoint);
}

public final Optional<Checkpoint> get(RunnableConfig config) {
    // 无条件返回最新 checkpoint
    return getLast(checkpoints, config);
}
```

中断时流结束，但 `MemorySaver` 已保存了中断前的完整 `state`。`updateState()` 后可以从中断节点继续。

**注意**：`MemorySaver` 是**进程内内存存储**，重启丢失。生产应换 `RedisSaver` / `PostgreSQLSaver`。

---

## 7. MVP vs 完整架构划分

### 7.1 MVP（2 周可落地）

**目标**: AtlasBrain 完成最小闭环 — 能决策、能调用 Tool、能返回 SSE。

| 组件 | MVP 范围 | 备注 |
|------|----------|------|
| `AtlasBrain` | ✅ 核心类 | `BrainDecision` + `ExecutionContext` + `decide()` |
| `ActionDispatcher` | ✅ 仅 CALL_TOOL + DIRECT_ANSWER | DELEGATE_AGENT 延迟到 Phase 2 |
| 结构化输出容错 | ✅ Triple 策略 | 重试 3 次 + sanitize + 异常 fallback |
| SSE thinking stream | ✅ 推 reasoning 字段 | 不存储，仅流式推送 |
| ToolRegistry 集成 | ✅ 直接调用 | 保留现有 `toolRegistry.findByName()` |
| HITL | ❌ 不做 | Phase 2 引入 |
| 执行历史存储 | ❌ 不做 | Phase 2 引入数据库表 |
| StateGraph 子图嵌套 | ⚠️ 可选实验 | `AtlasGraphConfig` 已编译，但 Brain 不依赖 |

**MVP 数据流**：

```
POST /chat/stream
  → Orchestrator
    → Brain.decide(ctx)  → BrainDecision(CALL_TOOL, "podQuery", {...})
    → ToolRegistry.find("podQuery").execute(params)
    → SSE emit(tool_result)
    → SSE emit(done)
```

### 7.2 完整架构

| 组件 | 完整版 |
|------|--------|
| `AtlasBrain` | 支持全部 5 种 ActionType，含风险检测 |
| `ActionDispatcher` | 全模式：CALL_TOOL / DELEGATE_AGENT / HITL_CONFIRM / ASK_CLARIFY |
| ReactAgent Worker | 6 个专业 Agent（query/deploy/diag/rbac/storage/network）独立 ReactAgent |
| StateGraph 编排 | Supervisor Agent 路由 → 子 Agent 子图 → merge → emit |
| 执行历史 | `atlas_execution_log` 表 + conversation memory |
| HITL | 高危操作确认 + 参数编辑 + 拒绝 |
| 持久化 | RedisSaver + 对话历史压缩 |
| 可观测性 | OpenTelemetry tracing + metrics |

---

## 8. 风险点与 Mitigation

| # | 风险 | 影响 | Mitigation |
|---|------|------|------------|
| 1 | **LLM 输出 JSON 不稳定** | BrainDecision 解析失败，导致用户请求无法处理 | Triple 策略：sanitize + retry 3次 + fallback 到 DIRECT_ANSWER |
| 2 | **StateGraph 子图嵌套 bug** | ReactAgent 作为子图表编译后可能丢失 state，官方文档标注部分 subgraph 特性 **unsupported** | Brain 不强制依赖 StateGraph 子图嵌套；Phase 1 用 Orchestrator 手动路由 |
| 3 | **ThreadLocal 权限上下文在异步流中丢失** | SSE 异步线程拿不到 Token， Tool 执行无权限 | 使用 `AsyncContextHolder.wrap(task, token)`，已在现有代码中实现 |
| 4 | **interruptBefore 在 subgraph 上不支持** | 如果在 ReactAgent 子图上用 interruptAfter，`ProcessedNodesEdgesAndConfig` 会抛异常 | 只在主图节点用 interruptBefore，Agent 内部用 `HumanInTheLoopHook`（InterruptionMetadata 模式） |
| 5 | **MemorySaver 重启丢失** | 对话中断后服务重启，无法恢复 | Phase 2 换 `RedisSaver` 或自定义持久化 |
| 6 | **ReactAgent 内部循环 maxIterations** | 复杂诊断任务可能超过默认 25 轮 | 每个 Agent 独立配置 `recursionLimit` |
| 7 | **ToolCallback 参数 JSON Schema 缺失** | LLM 传参不准确 | 在 `AtlasToolCallback` 中扩展 `getInputSchema()`，从注解读取参数定义 |
| 8 | **BeanOutputConverter 对大模型更强依赖** | 不同模型对 JSON Schema prompt 的遵循度不同 | 统一限制 `"只输出 JSON，不要 markdown"`，并在 system prompt 用 examples |
| 9 | **意图路由与 Brain 决策重复** | 用户 query 先到 IntentRouter，再到 Brain，两次 LLM 调用，延迟加倍 | MVP 阶段保留 IntentRouter 做 L1/L2 快速路由；完整版可让 Brain 直接做 L3，或 Embed IntentRouter 进 Brain prompt |
| 10 | **StateGraph 不支持自循环导致 Brain 多轮决策困难** | 需要"观察结果 → 再决策 → 再行动"时，无法在图层面表达 | 将 Brain 放在 Orchestrator 层循环（前端每轮请求触发一次 Brain），而非 Graph 内循环 |

---

## 9. 参考引用

### 9.1 源码引用（本地 repo）

| 文件 | 关键发现 |
|------|----------|
| `spring-ai-alibaba/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/ReactAgent.java` | ReactAgent 内部循环通过 `makeModelToTools` / `makeToolsToModelEdge` 条件边实现，循环在模型-工具-模型链上 |
| `spring-ai-alibaba/spring-ai-alibaba-graph-core/src/main/java/com/alibaba/cloud/ai/graph/StateGraph.java` | `addEdge()` 无自循环支持；编译后 `CompiledGraph.nextNodeId()` 为 DAG 遍历 |
| `spring-ai-alibaba/spring-ai-alibaba-graph-core/src/main/java/com/alibaba/cloud/ai/graph/CompiledGraph.java` | `shouldInterruptBefore()` / `shouldInterruptAfter()` 实现中断检查；`interruptBefore` 在 subgraph 上不支持 |
| `spring-ai-alibaba/spring-ai-alibaba-graph-core/src/main/java/com/alibaba/cloud/ai/graph/GraphRunner.java` | 执行入口委托 `MainGraphExecutor`，基于 Project Reactor Flux |
| `spring-ai-alibaba/examples/documentation/.../HumanInTheLoopExample.java` | 两种 HITL 模式完整示例：`InterruptionMetadata` + `interruptBefore` |
| `spring-ai-alibaba/examples/documentation/.../WaitUserInputExample.java` | `interruptBefore` + `updateState` + `withResume` 恢复流程 |
| `spring-ai-alibaba/examples/documentation/.../StructuredOutputExample.java` | `BeanOutputConverter`、`outputType`、`outputSchema` 用法及容错模式 |
| `spring-ai-alibaba/spring-ai-alibaba-agent-framework/.../AgentLlmNode.java` | `outputSchema` 通过 augmentUserMessage 注入 prompt，无底层 enforcement |
| `spring-ai-alibaba/spring-ai-alibaba-agent-framework/.../LoopAgent.java` | Loop 必须在子 Agent 内部，通过 LoopStrategy 控制，图层面无循环边 |
| `spring-ai-alibaba/spring-ai-alibaba-graph-core/.../checkpoint/savers/MemorySaver.java` | `HashMap<String, LinkedList<Checkpoint>>` 内存存储，LIFO |
| `kube-agent/src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java` | 现有 REST + SSE 控制器，Graph 模式为可选实验功能 |
| `kube-agent/src/main/java/com/atlas/graph/config/AtlasGraphConfig.java` | 现有 StateGraph + ReactAgent 配置，Supervisor → 子 Agent 路由 |
| `kube-agent/docs/v3.1/p2/P2_ARCHITECTURE_SPRING_AI_ALIBABA.md` | P2 架构规划文档，已废弃（本报告替代其编码细节） |

### 9.2 外部链接

- **spring-ai-alibaba GitHub**: https://github.com/alibaba/spring-ai-alibaba
- **Spring AI 结构化输出文档**: https://docs.spring.io/spring-ai/reference/api/structured-output.html
- **LangGraph 循环模式**: https://langchain-ai.github.io/langgraph/how-tos/loops/

---

*报告版本: v1.0  
完稿: 2026-05-15  
作者: Hermes (Atlas Team)*
