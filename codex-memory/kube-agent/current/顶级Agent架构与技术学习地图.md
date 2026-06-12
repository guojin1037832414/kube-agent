# kube-agent 顶级 Agent 架构与技术点学习地图

> 最后更新：2026-06-12
> 这个文件是长期学习地图，不是历史流水账。旧波次细节保留在 Git 历史和 `codex-memory` 中。

## 一句话架构

`kube-agent` 是一个 Java/Spring 控制平面 Agent：它把用户自然语言输入转成可审计的身份、意图、编排状态、Tool 调用候选、HITL 决策、kube-manager HTTP 请求和只读证据模型。

核心不是“让 LLM 直接操作集群”，而是让 LLM 的候选意图经过服务端 owned 的安全证据链之后，才可能触达真实系统。

```text
Frontend / API caller
  -> Spring Security / AuthTokenFilter / AgentPrincipal
  -> AtlasOrchestrator
  -> AtlasBrain / StateGraph / ReAct / Plan
  -> SafeToolExecutor
  -> ToolRegistry / HITL / Protected params / Audit
  -> KubeManagerHttpClient
  -> kube-manager 8100

Evidence side:
  Observability / Audit / Replay / Eval
  Memory-RAG contracts
  MCP Manifest / Governance
  Top-tier technology read models
  Multi-Agent expert review
  codex-memory recovery mirror
```

## 当前技术栈

| 层 | 当前选择 | 学习重点 |
|---|---|---|
| Java | 17 | 当前主线稳定，Java 21/25 需走兼容矩阵。 |
| Spring Boot | 3.5.14 | 后端控制平面、Security、Web、Actuator。 |
| Spring AI | 1.1.7 | OpenAI-compatible 模型适配，未来升级需证据门。 |
| Spring AI Alibaba | 1.1.2.2 | StateGraph、ReactAgent、Graph 编排能力。 |
| Resilience4j | 2.3.0 | kube-manager HTTP 出口读重试、熔断、bulkhead。 |
| Micrometer / OTel | 当前已接入基础观测 | GenAI 语义约定仍需兼容证据。 |
| ONNX / DJL tokenizer | 本地 embedding 基础 | 完整 RAG runtime 尚未打开。 |

先进技术策略：不盲目追新。OpenAI Responses/Agents、MCP runtime、A2A、OTel GenAI、GraphRAG、reranker、vector store、Spring AI 2、Spring Boot 4、Java 21/25 等都先进入官方来源审查、兼容矩阵、证据就绪、Vue 可视化和 release gate。

## 核心模块学习

### 1. 身份与权限入口

关键文件：

- `AgentSecurityConfig`
- `AuthTokenFilter`
- `AgentPrincipal`
- `AgentPrincipalResolver`
- `UserPermissionContext`
- `AuthController`

学习重点：

- 前端、LLM、请求体都不能直接声明自己是谁。
- kube-manager session / token 进入 kube-agent 后，要变成服务端可信身份快照。
- Spring Security 是主身份来源，历史 ThreadLocal 只作为兼容桥。
- 匿名、过期、跨用户、跨会话状态必须 fail closed。

### 2. HITL 人工确认

关键文件：

- `HITLController`
- `HitlGuard`

学习重点：

- confirm 创建服务端 marker；clarify 只补上下文，不等于授权。
- HITL 是写操作和高风险动作的必要门，但不是唯一门。
- Tool 权限、租户上下文、受保护参数、审计、release evidence 仍要独立成立。

### 3. Tool 执行边界

关键文件：

- `SafeToolExecutor`
- `SafeToolExecutionRequest`
- `ToolRegistry`
- `ProtectedToolParameterFilter`
- `BaseTool`

学习重点：

- LLM/Plan/前端参数只能是候选业务参数。
- `SafeToolExecutor` 是真实 `BaseTool.execute` 的统一入口。
- token、orgId、userId、trace、HITL、audit、release、writeAllowed 等字段必须由服务端证据提供。
- 受保护参数过滤用于防止调用方伪造控制平面字段。

### 4. kube-manager HTTP 出口

关键文件：

- `KubeManagerHttpClient`
- `KubeManagerHttpResiliencePolicy`

学习重点：

- 这是触达 `8100` 的真实外部网络边界。
- GET 读请求可以进入 read retry/circuit/bulkhead。
- POST/PATCH/PUT/DELETE 不允许自动重试，除非未来有幂等、durable audit、post-write readback、HITL 和 release evidence。
- 业务请求缺用户 Token 时禁止透明降级到 sysadmin。

### 5. Graph / ReAct / Plan 编排

关键文件：

- `AtlasOrchestrator`
- `AtlasGraphConfig`
- ReAct / Plan 相关 engine 和 node
- `AtlasToolCallback`

学习重点：

- Graph 状态不是普通 Map，它承载身份、会话、trace、SSE、Tool 结果和安全决策上下文。
- ReAct / Plan 产生的是候选行动，不是执行授权。
- ToolCallback 最终仍必须回到 `SafeToolExecutor`。
- 下一批中文注释应优先覆盖这里，因为这是学习 Agent 编排的主战场。

### 6. MCP 治理

关键文件：

- `McpToolManifestService`
- `McpManifestController`

学习重点：

- 当前 MCP Manifest 是只读能力目录。
- 它帮助外部系统理解可见 Tool 元数据，但不提供 `tools/call`。
- 敏感 READ、写操作、未知风险能力不能因为出现在内部 ToolRegistry 中就被导出。

### 7. Observability / Audit / Eval / Replay

学习重点：

- 顶级 Agent 需要能解释“发生了什么”，而不只是回答“成功/失败”。
- Audit 应保留 redacted evidence，不泄露 raw principal、raw token、raw params、endpoint secret。
- Replay / Eval 使用只读、确定性、脱敏证据，不能反向授权运行时执行。
- CI blocking 必须等待 reviewed trace evidence 和 release gate。

### 8. Memory / RAG

学习重点：

- Memory/RAG 不等于“把文本塞进 prompt”。
- 可信 Memory/RAG 需要 source custody、citation、tenant/privacy、retention/deletion/export、reviewed trace fixtures 和 eval gates。
- 当前主要是契约和读模型，retrieval runtime、vector store、durable memory 写入仍关闭。

### 9. Multi-Agent / Expert Review

学习重点：

- 多 Agent 不应先表现为运行时互相调用。
- 更安全的起点是专家角色、审查轮次、证据来源、blocked shortcuts、disabled runtime actions 和 release gates。
- A2A runtime handoff 只有在 provenance、audit、eval、privacy、human review 和 release evidence 完整后才可考虑。

## 中文注释学习计划

已完成：

- Batch 1：Controller / Security / Principal / HITL。
- Batch 2：Tool / MCP / SafeToolExecutor / kube-manager HTTP outlet。

待推进：

1. Batch 3：Orchestrator / Graph / ReAct / Plan 推理和状态机链路。
2. Batch 4：Memory / RAG / Eval / Observability / Audit 证据链。
3. Batch 5：DTO / support / config / store 支撑代码。

注释标准：

- 解释“为什么存在”，不是逐行翻译。
- 写清输入来自哪里、输出给谁用、不能做什么。
- 对只读、admin-only、外部调用、Tool/MCP/A2A/RAG/kube-manager/LLM、audit/memory 写入等边界要明确说明。
- 测试也要写中文注释，说明保护的契约和学习价值。

## 文档治理学习

2026-06-12 已完成文档清理：

- 当前 docs 树只保留主线 10 个文件。
- 旧 M4/M5 波次报告、旧 v3.1 文档和重复日志从当前树移除。
- Git 历史与 `codex-memory` 负责考古和恢复。

学习重点：文档是 Agent 架构的一部分。过时文档如果与当前文档同权，会变成误导性的隐形需求。一个顶级 Agent 项目需要清晰区分当前入口、长期学习、ADR、恢复记忆和历史证据。

## 下一步学习路线

1. 读 `README.md` 和 `开发路线图.md`，确认当前项目边界。
2. 读 Batch 1/2 已注释代码，理解身份和执行边界。
3. 开始 Batch 3，围绕 `AtlasOrchestrator` 和 `AtlasGraphConfig` 学习 Graph/ReAct/Plan。
4. 每完成一个小切片，都把“代码、测试、文档、恢复记忆、Git 提交”作为一个完整闭环。
