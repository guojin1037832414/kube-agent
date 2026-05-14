# ReAct Engine + TaskClassifier 集成方案

> 调研时间: 2026-05-14  
> 对应代码: `src/main/java/com/atlas/react/ReActEngine.java`, `TaskClassifier.java`

---

## 一、ReAct 模式调研结论

### 1.1 Java 生态中的 ReAct 实现

| 方案 | 存在性 | 说明 |
|------|--------|------|
| **LangChain (Python)** | ✅ 原生 | `langchain.agents.react.agent.py` — 开源标杆 |
| **langchain4j (Java)** | ⚠️ 部分存在 | `dev.langchain4j.agent.tool.ReActTool` — 有 ReAct 工具但无完整引擎 |
| **Spring AI (Java)** | ❌ 无原生 | 提供 `Function Calling` API，但无 ReAct 状态机 — 需手写 |
| **手写实现** | ✅ 推荐 | 基于 `ChatClient` + `ToolCallback` 构建循环 |

**结论**: Java/Spring AI 生态中无现成 ReAct 引擎，必须手写。

### 1.2 关键设计决策

| 问题 | 决策 | 理由 |
|------|------|------|
| 是否使用 Spring AI 自动 Function Calling | **否** | 自动模式下 Thought 过程不可见，无法 SSE 流式推送 reasoning |
| Action ↔ Tool 映射方式 | **手动解析** | LLM 按 ReAct Prompt 输出 `Action: toolName{...}` 文本，由正则解析 |
| Observation 回传 | **ToolResponseMessage** | 符合 OpenAI function calling 消息格式，兼容未来切换 |
| 终止条件 | 3种 | ① Final Answer ② `__COMPLETE__` 标记 ③ max_steps=10 |

### 1.3 核心循环结构

```
Step 1: LLM 输出 → 解析 Thought + Action → emit SSE(thought)
Step 2: 执行 Tool(Action) → 得到 Observation → emit SSE(tool_result)
Step 3: 组装 AssistantMessage(Thought+Action) + ToolResponseMessage(Observation)
Step 4: 追加到 messages 列表 → 回到 Step 1
```

---

## 二、TaskClassifier 设计结论

### 2.1 分级模型

| 级别 | 场景 | 来源 | 确认方式 | 现有对应 |
|------|------|------|----------|----------|
| **L1** | 无效输入/参数缺失 | IntentRouter 返回 unknown | 澄清追问 | `unknown` 意图 |
| **L2** | 低危操作(创建/部署/更新) | intents.yml `level=p1/p2` | 前端弹窗 | `deploy_create`, `nim_create`... |
| **L3** | 纯查询(节点/GPU/日志) | intents.yml `level=p3` | 免确认 | `node_query`, `gpu_query`... |
| **L4** | 高危操作(删除/重启/降配) | intents.yml `level=p0` | 命令式确认 | `deploy_delete`, `user_delete`... |

### 2.2 与现有系统的集成点

```
AtlasOrchestrator.streamChat()
    ├── IntentRouter.route(query) → IntentResult
    ├── [NEW] TaskClassifier.classify(IntentResult, params) → ClassificationResult
    │       └── 若 requiresConfirmation → 进入 HITL 流程
    ├── [NEW] 若 Agent 需要 ReAct
    │   └── ReActEngine.run(query, tools, context, eventSink)
    └── 普通单步执行
```

### 2.3 数据来源

- **权威来源**: `intents.yml` 中的 `level` 字段（`p0/p1/p2/p3`）
- **兜底规则**: 意图 ID 前缀推断 + 操作关键词匹配

---

## 三、文件清单

| 文件 | 说明 |
|------|------|
| `com.atlas.react.ReActEngine` | ReAct 推理引擎（状态机 + SSE 事件） |
| `com.atlas.react.ReActResult` | ReAct 执行结果（包含完整推理链） |
| `com.atlas.react.ReActStep` | 单步执行记录（Thought/Action/Observation） |
| `com.atlas.react.ReActEvent` | SSE 事件模型（thinking/thought/tool_call/tool_result/answer/error） |
| `com.atlas.orchestrator.TaskClassifier` | 任务分级器（L1~L4） |

---

## 四、后续集成 TODO

1. **AtlasOrchestrator 改造**: 在 `streamChat()` 中插入 `TaskClassifier.classify()` 调用
2. **HITL 接入**: `ConfirmationType.POPUP` → 前端弹窗；`COMMAND` → 命令式确认接口
3. **Agent ReAct 化**: `AtlasAgentBase` 增加 `supportsReAct()` 标记，DeployAgent → `true`
4. **ToolCallback 转换**: `BaseTool` → `MethodToolCallback`（需 Spring AI `@Tool` 注解）
5. **会话持久化**: ReAct 中断恢复（HITL 确认后从上次 step 继续）
