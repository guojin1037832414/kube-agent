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
  -> AtlasBrain / supervisorGraph / atlasGraph / ReAct / Plan
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
- 前端请求中的 `organizationId` 只用于 kube-manager 登录参数；SessionStore 里的 orgId 必须来自 kube-manager 响应或本次 token 反查。
- Bearer 请求也必须恢复 token+orgId 原子上下文，否则 Graph/Tool 运行时会 fail closed。
- 匿名、过期、跨用户、跨会话状态必须 fail closed。

支撑层补充学习线：

- `LoginRequest.organizationId` 是 kube-manager 登录候选参数，不是 kube-agent 本地可信 orgId。
- `LoginResponse.sessionId` 是 kube-agent 会话句柄，不是 kube-manager JWT，也不是权限本身。
- `SessionData` 只应存在于服务端内存，token 不能返回前端、写日志、进 Memory/RAG 或 prompt。
- `SessionStore` 用 SecureRandom 生成不可猜测 sessionId，并通过 TTL 限制本地会话生命周期。

### 2. HITL 人工确认

关键文件：

- `HITLController`
- `HitlGuard`

学习重点：

- confirm 创建服务端 marker；clarify 只补上下文，不等于授权。
- confirmToken 不是 owner 事实。confirm/clarify 必须先从 checkpoint 校验 `user_id`、当前 principal 和 orgId，再消费 pending 决策。
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
- 高风险写操作默认要求 ready durable audit prewrite；只有 HITL 匹配但 durable audit 缺失时仍必须阻断。

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
- 当前有两条 Graph 路径：`/api/agent/chat/stream` 走主 `supervisorGraph`；`/api/agent/chat/graph` 走实验 `compiledGraph` / `atlasGraph`。
- ReAct / Plan 产生的是候选行动，不是执行授权；Plan 是计划证据，ReAct Action JSON 是候选业务参数。
- ToolCallback 最终仍必须回到 `SafeToolExecutor`。
- Graph 条件边只决定下一站，不授予 Tool、HITL、audit、release 或写入权力。
- Graph `tool_call` 入口现在有快速 fail-closed 守卫：缺失 Tool 目标、缺失可信 orgId、或候选参数夹带 token/orgId/userId/conversationId/HITL/audit/release/write 控制字段时，不创建 `SafeToolExecutionRequest`，只写入未执行状态和前端可见原因。
- Graph State 不应保存 `SseEmitter`、Lambda 等运行期对象；ReAct 过程事件通过 registry 用 sessionId 间接发布。
- `execute_node` 是安全教学样例：先按计划形状 fail-closed，再委托 `SafeToolExecutor` 做最终执行边界。

支撑层补充学习线：

- `AtlasAsyncConfig` 保证跨线程传播服务端可信 token/orgId 快照，并在任务结束后恢复旧值。
- 异步上下文不能从请求体、LLM 参数或前端字段推导身份，否则 Graph/ReAct/Tool 运行时会出现跨用户或跨租户污染。
- `AtlasConfiguration` 只装配意图识别能力；Embedding 和 LLM 分类失败时应降级到规则层，不应阻断服务启动。
- 意图命中只是候选智能信号，不能绕过 `SafeToolExecutor`、HITL、审计、kube-manager 权限、Memory/RAG source custody 或 release gate。

### 6. MCP 治理

关键文件：

- `McpToolManifestService`
- `McpManifestController`

学习重点：

- 当前 MCP Manifest 是 admin-only 的只读治理目录。
- 它是 admin-only 治理读模型，帮助管理员理解可见 Tool 元数据，但不提供 `tools/call`。
- 敏感 READ、写操作、未知风险能力、NIM/HPC/Slurm/BCM 二期域能力，不能因为出现在内部 ToolRegistry 中就被导出。

### 7. Observability / Audit / Eval / Replay

学习重点：

- 顶级 Agent 需要能解释“发生了什么”，而不只是回答“成功/失败”。
- Audit 应保留 redacted evidence，不泄露 raw principal、raw token、raw params、endpoint secret。
- Replay / Eval 使用只读、确定性、脱敏证据，不能反向授权运行时执行。
- 当前允许 admin-only deterministic replay/eval 读模型和部分 suite run/gate 入口；CI blocking、LLM eval、Memory/RAG retrieval eval runtime 必须等待 reviewed trace evidence 和 release gate。

Batch 4 已补中文教学注释后的学习线：

- `AgentAuditEventFactory` 把服务端可信主体、工具元数据和参数“存在性摘要”固化为审计证据。
- `JsonlAgentAuditDurableSink` 只写 redacted durable audit record，不保存 raw reason、raw endpoint 或 raw parameter values。
- `JsonlAgentAuditQueryService` 是 admin-only redacted read model，当前通过有界 JSONL reverse scan 提供查询，retention 仍是 metadata-only。
- `AgentReplayTimelineService` 把审计读模型投影为前端 timeline，不重新执行 Tool/MCP/kube-manager。
- `AgentEvalReportService` 只读 replay DTO，生成 deterministic checks；它是治理信号，不参与 Tool 放行，也不授予 release authority。

### 8. Memory / RAG

学习重点：

- Memory/RAG 不等于“把文本塞进 prompt”。
- 当前 `ConversationSummaryMemoryStore` 只保存调用方提交的 bounded summary，并做基础正则脱敏和截断；它不是可信 RAG 证据源，也不能直接作为 prompt authority。
- 可信 Memory/RAG 需要 source custody、citation、tenant/privacy、retention/deletion/export、reviewed trace fixtures 和 eval gates。
- 当前主要是契约、读模型和轻量摘要缓存，retrieval runtime、vector store、durable memory 写入仍关闭。

Batch 4 已补中文教学注释后的学习线：

- `MemoryRagSourceEvidenceInput` 只接受稳定 ID、SHA-256 digest 和受控枚举，不接受原始文档、原始 prompt、Authorization header 或 token。
- `MemoryRagSourceEvidenceDigestDeriver` 生成 source/chunk/evidence 三层 digest，帮助未来引用、评测、审计和多 Agent 审阅共享同一证据锚点。
- `MemoryRagSourceEvidenceDigestResult` 中 `rawSourceAccepted`、`promptEvidenceAllowedNow`、`boundToIngestionRuntime`、`reusableAcrossTenantScope` 当前都必须保持 false。
- `AgentMemoryRagReadinessService` 和 digest contract service 是 admin-only 只读证据，不执行检索、不调用向量库/LLM/Tool/MCP/kube-manager。

### 8.1 DTO / Store / Config 支撑层

学习重点：

- 顶级 Agent 的安全不是只写在 Controller 和 Tool 里，DTO、Store、Config 也会承载重要边界。
- `ApiResponse.success` 是前端展示状态，不是权限、HITL、audit、eval 或 release 事实。
- `Conversation` 系列 DTO 只表达会话元数据；messages 空数组不代表服务端长期记忆，也不是 RAG 文档来源。
- `ConversationStore` 的 conversationId 只定位资源，详情、改名、删除必须再次按当前可信 owner 收敛。
- 支撑层越基础，越要写清“不能被拿去当什么”，因为后续功能很容易复用这些字段并误解含义。

### 8.2 支撑契约层

学习重点：

- `ToolParameterSpec` 描述 Tool 业务参数 schema、required 和 aliases，只输出给 schema 生成、参数归一化、提示词目录和调试面板；它不是 Tool 权限表，不能声明 token、orgId、userId、HITL、audit、release 或 writeAllowed 等控制字段。
- `SafeToolExecutionResult` 是 SafeToolExecutor 输出给 Graph/SSE/前端的执行回执；`executed`、`success`、`requiresClarification` 分别表达是否进入 Tool、业务是否成功、是否需要补参，不能反向授予权限或 release authority。
- `SafeToolExecutionSource` 只说明候选调用来自 Graph、Plan、ReAct、ToolCallback 或 fallback，来源标签不能绕过 SafeToolExecutor、ToolPermission、HITL、durable audit 或 kube-manager 权限。
- `AgentTraceContext.traceId` 是链路关联 ID，用于日志、SSE、审计、OTel 和回放；它不是身份、租户、Session、审计 receipt、release evidence 或 Tool 授权。
- `IntentDefinition` 是意图目录配置，`IntentResult` 是分类候选结果；confidence、matchedLevel、agent、level 和 rawQuery 只能帮助路由和观测，不能注册 Tool、导出 MCP、跳过权限、批准写操作或授予 release authority。
- `EmbeddingConfig` 和 `L3ClassificationResult` 只增强意图候选；模型路径/模型 ID 属于供应链配置，LLM confidence/reasoning 是不可信建议，必须经过阈值、unknown、intent 白名单和统一安全执行链。
- `IntentDefaults`、`DefaultValueRegistry`、`DefaultValueApplier` 只能补普通表单草稿字段，不能生成或覆盖 token、orgId、userId、HITL、audit、release、writeAllowed、admin 或 kube-manager 写控制字段。
- Tool 校验和权限异常只提供澄清、提示和审计信号；错误码、suggestions、deniedTool、requiredRole、currentRole 都不能泄露敏感事实，也不能被前端或 LLM 解释成可继续执行。

### 8.3 Query / Path / Body Helper

学习重点：

- 很多 Agent 风险发生在“把候选参数拼进真实 HTTP”这一刻。下载任务、课件、模板、TensorBoard、文件/存储和用户变更 helper 的任务，是把 LLM/Plan/前端输入收敛成 kube-manager 可接受的 path/query/body。
- 进入 URL path 的 ID 必须是正整数文本，拒绝 `../42`、`42/extra`、`1?debug=true`、fragment、脚本、负数、小数和空白。
- 资源 ID 只是定位符，不是授权凭证。能否读取或写入仍取决于当前可信 token/orgId、ToolPermission、HITL、audit、release evidence 和 kube-manager 权限。
- 文件/存储 helper 当前只做必填非空收敛，不能证明路径/PVC/挂载点属于当前用户；具体 Tool 仍要依赖成熟后端列表、白名单和后续门禁。
- 高风险用户变更 helper 只能构造最小白名单 body：充值只能传 `userId`、`amount`、`remark`，调用方传入的 token、orgId、approved、writeAllowed、releaseDecision 都必须被丢弃。
- helper 的失败应在本地变成结构化补参/纠错结果，不触发 kube-manager 调用。

### 8.4 Analysis / Catalog Query Helper

学习重点：

- 分析/目录类 helper 负责把自然语言或前端参数收敛成 kube-manager GET query/path，典型领域包括虚拟机详情、repository catalog、sale 产品报价、行业应用、成本账单。
- VM 名称进入 URL path 前必须做 path segment 校验和编码；VM 名称是定位符，不是启动、停止、删除权限。
- repository catalog/tag 查询是敏感只读目录；NIM 相关字段只是目录过滤，不等于镜像拉取、部署创建、NIM 创建或 Phase 2 runtime 恢复。
- sale 产品和租赁金额预估只读取目录/计算报价，不创建订单、不支付、不续费、不确认报价。
- industry app API history 只读调用历史，不传 requestBody、不重放请求、不调用行业应用 API。
- financial analysis 只筛选账单/成本记录，不能把 userId、orgId、paymentId、approved、writeAllowed、releaseDecision 等字段混入 query。
- 这类 helper 的共同原则是：只复制成熟 DTO 审阅过的字段，分页做限幅，布尔/数字做归一化，其他控制字段全部丢弃。

### 9. Multi-Agent / Expert Review

学习重点：

- 多 Agent 不应先表现为运行时互相调用。
- 更安全的起点是专家角色、审查轮次、证据来源、blocked shortcuts、disabled runtime actions 和 release gates。
- A2A runtime handoff 只有在 provenance、audit、eval、privacy、human review 和 release evidence 完整后才可考虑。

## 中文注释学习计划

已完成：

- Batch 1：Controller / Security / Principal / HITL。
- Batch 2：Tool / MCP / SafeToolExecutor / kube-manager HTTP outlet。
- Batch 3：Orchestrator / Graph / ReAct / Plan 推理和状态机链路。
- Batch 4：Memory / RAG / Eval / Observability / Audit 证据链。
- Batch 5 首批：DTO / store / config 支撑层地基。
- Batch 5 第二片：Tool 参数 schema、执行结果、执行来源、trace、意图分类、默认值补参和异常等支撑契约层。
- Batch 5 第三片：query/path/body helper 的 kube-manager 参数收敛边界。
- Batch 5 第四片：analysis/catalog query helper 的 GET-only 白名单与敏感只读边界。
- Orchestrator hardening 第一片：Graph `tool_call` 入口守卫与 SSE 安全停止原因展示。

待推进：

1. Orchestrator hardening 继续：检查 `execute_node`、`react_node`、delegate 子图、Tool result merge、SSE 事件语义和 trace 传播是否一致。
2. Batch 5 收尾支撑类：检查剩余 support/config/test helper 是否还存在未注释的隐性控制面字段。

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
2. 读 Batch 1/2/3 已注释代码，理解身份、执行边界和 Agent 编排状态机。
3. 读 Batch 4 已注释代码，理解 Memory/RAG/Audit/Replay/Eval 如何形成只读、脱敏、不可反向授权的证据链。
4. 读 Batch 5 首批已注释代码，理解 API 响应、登录会话、Conversation 元数据、异步上下文和意图配置为什么也是安全边界。
5. 继续 Batch 5 剩余支撑类。
6. 每完成一个小切片，都把“代码、测试、文档、恢复记忆、Git 提交”作为一个完整闭环。
