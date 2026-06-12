# ADR-010: AtlasBrain 手写决策器替代 ReactAgent Supervisor

> **状态**: Accepted  
> **日期**: 2026-05-16  
> **提出者**: Atlas Team

---

> 2026-06-12 现状说明：本文仍是当前 supervisor 方向的重要 ADR，但其中引用的旧
> `docs/archive/**` 文件已从当前 docs 树清理。需要查看旧设计时，请使用 Git 历史或
> `codex-memory` 历史快照。当前代码同时存在 `AtlasBrain`、`StateGraph`、手写
> `ReActEngine` 和统一 `SafeToolExecutor`，不能把 `BrainDecision` 或 ReAct 结果当作授权事实。

## 背景

在 M2 阶段的最初设计中，`supervisor` 节点计划使用 `ReactAgent` 作为分类器：

- 将 33 个 Tool 绑定到一个 ReactAgent
- 让其通过 ReAct 循环（思考 → 行动 → 观察）决定路由目标

但在实际运行中，该方案出现**严重性能问题**：
- ReactAgent ReAct 循环 50+ 轮才收敛到 `direct_answer`
- 单次请求耗时 8-10 秒
- LLM 在 ReAct prompt 中被大量 Tool 描述淹没，无法聚焦分类任务

---

## 决策

弃用 ReactAgent Supervisor，改为**手写 AtlasBrain 决策器**：

| 属性 | 旧方案（ReactAgent Supervisor） | 新方案（AtlasBrain） |
|------|-------------------------------|---------------------|
| 类型 | ReactAgent（ReAct 循环） | Spring @Component（单次 ChatClient.call） |
| 输入 | 用户查询 + 33 个 Tool schema | 用户查询 + 当前用户可见 Tool 列表（权限过滤后） |
| 输出 | 自由文本 → _regex 解析 | 结构化 JSON → `StructuredOutputParser` → `BrainDecision` |
| 耗时 | 8-10s（50+ ReAct 轮次） | <2s（单次 LLM 调用） |
| 可控性 | 低（ReAct 行为不可预测） | 高（固定格式 JSON，schema 校验） |

---

## AtlasBrain 核心设计

```
用户输入 → AtlasBrain.decide(ExecutionContext)
  → buildSystemPrompt(用户查询, 可见Tool列表)
  → chatClient.call(systemPrompt) 返回 JSON
  → StructuredOutputParser.parse() → BrainDecision
  → validateDecision(校验目标Tool可见性 + 高危标记)
  → 返回 BrainDecision

BrainDecision 结构：
  actionType: CALL_TOOL | DELEGATE_AGENT | DIRECT_ANSWER | ASK_CLARIFY | HITL_CONFIRM
  target: 工具名/Agent名/空
  confidence: 0-1
  reasoning: 决策理由
  parameters: Map<String, Object>
  requiredContext: List<String>
```

---

## 影响

- `AtlasGraphConfig` 中删除 `supervisorAgent` ReactAgent Bean
- 新增 `supervisor` Graph 节点：直接调用 `AtlasBrain.decide()`，产出 `BrainDecision`
- 条件边 `supervisor_result` 解析 `BrainDecision.target` 作为 routing key
- Worker Agent（6 个专业 ReactAgent）**保持不变**

---

## 替代方案

- ~~ReactAgent Supervisor~~（性能不可接受，已废弃）
- ~~HuggingFace Text Classification 模型~~（需要大量标注数据，维护成本高）
- ~~纯规则路由表~~（无法处理语义模糊查询，用户体验差）

---

## 相关文件

- `src/main/java/com/atlas/brain/AtlasBrain.java`
- `src/main/java/com/atlas/brain/BrainDecision.java`
- `src/main/java/com/atlas/brain/StructuredOutputParser.java`
- `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java`（supervisor 节点 wrapper）
- 旧 ReAct classifier 设计归档文件已从当前 docs 树清理，保留在 Git 历史 / `codex-memory` 中。
