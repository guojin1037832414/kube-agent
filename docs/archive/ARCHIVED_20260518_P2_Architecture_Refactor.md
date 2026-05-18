# Atlas v3.1 P2 架构重构方案 — "先清理再重构" vs "逐步改造" 深度论证

> **版本**: v3.1.0-P2  
> **作者**: 首席架构师  
> **日期**: 2026-05-14  
> **范围**: AtlasAgent → ReActEngine (StateGraph + ReactAgent) 全链路迁移

---

## 一、核心发现：启动失败的根因

启动失败的根本问题是 **Spring Bean 命名冲突**：

| 旧架构 (遗留) | 新架构 (P2) | 冲突表现 |
|--------------|-------------|---------|
| `queryAgent` (`@Component` in `QueryAgent.java`) | `queryAgent` (`@Bean` in `AtlasGraphConfig`) | 同名Bean注入冲突 |
| `deployAgent` (`@Component` in `DeployAgent.java`) | `deployAgent` (`@Bean` in `AtlasGraphConfig`) | 同名Bean注入冲突 |
| `diagAgent` | `diagAgent` | 同上 ×6 |
| `rbacAgent` | `rbacAgent` | 同上 ×6 |
| `storageAgent` | `storageAgent` | 同上 ×6 |
| `networkAgent` | `networkAgent` | 同上 ×6 |

> **旧Agent的bean名称来自类名首字母小写**（Spring 默认命名规则），**新ReactAgent的bean名称来自方法名**。
> `QueryAgent` → `queryAgent`，方法与类名首字母小写恰好一致，导致冲突。

---

## 二、"先清理再重构" vs "旧代码上逐步改造" 利弊论证

### 2.1 方案对比矩阵

| 维度 | A: 先清理再重构 | B: 旧代码上逐步改造 |
|------|----------------|-------------------|
| **启动时机** | 第一篇PR即启动成功 | 可能需多轮修转换层才能启动 |
| **认知负荷** | 低（新代码没有旧包袱） | 高（开发者需同时理解两套架构） |
| **回归风险** | 低（一次切割，边界清晰） | 高（切一半时若-else与Graph并存，行为可能错乱） |
| **测试覆盖** | 旧接口测试全部保留，新逻辑独立测试 | 混合测试，定位问题困难 |
| **回滚难度** | 低（Git一个revert） | 高（多处改动纠缠，无法原子回滚） |
| **工期估算** | 4个Phase约2-3周 | 6-8周持续修修补补 |
| **团队并行度** | 高（Phase边界清晰，多人可并行） | 低（一处改动牵一发动全身） |

### 2.2 关键论证：为什么必须"先清理"

#### 论据1：Bean冲突无法在"逐步改造"中优雅解决

旧6个Agent（`QueryAgent.java` ~ `NetworkAgent.java`）都有 `@Component`，其注入关系是：

```java
// AtlasOrchestrator.java:66
private final Map<String, AtlasAgentBase> agentMap;  // <-- 强依赖旧Agent

// AtlasOrchestrator.java:94-110 构造器
public AtlasOrchestrator(..., List<AtlasAgentBase> agents, ...) {
    this.agentMap = agents.stream()
        .collect(Collectors.toMap(AtlasAgentBase::getAgentType, a -> a));
}
```

`AtlasOrchestrator` 通过 `List<AtlasAgentBase>` 注入所有Agent。即使你把新ReactAgent改名（如 `newQueryAgent`），旧Orchestrator仍然会扫描到旧Agent子类，旧路由逻辑会继续生效。

**逐步改造方案**需要做的是：
1. 给旧Agent加 `@Qualifier("legacy")` + 改名
2. 给新ReactAgent改名
3. 在Orchestrator中同时维护两套路由逻辑（if-else + Graph条件分支）
4. 测试两套逻辑的互斥性和一致性

→ 这反而比直接清理更复杂。

#### 论据2："混血架构"是技术债务的最大坑

当前代码已经可以看到这个坑：

```java
// AtlasOrchestrator.java 已经试图兼容两套架构
private final CompiledGraph compiledGraph;  // P2新架构

public AtlasOrchestrator(..., CompiledGraph compiledGraph) { ... }  // 两个构造器重载

@PostMapping("/chat/stream")  // 旧接口
@PostMapping("/chat/graph")   // 新实验接口
```

`/chat/stream`（旧）和 `/chat/graph`（新实验）并行存在，意味着：
- 用户可能随机命中不同逻辑
- 两个入口都需要维护Token透传、限流、错误处理
- 任何功能升级都要改两处

> **行业最佳实践（Netflix/Shopify的经验）**：新旧架构必须在一个模块内原子替换，不要让两个入口同时存活超过一个Sprint。

#### 论据3：旧Agent抽象层对新架构是反模式

旧 `AtlasAgentBase` 的职责是：
- 持有 `ToolRegistry` 引用
- 权限二次校验（`executeTool`）
- 按 `agentType` / `intentId` 路由到Tool

新 `ReactAgent`（Spring AI Alibaba graph-core）的职责是：
- 内部使用ChatModel进行ReAct推理循环（Thought → Action → Observation）
- 动态决策调用哪个Tool（交给LLM判断，无需预编码agentType）
- 多步推理状态管理（由StateGraph维护）

**两者设计的哲学完全不同**：
- 旧：人写路由规则（if-else，Embedding分类 → Agent → Tool）
- 新：LLM自主决策（ReAct循环，StateGraph维护上下文）

留一个 `AtlasAgentBase` 的兼容层，等于强迫新架构穿旧鞋。这正是为什么P2的ReactAgent已经完全没有继承 `AtlasAgentBase`。

#### 论据4：意图路由系统 + ToolRegistry 已完全可复用

经过代码审查确认，以下组件是架构无关的"纯基础设施"：

| 组件 | 与旧架构耦合度 | 与新架构兼容性 | 结论 |
|------|--------------|--------------|------|
| `ToolRegistry` + 23 Tools | ❌ 无 | ✅ 直接复用 | **保留，无需改动** |
| `IntentRouter` (L1~L4) | ❌ 无（纯分类器） | ✅ 可被Supervisor Agent替代 | **Phase4逐步替换** |
| `StreamingEmitter` / `SseEvent` | ⚠️ 被Orchestrator引用 | ✅ 仅需适配Graph输出 | **保留，重构调用点** |
| `Auth` (Token透传/权限校验) | ❌ 无 | ✅ 通过ThreadLocal复用 | **完全保留** |
| `AtlasToolCallbackFactory` | ❌ 无（纯转换层） | ✅ ReactAgent直接使用 | **保留，扩展即可** |

**结论是：需要"清理"的不是基础设施，而是旧编排层（Orchestrator + 6个Agent类）**。

---

## 三、推荐的旧代码清理清单

### 3.1 清理策略总览

```
策略1: 直接删除        → 旧Agent子类6个 + AtlasAgentBase + AtlasAgent枚举
策略2: 改bean名保留    → 无（旧Agent与新ReactAgent语义完全不同，改名无意义）
策略3: 直接重写        → AtlasOrchestrator（成为Graph编排 Facade）
策略4: 保留不变        → ToolRegistry/23Tools/Auth/StreamingEmitter/意图系统
```

### 3.2 详细清单

#### 🔴 删除（这些文件导致Bean冲突，且对新架构无复用价值）

| # | 文件路径 | 删除理由 |
|---|---------|---------|
| 1 | `com.atlas.agent.QueryAgent` | `@Component` 导致Bean冲突；新架构由 `ReactAgent queryAgent(...)` 替代，无需继承旧类 |
| 2 | `com.atlas.agent.DeployAgent` | 同上 |
| 3 | `com.atlas.agent.DiagAgent` | 同上 |
| 4 | `com.atlas.agent.RbacAgent` | 同上 |
| 5 | `com.atlas.agent.StorageAgent` | 同上 |
| 6 | `com.atlas.agent.NetworkAgent` | 同上 |
| 7 | `com.atlas.agent.AtlasAgentBase` | P2后由 `ReactAgent` + `ToolRegistry` 替代全部职责；权限校验已下沉到 `AtlasToolCallback` |
| 8 | `com.atlas.agent.AtlasAgent` | 枚举仅用于旧 `AtlasOrchestrator#agentMap` 的Key映射；新架构通过StateGraph条件边路由，不再需要枚举 |

#### 🟢 保留但重构（核心基础设施，需适配新接口）

| # | 文件路径 | 处理方式 |
|---|---------|---------|
| 9 | `com.atlas.orchestrator.AtlasOrchestrator` | **重写**：保留 `/chat/stream` 端点，但内部逻辑从 `if-else IntentRouter` 改为直接调用 `compiledGraph.stream()`；旧构造器删除，只保留Graph版 |
| 10 | `com.atlas.orchestrator.StreamingEmitter` | 保留，适配Graph节点的输出格式 |
| 11 | `com.atlas.orchestrator.SseEvent` | 保留，无需改动 |

#### 🔵 保留并扩展（新架构直接复用）

| # | 文件路径 | 处理方式 |
|---|---------|---------|
| 12 | `com.atlas.tool.core.ToolRegistry` | 保留，零改动 |
| 13 | `com.atlas.tool.core.BaseTool` & `AtlasTool` | 保留，零改动 |
| 14 | 23个 `com.atlas.tool.impl.*Tool` | 保留，零改动 |
| 15 | `com.atlas.tool.core.AtlasToolCallback` | 保留，零改动 |
| 16 | `com.atlas.graph.bridge.AtlasToolCallbackFactory` | 保留，零改动 |
| 17 | `com.atlas.auth.*` (UserPermissionContext, AsyncContextHolder, 等) | 保留，零改动 |
| 18 | `com.atlas.intent.*` (全链路) | Phase 1~3 保留（作为兜底），Phase 4 决定是否迁移到LLM-native Supervisor |
| 19 | `com.atlas.graph.config.AtlasGraphConfig` | 保留，Phase 1 重点扩展，增加ReActEngine核心循环 |
| 20 | `com.atlas.graph.node.*` | Phase 1 深度重写（见下方设计建议） |

#### 🟡 新增（新架构所需）

| # | 文件路径 | 职责 |
|---|---------|------|
| 21 | `com.atlas.engine.ReActEngine` | 多步推理循环引擎（见§4核心设计） |
| 22 | `com.atlas.engine.ReActState` | ReAct循环的状态对象（thought/action/observation） |
| 23 | `com.atlas.engine.ReActStep` | 单步执行记录 |
| 24 | `com.atlas.graph.node.ReActLoopNode` | StateGraph节点：封装ReActEngine执行 |
| 25 | `com.atlas.graph.node.SupervisorNode` | 替换当前AtlasAgent枚举+if-else路由，改为LLM-native Supervisor |
| 26 | `com.atlas.graph.node.HumanInTheLoopNode` | HITL确认节点（高风险操作阻断） |

---

## 四、ReActEngine 核心设计建议

### 4.1 架构定位

`ReActEngine` 不是替换 `ReactAgent`，而是**封装并扩展** `ReactAgent` 的多步推理能力：

- `ReactAgent`（Spring AI Alibaba提供）：单Agent的 ReAct 循环（Thought → Action → Observation）
- `ReActEngine`（Atlas自定义）：跨Agent的多步协调 + 状态持久化 + HITL介入点 + SSE流式推送

### 4.2 StateGraph 新拓扑设计

当前拓扑（存在问题）：

```
START → supervisor → [query/deploy/diag/rbac/storage/network] → merge_result → emit_sse → END
            ↓
       direct_answer (fallback)
```

**问题**：
1. `supervisor` 只做一次决策，不支持多轮对话中的上下文修正
2. 每个专业Agent是孤立节点，Agent之间无法协作（如deploy需要先query确认资源）
3. 没有 HITL 中断点
4. SSE事件是副作用节点，不应在StateGraph中做纯副作用

**新拓扑（推荐）**：

```
                        ┌──────────────────────────────────────┐
                        │         ReAct Loop (循环)            │
                        │  ┌──────────┐                        │
     START → prepare ──→│  │ ReAct    │ ←──────────────────┐  │
              init       │  │  Engine  │                    │  │
              state      │  └────┬─────┘                    │  │
                        │       ↓ max_steps                  │  │
                        │  ┌──────────┐   ┌──────────┐      │  │
                        │  │thought   │──→│ action   │──┐   │  │
                        │  │(LLM推理) │   │(Tool调用) │  │   │  │
                        │  └──────────┘   └────┬─────┘  │   │  │
                        │                      ↓        │   │  │
                        │                ┌──────────┐   │   │  │
                        │                │observat'n│───┘   │  │
                        │                │(Tool结果)│       │  │
                        │                └──────────┘       │  │
                        │                      │             │  │
                        │                      ↓ need_more   │  │
                        │                ┌──────────┐        │  │
                        │                │ HITL?    │        │  │
                        │                │ (高风险) │        │  │
                        │                └────┬─────┘        │  │
                        │                     confirm/abort  │  │
                        └──────────────────────────────────────┘
                                          ↓
                                   ┌──────────┐
                                   │ finalize │ ──→ emit_sse (出Graph外) → END
                                   │  (汇总)  │
                                   └──────────┘
```

**关键改进**：

| 设计决策 | 理由 |
|---------|------|
| ReAct循环封装为单一StateGraph节点 | StateGraph边过多会导致状态爆炸；ReAct内部循环由Engine自己管理 |
| HITL在循环内部 | 高风险Tool调用后阻断，用户确认后继续循环 |
| SSE emit移出Graph | 副作用（网络IO）不应在StateGraph节点内做，改为Graph完成后统一发射 |
| Supervisor职责并入ReActEngine | LLM自己决定下一步调用哪个Tool，无需预编码6个Agent分类 |

### 4.3 ReActEngine 核心类设计

```java
package com.atlas.engine;

/**
 * ReAct 多步推理引擎 — Atlas v3.1 P2 核心。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>维护 ReActState（thought/action/observation 历史）</li>
 *   <li>执行 Thought → Action → Observation 循环</li>
 *   <li>注入权限上下文（Token透传）</li>
 *   <li>在关键节点触发 HITL（Human-in-the-Loop）</li>
 *   <li>控制最大步数，防止无限循环</li>
 * </ol>
 */
public class ReActEngine {

    private final ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final ToolRegistry toolRegistry;
    private final UserPermissionContext userPermissionContext;

    // 最大推理步数（安全阀）
    private static final int MAX_STEPS = 10;

    /**
     * 执行一次 ReAct 循环。
     *
     * @param userQuery 用户原始Query
     * @param conversationId 会话ID（用于MemorySaver恢复上下文）
     * @param threadId LangGraph线程ID
     * @return 最终状态（含final_answer或多步历史）
     */
    public ReActState execute(String userQuery, String conversationId, String threadId) {
        ReActState state = new ReActState(userQuery);

        for (int step = 0; step < MAX_STEPS; step++) {
            // 1. Thought: LLM 基于当前状态决定下一步
            String thought = think(state);
            state.addThought(thought);

            // 2. Action 解析: LLM 输出决定调用哪个Tool
            Action action = parseAction(thought);
            if (action == null || action.isFinalAnswer()) {
                // LLM认为无需更多工具调用，直接输出答案
                state.setFinalAnswer(extractAnswer(thought));
                break;
            }

            // 3. 权限预检 + HITL
            if (isHighRisk(action.toolName()) && !confirmWithUser(action)) {
                state.setFinalAnswer(Map.of("error", "用户取消了高风险操作"));
                break;
            }

            // 4. Observation: 执行Tool，捕获结果
            Map<String, Object> result = executeTool(action);
            state.addObservation(result);
        }

        return state;
    }

    private String think(ReActState state) { ... }
    private Action parseAction(String thought) { ... }
    private boolean isHighRisk(String toolName) { ... }
    private boolean confirmWithUser(Action action) { ... } // HITL hook
    private Map<String, Object> executeTool(Action action) { ... }
}
```

### 4.4 StateGraph与ReActEngine的协作

```java
// AtlasGraphConfig.java — P2 新配置（概念）

@Bean
public CompiledGraph atlasReActGraph(
        ChatModel chatModel,
        AtlasToolCallbackFactory toolFactory,
        StreamingEmitter streamingEmitter
) throws GraphStateException {

    // ReAct Engine 封装了多步推理
    ReActEngine engine = new ReActEngine(chatModel, toolFactory.buildAllVisible(), ...);

    KeyStrategyFactory keyFactory = buildKeyStrategyFactory();

    StateGraph graph = new StateGraph("atlas_react", keyFactory)
        // 1. 初始化：准备用户输入、Token透传、会话上下文
        .addNode("init", node_async(new InitNode()))

        // 2. ReAct 核心循环节点（内部包含 thought → action → observation）
        .addNode("react", node_async(new ReActLoopNode(engine)))

        // 3. 结果归一化（转为frontend可消费的final_answer）
        .addNode("finalize", node_async(new FinalizeNode()));

    // 边定义
    graph.addEdge(START, "init");
    graph.addEdge("init", "react");
    graph.addEdge("react", "finalize");
    graph.addEdge("finalize", END);

    return graph.compile(
        CompileConfig.builder()
            .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
            .interruptBefore("react")  // 可选：在ReAct开始前允许用户确认
            .build()
    );
}
```

### 4.5 关键设计决策说明

| 决策 | 选择 | 理由 |
|------|------|------|
| 单ReAct节点 vs 多节点拆分子图 | **单ReAct节点** | 拆分会大大增加Graph复杂度（10步循环就是10个节点的展开）；Spring AI Alibaba的 `ReactAgent` 本身已封装循环 |
| ReactAgent作为子图节点 vs ReActEngine手动实现 | **两者结合** | `ReactAgent` 直接提供 `getAndCompileGraph()`，将其嵌入大Graph的一个节点；`ReActEngine`  wraps it 以注入Atlas特有的逻辑（权限/HITL/SSE） |
| HITL在Graph级别还是Engine级别 | **Engine级别** | HITL通常发生在一次Tool调用后（Observation之后），这是循环内的决策，不应在Graph边级别处理 |
| 是否保留6个专业ReactAgent | **保留，但角色变化** | 它们不再是"路由目标"，而是"预配置的专家ReAct模板"（不同system prompt + tool集）。 Supervior Agent（或LLM本身）决定激活哪个。 |

---

## 五、分阶段迁移路线图

### Phase 0: 清理与地基修复（3天）

**目标**：启动成功，旧路由功能关闭，新Graph接管 `/chat/stream`

- [ ] **Day 1**: 删除旧Agent类（7个文件）
  - `QueryAgent.java` → Delete
  - `DeployAgent.java` → Delete
  - `DiagAgent.java` → Delete
  - `RbacAgent.java` → Delete
  - `StorageAgent.java` → Delete
  - `NetworkAgent.java` → Delete
  - `AtlasAgentBase.java` → Delete
  - `AtlasAgent.java` → Delete

- [ ] **Day 1**: 重构 `AtlasOrchestrator.java`
  - 删除 `private final Map<String, AtlasAgentBase> agentMap` 字段
  - 删除旧构造器（仅保留Graph版）
  - 删除 `/chat/stream` 方法中所有 `agentMap.get()` / `intentRouter.route()` / `agent.executeIntent()` 逻辑
  - `/chat/stream` 内部改为直接调用 `compiledGraph.stream()`（复用现有 `/chat/graph` 的调用方式）
  - 保留 `StreamingEmitter` / `SseEvent` / 限流 / Token透传 逻辑

- [ ] **Day 2**: 删除 `com.atlas.graph.node.SseEmitNode`
  - 该节点做纯副作用（SSE网络IO），不适合StateGraph
  - SSE推送移回 `AtlasOrchestrator` 在Graph完成后统一处理

- [ ] **Day 2**: 修复 `AtlasGraphConfig` Bean命名
  - 所有 `@Bean` 方法使用显式名称，确保不与任何旧类冲突（虽然旧类已删，但显式命名是防御性编程）
  - 例：`@Bean("atlasQueryAgent")` 代替默认方法名

- [ ] **Day 3**: 冒烟测试
  - 启动Spring Boot，确认无 BeanDefinitionOverrideException
  - POST `/api/v1/chat/stream` 验证基本流式输出

**交付物**：
- PR 编号: `P2-Phase0-cleanup`
- 删除文件数: 8
- 修改文件: `AtlasOrchestrator.java`, `AtlasGraphConfig.java`
- 删除文件: `SseEmitNode.java` (可选，Phase 1一起删)

---

### Phase 1: ReActEngine 核心实现（5天）

**目标**：完成多步推理循环，单轮对话中LLM可自行决策多次Tool调用

- [ ] **Day 1-2**: 新建核心类
  - `com.atlas.engine.ReActState` — 状态对象（thoughts/actions/observations列表 + final_answer）
  - `com.atlas.engine.ReActStep` — 单步记录（thought/action/observation/executionTime）
  - `com.atlas.engine.ReActEngine` — 核心循环引擎（见§4.3设计）

- [ ] **Day 3**: 重构 `AtlasGraphConfig`
  - 从"多Agent节点拓扑"改为"单ReAct循环节点拓扑"
  - 删除 `supervisorAgent`, `queryAgent`, `deployAgent` ... 等6个独立 `@Bean` 的专业Agent
  - 改为一个统一的 `atlasReactAgent(ChatModel, AtlasToolCallbackFactory)`
  - 新增 `ReActLoopNode` 封装循环逻辑

- [ ] **Day 4**: 权限注入
  - `ReActEngine.execute()` 入口接收 `capturedToken`
  - 在 `executeTool()` 内部通过 `AsyncContextHolder.wrap(...)` 保障子线程Token可用
  - ToolRegistry的 `isVisible()` / `canExecuteIntent()` 在Engine中做前置拦截

- [ ] **Day 5**: 集成测试
  - 测试Query场景：用户问"集群概览" → LLM一次调用 `ClusterOverviewTool`
  - 测试多步场景：用户问"部署一个Redis，先帮我看看有没有足够资源" → LLM先query节点资源 → 再deploy创建

**交付物**：
- PR 编号: `P2-Phase1-react-engine`
- 新增文件: `ReActState.java`, `ReActStep.java`, `ReActEngine.java`, `ReActLoopNode.java`
- 修改文件: `AtlasGraphConfig.java` (拓扑重构), `AtlasOrchestrator.java` (集成ReAct)

---

### Phase 2: HITL 人机回环（3天）

**目标**：高风险操作阻断，用户确认后继续

- [ ] **Day 1**: 风险分级判定
  - 复用 `application.yml` 中的 `atlas.task-levels` 配置
  - 在 `ReActEngine` 中引入 `RiskEvaluator`（根据toolName或actionType判断）

- [ ] **Day 2**: HITL节点实现
  - `com.atlas.graph.node.HumanInTheLoopNode`
  - 利用 `CompileConfig.interruptBefore("react")` 或Engine内部状态暂停
  - 设计确认协议：`{"action":"confirm","tool":"deployDelete","target":"redis-pod"}`

- [ ] **Day 3**: 测试
  - 场景：用户请求"删除deployment redis" → 系统返回 `{"type":"confirm_required","action":"deployDelete","target":"redis"}` → 用户确认后继续

**交付物**：
- PR 编号: `P2-Phase2-hitl`
- 新增文件: `HumanInTheLoopNode.java`, `RiskEvaluator.java`
- 修改文件: `ReActEngine.java`

---

### Phase 3: SSE 流式重构与持久化（4天）

**目标**：Graph执行过程中的实时流式事件推送到前端，支持会话恢复

- [ ] **Day 1-2**: 流式事件标准化
  - 设计 `ReActEvent` 枚举：`THOUGHT`, `ACTION`, `OBSERVATION`, `HITL_REQUIRED`, `FINAL_ANSWER`
  - `AtlasOrchestrator` 订阅 `compiledGraph.stream()` 的每个 `NodeOutput`，实时转换为SSE

- [ ] **Day 3**: MemorySaver持久化
  - 启用 `MemorySaver` checkpoint，每个ReAct step自动存盘
  - 支持 `threadId` 恢复会话上下文

- [ ] **Day 4**: 容错与超时
  - Graph执行超时控制（防止LLM无限推理）
  - Token失效自动降级（L3级别的降级，非P1的if-else）

**交付物**：
- PR 编号: `P2-Phase3-streaming`
- 修改文件: `AtlasOrchestrator.java`, `StreamingEmitter.java`

---

### Phase 4: 意图系统融合（3天，可选）

**目标**：决定是保留旧的L1~L4意图路由还是完全交由LLM-native Supervisor

- [ ] **Day 1**: A/B测试设计
  - 10%流量走向旧 `IntentRouter` + ReActEngine（有预分类）
  - 90%流量走向纯ReAct（LLM自主决策）
  - 收集准确率/延迟数据

- [ ] **Day 2-3**: 决策与裁剪
  - 如果纯ReAct准确率 ≥ IntentRouter，则删除 `IntentRouter` + `EmbeddingMatcher` + `L3IntentClassifier` 等全套旧组件
  - 如果仍有价值，则保留 `IntentRouter` 作为 ReActEngine 的 `think()` 阶段的前置加速层（Embedding预筛缩短LLM决策时间）

**交付物**：
- PR 编号: `P2-Phase4-intent-merge`
- 可能新增/可能删除文件（取决于测试结果）

---

## 六、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 删除旧Agent后，外部系统依赖某些Agent的Bean名称 | 低 | 高 | Phase 0 之前全局搜索所有 `@Autowired` / `@Qualifier` 引用旧Agent的地方 |
| ReactAgent / StateGraph API变动（Spring AI Alibaba尚未GA） | 中 | 中 | 抽象一层 `AtlasGraphAdapter`，隔离第三方API |
| ReAct循环失控（LLM无限调用Tool） | 低 | 高 | `MAX_STEPS` 硬限制 + Token消耗监控 |
| HITL引入后用户体验下降（每次都要确认） | 低 | 中 | 仅P0高危操作触发，P3查询免确认 |
| 意图系统迁移的准确率回退 | 中 | 高 | Phase 4保留A/B对比，不达标不删除旧系统 |

---

## 七、总结

| 问题 | 结论 |
|------|------|
| 是否先彻底清理？ | **是**。Bean冲突是根本性、不可调和的架构冲突，没有优雅的兼容层方案 |
| 清理策略？ | **删**：旧Agent 6个 + AtlasAgentBase + AtlasAgent枚举；**保留扩**：ToolRegistry/23Tools/Auth/意图系统；**重写**：Orchestrator |
| P2核心设计？ | **单ReAct循环节点**封装多步推理，HITL内嵌于循环中，SSE移出Graph统一发射 |
| 总工期？ | **15天**（Phase 0~4，不含Phase 4的A/B观察期） |

---

## 附录：Phase 0 立即执行的清理代码示意

### A. 删除文件清单（git rm）

```bash
# 在 /home/guojin/kube-agent 目录下
git rm src/main/java/com/atlas/agent/QueryAgent.java
git rm src/main/java/com/atlas/agent/DeployAgent.java
git rm src/main/java/com/atlas/agent/DiagAgent.java
git rm src/main/java/com/atlas/agent/RbacAgent.java
git rm src/main/java/com/atlas/agent/StorageAgent.java
git rm src/main/java/com/atlas/agent/NetworkAgent.java
git rm src/main/java/com/atlas/agent/AtlasAgentBase.java
git rm src/main/java/com/atlas/agent/AtlasAgent.java
```

### B. AtlasOrchestrator 改造要点（Phase 0）

```java
@RestController
@RequestMapping("/api/v1")
public class AtlasOrchestrator {

    private final CompiledGraph compiledGraph;
    private final StreamingEmitter streamingEmitter;
    private final UserPermissionContext userPermissionContext;
    private final Executor asyncExecutor;

    // 删除字段：
    // private final IntentRouter intentRouter;     // Phase 0 保留但不再使用
    // private final ToolRegistry toolRegistry;       // Phase 0 保留但不再使用（ReActEngine内部使用）
    // private final Map<String, AtlasAgentBase> agentMap;  // DELETE

    @Autowired
    public AtlasOrchestrator(
            StreamingEmitter streamingEmitter,
            UserPermissionContext userPermissionContext,
            @Qualifier("atlasTaskExecutor") Executor asyncExecutor,
            CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
        this.streamingEmitter = streamingEmitter;
        this.userPermissionContext = userPermissionContext;
        this.asyncExecutor = asyncExecutor;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        // 直接复用原来的 /chat/graph 逻辑
        return executeGraph(request, "");  // 原来是 /chat/graph，现在 /chat/stream 走Graph
    }

    // 原来的旧 /chat/stream 逻辑（IntentRouter + agentMap）全部删除
}
```

### C. AtlasGraphConfig Bean命名防御（Phase 0）

```java
@Configuration
public class AtlasGraphConfig {

    @Bean("atlasSupervisorAgent")  // 显式命名，避免任何潜在冲突
    public ReactAgent supervisorAgent(...) { ... }

    @Bean("atlasQueryAgent")
    public ReactAgent queryAgent(...) { ... }

    // ... 其他6个Agent同理
}
```

---

> **下一步行动**: 请审阅本方案，如确认，即刻启动 Phase 0 清理工作。
