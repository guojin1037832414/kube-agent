# kube-agent 顶级 Agent 架构与技术点学习地图

> 维护规则：这个文件是长期学习文档，不是一次性审计记录。后续每完成一个重要阶段，都要把新的架构决策、技术点、测试模式和学习要点同步进来。

## 项目定位

`kube-agent` 不只是把 `kube-manager` / `vue-kube-manager` 的功能包成一个 Agent。它的目标是建设一个顶级 Kubernetes / Cloud / HPC Agent，并且把建设过程本身变成可学习、可复盘、可继续演进的教材。

2026-06-08 用户进一步明确：一期目标就是完成顶级 Agent 的核心系统，而不是一个缩水版或过渡版 Agent。NIM / HPC / Slurm / BCM 进入二期，只代表这些专项领域插件后置，并不降低一期在架构、安全、编排、工具、前端、观测、评测和教学文档上的顶级标准。

这个项目同时承担两件事：

- 工程目标：生产可用、安全可审计、权限边界清晰、能对接成熟 kube-manager 能力。
- 教学目标：通过真实项目学习 Agent 架构、Tool 设计、ReAct/HITL、安全证据链、测试治理和文档治理。

## 总体架构

```text
User / Frontend
    |
    v
Atlas API / SSE Streaming
    |
    v
Intent Routing
    |-- L1 local embedding / semantic shortlist
    |-- L2 deterministic rules
    |-- L3 LLM classifier
    |-- L4 fallback rules
    |
    v
AtlasBrain / Orchestrator
    |
    |-- Direct answer
    |-- Plan only
    |-- ReAct loop
    |-- Delegate specialist agent
    |
    v
Tool Registry + Tool Metadata
    |
    |-- risk metadata
    |-- permission level
    |-- operation type
    |-- HTTP method / mature endpoint evidence
    |
    v
Safe Execution Boundary
    |
    |-- RBAC / tenant context
    |-- HITL confirmation
    |-- protected parameter filtering
    |-- secret material detection
    |-- fail-closed write guards
    |
    v
kube-manager mature API / offline contract shell / HOLD gate
```

## 核心模块

### 1. Tool 层

Tool 层负责把成熟后端能力变成 Agent 可调用能力。一个高质量 Tool 不只是有 `execute()`，还要有机器可读的风险和接口元数据：

- `httpMethod`
- `apiEndpoints`
- `operationType`
- `requiresConfirmation`
- 权限级别：`PUBLIC` / `AUTHENTICATED` / `ADMIN_ONLY`
- 风险类型：普通读、敏感读、创建、更新、删除、动作类操作

学习重点：Tool schema 是 Agent 的“可行动作空间”。如果 schema 过宽，LLM 会获得不该拥有的行动空间；如果 metadata 不准，HITL、审计和编排层都会失去依据。

### 2. 意图与编排层

当前路线采用多层意图判断与统一编排：

- L1/L2 尽量用本地语义或确定性规则快速解决。
- L3 只在需要语义判断时调用 LLM。
- ReAct 用于多步诊断或需要观察-推理-行动循环的任务。
- Plan-only 用于用户明确要求计划、不执行的场景。
- 高风险动作即使 LLM 选择了 Tool，也必须由执行层重新拦截。

学习重点：顶级 Agent 不能把 LLM 的选择等同于执行许可。LLM 负责建议，执行边界负责授权。

### 3. 安全执行边界

安全边界遵循 defense-in-depth：

- ToolRegistry 只暴露当前身份可用的工具。
- SafeToolExecutor / 执行守卫在每次工具调用前重新检查权限。
- 高风险 Tool 必须 HITL。
- 写操作默认 HOLD，直到证据链、审计、签名、回滚/后验验证都具备。
- 任何 caller-supplied 的 `orgId`、`userId`、`releaseEligible`、`writeExecutionAllowed` 等字段都不能直接成为权限事实。

学习重点：Agent 的安全不是单个 if 判断，而是一条多层证据链。每层都假设上一层可能被提示词、参数或未来代码变更误导。

## 一期与二期范围

2026-06-08 用户明确调整优先级：HPC / Slurm / BCM 与 NIM 相关能力先暂停，统一作为二期项目再继续添加和真实化。

一期验收口径必须保持“顶级 Agent 核心完整实现”：

- 通用 kube-manager Agent 必须形成完整闭环：意图识别、任务规划、Tool 调用、结果解释、失败恢复、trace 回放和前端工作台体验。
- Tool Registry 必须具备高质量元数据、风险分级、权限约束、参数契约和成熟 kube-manager 证据，不靠猜测路径扩展能力。
- Safe Execution Boundary 必须覆盖 READ / SENSITIVE_READ / CREATE / UPDATE / DELETE / ACTION，并对写操作建立 HITL、审计、幂等、前置/后置校验和 fail-closed 策略。
- 多专家流程、测试体系、中文教学文档、恢复记忆和 commit/push 节奏都属于一期顶级标准的一部分，不因专项域延期而减少。

一期能力范围聚焦通用 manager Agent：

- Kubernetes 基础查询与资源状态解释。
- Dashboard、资源监控、日志、事件、命名空间、Pod、Deployment、Service、Ingress 等通用只读能力。
- 文件、镜像、仓库、模板、产品、课程、行业应用、用户/RBAC 等非 HPC/NIM 的 manager 功能块梳理。
- 安全只读接口逐步接入本地 `8100` 做真实查询验证。
- 通用 Tool 元数据、HITL、审计、执行边界、trace、恢复和前端工作台体验。

### 一期顶级 Agent 验收清单

- Core workflow：用户可以通过 Agent 完成通用 kube-manager 的查询、诊断、解释、计划生成、工具调用和失败恢复。
- Tool governance：所有 Tool 都来自成熟 `kube-manager` / `vue-kube-manager` 证据，具备 method、endpoint、operationType、risk、permission、HITL policy 和参数契约。
- Safe execution：所有真实执行都经过统一安全边界，不能由 ReAct、Graph、ToolCallback 或前端参数绕过；未知 Tool、未知风险、缺租户、缺权限、未知参数全部 fail closed。
- HITL / audit：敏感读、高风险写、删除、状态变更和动作类能力必须绑定服务端 HITL marker、审计事件、参数摘要、操作者、租户、traceId 和执行结果。
- RBAC / tenant：权限事实只来自服务端可信上下文，不信任 LLM 或 caller-supplied 的 `orgId`、`userId`、`role`、`confirmed`、`releaseEligible`、`writeExecutionAllowed`。
- Observability：每次请求都有贯穿 intent、plan、tool、HTTP、HITL、audit、final answer 的 trace，可以在前端工作台回放关键证据。
- Frontend workflow：`vue-kube-manager` 工作台能展示计划、风险解释、确认卡片、工具执行、失败原因、重试/恢复和审计摘要。
- Evaluation：保留意图路由、工具选择、参数抽取、多步 ReAct、中文口语、模糊资源名、安全红队和 must-block 用例；高风险绕过类用例必须 100% 阻断。
- Recovery / teaching：每个重要批次都同步架构文档、中文学习笔记、恢复记忆、SHA256 manifest、commit 和 push。

### 多专家协作角色

- Archimedes / 架构专家：守住一期 Core 与二期插件边界，检查模块所有权和长期演进路线。
- Newton / 后端专家：核对 kube-manager controller、DTO、HTTP 方法、`8100` 只读验证顺序和后端语义。
- Boole / 安全测试专家：检查 RBAC、HITL、protected params、审计、trace、红队用例和 fail-closed 门槛。
- Hubble / 前端专家：检查 vue-kube-manager 工作台、确认流、风险展示、结果解释和失败恢复体验。
- Herschel / 可观测专家：检查 trace、metrics、timeline、eval 报告和审计回放。
- Lorentz / 教学文档专家：维护中文注释、架构学习图、技术点说明和跨会话恢复记忆。

二期再恢复：

- HPC / Slurm / BCM 相关查询、作业提交、节点分配、环境模块和集群管理。
- NIM 创建、NIM readiness、NIM durable audit、validation result、release decision、code switch 和真实写执行链路。

学习重点：顶级 Agent 的路线规划也要可审计。暂停不是丢弃，更不是降低一期标准，而是把风险较高、链路较深的专项域能力明确做成二期插件扩展。这样一期可以先把通用 Agent 大脑、执行边界、工具治理、前端闭环、观测评测和教学体系做到顶级，再把 NIM/HPC 等专项域接入同一套强内核。

## kube-manager 功能块覆盖现状

从功能域看，当前 manager 能力分成三种成熟度：

- 已较稳的查询/敏感查询：Kubernetes 基础查询、Dashboard、资源监控、镜像/仓库只读、文件/存储只读、EasyFlow/TensorBoard 只读、课程/行业应用/模板/产品只读、用户/RBAC/组织只读等。
- 可以逐步接入真实 `8100` 验证的查询类：dashboard/resource/file/registry/EasyFlow/TensorBoard 等安全只读接口。接入时要先做 method/path/query/body 契约测试，再做真实返回结构验证。
- 必须继续 HOLD/HITL/mock-first 的高风险动作：deployment create/delete/scale/restart、Helm/Compose install/update/delete/rollback、storage create/delete、user create/delete/enable/disable/recharge、experiment start/stop/delete、image pull/delete/build/push/load、支付/充值、集群变更、环境安装等。

当前还差的 manager 大块：

1. 批量把安全只读能力接入本地 `8100` 做真实查询验证。
2. 给所有写操作建立统一执行边界：HITL、审计、幂等、前置/后置校验、失败恢复。
3. 把真实 RBAC/tenant/license/quota/provider 接入 Agent，而不是信任 caller 自报字段。
4. 把 vue-kube-manager 的真实工作流变成 Agent 工作台体验：确认卡片、风险解释、证据回放、执行追踪、失败恢复。
5. 建立 agent trace、tool-call trace、危险动作红队用例和长期评测集。

学习重点：manager 的“查”可以逐步真实化，“做动作”必须先证据链化。顶级 Agent 不追求最快把按钮变成 Tool，而是先证明这个 Tool 在当前身份、当前证据、当前风险下为什么可以被调用。

## NIM 创建写放行链路

`nim_create` 是当前最重要的教学链路之一。它仍然是 HOLD/mock-first，因为真实创建 NIM 服务属于高风险写操作。

安全链路按阶段演进：

```text
NimDeploymentPreflightTool
    -> NimTemplateMergeSupport
    -> NimCreationGateSupport
    -> NimTrustedPolicySnapshot / Provider
    -> NimCreateStateMachineSupport
    -> NimCreateAuditReadinessSupport
    -> NimCreateAuditWriterSupport
    -> NimCreateReadinessExecutorSupport
    -> NimCreateReadinessHttpAdapterSupport
    -> NimCreateWriteBodyRebuilderSupport
    -> NimCreateWriteRequestSpecAdapterSupport
    -> NimCreateWriteExecutionHandoffSupport
    -> NimCreateDurableWriteExecutorSupport
    -> NimCreateDurableAuditStorageSupport
    -> NimCreateDurableAuditWriterPlanSupport
    -> NimCreateDurableAuditStorageAvailabilityGateSupport
    -> NimCreateDedicatedDurableAuditWriterBoundarySupport
    -> NimCreateDurableAuditWriterInterfaceSpecSupport
    -> NimCreateDurableAuditReceiptSchemaSupport
    -> NimCreateDurableAuditReceiptValidationGateSupport
    -> NimCreateDurableAuditValidationResultMigrationSupport
    -> release decision / code release switch / runtime source guard
```

这条链路的核心思想是：真实 `POST /api/{orgId}/deployment` 不能只靠“用户确认了”或“前面步骤成功了”来放行。它必须同时具备：

- 服务端可信身份快照
- 受控 body rebuild
- 受控 request spec
- 写前/写后 durable audit
- typed ack / receipt
- receipt validation result
- server-issued release decision
- code release switch
- runtime source guard
- durable executor 二次校验

学习重点：顶级 Agent 的写能力不是“能调接口”这么简单，而是能证明“为什么此刻允许调这个接口”。

## 当前重点技术点

### 先进后端工程底座

M5.22-1 引入的是一期顶级 Agent Core 的第一批先进工程底座：

- Spring Boot `3.5.14` 与 Spring AI `1.1.7` 作为当前 Java 17 可验证主线；
- Resilience4j 作为 kube-manager HTTP 出口韧性治理底座；
- Micrometer Tracing + OpenTelemetry OTLP 作为未来全链路 trace 底座；
- Testcontainers 作为真实依赖集成测试底座；
- Maven Enforcer、Surefire/Failsafe、JaCoCo、CycloneDX SBOM、SpotBugs quality profile 和 GitHub Actions 作为 CI/供应链/质量门禁底座；
- 生产敏感配置改为环境变量驱动，避免把本地代理地址、actuator 详情和 DEBUG 日志固化为生产默认。

学习重点：顶级 Agent 的“先进”不等于把版本号推到最高，而是让每次升级都可构建、可测试、可审计、可回滚。Java 21/25、Spring Boot 4 与 Spring AI 2 应通过兼容矩阵逐步验证，不能破坏当前 Java 17 + Spring AI 1.1 稳定主线的恢复能力。

### 统一 Tool 执行内核

M5.22-2 收口了 Spring AI / Graph Bridge `AtlasToolCallback` 入口：

- `AtlasToolCallback` 不再直接调用 `BaseTool.execute(...)`；
- callback 只做 JSON 解析、ToolParameterSpec/alias 归一化和 JSON 序列化；
- 真正执行统一进入 `SafeToolExecutor`，来源标记为 `TOOL_CALLBACK`；
- 可信 `token/orgId/userId` 来自服务端 ThreadLocal / `UserPermissionContext`，不是 LLM JSON；
- 缺少可信 org 上下文时 fail-closed；
- 源码契约测试已把 Graph Bridge 从临时裸执行白名单移除。

学习重点：多 Agent 架构不是“子 Agent 越多越强”。如果每个子 Agent 都能绕过统一执行边界直接调 Tool，系统会变成多个安全语义不一致的入口。顶级 Agent 的正确做法是：所有入口共享同一个执行内核，只把来源作为审计和策略扩展信息。

M5.22-3 继续收口手写 `ReActEngine`：

- ReAct Action 不再直接调用 `meta.instance().execute(...)`；
- 每轮 Action 构造 `SafeToolExecutionRequest`，通过 `SafeToolExecutor` 执行，来源标记为 `REACT_ENGINE`；
- `executionParams` 保留服务端可信上下文，供执行器绑定 ThreadLocal、补齐 `organizationId/userId/conversationId`；
- `timelineParams` 使用 `ProtectedToolParameterFilter.copyWithoutProtected(...)` 生成，只进入 ReAct 记忆和 SSE `tool_start` 事件；
- LLM 伪造的 `orgId/userId/token/confirmed/hitlConfirmed/auditReceipt/releaseDecision/writePermitted` 等字段不会进入业务 Tool，也不会泄露到前端时间线。

学习重点：Agent 的“行动”和“展示”必须分开。执行层需要可信上下文才能访问后端，但前端时间线和 LLM 后续 Observation 不应该看到 token、租户控制字段和发布/审计控制字段。顶级 Agent 不是把所有上下文都塞给模型，而是让模型只看到完成任务所需的最小证据。

M5.22-4 收口 legacy core `AtlasToolCallback`：

- 旧 core callback 不再直接调用 `tool.execute(params)`；
- 旧构造器保留，但内部构建单 Tool 兼容运行时，并委托 `SafeToolExecutor`；
- 新增可注入构造器，测试和未来 Spring wiring 可以传入真实 `SafeToolExecutor`、`ToolParameterNormalizer`、`UserPermissionContext` 和 Tool metadata；
- 缺少可信 orgId 时 fail-closed，LLM JSON 伪造的 `orgId/organizationId/userId/token/confirmed/auditReceipt/writePermitted` 不会进入业务 Tool。

学习重点：安全债务不能因为“旧入口可能很少用”就继续保留。顶级 Agent 的执行边界要求所有可能通向真实 Tool 的路径都共享同一个内核；低频 legacy path 也必须可审计、可测试、可失败关闭。

M5.22-5 收口 `AtlasOrchestrator` legacy fallback：

- 传统 `IntentRouter` fallback 不再做本地 HITL + `tool.execute(toolParams)`；
- `AtlasOrchestrator` 注入 `SafeToolExecutor`，构造 `SafeToolExecutionRequest`；
- 执行来源标记为 `ORCHESTRATOR_FALLBACK`；
- 旧 SSE 与结果润色体验保留，但安全语义由 `SafeToolExecutor` 统一负责；
- `M4Px4ToolExecuteEntrypointContractTest` 的临时直接执行白名单已归零。

学习重点：顶级 Agent 的安全不是“主路径很安全”就够了，fallback 同样是生产攻击面。现在 Graph tool_call、Plan execute_node、Spring AI callback、手写 ReAct、legacy callback 和 Orchestrator fallback 都共享同一个执行边界。以后任何新增路径如果想调用真实 Tool，必须先回答：为什么不能复用 `SafeToolExecutor`？

当前执行边界状态：生产代码中唯一永久真实 `BaseTool.execute(...)` 位置是 `SafeToolExecutor`。

### M5.23 Trace 与 Observability 内核

M5.23-1 开始把“可观测性”从依赖清单推进为可测试的 Agent 运行时对象。第一步不是直接搭 Tempo / Collector / dashboard，而是先建立一条所有入口共享的 traceId 语义线：

- `AgentTraceContext` 使用 ThreadLocal + MDC 绑定当前 Agent trace；
- traceId 默认由服务端生成，格式为 `trc_` + 32 位十六进制；
- 外部 `X-Trace-Id` / checkpoint trace 候选值必须通过长度、字符集和空白控制字符校验，非法值会被丢弃并重新生成；
- `SafeToolExecutionRequest` / `SafeToolExecutionResult` 携带 traceId，`tool_result` 与 Graph updates 也继续透传；
- `AtlasOrchestrator`、`/chat/graph`、`HITLController` resume、`ReActEngine`、Graph `tool_call` / `execute_node`、两个 `AtlasToolCallback` 都进入同一 trace 语义；
- ReAct 的 `thinking/tool_start/tool_done/observation/content/error` 事件都能带 traceId metadata；
- `ProtectedToolParameterFilter` 把 `traceId/trace_id/traceparent/tracestate` 视为控制平面字段，不允许它作为业务参数透传给 Tool。

几个容易混淆的 ID 要分清：

- `traceId`：一次 Agent 请求或恢复链路的观测主键，用来串起 SSE、日志、Tool、HITL、HTTP、审计和最终回答。
- `spanId`：未来接入 OpenTelemetry 后，一次请求中的某个子步骤，如 LLM 调用、Tool 调用、HTTP 出口。
- `auditId`：安全/合规事实的持久化编号，用来证明某个敏感读或高风险动作如何被允许、阻断或确认。
- `conversationId`：产品会话 ID，用来管理用户对话，不等价于运行时 trace。
- `toolCallId`：单次 Tool 调用编号，用来区分同一 trace 下的多次工具调用。

学习重点：traceId 不是日志装饰，而是顶级 Agent 的证据链主键。没有统一 trace，就很难做前端回放、审计查询、红队复盘、SLA 诊断和多 Agent 协同调度。M5.23-1 的价值是先把这条线接进所有执行入口，后续再把它映射到 OpenTelemetry Span、审计表、HTTP header 和前端工作台。

M5.24-1 把这条证据链继续接到 kube-manager HTTP 出口：

- `AgentTraceContext` 保留内部 `trc_` 前缀，便于日志搜索和人工排障；
- 当 traceId 能提取出合法 32 位十六进制 trace-id 时，会生成 W3C `traceparent`，格式为 `00-traceid-spanid-01`；
- `KubeManagerHttpClient` 的 GET / POST / PATCH / PUT / DELETE 和 `resolveOrgId` 桶式搜索统一通过 `applyUserAndTraceHeaders(...)` 写入 `X-Token`、`X-Trace-Id` 和 `traceparent`；
- 非 32hex 的外部网关 trace 只传播 `X-Trace-Id`，不伪造 W3C traceparent；
- fallback login 保持不接入用户业务 trace helper，因为它是认证 bootstrap，不是 Tool 发起的 kube-manager 业务请求；
- `KubeManagerHttpClientTracePropagationTest` 同时锁定真实请求头契约和源码契约，防止未来新增 HTTP 分支时手写 `X-Token` 漏掉 trace。

学习重点：OpenTelemetry 不是从 dashboard 开始的，而是从每个出口都能带同一个证据主键开始的。M5.24-1 让 Agent 内部 trace 能进入成熟 kube-manager 后端，后续才能把 LLM、Tool、HTTP、HITL、audit 和前端回放映射成同一条时间线。

### M5.25 Trace-Aware 审计证据内核

M5.25-1 开始把 trace 证据链接到通用 Agent 审计模型。它不是 NIM 二期 durable audit 链路的继续开发，而是一期通用 Agent Core 的审计内核第一步：

- `AgentAuditEvent` 定义统一审计事件：`auditId`、`occurredAt`、`traceId`、`conversationId`、`userId`、`organizationId`、`intentId`、`toolName`、执行来源、HTTP 风险元数据、HITL 要求、执行结果和参数摘要；
- `AgentAuditOutcome` 建立统一结果词表：`SUCCESS`、`BUSINESS_FAILURE`、`BLOCKED`、`ERROR`；
- `AgentAuditRecorder` 是接口先行，当前使用 `InMemoryAgentAuditRecorder` 做诊断和测试，后续可以替换成数据库、安全日志、Kafka 或 OpenTelemetry event；
- `SafeToolExecutor` 在所有真实 Tool 执行路径上记录审计事件：缺少租户、未知 Tool、权限不足、HITL 阻断、Plan schema 失败、Tool 异常、业务失败和业务成功；
- 权限不足时仍从系统审计视角提取 Tool 风险元数据，这样阻断事件能记录 `DELETE`、endpoint、`requiresConfirmation=true` 等事实，但不会把不可见 Tool 暴露给用户 Prompt；
- `ObservabilityController` 的 `/api/agent/observability/snapshot` 需要服务端管理员身份，只返回脱敏诊断摘要，不返回原始 `userId`、`organizationId`、`conversationId`、完整 reason 或受保护参数名/值；
- 审计诊断快照带 `schemaVersion=agent-audit-snapshot.v1`、`generatedAt` 和 `replayCapabilities`，明确当前只支持最近事件诊断回放，不承诺 durable retention，也不包含原始身份、原因或参数值。

当前事件链可以这样理解：

```text
AgentTraceContext
    -> SafeToolExecutionRequest.traceId
    -> SafeToolExecutor decision / Tool execution
    -> AgentAuditEvent(auditId, traceId, tool metadata, outcome)
    -> InMemoryAgentAuditRecorder diagnostic snapshot
    -> future OpenTelemetry span / durable audit / frontend replay
```

几个设计边界必须记住：

- M5.25 的 in-memory recorder 是诊断证据内核，不是最终合规持久化。它用于证明审计语义和执行边界已经打通。
- 诊断 recorder 失败不能把一个已经成功的只读 Tool 伪装成未执行；否则审计系统反而会篡改事实。当前做法是记录 warn，并保持执行结果不变。
- BaseTool 已经被调用但包装成 `TOOL_EXECUTION_ERROR` 时，对外仍返回 fail-closed 兼容结果；审计事件必须写 `ERROR + executed=true + success=false`，避免取证时把“已尝试调用”误写成“未调用”。
- 高风险写操作未来不能沿用“诊断 recorder 失败也继续”的语义。写操作需要独立的 durable audit pre-write gate：持久化审计不可用时应在 Tool 调用前 fail-closed。
- 参数审计只记录 key、类型、是否受保护、是否 present、数量和截断标记，不记录真实参数值。
- HTTP 诊断摘要进一步隐藏受保护参数名，避免 `token`、`password`、`secret` 等敏感信号通过 observability endpoint 被过度暴露。
- 快照里的 `replayCapabilities` 是教学上很重要的边界声明：顶级 Agent 不只是“能展示数据”，还要告诉前端、测试和运维这些数据能否用于回放、是否有原始证据、是否可长期保留。

学习重点：审计不是“写一行日志”。顶级 Agent 的审计要回答四个问题：谁在什么 trace 下、以什么 Tool 风险元数据、因为什么原因被允许/阻断、是否真的调用了外部能力。M5.25-1 先让这些问题在统一执行边界上都有结构化答案；后续再把这些答案接入 OpenTelemetry span、前端时间线、持久化表和 Agent eval 报告。

### M5.26 审计遥测投影契约

M5.26-1 把 M5.25 的审计事件再推进一步：从“能记录审计事实”变成“能稳定投影到观测和回放系统”。

新增的 `AgentAuditTelemetryProjector` 做两件事：

- 生成稳定内部属性：`atlas.agent.audit.id`、`atlas.agent.trace.id`、`atlas.agent.intent.id`、`atlas.agent.tool.name`、`atlas.agent.operation.type`、`atlas.agent.audit.outcome`、`atlas.agent.tool.executed`、`atlas.agent.tool.success`、参数数量、reason 长度和隐私标记等。
- 生成实验兼容属性：`gen_ai.operation.name`、`gen_ai.tool.name`、`gen_ai.tool.call.id`、`http.request.method`、`otel.status_code`、`error.type` 等，后续接 OpenTelemetry span/event 时可以直接复用。

为什么要分成两层：

- `atlas.agent.*` 是项目自己的稳定教学/工程契约，前端回放、durable audit、Agent eval 都可以长期依赖。
- `experimentalOtelAttributes` 用来吸收 OpenTelemetry GenAI semantic conventions 的最新变化。该规范仍在演进，不能直接把实验字段名固化成数据库主契约。
- 投影层默认脱敏：不包含原始 `userId`、`organizationId`、`conversationId`、完整 reason、endpoint 字符串或参数值，只暴露计数、长度、枚举和 ID。

学习重点：先进观测不是“接上一个 dashboard”这么简单。顶级 Agent 要先把事件语义稳定下来，再映射到外部标准。这样当 OTel / GenAI / A2A / MCP 规范变化时，我们只改 adapter，不改 Agent 的核心证据模型。

### M5.27 审计遥测 Observation 发布

M5.27-1 把 M5.26 的脱敏投影接入 Micrometer Observation。也就是说，审计事件现在不只是能被 admin snapshot 看到，还能进入 Spring Boot / Micrometer / OpenTelemetry 的标准观测链路。

新增的 `AgentAuditTelemetryPublisher` 有三个关键约束：

- 只消费 `AgentAuditTelemetryProjection`，不直接导出原始 `AgentAuditEvent`。这样 raw principal、conversation、reason、endpoint 字符串和参数值不会因为接入 APM 被意外带出去。
- Observation 名称固定为 `atlas.agent.audit`，事件名称固定为 `atlas.agent.audit.recorded`，为后续 trace 查询、dashboard、告警和前端回放提供稳定锚点。
- 低基数字段和高基数字段分开：Tool 名、intent、source、method、operation、outcome、执行/成功布尔值和隐私标记可以进入低基数标签；`auditId`、`traceId`、时间、计数等易爆炸字段只进入高基数字段。

`InMemoryAgentAuditRecorder` 现在会在写入内存诊断快照之后调用 publisher，但 publisher 异常会被吞掉。原因是当前 recorder 仍是诊断链路：审计事实已经记录，APM 后端不可用不能反过来改变 Tool 的业务结果。未来真正高风险写操作则不同，它需要 durable audit pre-write gate，那个边界必须 fail closed。

学习重点：OpenTelemetry 的难点不是“能不能发出去”，而是“什么可以成为指标标签”。顶级 Agent 需要既能追踪每次 Tool 行为，又不能把用户、会话、trace、endpoint、reason 或参数值扩散成高风险观测数据。M5.27-1 让观测链路前进了一步，同时保留了隐私和基数治理。

### 最新技术引入原则

你要求一期就打造顶级 Agent，所以“最新技术”会全部进入一期路线，但分成两层：

- 稳定主线：直接进入可构建、可测试、可提交的生产内核，例如 `SafeToolExecutor`、trace/audit、Resilience4j、Micrometer、OpenTelemetry OTLP、SBOM、质量门禁、MCP 安全 manifest、评测集和前端回放契约。
- 实验矩阵：进入兼容验证和试验分支，例如 Java 21/25、Spring Boot 4、Spring AI 2、OpenTelemetry GenAI semantic conventions、MCP 新规范完整调用层、A2A 多 Agent 互操作协议。

这样做不是降低标准，而是顶级 Agent 的工程纪律：稳定主线负责真实交付，实验矩阵负责吸收最新技术并证明它们不会破坏安全、观测、评测和恢复能力。

### Java 后端技术栈审计

2026-06-08 针对“后端 Java 是否仍是最先进主语言”做了技术栈审计，结论是：Java / Spring 继续作为一期主线是合理且先进的，但升级方式必须是兼容矩阵，而不是盲目追主版本。

- 当前稳定主线：`Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17`；
- 已进入项目底座：Resilience4j、Micrometer Tracing、OpenTelemetry OTLP、Testcontainers、Maven Enforcer、Surefire/Failsafe、JaCoCo、CycloneDX SBOM、SpotBugs、GitHub Actions；
- 兼容矩阵目标：Java 21/25、Spring Boot 4、Spring AI 2；
- 不能直接强切的原因：当前开发机和验证主线是 Java 17，Spring Boot 4 会引入 Spring Framework 7 / Tomcat 11 / Servlet 6.1 等生态跃迁，Spring AI 2 仍需候选线兼容验证。

详细审计文档：`docs/tech-stack/BACKEND_JAVA_TECH_STACK_AUDIT_20260608.md`。

学习重点：顶级 Agent 的“先进”不是版本号最大，而是每个技术选择都能被测试、观测、审计、回滚和教学解释。对控制面 Agent 来说，统一执行边界、trace/audit/eval、HTTP 韧性和发布门禁，比换语言更接近真正的先进性。

### M5.28 kube-manager HTTP 出口韧性治理

M5.28-1 把 HTTP 韧性从“依赖和配置已经存在”推进到真实业务出口。之前 `KubeManagerHttpClient` 使用 Spring Retry 注解，问题是 GET、POST、PATCH、PUT、DELETE 都可能因为网络异常自动重试。对读请求这通常是好事；对写请求则很危险，因为后端可能已经创建、删除或修改了资源，只是响应丢了。

本轮新增 `KubeManagerHttpResiliencePolicy`，并明确分成两条路径：

- READ：`Retry + CircuitBreaker + Bulkhead`。GET 是幂等读，遇到临时网络故障可以自动重试，减少 Agent 因短暂抖动而失败。
- WRITE：`CircuitBreaker + Bulkhead`，但不自动重试。POST/PATCH/PUT/DELETE 在没有 idempotency key、durable audit、HITL 和 release evidence 前，不能被框架悄悄执行多次。

为什么不用 Spring Retry 注解：

- 注解挂在方法上时，很容易让所有 HTTP 方法共享同一种重试语义。
- 控制面 Agent 的写操作需要“证据足够才允许重试”，而不是“异常了就再试一次”。
- Resilience4j 的显式 policy 更适合表达 read/write 分层，也更容易接入指标、熔断状态和后续发布门禁。

学习重点：生产级韧性不等于“所有失败都重试”。顶级 Agent 的韧性必须理解操作语义：读请求追求可用性，写请求优先防止副作用放大。后续只有当写请求绑定 idempotency key、durable audit receipt、HITL confirmation 和 release evidence 后，才能为特定写路径设计受控重试。

### 2026-06-09 Java 后端技术栈审计学习笔记

本轮复核回答了一个很关键的问题：后端继续以 Java 作为主语言是否足够先进？

结论是：Java/Spring 不是短板，反而是当前 Agent Core 的优势。`kube-agent` 的核心不是“聊天脚本”，而是 Kubernetes / kube-manager 控制面：它要处理用户身份、租户、权限、HITL、Tool 风险、审计、trace、HTTP 出口、质量门禁和长期演进。对这类系统，Java/Spring 的类型系统、测试生态、Actuator/Micrometer、Spring Security、Resilience4j、Maven/SBOM 和企业级维护能力非常适合做主线。

但“Java 适合”不代表“已经顶级”。当前真正要补的是闭环：

- 标准安全入口：用 Spring Security `SecurityFilterChain` / `Authentication` 承担全局鉴权，`UserPermissionContext` ThreadLocal 逐步退成兼容桥。
- 持久审计：`InMemoryAgentAuditRecorder` 只适合诊断，未来必须有 append-only durable audit、脱敏查询、保留策略和高风险写 pre-write fail-closed gate。
- 硬质量门禁：SpotBugs、SBOM、coverage、secret scan 和 Agent eval 不能只生成报告，要能阻断发布。
- 评测与记忆：RAG / persistent Memory / Agent eval 要有租户隔离、引用证据、脱敏、可删除和回归报告。
- 协议互操作：MCP 先做 read-only schema adapter；A2A、完整 MCP broker、GraphRAG、virtual threads、Boot 4、Spring AI 2、Java 21/25 先进但要先走兼容矩阵。

学习重点：顶级 Agent 的先进性不是“哪个语言看起来更 AI”，而是主控制面能不能证明每次执行安全、可追踪、可审计、可评测、可恢复。框架版本是入口，工程闭环才是主体。

### Fail-Closed

当证据缺失、来源不可信、格式不完整、digest 不匹配、词表扩展未审查时，系统必须拒绝，而不是降级为“试试看”。

典型实现：

- `HOLD_STATE`
- `REJECTED_STATE`
- `blockedBy`
- `writeExecutionAllowed=false`
- `releaseEligible=false`

### Digest Binding

Digest 用来绑定证据对象，防止对象在链路中被无声替换。

但 digest 不是语义授权。一个攻击者或未审查调用方可以改 JSON 后重新计算 digest，因此还需要闭合 schema、闭合清单和源码级白名单。

### Source-Owned Closed Lists

M5.21-120 到 M5.21-124 连续收口的核心技术点就是闭合清单：

- `requiredFields`
- `failureStatuses`
- `forbiddenSuccessClaims`
- `mustNotReturnTypeInstances`
- `forbiddenShortcuts`

这些清单看起来像普通字符串数组，但在写放行链路里会变成协议词表和未来权限语义。它们必须由生产代码持有，下游消费者做 exact equality，而不是 `contains(...)` 或非空校验。

### Digest-Consistent Forgery Tests

很多测试不是只篡改字段后期待 digest mismatch，而是篡改字段并重新计算 digest。

这种测试证明的是：

- 系统不是仅靠旧 hash 拦截。
- 系统会做语义级协议校验。
- 未来有人扩展 JSON 词表时会被测试拦住。

### Secret Material Detection

NIM 链路明确禁止真实 Authorization、token、password、secret、NGC/NIM API Key 等材料进入计划、schema、receipt、release decision 或 runtime binding。

学习重点：Agent 处理写操作时，敏感凭据不应该成为 LLM 上下文里的自由文本。凭据必须由服务端受控边界按需注入。

## 测试治理模式

当前项目大量使用以下测试方式：

- 单元契约测试：验证 support class 的输出 shape 和 fail-closed 状态。
- mock HTTP contract：验证 mature kube-manager endpoint 的 method/path/query/body。
- source-level static contract：防止未来代码绕过统一边界。
- digest-consistent forgery regression：验证语义闭合，而不是只验证 hash。
- full `mvn -q test`：防止局部改动破坏 Agent 全链路。

学习重点：顶级 Agent 的测试不是只测 happy path。更重要的是证明“该拒绝的东西一定拒绝”。

## 文档治理

每个重要阶段都要同步：

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- 对应 M5.21 审计文档
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`
- 本文件：`docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md`
- `codex-memory/kube-agent/current` 恢复快照

学习重点：对于长期 Agent 项目，文档不是附属物。文档是架构记忆、教学材料和恢复机制的一部分。

## M5.21-139 最新学习笔记

本轮关闭的是 `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 输出、并被 receipt validation result 消费的完整 `enhancedMigrationPlan`：

- 顶层 enhanced migration plan 字段
- `trustedIdentityBinding`
- `probeBindingRequirement`
- `enhancedValidationResultContract`
- `enhancedValidationResultContract.currentTemplate`
- `enhancedReleaseDecisionContract`
- `enhancedReleaseDecisionContract.currentTemplate`
- `migrationSequencePatch`
- `currentDecisionTemplate`
- `failureContract`
- `forbiddenShortcuts`

关键收获：

- `enhancedMigrationPlanDigest` 只能证明 enhanced plan 对象自洽，不能证明新增 key 已经被评审为合法 validation / release 语义。
- `enhancedMigrationPlan` 是 validation result 生成前的桥接协议。它虽然不是 PASS 结果，但会决定未来 validation result 和 release decision 必须绑定哪些上游证据。
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 现在提供 `enhancedMigrationPlanFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditReceiptValidationResultSupport` 现在只接受完整 canonical enhanced migration plan exact equality，同时保留 report 顶层 HOLD、未执行 false 状态、digest、blockedBy、secret/forged-claim 等前置门。
- 本轮新增 digest-consistent forgery：篡改顶层 key、identity map、probe requirement、validation result contract、release decision contract、sequence patch、current decision template、failure contract 和 forbidden shortcuts，重算 `enhancedMigrationPlanDigest` 后仍要求 fail closed。

学习总结：顶级 Agent 的 proof object 安全不是“我认识几个字段，所以我认为它安全”。越靠近 validation result / release decision / write path 的 map，越要让 producer 拥有完整 canonical shape，让 consumer 做 exact equality。用户已经决定 NIM 进入二期，本轮因此作为二期暂停前的安全 checkpoint 保存。

## M5.21-138 最新学习笔记

本轮关闭的是 `NimCreateDurableAuditValidationResultMigrationSupport` 输出、并被 validation-result probe-binding migration 消费的完整 `migrationPlan`：

- 顶层 migration plan 字段
- `trustedIdentityBinding`
- `migrationSequence`
- `validationResultContract`
- `validationResultContract.currentTemplate`
- `releaseDecisionContract`
- `releaseDecisionContract.currentTemplate`
- `legacyCompatibilityPolicy`
- `releaseCredentialRules`
- `failureContract`
- `forbiddenShortcuts`

关键收获：
- `migrationPlanDigest` 只能证明 migration plan 对象自洽，不能证明新增 key 已经被评审为合法 migration 语义。
- `migrationPlan` 不是 release credential，但它会定义未来 validation result 与 release decision 的协议语法，所以仍然是 release-adjacent proof object。
- `NimCreateDurableAuditValidationResultMigrationSupport` 现在提供 `migrationPlanFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 现在只接受完整 canonical migration plan exact equality，同时保留 report 顶层 HOLD、未执行 false 状态、digest、blockedBy、cross-binding、secret/forged-claim 等前置门。
- 本轮新增 digest-consistent forgery：篡改顶层 key、identity map、migration sequence、validation result contract、release decision contract、template、legacy policy、release credential rules、failure contract 和 forbidden shortcuts，重算 `migrationPlanDigest` 后仍要求 fail closed。

学习总结：顶级 Agent 不能把“下游读懂了几个已知字段”当成协议安全。越是未来会影响 validation result / release decision 的 proof map，越要让生产者拥有完整 canonical shape，让消费者只接受 exact equality。这样 schema 扩展必须经过源代码、测试、文档和审查，而不是依靠 digest 自洽悄悄进入 release path。

## M5.21-137 学习笔记

本轮关闭的是 `NimCreateDurableAuditStorageProbeResultSupport` 输出、并被 receipt-validation probe-result binding 消费的完整 `probeResultContract`：
- 顶层 storage probe result contract 字段
- `evidenceBinding`
- `trustedIdentityBinding`
- `requiredFutureFields`
- `currentTemplate`
- `passPrerequisites`
- `failureModel`
- `failureModel.failureStatuses`

关键收获：
- `probeResultContractDigest` 只能证明 contract 对象自洽，不能证明新增 key 已经被评审为合法 storage probe 语义。
- `probeResultContract` 是未来 server-issued storage probe result 与 receipt validation 之间的桥。它虽然现在仍是 HOLD，但未来会影响 receipt validation 是否能接受 storage probe evidence。
- `NimCreateDurableAuditStorageProbeResultSupport` 现在提供 `probeResultContractFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` 现在只接受完整 canonical contract exact equality，同时校验 source digest、source audit digest、trusted principal digest 和 source identity。
- 本轮新增 digest-consistent forgery：篡改顶层 key、evidence map、identity map、required future field list、current template、pass prerequisites、failure model 和 failure status list，重算 `probeResultContractDigest` 后仍要求 fail closed。

学习总结：顶级 Agent 的安全链路里，“hash 正确”不是“语义被批准”。越是靠近未来写放行的 proof object，越不能让下游自己解释一部分 JSON。生产者必须拥有完整形状，消费者必须做 exact canonical equality，这样新字段必须经过代码、测试、文档和审查才能进入授权路径。

## M5.21-136 学习笔记

本轮关闭的是 `NimCreateDurableAuditReceiptValidationGateSupport` 输出、并被两个下游共同消费的完整 `validationPlan`：

- 顶层 validation plan 字段
- `trustedIdentityBinding`
- `validationSequence`
- `requiredEvidence`
- `storageProbeReceipt`
- `preWriteDurableAck`
- `postWriteDurableAck`
- `durableReceipt`
- `releaseDecisionTemplate`
- `failureContract`
- `forbiddenShortcuts`

关键收获：

- `validationPlanDigest` 只能证明 validation plan 对象自洽，不能证明新增 key 已经被评审为合法验证语义。
- `validationPlan` 被 validation-result migration 和 probe-result binding 两个边界消费，所以不能只修其中一个 consumer。
- `NimCreateDurableAuditReceiptValidationGateSupport` 现在提供 `validationPlanFromReport(...)` 作为 producer-owned canonical proof object。
- 下游现在只接受完整 canonical validation plan exact equality，同时校验 source digest、source audit digest 和 source identity。
- 本轮新增 digest-consistent forgery：篡改顶层 key、identity map、required evidence map、四段 nested evidence、validation sequence、release decision template、failure contract 和 forbidden shortcut list，重算 `validationPlanDigest` 后仍要求 fail closed。

学习总结：顶级 Agent 的 proof object 一旦被多个边界消费，就要从 producer 处统一拥有完整协议形状。下游不应该各自“理解一部分 JSON”，因为最宽松的 consumer 会决定整条安全链路的实际强度。

## M5.21-135 学习笔记

本轮关闭的是 validation-result probe-binding migration 消费上游 probe binding report 时的完整 `bindingPlan`：

- 顶层 binding plan 字段
- `trustedIdentityBinding`
- `requiredBindingEvidence`
- `storageProbeResultContract`
- `receiptValidationGate`
- `futureStorageProbeReceipt`
- `validationSequencePatch`
- `currentDecisionTemplate`
- `failureContract`
- `forbiddenShortcuts`

关键收获：

- `bindingPlanDigest` 只能证明 binding plan 对象自洽，不能证明新增 key 已经被评审为合法迁移语义。
- `bindingPlan` 不是最终 release decision，但它会影响未来 validation result migration 是否可以依赖 storage probe evidence，因此它也是 release-adjacent protocol。
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` 现在提供 `bindingPlanFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 现在只接受完整 canonical binding plan exact equality，同时校验 source digest、source audit digest 和 source identity。
- 本轮新增多组 digest-consistent forgery：篡改顶层 key、identity map、evidence map、nested probe contract、decision template、failure contract 和 forbidden shortcut list，重算 `bindingPlanDigest` 后仍要求 migration fail closed。

学习总结：顶级 Agent 的安全协议不只保护最终授权对象，也要保护中间桥接 proof。因为中间 proof 会塑造未来下游“能不能继续往 release path 走”。只要某个 map 会被后续边界消费，它就应该被当成协议对象，由 producer 拥有完整形状，由 consumer 做 exact canonical equality。

## M5.21-134 学习笔记

本轮关闭的是 release decision 消费 validation result report 时的完整 `validationResultContract`：

- 顶层 validation result contract 字段
- `trustedIdentityBinding`
- `evidenceBinding`
- `currentTemplate`
- `passPrerequisites`
- `failureContract`
- `forbiddenShortcuts`
- `requiredFutureEvidenceDigestFields`

关键收获：

- `validationResultContractDigest` 只能证明对象内容自洽，不能证明新增 key 已经被评审为合法验证语义。
- validation result 是 release decision 的直接上游 proof。如果这里允许下游局部解释 map，未来新增字段可能被误读成 release 前置条件已经满足。
- `NimCreateDurableAuditReceiptValidationResultSupport` 现在提供 `validationResultContractFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditReleaseDecisionContractSupport` 现在只接受完整 canonical contract exact equality，同时校验 source audit digest、trusted principal digest 和 source identity。
- 本轮新增多组 digest-consistent forgery：篡改顶层 key、identity/evidence nested map、prerequisite 值、failure contract 和 forbidden shortcut list，重算 `validationResultContractDigest` 后仍要求 release decision fail closed。

学习总结：顶级 Agent 的 release 链路不是“每层读懂上一层 JSON 的一部分”就够了。validation result 这种紧邻 release decision 的 proof object 必须由 producer 拥有完整协议形状；consumer 只接受 exact canonical equality。这样新增验证语义必须经过源码、测试、文档和审查，而不是靠 hash 自洽悄悄进入 release path。

## M5.21-133 学习笔记

本轮关闭的是 code release switch 消费 release decision report 时的完整 `releaseDecisionContract`：

- 顶层 release decision contract 字段
- `validationResultBinding`
- `stateMachineBinding`
- `durableExecutorBinding`
- `allowPrerequisites`
- `currentTemplate`
- `failureContract`
- `forbiddenShortcuts`
- `requiredFutureEvidenceDigestFields`

关键收获：

- `releaseDecisionContractDigest` 只能说明对象被重新 hash，不能说明新增 key 已被安全评审。
- code release switch 比 release gate 更接近真实写放行，所以它不能用局部字段检查来“解释”上游 release decision contract。
- `NimCreateDurableAuditReleaseDecisionContractSupport` 现在提供 `releaseDecisionContractFromReport(...)` 作为 producer-owned canonical proof object。
- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` 现在只接受完整 canonical contract exact equality，同时校验 source audit digest、trusted principal digest 和 source identity。
- 本轮新增多组 digest-consistent forgery：篡改顶层 key、多个嵌套 map、prerequisite 值、failure contract 和 forbidden shortcut list，重算 `releaseDecisionContractDigest` 后仍要求 code switch fail closed。

学习总结：顶级 Agent 不能把“hash 自洽的 JSON”当成“语义可信的授权对象”。越靠近写放行边界，越要让 producer 拥有完整 proof object，consumer 只接受 exact canonical equality。这样未来新增 release 语义必须通过源码、测试、文档和审查，而不是悄悄混进下游可解释的 map。

## M5.21-132 最新学习笔记

本轮关闭的是 release decision gate 消费 migration plan 时的两个上游 contract map:
- `migrationPlan.validationResultContract`
- `migrationPlan.releaseDecisionContract`

关键收获:
- validation result / release decision contract 是 release gate 的上游协议对象, 不是普通说明字段。
- 如果 release gate 只逐字段检查已知字段, 调用方可以追加新的 future authority key, 重新计算 `migrationPlanDigest`, 让旧校验误以为合同仍然有效。
- `NimCreateDurableAuditValidationResultMigrationSupport` 现在提供 producer-owned canonical helper, `NimCreateDurableAuditReleaseDecisionGateSupport` 只接受这些 helper 的 exact equality。
- 本轮新增 digest-consistent forgery: 给 `validationResultContract` / `releaseDecisionContract` 追加 fake fallback key, 重算 digest 后仍要求 release gate fail closed。

学习总结: 顶级 Agent 的安全协议要避免 "consumer 重新理解 producer 的 JSON"。更稳的做法是让 producer 拥有 canonical shape, consumer 复用 producer helper 做 exact validation。这样 schema 扩展必须经过生产者代码、测试和文档, 不会悄悄变成下游授权语义。

## M5.21-131 学习笔记

本轮关闭的是 runtime binding contract 的两个运行时绑定 map：

- `runtimeBindingContract.stateMachineRuntimeBinding`
- `runtimeBindingContract.durableExecutorRuntimeBinding`

关键收获：

- runtime binding map 是 runtime source guard 的输入协议，不是普通说明性 metadata。
- source guard 不能只逐字段确认几个已知字段正确；如果 map 里多出一个未来授权 key，且调用方重新计算了 `runtimeBindingContractDigest`，逐字段校验仍可能放过它。
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport` 现在拥有标准 helper maps，`NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` 只接受这些 helper 的 exact equality。
- 本轮新增 digest-consistent forgery：给 state-machine / durable-executor runtime binding 追加 fake fallback key，重算 digest 后仍要求 source guard fail closed。

学习总结：顶级 Agent 的运行时安全不是等真实 runtime binding 安装后才开始防护。越靠近未来写放行路径的 HOLD contract，越要提前把 map 的 key-set、值、digest 和下游消费者一致性锁死。

## M5.21-130 学习笔记

本轮关闭的是 code release switch contract 的 binding maps：

- `codeReleaseSwitchContract.releaseDecisionBinding`
- `codeReleaseSwitchContract.stateMachineBinding`
- `codeReleaseSwitchContract.durableExecutorBinding`

关键收获：

- binding map 是组件之间的授权合同，不是说明性 metadata。它定义未来 release decision、state machine、durable executor 如何彼此绑定。
- `releaseDecisionBinding` 包含动态 digest 字段，因此 exact 校验要能从上游 release decision report 或 code switch report 重建标准 map，而不是简单硬编码。
- `stateMachineBinding` 和 `durableExecutorBinding` 当前仍处于 HOLD，但越是未来会接近写放行，越要提前关闭 key-set。
- 本轮继续使用 digest-consistent forgery：追加 fake fallback key，重新计算 `codeReleaseSwitchContractDigest`，仍然要求 state machine、durable executor、runtime binding 拒绝。

学习总结：顶级 Agent 的 release 链路要把“组件之间如何互相信任”建模成显式协议。binding map 一旦被下游接受，就可能成为未来授权解释的来源；所以它必须由 producer 拥有，由所有消费者 exact validation。

## M5.21-129 学习笔记

本轮关闭的是 code release switch contract 中两个结构化 map：

- `codeReleaseSwitchContract.currentTemplate`
- `codeReleaseSwitchContract.openPrerequisites`

关键收获：

- 在写放行链路里，map 的 key-set 本身就是协议，不只是普通 JSON 形状。新增一个 key 可能在未来被某个消费者误读成授权事实。
- `currentTemplate` 描述当前 HOLD 状态模板，不能被调用方或未审查集成随意追加 `writePermitted` 类字段。
- `openPrerequisites` 描述未来打开 code release switch 前必须满足的条件，不能被追加 `recheckWaived` 或 `reviewSkipped` 类字段。
- 本轮让生产者 `NimCreateDurableAuditCodeReleaseSwitchContractSupport` 拥有标准 helper maps，并让 state machine、durable executor、runtime binding 三个当前消费者全部 exact equality。
- 测试继续使用 digest-consistent forgery：向 nested map 追加 fake future authority key，重新计算 `codeReleaseSwitchContractDigest`，仍然要求所有消费者拒绝。

学习总结：顶级 Agent 的 proof object 不能只校验“我现在读到的几个字段”。越靠近 release/write authority，越要把字段集合、字段值、digest、来源和多消费者一致性一起闭合。否则今天看似无害的扩展字段，明天可能变成绕过审查的潜伏授权。

## M5.21-128 学习笔记

本轮关闭的是 code release switch contract 输出并被两个下游共同消费的两类词表：

- `codeReleaseSwitchContract.failureContract.failureStatuses`
- `codeReleaseSwitchContract.forbiddenShortcuts`

关键收获：

- code release switch 比 release decision 更接近真实写放行，它描述未来“代码级开关是否打开”。因此它的失败状态和禁止捷径是高权限协议词表。
- 同一个 proof object 如果有多个当前消费者，不能只修其中一个。`NimCreateStateMachineSupport` 和 `NimCreateDurableWriteExecutorSupport` 都会消费 code switch contract，所以两边都必须做 exact equality。
- `codeReleaseSwitchContractDigest` 仍然只是完整性绑定。攻击者或未审查代码可以追加 JSON 字段并重算 digest，因此下游还必须校验 source-owned closed vocabulary。
- forbidden shortcut 是负面授权协议。它告诉未来实现“哪些路径永远不能当成 release approval”，不能被当作普通说明文本。
- 本轮的测试继续使用 digest-consistent forgery：追加 fake future failure/shortcut 值，重新计算 `codeReleaseSwitchContractDigest`，仍然要求状态机和 durable executor 拒绝。

学习总结：顶级 Agent 的安全链路常常不是单生产者单消费者，而是一份 proof object 被多个边界共同消费。闭合协议时要问：“当前谁会读它？”而不是只问“最重要的消费者是谁？”安全性最终由最宽松的当前消费者决定。

## M5.21-127 最新学习笔记

本轮关闭的是 state-machine release requirement 自己输出的两类词表：

- `stateMachineRequirementPlan.failureContract.failureStatuses`
- `stateMachineRequirementPlan.forbiddenShortcuts`

关键收获：

- 有些协议词表即使暂时还没有真实生产下游消费，也应该先由 producer 的生产代码拥有。
- `stateMachineRequirementPlan` 是未来状态机接入 release decision gate report 的桥。它现在仍然 `IMPLEMENTATION_HOLD`，但它的 failure vocabulary 和 shortcut vocabulary 已经接近未来 `writePermitted` 判断。
- 如果这些词表只是测试里零散 `contains(...)`，未来新增下游 consumer 时很容易复制出局部校验，导致协议漂移。
- 把词表提升成 package-private helper 后，后续 state-machine 或 durable executor consumer 可以直接 exact equality，而不是重新手写字符串。
- 本轮也把 `releaseDecisionGateReportAcceptedRequiredCompanionSignals` 改成精确断言，避免 compatibility-only 信号被偷偷扩展成误导性 release 信号。

学习总结：顶级 Agent 的安全不是等真实写路径上线后才补。越接近 release path 的 HOLD contract，越应该提前把协议词汇、失败状态和禁止捷径沉淀成 source-owned closed lists。这样未来接入真实状态机时，新增授权语义必须通过代码评审、测试和文档，而不是悄悄混进 JSON。

## M5.21-126 最新学习笔记

本轮关闭了 M5.21-59 release decision gate 输出给 state-machine requirement 的两类词表：

- `releaseDecisionGatePlan.failureContract.failureStatuses`
- `releaseDecisionGatePlan.forbiddenShortcuts`

关键收获：

- release gate report 比 migration plan 更接近未来写放行边界，因此它的词表更不能被当成可扩展说明文本。
- state-machine requirement 不能只检查几个关键 failure status 或 forbidden shortcut 是否存在；它必须确认整个词表与 producer 源码拥有的词表完全一致。
- 对 release-proof protocol list 来说，version skew 应该 fail closed。旧 producer、新 consumer、额外字段、缺失字段或乱序字段都不应该被“兼容性”吞掉。
- 测试继续采用 digest-consistent forgery：篡改 `releaseDecisionGatePlan`，重新计算 `releaseDecisionGatePlanDigest`，仍然要求下游拒绝。

学习总结：越接近真实写执行，越要减少“宽容解析”。顶级 Agent 的 release protocol 需要严格、可审查、可同步演进，而不是像普通配置一样随意扩展。

## M5.21-125 最新学习笔记

本轮关闭了 M5.21-58 validation result migration 自己输出并被下游消费的两类词表：

- `migrationPlan.failureContract.failureStatuses`
- `migrationPlan.forbiddenShortcuts`

关键收获：

- `migrationPlanDigest` 是完整性证据，不是语义批准。
- 生产者必须在源码里拥有标准词表。
- 当前所有消费这个 proof object 的下游都必须校验同一份源码词表，而不能只修最靠近 release 的一个消费者。
- `contains(...)`、`containsAll(...)` 和非空校验不适合 release-proof protocol list，因为攻击者或未审查代码可以追加新值并重新计算 digest。
- 多专家审查很有价值：本轮并行审查发现了相邻的 M5.21-69 consumer，所以 release gate 和 probe-binding migration 两条消费路径都被同步关闭。

学习总结：顶级 Agent 要把 proof object 当成协议，而不是普通 JSON。给协议对象做 hash 是必要的，但允许的协议词表仍然必须闭合、可审查、可测试。

## M5.21-124 最新学习笔记

本轮关闭了 validation gate 自己输出给 validation result migration 的两类词表：

- `validationPlan.failureContract.failureStatuses`
- `validationPlan.forbiddenShortcuts`

关键收获：

- forbidden shortcut 不是提示文本，而是未来 release decision 的负面协议。
- failure status 不是日志枚举，而是未来 validation result 的失败语义。
- 下游 migration 不能只检查列表非空或包含几个关键值，必须精确匹配上游源码拥有的清单。
- 测试必须重算 `validationPlanDigest`，证明系统拒绝的是语义扩展，不是旧 digest。

## 后续学习路线

建议后续按这条路径继续学习和建设：

1. 完成 NIM 写放行链路的所有闭合协议清单。
2. 把 validation result / release decision / code switch 的证据对象继续收口。
3. 逐步把只读 kube-manager 成熟查询能力接入真实 8100 验证。
4. 在真实写能力释放前，完成 durable writer、receipt validator、release signer、runtime switch 的端到端审计。
5. 把每个安全模式沉淀成可复用组件，而不是散落在单个 Tool 里。
