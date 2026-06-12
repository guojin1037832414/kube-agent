# 以 kube-agent 为例的顶级 Agent 开发学习指南

> 最后更新：2026-06-12
> 适用范围：Phase 1 顶级 Agent Core。NIM / HPC / Slurm / BCM 是 Phase 2 暂停域。

## 1. 学习目标

学习 kube-agent，不是只学“怎么调一个大模型接口”，而是学一个 Agent 后端如何在真实系统里安全地接入身份、工具、外部 HTTP、人工确认、审计、记忆、评测和前端治理面。

本项目的核心观念是：LLM 只能提出候选意图，不能直接获得运行时权力。一个顶级 Agent 要把“候选意图”变成“可审计、可解释、可回放、可阻断的工程动作”。

## 2. 项目地图

当前后端主线：

- `src/main/java/com/atlas/controller`：登录、会话、HITL 等 HTTP 入口。
- `src/main/java/com/atlas/auth`：Spring Security、Principal、ThreadLocal 兼容桥。
- `src/main/java/com/atlas/orchestrator`：聊天流、Graph 调度、SSE、上下文组装。
- `src/main/java/com/atlas/graph`：StateGraph 配置与节点。
- `src/main/java/com/atlas/brain`：`AtlasBrain`、`BrainDecision`、结构化输出解析。
- `src/main/java/com/atlas/react`：手写 ReAct 循环与执行事件。
- `src/main/java/com/atlas/plan`：计划/反思结构。
- `src/main/java/com/atlas/tool`：Tool 注解、注册、参数治理和具体 Tool。
- `src/main/java/com/atlas/tool/execution`：统一 `SafeToolExecutor`。
- `src/main/java/com/atlas/http`：kube-manager HTTP outlet。
- `src/main/java/com/atlas/audit`：审计事件、durable audit、遥测投影。
- `src/main/java/com/atlas/mcp`：MCP manifest/governance 只读治理。
- `src/main/java/com/atlas/memory`：轻量摘要记忆。
- `src/main/java/com/atlas/observability`：admin-only 读模型、Replay、Eval、Top-tier governance。

先读入口：

- `README.md`
- `开发路线图.md`
- `docs/顶级Agent架构与技术学习地图.md`
- `docs/项目使命与当前记忆.md`
- `Tool开发规范.md`
- `codex-memory/kube-agent/current/当前恢复状态.md`

## 3. 一条请求的生命线

一个普通聊天请求大致经过这些阶段：

1. 前端携带 `X-Session-Id: ses_*` 或 Bearer token 调用后端。
2. `AuthTokenFilter` 把服务端保存的 session/token/orgId 桥接成 Spring Security `Authentication` 和 `UserPermissionContext` ThreadLocal。
3. `AgentPrincipalResolver` 解析当前可信主体。
4. `AtlasOrchestrator` 组装用户输入、conversation、traceId、token/orgId、Graph state。
5. `/api/agent/chat/stream` 走主 `supervisorGraph`；`/api/agent/chat/graph` 走实验 `compiledGraph` / `atlasGraph`。
6. `AtlasBrain`、Graph、ReAct 或 Plan 产出 `BrainDecision` 或候选 Tool 调用。
7. 所有真实 Tool 执行必须进入 `SafeToolExecutor`。
8. `SafeToolExecutor` 重新校验权限、租户、HITL、受保护参数和 durable audit gate。
9. `BaseTool` 通过 `KubeManagerHttpClient` 访问 kube-manager 8100。
10. 结果经 SSE、审计、Trace、Eval/Replay 读模型回到前端。

学习重点：每一步都在减少“不可信输入”对运行时权力的影响。

## 4. 身份与租户边界

关键代码：

- `AuthController`
- `SessionStore`
- `AuthTokenFilter`
- `UserPermissionContext`
- `AgentPrincipalResolver`
- `AgentSecurityConfig`

当前规则：

- 登录请求里的 `organizationId` 只作为 kube-manager 登录参数，不是本地可信租户事实。
- 本地 `SessionStore` 里的 orgId 必须来自 kube-manager 响应或本次 token 反查。
- Bearer 认证路径也要恢复 token+orgId 原子上下文。
- `X-Session-Id` 是 `ses_*` 会话索引，不是用户身份，也不是 conversation owner。
- 前端、LLM、请求体中的 `userId` / `role` / `orgId` 都不能成为授权事实。

这轮 review 修复的典型问题：如果登录响应只有 token，而请求带 `organizationId=999999`，旧代码会把请求值当可信 orgId；现在必须用 token 反查或拒绝创建 Session。

## 5. HITL 不是一个按钮

关键代码：

- `HITLController`
- `TimedDecisionCache`
- `HitlConfirmation`
- `HitlGuard`

HITL 的正确理解：

- confirmToken 只能证明“前端拿到了凭证”，不能证明凭证属于当前用户。
- confirm 必须先校验 checkpoint 的 `user_id`、checkpoint orgId、当前 principal 和当前 principal orgId，再消费 token。
- clarify 虽然不需要 confirmToken，也必须先校验 owner，再删除 pending 决策。
- confirm 只注入服务端 `HitlConfirmation` marker；真正 Tool 执行仍要走 `SafeToolExecutor`。
- HITL 通过了，不代表 durable audit、权限、租户、release gate 也通过了。

这轮 review 修复的典型问题：旧顺序是先 `decisionCache.remove(...)` 再做 owner 校验，跨用户失败请求也可能破坏原用户的待确认状态；现在改成 owner-first，并且要求当前主体 orgId 与 checkpoint orgId 同时存在且一致。

## 6. Tool 执行安全链

关键代码：

- `ToolRegistry`
- `ProtectedToolParameterFilter`
- `ToolParameterNormalizer`
- `SafeToolExecutor`
- `BaseTool`
- `KubeManagerHttpClient`

正确执行顺序：

1. 从 `ToolRegistry` 找 Tool 和风险元数据。
2. 校验当前用户是否可见/可执行该 Tool。
3. 过滤 `token`、`orgId`、`userId`、`confirmed`、`audit`、`release` 等受保护字段。
4. 用服务端可信 token/orgId 重新绑定 ThreadLocal。
5. 高风险 Tool 先过 HITL。
6. 高风险写操作再过 durable audit prewrite gate。
7. 执行 `BaseTool.execute(...)`。
8. 记录审计、恢复 ThreadLocal。

学习重点：Tool 的输入 Map 不是安全上下文，只是候选业务参数。子类需要租户时调用 `resolveOrganizationId(params)`，不能从 `params.organizationId` 取值，更不能给默认租户。

## 7. 写操作为什么默认难

顶级 Agent 的写操作不是“LLM 说要创建，所以调用 POST”。它至少需要：

- 显式 `operationType` 风险元数据。
- 当前用户真实 token。
- 可信 orgId。
- 权限校验。
- HITL 确认。
- durable audit 可用。
- 执行前 durable prewrite receipt。
- 幂等键。
- 写后 readback。
- release gate。
- 失败补偿/回滚说明。
- reviewed trace/eval 证据。

当前默认策略：高风险写在 durable audit 不 ready 时 fail-closed。已有历史写 Tool 代码可以存在，但不能被文档或前端当成一期已开放 runtime authority。

## 8. MCP 当前只是治理面

关键代码：

- `McpManifestController`
- `McpToolManifestService`
- `McpGovernanceOverviewService`

当前 MCP 事实：

- `/api/agent/mcp/**` 是 admin-only。
- Manifest / governance 是只读读模型。
- 不开放 MCP runtime server。
- 不开放 `tools/call`。
- 不接受外部 caller 提供 Tool 参数。
- 不导出写 Tool、敏感 Tool、未知风险 Tool、需要 HITL 的 Tool。
- NIM / HPC / Slurm / BCM 二期域 Tool 不进入一期 MCP manifest。

学习重点：MCP 是能力协议，不是权限模型。接 MCP 越容易，越要先把 governance、consent、audit、tool safety 做清楚。

## 9. Memory/RAG 的当前边界

关键代码：

- `MemoryController`
- `ConversationSummaryMemoryStore`
- `AgentMemoryRag*` observability services

当前 Memory 有两层：

- 用户级轻量摘要接口：保存 caller-submitted bounded summary，做基础正则脱敏和截断。
- admin-only Memory/RAG 读模型：描述未来 source custody、citation、digest、lifecycle、eval gate、trace curation 的证据合同。

不能误解的地方：

- 当前摘要不是服务端验证过的事实。
- 当前摘要不是可信 RAG 引用源。
- 当前摘要不能直接自动注入 prompt 成为权威。
- 正则脱敏不是完整 DLP。

未来要打开 RAG prompt influence，必须先有 source custody、tenant/privacy、delete/export、reviewed traces、eval gate、operator visibility。

## 10. Eval / Replay / Observability

关键代码：

- `ObservabilityController`
- `AgentReplayTimelineService`
- `AgentEval*` services
- `AgentAudit*`

当前能力：

- Observability 和 Top-tier 读模型基本是 admin-only。
- Replay / Eval 有确定性、脱敏、admin-only 读模型和部分 suite run/gate 入口。
- Eval 的目的不是给运行时“自动授权”，而是为 release gate、回归测试和学习复盘提供证据。

仍关闭的能力：

- CI blocking promotion。
- LLM-as-judge runtime eval。
- Memory/RAG retrieval eval runtime。
- 未 reviewed trace 的 release authority。

学习重点：Eval 不是装饰品。没有 reviewed trace 和可解释 gate 的 Agent，很难称为顶级。

## 11. Graph / ReAct / Plan 怎么学

建议阅读顺序：

1. `AtlasOrchestrator`：看 HTTP/SSE 入口如何准备 state。
2. `AtlasGraphConfig`：看 StateGraph 节点、条件边、checkpoint 策略。
3. `AtlasBrain`：看自然语言如何变成结构化 `BrainDecision`。
4. `BrainDecision` / `ExecutionContext`：记住它们是候选决策，不是授权事实。
5. `ReActEngine`：看多步 reasoning/action/observation 如何保持执行边界。
6. `PlanEngine`：看计划/反思如何与 Tool 执行分离。
7. `SafeToolExecutor`：确认所有路径最终回到统一执行边界。

下一批中文注释应重点覆盖这里，因为这里是从“会写 Controller”升级到“会设计 Agent 编排”的关键。

## 12. 前端与后端的分工

后端 owned：

- 身份事实。
- Tool 元数据。
- MCP/export policy。
- release / audit / eval / memory readiness。
- 只读治理模型。
- 是否允许执行。

前端 owned：

- 渲染。
- 筛选、搜索、折叠、详情。
- 本地草稿和当前会话 UI 状态。

前端不能 owned：

- 权限。
- release decision。
- HITL marker。
- durable audit receipt。
- kube-manager writeAllowed。
- MCP runtime enablement。

这也是为什么当前 Vue 工作台应优先读后端 GET read models，而不是自己造治理逻辑。

## 13. 一个切片的标准闭环

以这轮 review 为例，一个合格切片应包含：

1. 读代码与测试，确认真实行为。
2. 找文档与代码不一致处。
3. 修最危险的代码边界。
4. 补源码契约或 focused tests。
5. 修文档和学习说明。
6. 更新 `codex-memory/kube-agent/current`。
7. 跑 focused tests、全量 test、validate、diff check。
8. commit + push。

这样做的意义：项目进度、学习记忆和工程质量不会因为会话重启或上下文丢失而断裂。

## 14. 练习题

1. 找一个 READ Tool，解释它从哪里拿 token/orgId，为什么不能信 `params.organizationId`。
2. 跟踪一次 HITL confirm，从 SSE `hitl_request` 到 `SafeToolExecutor`，列出每个 fail-closed 点。
3. 比较 `/api/agent/chat/stream` 和 `/api/agent/chat/graph` 的 state 初始化差异。
4. 选一个 Memory/RAG read model，说明它目前为什么还不能打开 retrieval runtime。
5. 给一个未来写 Tool 设计 release gate checklist，至少包含 durable audit、idempotency、readback、HITL、rollback。

## 15. 当前最重要的安全不变量

- 当前代码事实高于文档，文档冲突时先读代码和测试。
- LLM/Plan/ReAct/前端输出都是候选输入，不是授权事实。
- token/orgId/userId/HITL/audit/release 只能来自服务端可信上下文。
- HITL 确认不是写操作充分条件。
- 高风险写默认 fail-closed，durable audit 缺失不执行。
- MCP governance admin-only，runtime tools/call 关闭。
- Memory summary 不是可信 RAG authority。
- Phase 2 NIM/HPC/Slurm/BCM 暂停，不降低 Phase 1 顶级 Agent Core 标准。
