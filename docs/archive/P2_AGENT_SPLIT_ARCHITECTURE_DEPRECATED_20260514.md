# Atlas v3.1 P2 Agent拆分 + ReAct引擎 架构方案

> 基于 LangGraph + AutoGen v1 源码分析的一手调研结论
> 调研时间: 2026-05-14
> 数据来源: GitHub 真实源码（curl + 代理穿透）

---

## 一、选型结论

| 框架 | 核心模式 | 是否采用 | 理由 |
|------|----------|----------|------|
| **LangGraph** (1964行 StateGraph) | StateGraph 图编排 + Pregel 消息传递 | ✅ **核心采用** | 状态机驱动最适合 SSE 流式 + HITL 中断恢复 |
| **AutoGen v1** (834行 GroupChat) | BaseChatAgent + GroupChat 轮询 | ✅ **接口借鉴** | Agent 契约设计优秀 (`on_messages`, `run_stream`) |
| CrewAI | Agent + Task + Crew 角色驱动 | ❌ 不采用 | 角色模型太重，不适合 Spring AI 环境 |

**最终策略**: **LangGraph 图编排引擎**（核心架构）+ **AutoGen Agent 接口契约**（Agent 基类设计）

---

## 二、核心架构设计

### 2.1 总体架构（类比 LangGraph StateGraph + Spring AI）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AtlasOrchestrator (SSE入口)                          │
│  ┌─ 接收 ChatRequest                                                        │
│  ├─ 创建 AgentSession (状态上下文)                                          │
│  ├─ AgentGraph.compile().invoke(sessionState)                               │
│  └─ 每步 emit SSE event (thinking/tool_call/tool_result/done)               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AgentGraph (图定义层)                              │
│  ┌─ addNode("query", queryAgent)     ← Agent = 图中的节点                    │
│  ├─ addNode("deploy", deployAgent)                                          │
│  ├─ addEdge(START, "query")          ← 顺序边                               │
│  ├─ addConditionalEdge("query", condition)  ← 条件路由（如部署意图→deploy） │
│  └─ compile() → CompiledGraph        ← 生成可执行图                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CompiledGraph (执行引擎)                              │
│  ┌─ Pregel-like 消息循环（单线程顺序执行，非真并行）                          │
│  ├─ 每轮: 当前节点.run(state) → 更新 state → 路由到下一节点                   │
│  ├─ 状态持久化: Checkpoint (支持恢复/回滚)                                   │
│  └─ 终止条件: max_steps / HITL中断 / 任务完成                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ReActEngine (推理循环)                               │
│  ┌─ State Machine:                                                          │
│  │   WAITING → THINKING → TOOL_CALLING → OBSERVING → [循环/完成/中断]       │
│  ├─ 每步状态变更 → emit SSE event                                           │
│  ├─ Tool 失败 → RetryPolicy (重试N次) → 备选Tool → HITL                     │
│  └─ 循环检测: 相同(state, action) 连续出现则终止                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 关键设计决策

#### 决策1: 单线程顺序执行（非 LangGraph 真并行）

**理由**: 
- Spring AI `ChatClient` 是阻塞式 sync API
- Agent 推理需要 LLM 调用，LLM 调用是串行的
- 本场景不需要多 Agent 同时推理（Kubernetes 操作是顺序的）

**实现**: 用 `CompletableFuture` 链式调用模拟图的顺序执行，每步 `thenCompose` 到下一步。

#### 决策2: 状态 = TypedDict（Java 用 Record/Class）

```java
public record AgentState(
    String conversationId,           // 会话ID
    String userQuery,                // 原始用户输入
    List<Message> messages,          // 对话历史 (System + User + AI + Tool)
    String currentAgent,             // 当前执行的 Agent
    Map<String, Object> toolResult,  // 最近一次 Tool 结果
    int stepCount,                   // 已执行步数
    boolean isComplete,              // 是否完成
    boolean requiresHITL,            // 是否需要人工确认
    String hitlReason                // HITL原因
) {}
```

**理由**: 
- LangGraph 的 StateT 是图的核心，所有节点读写共享状态
- Record 不可变保证状态变更可追溯（便于调试）
- `messages` 列表按 OpenAI 消息格式存储，兼容 ChatClient

#### 决策3: Agent 即图中的节点（Node = Agent.run(state)）

```java
// AgentNode = 图中的执行单元
public interface AgentNode {
    String getName();                          // 节点名 = Agent类型
    AgentState invoke(AgentState state);       // 执行 + 返回新状态
    boolean canHandle(IntentResult intent);    // 是否能处理该意图
}
```

**理由**: 
- 当前 AtlasAgentBase 已经有 `executeIntent`/`executeTool` 方法，只需适配接口
- 与 AutoGen 的 `on_messages` 对齐，未来可扩展

---

## 三、核心类设计

### 3.1 AgentGraph（图定义层）

```java
/**
 * Agent 编排图 — 类似 LangGraph 的 StateGraph。
 *
 * <p>定义 Agent 间的执行顺序和条件路由。</p>
 */
public class AgentGraph {

    private final Map<String, AgentNode> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<ConditionalEdge> conditionalEdges = new ArrayList<>();
    private String entryPoint = "query";  // 默认从 QueryAgent 开始

    // ── 节点注册 ──
    public AgentGraph addNode(String name, AgentNode node) {
        nodes.put(name, node);
        return this;
    }

    // ── 顺序边 ──
    public AgentGraph addEdge(String from, String to) {
        edges.add(new Edge(from, to));
        return this;
    }

    // ── 条件边（核心！）──
    public AgentGraph addConditionalEdge(String from, 
                                          Predicate<AgentState> condition,
                                          String to) {
        conditionalEdges.add(new ConditionalEdge(from, condition, to));
        return this;
    }

    // ── 编译为可执行图 ──
    public CompiledGraph compile() {
        validate();
        return new CompiledGraph(this);
    }

    // ── 预设图模板 ──
    public static AgentGraph defaultGraph(Map<String, AgentNode> agents) {
        return new AgentGraph()
            .addNode("query", agents.get("query"))
            .addNode("diag", agents.get("diag"))
            .addNode("deploy", agents.get("deploy"))
            .addNode("rbac", agents.get("rbac"))
            .addNode("storage", agents.get("storage"))
            .addNode("network", agents.get("network"))
            // 条件路由: 如果意图是部署相关 → 走 deploy
            .addConditionalEdge("query", 
                state -> "deploy".equals(state.currentAgent()), "deploy")
            // 默认: query 执行完就结束
            .addEdge("query", END);
    }
}
```

### 3.2 CompiledGraph（执行引擎）

```java
/**
 * 编译后的可执行图 — 类似 LangGraph 的 CompiledStateGraph。
 *
 * <p>负责状态循环: 当前节点 → 执行 → 路由 → 下一节点。</p>
 */
public class CompiledGraph {

    private final AgentGraph graph;
    private final int maxSteps = 10;  // 最大推理步数防止死循环

    public AgentState invoke(AgentState initialState) {
        AgentState state = initialState;
        String currentNode = graph.getEntryPoint();

        for (int step = 0; step < maxSteps; step++) {
            // ① 获取当前节点
            AgentNode node = graph.getNode(currentNode);
            if (node == null) break;

            // ② 执行节点 (Agent.run)
            state = node.invoke(state);

            // ③ 检查终止条件
            if (state.isComplete() || state.requiresHITL()) break;

            // ④ 路由到下一节点
            String nextNode = route(state, currentNode);
            if (nextNode == null || END.equals(nextNode)) break;

            currentNode = nextNode;
        }

        return state;
    }

    // SSE 流式版本 (核心！每步 emit)
    public void invokeStream(AgentState initialState, 
                             Consumer<SseEvent> emitter) {
        AgentState state = initialState;
        String currentNode = graph.getEntryPoint();

        for (int step = 0; step < maxSteps; step++) {
            // emit: thinking
            emitter.accept(new SseEvent("thinking", 
                Map.of("step", step, "agent", currentNode)));

            // 执行
            state = graph.getNode(currentNode).invoke(state);

            // emit: tool_result / content
            emitter.accept(new SseEvent("tool_result", state.toolResult()));

            // 路由
            String nextNode = route(state, currentNode);
            if (nextNode == null || END.equals(nextNode)) {
                emitter.accept(new SseEvent("done", Map.of()));
                break;
            }
            currentNode = nextNode;
        }
    }
}
```

### 3.3 ReActEngine（推理状态机）

```java
/**
 * ReAct (Reasoning + Acting) 多步推理引擎。
 *
 * <p>状态机: OBSERVE → THINK → ACT → [循环]</p>
 */
public class ReActEngine {

    enum ReActState {
        OBSERVE,    // 观察环境（解析用户输入 + 历史）
        THINK,      // 思考下一步（LLM 推理）
        ACT,        // 执行 Tool（调用 Agent.executeTool）
        PAUSE,      // 等待用户确认（HITL）
        COMPLETE,   // 任务完成
        FAILED      // 失败（重试超限 / 无法处理）
    }

    public AgentState run(AgentState state, AgentNode agent) {
        ReActState reactState = ReActState.OBSERVE;
        int retryCount = 0;
        final int maxRetry = 3;

        while (reactState != ReActState.COMPLETE 
               && reactState != ReActState.FAILED) {

            switch (reactState) {
                case OBSERVE:
                    // 解析用户意图 + 历史上下文
                    state = agent.observe(state);
                    reactState = ReActState.THINK;
                    break;

                case THINK:
                    // LLM 推理下一步（或选择 Tool）
                    ThinkResult think = agent.think(state);
                    if (think.isComplete()) {
                        reactState = ReActState.COMPLETE;
                    } else if (think.requiresConfirmation()) {
                        reactState = ReActState.PAUSE;
                    } else {
                        reactState = ReActState.ACT;
                    }
                    break;

                case ACT:
                    // 执行 Tool
                    try {
                        state = agent.act(state);
                        reactState = ReActState.OBSERVE;  // 观察结果
                    } catch (ToolException e) {
                        if (++retryCount > maxRetry) {
                            reactState = ReActState.FAILED;
                        }
                        // 失败 → 重新思考（备选 Tool）
                        reactState = ReActState.THINK;
                    }
                    break;

                case PAUSE:
                    // HITL: 等待前端用户确认
                    state = state.withRequiresHITL(true)
                                 .withHitlReason("高危操作需确认");
                    return state;  // 中断，等用户回复后继续

                case COMPLETE:
                case FAILED:
                    break;
            }
        }

        return state;
    }
}
```

### 3.4 AtlasAgentBase 扩展（兼容现有代码）

```java
/**
 * Agent 抽象基类 — P2 扩展版。
 *
 * <p>兼容 P1.4 权限感知，新增 ReAct 三阶段接口。</p>
 */
public abstract class AtlasAgentBase implements AgentNode {

    // ═══ P1.4 已有（保持不变）═══
    protected final ToolRegistry toolRegistry;
    public Map<String, Object> executeTool(String toolName, Map<String, Object> params);
    public Map<String, Object> executeIntent(String intentId, Map<String, Object> params);

    // ═══ P2 新增: ReAct 三阶段 ═══

    /**
     * OBSERVE: 观察环境 — 解析用户输入，提取参数。
     */
    public AgentState observe(AgentState state) {
        // 默认实现: 直接透传
        return state;
    }

    /**
     * THINK: 思考推理 — LLM 决定下一步。
     *
     * <p>默认实现: 简单意图匹配（可覆盖为多步推理）。</p>
     */
    public ThinkResult think(AgentState state) {
        IntentResult intent = intentRouter.route(state.userQuery());
        return new ThinkResult(intent.intentId(), false, false);
    }

    /**
     * ACT: 执行 — 调用 Tool。
     */
    public AgentState act(AgentState state) {
        ThinkResult think = state.lastThinkResult();
        Map<String, Object> result = executeIntent(think.intentId(), Map.of());
        return state.withToolResult(result)
                    .withStepCount(state.stepCount() + 1);
    }

    // AgentNode 接口实现
    @Override
    public AgentState invoke(AgentState state) {
        return new ReActEngine().run(state, this);
    }
}
```

---

## 四、与现有代码的兼容策略

| 现有类 | 改动 | 兼容方式 |
|--------|------|----------|
| `AtlasOrchestrator` | **重构** | 从 `agentMap.get()` 路由 → `AgentGraph.compile().invokeStream()` |
| `AtlasAgentBase` | **扩展** | 新增 `observe/think/act/invoke` 方法，P1 的 `executeTool/executeIntent` 不变 |
| `ToolRegistry` | 不变 | P1.4 权限感知继续使用 |
| `IntentRouter` | 不变 | L1-L4 分级继续使用，作为 `think()` 的默认实现 |
| `ReActEngine` | **新建** | 当前空壳重写，状态机驱动 |
| `AuthTokenFilter` | 不变 | P1.4 ThreadLocal 继续使用 |

---

## 五、实施路线图

### P2.1 AgentGraph 骨架 (1-2天)
- [ ] `AgentGraph` 类（addNode/addEdge/addConditionalEdge/compile）
- [ ] `CompiledGraph` 类（invoke/invokeStream）
- [ ] `AgentState` Record（状态定义）
- [ ] `AgentNode` 接口
- [ ] 基础图模板（defaultGraph）

### P2.2 ReActEngine 落地 (2-3天)
- [ ] `ReActEngine` 状态机（OBSERVE→THINK→ACT→...）
- [ ] `ThinkResult` 对象（下一步决策封装）
- [ ] Tool 失败重试策略
- [ ] 循环检测（防死循环）
- [ ] HITL 中断点（PAUSE → 等待用户回复）

### P2.3 AgentBase ReAct 化 (2-3天)
- [ ] `AtlasAgentBase` 扩展 observe/think/act
- [ ] QueryAgent: 单步查询模式（当前兼容）
- [ ] DeployAgent: 多步推理（查镜像→创建→确认）
- [ ] 其他 Agent: 同理扩展

### P2.4 Orchestrator 重构 (2天)
- [ ] AtlasOrchestrator 接入 AgentGraph
- [ ] SSE 流式与状态机步骤映射
- [ ] 会话状态持久化（Checkpoint）

### P2.5 集成测试 (1-2天)
- [ ] 单 Agent 执行测试
- [ ] 多 Agent 协作测试
- [ ] HITL 中断恢复测试
- [ ] 死循环/超时保护测试

**总计: 8-12天**

---

## 六、风险评估

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Spring AI sync API 阻塞 SSE | 中 | 高 | 用 `CompletableFuture` 异步链 + 每步 emit |
| ReAct 状态机复杂度 | 高 | 中 | 先单步模式(P2.1)，再逐步加多步(P2.3) |
| HITL 中断恢复实现复杂 | 中 | 中 | P2.2 预留接口，P3 完整实现 |
| 与现有 Tool 系统兼容 | 低 | 高 | P1 的 ToolRegistry 不变，只在上层加 Graph |
| LLM 推理延迟影响响应 | 中 | 中 | 流式 SSE 每步输出，用户感知不到阻塞 |

---

## 七、与开源方案对比

| 特性 | LangGraph (Python) | 本方案 (Spring AI) | 说明 |
|------|-------------------|-------------------|------|
| 图定义 | `StateGraph.add_node/edge` | `AgentGraph.addNode/Edge` | 完全对齐 |
| 状态类型 | `TypedDict` | `AgentState` Record | Java Record 等价 |
| 执行模式 | Pregel 消息传递 | 单线程顺序 + CompletableFuture | 简化适配 |
| 并行节点 | 支持 | 暂不支持（单Agent场景不需要） | 未来可扩展 |
| HITL | `Command(resume=...)` | `PAUSE` 状态 + 外部恢复 | 等价 |
| 流式输出 | 原生 AsyncIterator | SSE emitter | 前端等价 |
| 持久化 | `Checkpointer` | `AgentSessionRepository` | JDBC/Redis 替换 |

---

## 附录: 源码调研记录

- **LangGraph `state.py`**: 1964行，`StateGraph` 类定义了 `add_node`, `add_edge`, `add_conditional_edges`, `compile` 等方法，`CompiledStateGraph` 负责执行循环。
- **LangGraph `types.py`**: 968行，定义了 `Command`, `Send`, `Checkpoint` 等核心类型。
- **AutoGen `BaseGroupChat`**: 834行，`select_speaker` 轮询机制，`run_stream` 流式输出。
- **AutoGen `BaseChatAgent`**: 245行，`on_messages`, `run`, `run_stream`, `save_state/load_state` 接口。

---

> 调研结论: LangGraph 的 StateGraph 模式是 Agent 编排的最先进实践，
> 其状态机驱动 + HITL 中断恢复机制完美匹配本项目的 SSE 流式 + 高危确认需求。
> Spring AI 环境下以简化版实现（单线程顺序执行），保留未来扩展空间。
