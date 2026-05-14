# Atlas v3.1 P2 架构方案（Spring AI Alibaba 版）

> **状态**: ✅ 已决策  
> **决策架构**: 引入 Spring AI Alibaba 作为核心 Agent 框架  
> **版本**: 3.1.0-P2  
> **日期**: 2026-05-14  
> **替代文档**: 已废弃的 /docs/P2_AGENT_SPLIT_ARCHITECTURE.md

---

## 一、总体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        用户输入层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  微信/Hermes  │  │  Web 前端    │  │  MCP Client  │          │
│  │  conversation│  │  chat SSE    │  │  tool call   │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
└─────────┼─────────────────┼─────────────────┼──────────────────┘
          └─────────────────┴─────────────────┘
                            │
                    ┌───────▼────────┐
                    │ AgentController │  REST API
                    └───────┬────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                  AtlasOrchestrator v3.1（新版）                    │
│                                                                  │
│  ┌─ 接收 ChatRequest                                           │
│  ├─ 创建 AgentSession (对话上下文 + 权限信息)                    │
│  ├─ 构建 StateGraph (运行时动态组装)                            │
│  │   ├─ Node("entry", IntentRouterNode)  ← L1-L4 意图路由    │
│  │   ├─ Node("query", QueryAgent)        ← ReactAgent 驱动   │
│  │   ├─ Node("diag", DiagAgent)          ← ReactAgent 驱动   │
│  │   ├─ Node("deploy", DeployAgent)      ← ReactAgent 驱动   │
│  │   ├─ Node("rbac", RBACAgent)          ← ReactAgent 驱动   │
│  │   ├─ Node("storage", StorageAgent)    ← ReactAgent 驱动   │
│  │   ├─ Node("network", NetworkAgent)    ← ReactAgent 驱动   │
│  │   ├─ Node("hitl", HITLNode)           ← 人工确认节点      │
│  │   └─ ConditionalEdge 条件路由                               │
│  ├─ compiledGraph.invokeStream(sessionState, emitter)          │
│  └─ 每步 emit SSE event (thinking/tool_call/tool_result/done)  │
└───────────────────────────┬──────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                  StateGraph 执行引擎（框架层）                     │
│                                                                  │
│  ┌─ compiledGraph: CompiledGraph (spring-ai-alibaba-graph)    │
│  ├─ 每轮: node.run(state) → 更新 OverAllState → 路由下一节点   │
│  ├─ 状态持久化: MemorySaver / RedisSaver / FileSaver          │
│  ├─ 流式推送: NodeOutput → Flux → SSE Emitter                 │
│  └─ 终止条件: max_steps / hitl 中断 / 完成 / 异常              │
└───────────────────────────┬──────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                    ReactAgent（每个节点内部）                     │
│                                                                  │
│  ┌─ OBSERVE: 解析用户输入 + 历史上下文 + 环境信息                │
│  ├─ THINK: LLM 推理 → "调什么Tool？还是直接回答？"            │
│  ├─ ACT: 调用 ToolCallback.call() → 得到 Observation         │
│  ├─ 将 Tool Result 加入 Message 列表 → 回传给 LLM              │
│  ├─ 循环检测: 相同 state + action 连续出现 → 终止               │
│  ├─ 重试策略: 指数退避 + 备选 Tool                              │
│  └─ 最多 maxSteps 次循环，超时终止                              │
│                                                                  │
│  工具来源: ToolRegistry.listByAgent() → AtlasToolCallback     │
│  (按权限过滤，仅暴露当前用户可见的 Tool)                         │
└──────────────────┬───────────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────────┐
│                    BaseTool 体系（保留）                          │
│                                                                  │
│  @Tool 注解自动扫描 → MethodToolCallbackProvider              │
│  BaseTool.execute(Map) → 校验 → 类型转换 → doExecute          │
│  → AtlasToolResult → JSON → LLM 接收为 Observation            │
│                                                                  │
│  23个域 Operation 全部保留，无需修改！                          │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、核心组件说明

### 2.1 StateGraph（图定义层）

Spring AI Alibaba 提供的 `StateGraph` API 与 LangGraph 概念一致：

```java
// 运行时动态构建图
StateGraph graph = new StateGraph(OverAllState::new)
    .addNode("entry", new IntentRouterNode(intentRouter))
    .addNode("query", new ReactAgentNode(queryAgent))
    .addNode("diag", new ReactAgentNode(diagAgent))
    .addNode("hitl", new HITLNode())
    .addEdge("entry", "query", condition -> 
        "query".equals(condition.get("agentType")))
    .addEdge("entry", "diag", condition ->
        "diag".equals(condition.get("agentType")))
    .addConditionalEdge("diag", state -> 
        state.isHighRisk() ? "hitl" : "__end__");

CompiledGraph compiled = graph.compile();
```

### 2.2 ReactAgent（推理引擎）

Spring AI Alibaba 内建的 ReactAgent 实现：

```java
ReactAgent agent = ReactAgent.builder()
    .name("QueryAgent")
    .systemPrompt("你是 K8s 集群查询专家...")
    .chatClient(chatClient)
    .tools(toolRegistry.listByAgent("query"))  // 权限过滤后的 Tools
    .maxIterations(5)
    .retryPolicy(RetryPolicy.exponentialBackoff())
    .build();
```

**ReactAgent 内部循环**（框架自动处理）：
1. LLM 收到 User Query + System Prompt + Tool Definitions
2. LLM 决定：调用 Tool（生成 JSON 参数）或 直接回答
3. 如调用 Tool：
   - Spring AI 自动反序列化参数
   - 调用 `ToolCallback.call()` → 执行 BaseTool
   - Tool Result 转为 Message 追加到对话
   - 回到步骤 1（LLM 基于新 Observation 再推理）
4. 如直接回答：返回最终答案
5. 循环控制：maxIterations / 循环检测 / 重试策略

### 2.3 AgentState（状态管理）

框架提供的 `OverAllState` 替代手写的 AgentState：

```java
OverAllState state = new OverAllState();
state.input("userQuery", userQuery);
state.input("permissions", userPermissionContext.current());
state.input("history", conversationHistory);
state.input("toolResults", new ArrayList<>());
```

支持状态持久化：
```java
SaverConfig saverConfig = SaverConfig.builder()
    .saver(new RedisSaver(redisTemplate))
    .build();

CompiledGraph compiled = graph.compile(
    CompileConfig.builder().saverConfig(saverConfig).build()
);
```

### 2.4 HITL（人工确认）

框架内建 HITL 支持：

```java
// 在 Graph 中插入 HITL 节点
graph.addNode("hitl", new HumanInterruptNode(
    HumanInterruptConfig.builder()
        .description("高危操作：删除 Production Deployment")
        .confirmationText("请输入 '确认删除' 以继续")
        .build()
));

// 条件路由到 HITL
graph.addConditionalEdge("diag", state -> 
    state.get("riskLevel").equals("HIGH") ? "hitl" : "__end__"
);
```

前端体验：
1. SSE 推送 `hitl_request` 事件
2. 前端显示确认对话框
3. 用户输入确认 → SSE 发送恢复请求
4. Graph 从 Checkpoint 恢复执行

---

## 三、与现有代码的集成策略

### 3.1 保留组件（100%兼容）

| 组件 | 状态 | 集成方式 |
|------|------|---------|
| BaseTool | ✅ 保留 | ReactAgent.tools() 接收 BaseTool 通过 AtlasToolCallback 包装后的 ToolCallback |
| ToolRegistry | ✅ 保留 | 为 ReactAgent 提供按 Agent 分组的 Tools |
| IntentRouter L1-L4 | ✅ 保留 | 作为 Graph 的 Entry Node，选择初始 Agent |
| SSE Emitter | ✅ 保留 | 对接 Graph Streaming API |
| AuthTokenFilter | ✅ 保留 | 透传权限上下文到 AgentSession |
| UserPermissionContext | ✅ 保留 | Graph 节点内通过 ThreadLocal 获取 |

### 3.2 废弃组件（被框架替代）

| 组件 | 状态 | 替代方案 |
|------|------|---------|
| 手写的 ReActEngine | ❌ 废弃 | Spring AI Alibaba ReactAgent |
| 手写的 AgentGraph | ❌ 废弃 | Spring AI Alibaba StateGraph |
| 手写的 AgentState | ❌ 废弃 | Spring AI Alibaba OverAllState |
| 手写的 Checkpoint | ❌ 废弃 | Spring AI Alibaba MemorySaver/RedisSaver |
| Map.of() 硬编码 | ❌ 废弃 | ReactAgent 自动让 LLM 选择 Tool + 传参 |

### 3.3 新增组件

| 组件 | 说明 |
|------|------|
| ReactAgentNode | 包装 ReactAgent 为 Graph Node 的适配器 |
| IntentRouterNode | 包装 IntentRouter 为 Graph Entry Node |
| AtlasToolCallback | 桥接 BaseTool → Spring AI ToolCallback（已有，需微调） |
| AgentSession | 聚合用户查询 + 权限 + 对话上下文 |
| ConversationMemory | 基于 Checkpoint 的对话历史管理 |

---

## 四、关键设计决策

### 决策1：StateGraph 运行时动态组装（非静态配置）

**理由**：每个用户的权限不同，可用的 Tools 不同，因此 Graph 需要根据用户权限**运行时动态构建**。

```java
public StateGraph buildGraphForUser(UserPermissionContext ctx) {
    StateGraph graph = new StateGraph(OverAllState::new);
    
    // 根据权限动态添加可用的 Agent 节点
    if (ctx.canQuery()) {
        graph.addNode("query", new ReactAgentNode(queryAgent));
    }
    if (ctx.canDeploy()) {
        graph.addNode("deploy", new ReactAgentNode(deployAgent));
    }
    // ... 其他 Agent
    
    // 动态路由边
    graph.addConditionalEdge("entry", state -> {
        String agentType = (String) state.get("agentType");
        return ctx.hasPermission(agentType) ? agentType : "__end__";
    });
    
    return graph;
}
```

### 决策2：每个 Agent 独立 ReactAgent 实例（非统一 ChatClient）

**理由**：
- 不同 Agent 的 System Prompt 差异大（运维专家 vs 安全专家）
- 不同 Agent 的 Tool 集合不同
- 不同 Agent 的 maxIterations / retryPolicy 可能不同
- 未来可为不同 Agent 配置不同模型（本地小模型 vs 云端大模型）

但 ChatClient 的底层 ChatModel 可以复用（同一个 HTTP 连接池）。

### 决策3：ConversationMemory 基于 Checkpoint（非独立存储）

**理由**：
- Graph 的 Checkpoint 已保存完整状态（包括 messages 列表）
- 直接复用，无需额外的 Redis/Memory 存储
- 支持断点恢复（技术术语："从上次中断处继续"）

---

## 五、数据流详解

### 5.1 单步查询流（QueryAgent）

```
用户: "查看所有节点状态"
  │
  ▼
AtlasOrchestrator
  ├─ 构建 AgentSession (userQuery="查看所有节点状态", permissions=ADMIN)
  ├─ 构建 StateGraph (entry → query)
  └─ compiledGraph.invokeStream(sessionState, emitter)
       │
       ▼
Entry Node (IntentRouter)
  ├─ L1 Embedding: "query" 域 score=0.92 → 命中
  ├─ L2 规则: queryNodes 关键词命中 → intentId="query_nodes"
  ├─ L3 LLM: 无需触发（L2已命中）
  └─ state.agentType = "query", state.intentId = "query_nodes"
       │
       ▼
Routing → "query" Node
       │
       ▼
QueryAgent (ReactAgent)
  ├─ SystemPrompt: "你是 K8s 查询专家，只能查询..."
  ├─ Tools: [node_query, pod_query, gpu_query, ...] (按权限过滤)
  ├─ LLM Call 1:
  │   输入: "查看所有节点状态"
  │   输出: tool_call { name: "node_query", params: {clusterId: "default"} }
  │   emit("tool_call", {tool: "node_query", params: {...}})
  │
  ├─ Tool Execution:
  │   BaseTool "node_query" 执行 → {nodes: [...], status: "ok"}
  │   emit("tool_result", {tool: "node_query", result: {...}})
  │
  ├─ LLM Call 2: (Tool Result 追加到上下文)
  │   输入: [System, User, Assistant(tool_call), ToolResult]
  │   输出: "集群共有 8 个节点，全部运行正常..."
  │   emit("content", "集群共有 8 个节点...")
  │
  └─ 无更多 Tool Call → COMPLETE
       │
       ▼
Graph End
  └─ emit("done", {steps: 2, tokens: 1532})
```

### 5.2 多步诊断流（DiagAgent）

```
用户: "为什么训练任务一直失败？"
  │
  ▼
DiagAgent (ReactAgent)
  Tools: [pod_status, pod_logs, node_gpu, node_memory, training_job_status]
  maxIterations: 8
  
  Step 1:
    LLM: tool_call { name: "training_job_status", params: {jobName: "train-001"} }
    Tool Result: {status: "Failed", reason: "Pod CrashLoopBackOff"}
    
  Step 2:
    LLM: tool_call { name: "pod_logs", params: {podName: "train-001-xxx"} }
    Tool Result: {logs: "CUDA out of memory..."}
    
  Step 3:
    LLM: tool_call { name: "node_gpu", params: {nodeName: "gpu-node-1"} }
    Tool Result: {gpuUsage: 100%, memoryUsage: 99.2%}
    
  Step 4:
    LLM: 无需更多 Tool → 直接回答
    "您的训练任务失败原因是 GPU 内存不足。
     当前节点 GPU 已被其他任务占满（使用率 100%）。
     建议：1) 减少 batch size  2) 选择空闲节点  3) 等待其他任务完成"
     
  emit("content", "您的训练任务失败原因是...")
  emit("done", {steps: 4, tools: ["training_job_status", "pod_logs", "node_gpu"]})
```

---

## 六、实施路线图（修订版）

### Phase 1: 引入验证（2天）

**目标**：验证 Spring AI Alibaba 可以跑通，确认版本兼容性

| 任务 | 时间 | 产出 |
|------|------|------|
| 添加 Maven 依赖 | 0.5天 | pom.xml 更新，编译通过 |
| 解决版本冲突 | 0.5天 | Spring AI 1.1.6 / 1.1.2 兼容性确认 |
| 最小 PoC：ReactAgent + NodeQueryTool | 1天 | 可运行的 Demo |
| OpenAI 代理连通性验证 | 0.5天 | 确认模型响应正常 |

### Phase 2: 基础迁移（3天）

**目标**：核心架构接入框架

| 任务 | 时间 | 产出 |
|------|------|------|
| ReactAgentNode 适配器 | 1天 | BaseTool → ToolCallback 桥接 |
| AtlasOrchestrator 接入 StateGraph | 1天 | 运行时动态图构建 |
| SSE 流式对接 Graph Streaming | 1天 | 前端无需修改 |
| IntentRouter 接入 Entry Node | 0.5天 | L1-L4 路由保留 |

### Phase 3: Agent 实质化（4天）

**目标**：6个 Agent 全部可用

| 任务 | 时间 | 产出 |
|------|------|------|
| QueryAgent 实质化 | 1天 | ChatClient + Tool 配置 |
| DiagAgent 实质化 | 1天 | ReAct 多步推理 |
| DeployAgent 实质化 | 1天 | 部署操作支持 |
| RBAC/Storage/Network Agent | 1天 | 批量配置 |
| Checkpoint 持久化（Redis） | 0.5天 | 对话恢复 |

### Phase 4: 功能增强（3天）

**目标**：高级功能补齐

| 任务 | 时间 | 产出 |
|------|------|------|
| HITL 高危操作确认 | 1天 | 命令式确认+弹窗确认 |
| Multi-Agent 编排示例 | 1天 | Sequential/Routing 模式 |
| Context Engineering | 0.5天 | 动态 Tool 选择 |
| 可观测性（Metrics/Tracing） | 0.5天 | OpenTelemetry |

### Phase 5: 测试与提交（2天）

**目标**：完整测试 + Review + 提交

| 任务 | 时间 | 产出 |
|------|------|------|
| 端到端测试（10个query） | 1天 | E2E 验证报告 |
| 代码 Review | 0.5天 | Review Log |
| 文档同步 | 0.3天 | 所有文档更新 |
| GitLab + GitHub 双推送 | 0.2天 | 提交记录 |

**总计**：约 14 天（2周）

---

## 七、与旧方案对比总结

| 维度 | 旧方案（手写所有） | 新方案（Spring AI Alibaba） |
|------|-------------------|--------------------------|
| ReAct 引擎 | 手写 300+ 行，需测试打磨 | ✅ 框架内建，生产级 |
| 状态管理 | 手写 Record + 持久化 | ✅ 框架内建，支持 Redis/文件 |
| HITL | 手写 PAUSE 状态机 | ✅ 框架内建，可配置 |
| 多 Agent 编排 | 手写 Graph API | ✅ 框架内建 4 种模式 |
| 循环检测 | 手写 | ✅ 框架内建 |
| 重试策略 | 手写 | ✅ 框架内建 |
| 流式 sse | 已有 | ✅ 框架原生支持 |
| 工具容错 | 手写 wrapCall | ✅ 框架内建 + BaseTool 保留 |
| 可观测性 | ❌ 无 | ✅ OpenTelemetry |
| 学习曲线 | 平缓（自己写自己理解） | 需要先学框架 API |
| 扩展性天花板 | 需要自己实现 | 框架持续演进（A2A/MCP/多模态） |
| 生产稳定性 | 需长期打磨 | ✅ 阿里巴巴生产验证 |
| 开发周期 | 4-6 周 | 2 周 |

---

## 八、相关文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 架构决策记录 | [ADR-008-SPRING_AI_ALIBABA.md](ADR-008-SPRING_AI_ALIBABA.md) | 本方案决策记录 |
| 旧版 P2 方案 | [../../P2_AGENT_SPLIT_ARCHITECTURE.md](../../P2_AGENT_SPLIT_ARCHITECTURE.md) | 已废弃，保留参考 |
| 专家会诊报告 | [ATLAS_V3_1_OPEN_SOURCE_RESEARCH_REPORT.md](ATLAS_V3_1_OPEN_SOURCE_RESEARCH_REPORT.md) | 开源调研报告 |
| Spring AI 设计 | [QUERY_AGENT_FUNCTION_CALLING_DESIGN.md](QUERY_AGENT_FUNCTION_CALLING_DESIGN.md) | Function Calling 设计 |
| Tool 架构设计 | [TOOL_ARCHITECTURE_DESIGN.md](TOOL_ARCHITECTURE_DESIGN.md) | BaseTool 体系设计 |

---

*文档版本: v1.0  
创建: 2026-05-14  
作者: Hermes (Atlas Team)*
