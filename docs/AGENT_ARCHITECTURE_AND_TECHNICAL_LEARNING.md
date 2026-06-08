# kube-agent 顶级 Agent 架构与技术点学习地图

> 维护规则：这个文件是长期学习文档，不是一次性审计记录。后续每完成一个重要阶段，都要把新的架构决策、技术点、测试模式和学习要点同步进来。

## 项目定位

`kube-agent` 不只是把 `kube-manager` / `vue-kube-manager` 的功能包成一个 Agent。它的目标是建设一个顶级 Kubernetes / Cloud / HPC Agent，并且把建设过程本身变成可学习、可复盘、可继续演进的教材。

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
