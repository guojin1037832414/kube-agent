# Atlas v3.1 开发指南

## 快速开始

### 1. 环境要求
- Java 17+ (推荐 GraalVM)
- Maven 3.8+
- WSL (Windows Subsystem for Linux)

### 2. 首次启动

```bash
# 进入项目目录
cd ~/kube-agent

# 编译
mvn clean compile

# 启动 (开发模式)
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.ai.openai.api-key=YOUR_KEY"

# 或使用打包方式 (推荐，避免WSL僵尸进程)
mvn clean package -DskipTests
java -jar target/kube-agent-3.1.0-SNAPSHOT.jar \
  --spring.ai.openai.api-key=YOUR_KEY
```

### 3. 本地Embedding模型首次下载

首次启动时会自动从 HuggingFace 下载模型到 `~/.atlas/models/all-MiniLM/`：
```
~/.atlas/models/all-MiniLM/
├── model.onnx           # ONNX模型文件 (~90MB)
└── tokenizer.json       # 分词器配置
```

如果网络受限，可手动下载后放入该目录。

### 4. API密钥配置

```bash
# 方式1: 环境变量 (推荐)
export ATLAS_LLM_API_KEY="sk-REPLACE_WITH_YOUR_KEY"

# 方式2: 启动参数
--spring.ai.openai.api-key=sk-...

# 方式3: ~/.hermes/secure_config.yml (如果你用Hermes管理)
```

### 5. 开发规范

- 所有新增类必须有 **中文注释**
- 提交前必须跑通 `mvn test`
- 修改记录到 `docs/REVIEW_LOG.md`
- 遵循 **专家会诊 → 编码 → Review → 测试 → 记录 → GitLab+GitHub 双推** 流程
- 文档更新门控：新增/修改 Tool、API、Config、Prompt → 必须同步更新契约文档

---

## 模块开发顺序 (Milestone)

```
M0: 地基 — v2.x 基线、23 Plugin、ChatMemory、SSE 流式
M1: 智能引擎 — L1-L4 意图路由、AtlasBrain 决策、StateGraph 编排、6 Worker、109 Tool
  M1.5: HITL SSE 后端闭环（前端弹窗代码完成，待 M3 联调）
M2: 查询全覆盖与质量加固 — 35+ 单元测试、Query E2E ≥95%、硬编码清理
M3: 写操作 + HITL 前端联调 — ThreadLocal→State 重构、浏览器验证 HITL
M4: Plan-and-Execute + Reflection — 多步任务拆解、自我修正循环
M5: 长期 Memory + MCP + 可观测性 — Redis/Chroma、Micrometer、Guardrails
```

> 详细路线图见项目根目录 `ROADMAP.md`

---

## 测试账号

- sysadmin / SuperAdmin@2035
- zhaotiandi / ninePwd!

---

## 常见问题

### WSL僵尸进程
- 永远不要用 `mvn spring-boot:run` 跑长服务
- 永远用 `java -jar` 方式启动
- 检查端口: `netstat -ano | grep 8300` (Windows)
- 杀进程: `taskkill /PID <pid> /F` (Windows)

### kube-manager连接
- WSL Mirrored模式: `localhost:8100` 直连Windows主机
- 检查状态: `curl http://localhost:8100/api/login -X POST ...`
- CLOSE_WAIT风暴 = 后端线程池耗尽，不是网络问题

---

## NIM 写链路安全门学习笔记

`nim_create` 当前仍然保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`，不得在审计波次中直接访问真实 kube-manager `8100` 或执行 `POST /api/{orgId}/deployment`。

未来真正开放 NIM 创建前，至少需要连续满足这些服务端可信关卡：

- trusted policy provider 读取真实 license、角色和组织事实，且不能相信 Tool 入参里的自报权限。
- creation gate 进入 `READY_FOR_SERVER_CONFIRMED_WRITE`，并明确禁止 preflight preview 或 fallback Tool 直通写入。
- HITLController 注入 target 精确为 `nim_create` 的服务端确认，调用方参数里的 `confirmed=true` 不可信。
- audit context 先被 durable audit writer 持久化，并返回 `DURABLE_RECORDED + DURABLE_AUDIT_LOG` receipt。
- mature `kube-manager` 的 `sys_log` 只能作为 durable audit storage 候选证据；它是通用系统日志，不能直接替代 NIM 专用 pre-write audit receipt。
- 未来 NIM audit writer 必须从可信服务端 principal 获取 username/orgId/userId，只写脱敏 params/body 摘要，并区分 pre-write intent 与 post-write result。
- 当前 `NimCreateDurableAuditWriterPlanSupport` 只生成两阶段 writer plan 和 record templates，仍然 `IMPLEMENTATION_HOLD`；计划层不能替代 `DURABLE_RECORDED` receipt。
- 真实 dedicated writer 必须先通过 storage availability gate，再持久化 pre-write intent，POST 结束后持久化 post-write result，两条记录都确认 durable 后才允许签发 receipt。
- 当前 `NimCreateDurableAuditStorageAvailabilityGateSupport` 只生成未来 probe plan，仍然 `storageProbeExecuted=false`、`storageAvailable=false`；真实可用性探测必须在 dedicated writer 服务端边界内完成。
- 当前 `NimCreateDedicatedDurableAuditWriterBoundarySupport` 只生成未来 `NimDurableAuditWriter` 的边界计划和测试替身契约，仍然 `IMPLEMENTATION_HOLD`；test double 只能验证顺序、digest/identity binding 和 fail-closed blocker，不能声称 `storageAvailable=true`、`preWritePersisted=true`、`postWritePersisted=true` 或 `DURABLE_RECORDED`。
- 当前 `NimCreateDurableAuditWriterInterfaceSpecSupport` 只定义未来 `NimDurableAuditWriter` 的 request/response/method/failure/test-double 规格，仍然 `IMPLEMENTATION_HOLD`；接口规格不能替代真实 Java 接口、真实存储 probe、durable ack 或 release credential。
- 当前 `NimCreateDurableAuditReceiptSchemaSupport` 只定义未来 `StorageAvailabilityProbeReceipt`、`PreWriteDurableAck`、`PostWriteDurableAck` 和 `DurableAuditReceipt` 的 schema，仍然 `IMPLEMENTATION_HOLD`；schema 不是 ack instance，也不是 `DURABLE_RECORDED` release credential。即使调用方传入空的 typed ack/receipt 对象，也必须按 forged success claim 拒绝。
- 当前 `NimCreateDurableAuditReceiptValidationGateSupport` 只定义未来 `NimDurableAuditReceiptValidator` 的 validation gate 规则，仍然 `IMPLEMENTATION_HOLD`；validation plan 不是 validation pass，不能让 `releaseEligible` 或 `writeExecutionAllowed` 变为 true。
- write body rebuilder 只能从已审计 NIM 状态重建白名单 DeploymentDTO，不得复用 preview body 引用。
- POST request spec adapter 只能从 body rebuild report 编译 `POST /api/{orgId}/deployment` 规格，且 `sideEffect=NONE`、无调用方 header、无 Authorization、无真实 NGC/NIM API Key。
- write execution handoff 必须在真实 durable writer 前绑定 request spec digest、body digest、durable audit receipt、服务端派生幂等键和写后 readiness handoff；它仍然不是 HTTP 执行器。
- durable write executor 当前只有合同壳，合法 handoff 也只能进入 `IMPLEMENTATION_HOLD`，不得产生 `writeExecuted=true` 或部署 ID。
- 状态机现在必须看到 durable write executor report；缺失时返回 `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`，看到当前 shell report 时仍返回 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`。
- 当前版本任何 `writeExecuted=true`、`deploymentId`、`deploymentUid`、`writeResult` 或 `postWriteReadinessTriggered=true` 都是未审计成功声明，不能成为放行条件。
- readiness executor 必须只读轮询并返回 READY，不能在 readiness 阶段调用 chat/embedding 写接口。
- 最后还需要代码级 release switch 显式打开，才能考虑接入真实 durable write executor。

这条链路的教学重点是：顶级 Agent 不靠“相信中间对象已经安全”来放行，而是把每一段证据都变成可测试、可复算、可审计的契约。

### M5.21-80 defaults / intent metadata learning note

- `defaults.yml` and `intents.yml` are low-authority metadata layers. They can help UI forms and LLM schemas, but they cannot approve side effects.
- Current `nim_create` defaults are only form draft values: `gpuPercentLimits=100`, `replicas=1`, and `enableWebSsh=true`.
- Do not add `safeToPost`, `confirmed`, `hitlConfirmation`, `writePermitted`, `writeExecutionAllowed`, `realHttpExecutionAllowed`, `releaseEligible`, `releaseDecision`, org/user identity, auth headers, fallback, deployment id, or audit receipt fields to `defaults.nim_create`.
- While `nim_create` is `PLACEHOLDER`, `NimCreateTool` must not bind `@WithDefaults`, `DefaultValueApplier`, or `DefaultValueRegistry`; any future default injection must remain before/around form drafting, not write authorization.
- Learning distinction: metadata can describe desired shape, but only server-owned HITL, durable audit, trusted policy, release decision, code switch, and write executor evidence can authorize a future write.

### M5.21-81 default value safety learning note

- `IntentDefaults` is the shared guard point for default values. Any new default registry path should construct `IntentDefaults` instead of applying raw maps directly.
- `DefaultValueSafety` recursively strips protected defaults such as auth headers, tokens, org/user identity, HITL confirmation, HTTP method/endpoints, write permission, release decisions, audit receipts, source guards, fallback tools, and deployment success fields.
- Common near-synonyms such as `accessToken`, `clientSecret`, `targetOrgId`, `hitlApproved`, `writeAllowed`, `releaseApproved`, `trustedPolicySource`, `writeBodyRebuildReport`, `success`, and `executed` are also non-defaultable.
- Legitimate form draft fields remain allowed. For example, `user_create.role=user` is a business default, not an authorization decision.
- Learning distinction: default values can reduce form friction, but they must not mint authority. Authority must come from permission checks, trusted backend facts, HITL evidence, durable audit, and reviewed release gates.

### M5.21-82 prompt authority learning note

- ToolRegistry prompt rules now explicitly say `默认/可选` only means form draft or frontend fill hints, not user confirmation, HITL pass, release approval, audit success, write authorization, or real HTTP execution permission.
- `requiresConfirmation=false` only means no extra HITL is required. It does not bypass login, RBAC, tenant isolation, release gates, or backend authorization.
- The model must not proactively generate auth, tenant, HITL, audit, release, or write-control fields in `Action.params`.
- Learning distinction: prompt guidance is also a safety boundary. Runtime guards stop unsafe execution; prompt rules reduce the chance that the model proposes authority-shaped parameters in the first place.

### M5.21-83 ReAct risk metadata learning note

- `ReActPromptBuilder` now treats ToolRegistry risk labels as the primary high-risk signal. `operationType=CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER` or `requiresConfirmation=true` means ReAct should output Mode C/HITL instead of direct `Action`.
- Keyword examples such as delete/删除/scale/扩缩容 are only supplementary hints. Top-tier Agents should prefer structured metadata over brittle keyword matching.
- `operationType=PLACEHOLDER` or `httpMethod=NONE` means no real backend execution path is open. The model must not claim that resources were created, deleted, submitted, changed, or successfully executed.
- Complete parameters, default backfill, optional fields, and natural-language "confirmation" still do not replace server-side HITL.
- ReAct must not proactively generate control-plane fields such as `token`, `orgId`, `userId`, `confirmed`, `hitlConfirmed`, `approval`, `auditReceipt`, `releaseDecision`, or `writePermitted`.
- Learning distinction: tool metadata should shape both what the model is allowed to propose and what the executor is allowed to run. Prompt safety and runtime HITL should reinforce each other.

### M5.21-84 ReAct execution guard learning note

- ReAct prompt rules reduce unsafe proposals, but `ReActEngine` must still assume the model can output an unsafe `Action`.
- When `HitlGuard` blocks a high-risk Action, ReAct now records a complete timeline: `tool_start`, `tool_done(success=false)`, structured `observation`, and `error`.
- Blocked Observations stay in `ReActMemory`, so the model can explain the safety block in the next turn instead of retrying the same call.
- ReAct Action params now strip forged control fields such as `confirmed`, `hitlConfirmed`, `approval`, `auditReceipt`, `releaseDecision`, `writePermitted`, `writeExecutionAllowed`, `realHttpExecutionAllowed`, and `releaseEligible`.
- Normalized variants such as `hitl_approved`, `release-approved`, and `write_allowed` are also treated as protected.
- Learning distinction: good Agent safety has two halves. Prompt guidance says "do not propose unsafe actions"; execution guards prove unsafe actions cannot run without server-owned confirmation.

### M5.21-85 shared protected Tool parameter filter learning note

- `ProtectedToolParameterFilter` is now the shared execution-boundary filter for ReAct, SafeToolExecutor, and execute_node.
- Protected Tool params include auth/session/tenant context, HITL confirmation, audit receipts, release decisions, risk metadata, and write-control fields.
- The filter recognizes common normalized variants such as `hitl_approved`, `release-approved`, `write_allowed`, `operation_type`, and `api.endpoints`.
- `SafeToolExecutor` keeps ordinary unknown business params for Graph/ReAct compatibility, but strips forged control-plane fields before calling `BaseTool.execute(...)`.
- `execute_node` is stricter: if Plan parameters contain protected fields at any nesting level, it fails closed before delegating to SafeToolExecutor.
- Learning distinction: `DefaultValueSafety` protects configuration/default-value injection, while `ProtectedToolParameterFilter` protects runtime Tool execution authority. They overlap by design, but they are not the same boundary.

### M5.21-86 shared NIM forbidden secret material detector learning note

- `NimForbiddenSecretMaterialDetector` centralizes common forbidden NIM secret key and secret-looking value detection.
- Shared safety helpers must preserve policy differences explicitly. Receipt schema contracts can document forbidden field names such as `Authorization` or `ngcApiKey`, but they must still reject real values such as `Authorization=Bearer ...`.
- Runtime source guard evidence is stricter: any non-null value under secret-bearing keys remains unsafe because release-source claims are caller-visible and non-authoritative.
- The first migration covers only `NimCreateDurableAuditReceiptSchemaSupport` and `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`; follow-up waves should migrate small homogeneous groups and keep blocker codes unchanged.
- Learning distinction: deduplicating safety code is good engineering only when the extracted abstraction makes the security policy matrix more visible, not flatter.

### M5.21-87 NIM write-chain detector migration learning note

- `NimCreateWriteRequestSpecAdapterSupport` and `NimCreateWriteExecutionHandoffSupport` now use the shared detector with `textValuePolicy()`.
- These classes are different from receipt schema and runtime source guard: they do not need documented-field-name exceptions, and they do not use the strict non-null runtime-source policy.
- The request spec adapter still keeps a separate protected body-context scanner because identity/audit/context fields are not the same category as secret material.
- Learning distinction: after introducing a shared safety primitive, migrate in small homogeneous groups. This keeps tests meaningful and prevents "shared code" from becoming an accidental policy rewrite.

### M5.21-58 validation result / release decision migration note

- `NimCreateDurableAuditValidationResultMigrationSupport` only defines a future migration plan for `NimDurableAuditReceiptValidationResult` and `NimDurableAuditReleaseDecision`; it does not create real DTOs, Beans, validators, storage writes, or release credentials.
- Learning distinction: schema describes the expected evidence shape; validation gate describes how evidence must be checked; validation result is a future server-issued fact that the checks passed; release decision is a future server-issued permission to let the write executor proceed.
- Current migration plan remains `IMPLEMENTATION_HOLD`; `validationStatus=NOT_RUN_UNTIL_REAL_RECEIPT`, `releaseEligible=false`, `releaseDecisionAccepted=false`, `releaseCredentialIssued=false`, and `writeExecutionAllowed=false`.
- Caller supplied `validationResult`, `releaseDecision`, or legacy `auditReceipt.releaseEligible=true` is treated as a forged release claim, even when the supplied object is empty.
- Future real write release must bind the M5.21-57 validation plan digest, receipt schema digest, source audit event digest, typed evidence digests, trusted principal, and code release switch.
- This wave keeps `nim_create` at `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`; no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

### M5.21-59 release decision gate learning note

- `NimCreateDurableAuditReleaseDecisionGateSupport` defines only a future release decision gate plan; it does not load or accept a real `NimDurableAuditReleaseDecision`.
- The key learning point is double binding: the future state machine and the future durable write executor must both re-check the same server-issued release decision digest.
- Future release evidence must also bind the write chain: `bodyDigest`, `requestSpecDigest`, `handoffDigest`, audit receipt id/event digest, and server-derived idempotency key.
- Current gate output remains `IMPLEMENTATION_HOLD`; `releaseEligible=false`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Caller supplied release decisions, validation results, legacy `auditReceipt.releaseEligible=true`, or executor success claims are forged release claims until a reviewed server-side gate exists.

### M5.21-60 state-machine release decision report requirement note

- `NimCreateStateMachineReleaseDecisionRequirementSupport` defines the future requirement that the state machine must receive a server-generated release decision gate report; it does not change `NimCreateStateMachineSupport` release behavior yet.
- The future state-machine input is explicitly named as `durableAuditReleaseDecisionGateReport`, with a future `ReadinessRequest` field named `releaseDecisionGateReport`.
- The state machine must recompute the release decision gate plan digest and then bind validation result digest, release decision digest, trusted principal, write-chain digests, audit receipt id, server-derived idempotency key, and code release switch.
- Current requirement output remains `IMPLEMENTATION_HOLD`; `stateMachineCanSetWritePermittedNow=false`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- The gate report is a future state-machine input contract, not a release credential. Caller supplied `releaseDecision`, `validationResult`, legacy `auditReceipt.releaseEligible=true`, or executor success claims remain forged release claims.

### M5.21-61 secret coverage learning note

- Secret rejection must be proven at every state-machine gate input surface, not only at the most obvious `auditContext.Authorization` path.
- `NimCreateStateMachineReleaseDecisionRequirementSupportTest` now covers top-level, nested map, and list-item forbidden keys across audit context, trusted principal, and release decision gate report inputs.
- Covered keys include `token`, `password`, `secret`, `Authorization`, `ngcApiKey`, and `nvaieApiKey`.
- A rejected secret case must never fall through to the positive HOLD path; it must return `REJECTED`, empty `stateMachineRequirementPlan`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Test data should use redacted values when key-name detection is enough; avoid embedding credential-shaped fake tokens unless a test specifically needs value-pattern detection.

### M5.21-62 list-item matrix note

- Secret tests should distinguish input surface and nesting shape. A single list-item test proves recursion exists, but it does not prove every input surface is protected against list-carried metadata.
- The state-machine release decision requirement now tests list-item secret rejection in all three inputs: `auditContext`, `trustedPrincipalSnapshot`, and `durableAuditReleaseDecisionGateReport`.
- This remains test/docs-only and keeps `nim_create` held. The goal is to sharpen evidence before any future state-machine release gate is wired into production behavior.

### M5.21-63 acceptance semantics note

- `releaseDecisionGateReportAccepted=true` means only that the contract shell accepted the input shape and prepared a future state-machine requirement plan.
- It is not a real release decision and is not a state-machine acceptance of write permission.
- The explicit scope is `releaseDecisionGateReportAcceptanceScope=CONTRACT_INPUT_SHAPE_ONLY`; rejected inputs use `NOT_ACCEPTED`.
- `releaseDecisionGateReportAcceptanceIsRealStateMachineRelease=false` and `releaseDecisionGateReportAcceptanceCanEnableWrite=false` must remain false until a reviewed real state-machine release gate exists.
- In safety-critical Agent state machines, every boolean `accepted` field should have a scope, otherwise future code can accidentally treat a planning artifact as permission.

### M5.21-64 non-authoritative boolean note

- `releaseDecisionGateReportAccepted` is now explicitly a compatibility/readability field, not an authoritative release signal.
- Future state-machine code must not consume it alone; `releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed=false` is part of the contract.
- The release path must require scope, digest verification, trusted principal binding, code release switch, and durable executor re-check before any write permission can be considered.
- Prefer machine-readable "not authoritative" flags for compatibility booleans instead of relying only on prose documentation.

### M5.21-65 source guard note

- `M521NimAcceptedBooleanSourceContractTest` scans production source so future code cannot directly read `releaseDecisionGateReportAccepted` as a standalone signal.
- The only allowed production occurrences are the contract shell outputs and the explicit forbidden-shortcut wording.
- Use source-level contract tests when a dangerous compatibility field is easy to search for and easy to misuse later.

### M5.21-66 storage probe executor contract note

- `NimCreateDurableAuditStorageProbeExecutorSupport` defines only a future storage probe executor contract; it does not run a real storage probe, bind a storage client, or issue durable ack evidence.
- The executor contract must consume both the M5.21-53 availability gate report and the M5.21-54 dedicated writer boundary report. A storage probe that is not inside the dedicated writer boundary is a bypass risk.
- Current output remains `IMPLEMENTATION_HOLD`; `storageProbeExecuted=false`, `storageAvailable=false`, `durableAckVerified=false`, `readAfterWriteVerified=false`, `preWriteAllowed=false`, `writeExecutionAllowed=false`, and `durableReceiptCanBeIssued=false`.
- Diagnostic probe snapshots are explicitly non-authoritative. Caller or mock supplied `storageAvailable=true`, `availabilityStatus=AVAILABLE`, `durableAckVerified=true`, `readAfterWriteVerified=true`, `writePermitted=true`, or `receiptStatus=DURABLE_RECORDED` remains a forged success claim.
- Future real implementation must issue a server-side `NimDurableAuditStorageProbeResult` bound to audit event digest, availability plan digest, writer boundary digest, and trusted principal before any pre-write intent can be allowed.

### M5.21-67 storage probe result contract note

- `NimCreateDurableAuditStorageProbeResultSupport` defines the future server-issued `NimDurableAuditStorageProbeResult`; it does not create a real result instance, storage probe receipt, durable ack, or release credential.
- The result contract binds M5.21-66 `probeExecutorPlanDigest` and M5.21-56 `schemaDigest`, then cross-checks both reports share the same audit event, writer plan, availability plan, and writer boundary.
- Current output remains `IMPLEMENTATION_HOLD`; `serverIssuedProbeResultAccepted=false`, `storageAvailable=false`, `storageProbeReceiptIssued=false`, `preWriteAllowed=false`, `writeExecutionAllowed=false`, and `durableReceiptCanBeIssued=false`.
- Caller supplied `probeResult`, `storageProbeResult`, `NimDurableAuditStorageProbeResult`, or `storageProbeReceipt` is rejected even when the object shape is plausible.
- Learning distinction: executor plan says what must happen, typed schema says what evidence should look like, but only a reviewed server-issued result can become future evidence.

### M5.21-68 receipt validation probe result binding note

- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` defines the future requirement that receipt validation must bind the M5.21-67 storage probe result contract before validating any storage probe receipt or durable ack.
- The binding contract consumes the M5.21-67 probe result report and the M5.21-57 receipt validation gate report, then cross-checks audit event, schema, interface spec, writer boundary, writer plan, availability plan, and trusted principal digests.
- Current output remains `IMPLEMENTATION_HOLD`; `storageProbeResultBoundForValidation=false`, `serverIssuedProbeResultAccepted=false`, `validationCanRunNow=false`, `durableReceiptValidationPassed=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Schema-only validation and validation-gate-only shortcuts are forbidden. Caller supplied probe result, storage receipt, validation result, or release decision remains non-authoritative.
- Learning distinction: M5.21-57 `requiredEvidence` describes future evidence rules, while M5.21-68 requires those rules to be bound to a server-issued probe result contract before any real receipt validator can pass.

### M5.21-69 validation result probe binding migration note

- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` defines the bridge requiring future validation result / release decision migration to consume both M5.21-58 and M5.21-68.
- M5.21-58 `migrationPlanDigest` is a migration-plan digest, not a validation PASS, release decision, or release credential.
- Future validation result must bind M5.21-68 `bindingPlanDigest`, `sourceProbeResultContractDigest`, M5.21-58 `migrationPlanDigest`, receipt schema, validation plan, pre/post durable ack digests, trusted principal, and audit event.
- Future release decision must bind the probe binding digest, probe result contract digest, validation result digest, schema/validation plan digest, audit event, trusted principal, and code release switch.
- Current output remains `IMPLEMENTATION_HOLD`; `legacyMigrationReportAloneAllowed=false`, `realValidationResultCreated=false`, `realReleaseDecisionCreated=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Learning distinction: never let an intermediate plan object become a permission credential. A future release path that consumes M5.21-58 without M5.21-68 must fail closed.

### M5.21-70 receipt validation result contract note

- `NimCreateDurableAuditReceiptValidationResultSupport` defines only the future server-issued `NimDurableAuditReceiptValidationResult` value contract; it does not create a real validation result or release decision.
- The contract consumes the M5.21-69 enhanced migration report and requires binding M5.21-69 enhanced migration digest, M5.21-68 probe binding/result digests, M5.21-67 probe executor digest, M5.21-58 migration digest, receipt schema, validation plan, writer interface/boundary/plan, availability plan, audit event, and trusted principal digest.
- Future PASS also requires typed storage probe receipt, pre-write durable ack, post-write durable ack, and final durable receipt digests, with explicit future fields named `storageProbeReceiptDigest`, `preWriteDurableAckDigest`, `postWriteDurableAckDigest`, and `durableReceiptDigest`. None of those are accepted from caller evidence in this wave.
- Current output remains `IMPLEMENTATION_HOLD`; `serverIssuedValidationResultRequired=true`, `callerValidationEvidenceAuthoritative=false`, `legacyMigrationReportAloneAllowed=false`, `realStorageTouched=false`, `validationPassed=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Learning distinction: a validation result is a server-issued fact, not a plan object, schema, gate, binding report, or caller-supplied JSON object with `validationStatus=PASS`.

### M5.21-71 release decision contract note

- `NimCreateDurableAuditReleaseDecisionContractSupport` defines only the future server-issued `NimDurableAuditReleaseDecision` value contract; it does not create a real release decision, release credential, state-machine release, or durable executor release.
- The contract consumes the M5.21-70 validation result contract report and requires binding M5.21-70 `validationResultContractDigest`, future server-issued `validationResultDigest`, future `releaseDecisionDigest`, `codeReleaseSwitchDigest`, audit event, trusted principal, and write-chain digests.
- Future write release also requires `bodyDigest`, `requestSpecDigest`, `handoffDigest`, `auditReceiptId`, and `serverDerivedIdempotencyKey`; these fields must be rechecked by the state machine and durable executor immediately before any real POST can run.
- Current output remains `IMPLEMENTATION_HOLD`; `serverIssuedReleaseDecisionRequired=true`, `callerReleaseEvidenceAuthoritative=false`, `realReleaseDecisionCreated=false`, `releaseDecisionAccepted=false`, `releaseCredentialIssued=false`, `releaseEligible=false`, `writePermitted=false`, and `writeExecutionAllowed=false`.
- Learning distinction: validation fact and release fact are two separate server-issued layers. Caller release evidence, legacy `auditReceipt.releaseEligible`, executor success, and `releaseDecisionGateReportAccepted` are not release credentials.

### M5.21-72 code release switch contract note

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` defines only the future reviewed/server-owned `NimCreateDurableAuditCodeReleaseSwitch` contract; it does not open a real switch or allow write execution.
- The contract consumes the M5.21-71 release decision contract report and requires binding M5.21-71 `releaseDecisionContractDigest`, M5.21-70 `validationResultContractDigest`, future validation/release decision digests, future `codeReleaseSwitchDigest`, trusted principal, audit event, and write-chain digests.
- Future switch-open evidence also requires `codeReviewDigest`, `testEvidenceDigest`, `securityApprovalDigest`, `rollbackPlanDigest`, and `changeWindowDigest`.
- Current output remains `IMPLEMENTATION_HOLD`; `serverOwnedCodeReleaseSwitchRequired=true`, `callerSwitchEvidenceAuthoritative=false`, `realCodeReleaseSwitchOpened=false`, `codeReleaseSwitchDigestVerified=false`, `releaseEligible=false`, `writePermitted=false`, and `writeExecutionAllowed=false`.
- Learning distinction: release switch is a reviewed release-governance fact, not a caller JSON object, environment variable, runtime flag, or legacy config boolean.

### M5.21-73 code release switch runtime binding note

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport` turns the M5.21-72 switch value contract into a future state-machine / durable-executor runtime binding requirement.
- Future state-machine code must consume `codeReleaseSwitchContractReport`, recompute `codeReleaseSwitchContractDigest`, bind server-issued release/validation digests, and reject `nimCreateReleased=true` as standalone authorization.
- Future durable executor code must re-check the same switch digest immediately before real POST and must not trust state-machine `writePermitted`, executor success, runtime flags, environment variables, or legacy booleans alone.
- Owner policy update: kube-manager query/read methods may use local `8100` for real query tests when safely scoped. This does not apply to `nim_create` or other write/create/delete/state-changing capabilities, which remain HOLD/mock-first until explicitly released.

### M5.21-74 code release switch contract report binding note

- `NimCreateStateMachineSupport` now has a `codeReleaseSwitchContractReport` input and validates the M5.21-72 report before any future release path can be considered.
- `NimCreateDurableWriteExecutorSupport` now also requires the same M5.21-72 report before accepting a controlled handoff/request-spec pair.
- The shells recompute `codeReleaseSwitchContractDigest`, reject tampered/forged-open reports, and keep `codeReleaseSwitchDigestVerified=false`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Learning distinction: M5.21-72 is the source switch contract report, M5.21-73 is the runtime-binding requirement, and M5.21-74 wires the report into current shells. None of these are release credentials yet.

### M5.21-75 code release switch runtime source guard note

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` defines a source taxonomy after M5.21-73.
- M5.21-72 and M5.21-73 reports are accepted only as planning/shape evidence, not as release sources.
- Caller/LLM JSON, environment variables, runtime flags, legacy `nimCreateReleased`, state-machine `writePermitted`, durable executor success, backend readback, and storage backfills remain forbidden release sources.
- The matrix tracks dangerous field names such as `codeReleaseSwitchContractReportAcceptedForRelease`, `codeReleaseSwitchDigestVerified`, `writeExecuted`, `deploymentId`, and `writeResult`.
- Learning distinction: a top-tier Agent write path needs source governance, not only value validation. Evidence can be shaped correctly and still come from the wrong source.

### M5.21-76 source guard report binding note

- `NimCreateStateMachineSupport` and `NimCreateDurableWriteExecutorSupport` now both require the M5.21-75 `codeReleaseSwitchRuntimeSourceGuardReport`.
- The durable executor recomputes `sourceGuardMatrixDigest`, binds the same M5.21-72 switch contract digest, and still returns `IMPLEMENTATION_HOLD`.
- The state machine independently validates the same report and checks the durable executor echoes the same source guard/runtime-binding digests.
- A legal full shell remains held by executor HOLD, switch-contract HOLD, and source-guard HOLD; no report can make `writePermitted`, `writeExecutionAllowed`, `realHttpExecutionAllowed`, `writeAttempted`, or `writeExecuted` true.
- Learning distinction: source guard binding means "this evidence source has been checked and is still not enough." It is a required fail-closed guard, not a release credential.

### M5.21-77 source guard binding static contract note

- `M521NimRuntimeSourceGuardBindingContractTest` protects the M5.21-76 binding by reading production source directly.
- The contract requires both current shells to keep `codeReleaseSwitchRuntimeSourceGuardReport` as an input, validation target, digest-binding source, and secret-scan target.
- The contract rejects environment/property/Spring/HTTP/storage/sys_log/8100 shortcuts and direct write-success true flags in the binding shells.
- Learning distinction: static source contracts are useful for architectural invariants that are easy to accidentally remove and hard to notice through ordinary happy-path tests.

### M5.21-78 durable audit writer/probe boundary static contract note

- `NimCreateDedicatedDurableAuditWriterBoundarySupport` now recursively rejects forged success claims hidden inside nested maps and list items.
- `M521NimDurableAuditWriterProbeBoundaryStaticContractTest` protects the dedicated writer boundary, storage probe executor, and wider durable audit/release digest chain at source level.
- The contract keeps future storage/writer/probe integration explicit-review only: no Spring injection, HTTP client, Elasticsearch client, `ISysLogService`, `sys_log`, `8100`, or direct success-state writes may appear in these shells.
- Learning distinction: success-shaped diagnostic data is still caller data unless it is server-issued, typed, digest-bound, and produced by the reviewed side-effect boundary.

### M5.21-79 nim_create Tool entry no-I/O note

- `NimCreateTool` no longer receives `KubeManagerHttpClient`; the public placeholder Tool entry has no runtime HTTP client dependency.
- `M521NimCreateToolEntryStaticContractTest` locks the entry to `httpMethod=NONE`, `apiEndpoints={}`, `PLACEHOLDER`, authenticated access, confirmation required, fail-closed execution, and state-machine HOLD reporting.
- The entry contract forbids HTTP/storage/sys_log/8100 shortcuts and direct write-success state in `NimCreateTool`.
- Learning distinction: for high-risk Agent tools, removing unused dangerous dependencies is a safety feature. A placeholder entry should not be injectable as a writer.

### M5.21-88 durable write-chain shared detector note

- `NimCreateDurableWriteExecutorSupport`, `NimCreateDurableAuditWriterPlanSupport`, and `NimCreateDurableAuditWriterInterfaceSpecSupport` now use `NimForbiddenSecretMaterialDetector.textValuePolicy()` for input secret scanning.
- The migrated policy matches the previous local behavior: forbidden-key values fail when `hasText(value)` is true, and recursive map/list string scanning still rejects Bearer, API key, token, password, secret, NGC/NVAIE, and common cloud key shapes.
- Interface-spec generated `requestContract.forbiddenFields` remains documentation output, not a credential leak. Input metadata that looks like real material, such as `Authorization=Bearer ...`, still fails closed.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` is now the drift guard for seven migrated NIM support classes. Do not reintroduce local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, or `isForbiddenSecretKey(...)` copies in those classes.
- Learning distinction: shared safety utilities are not one-size-fits-all. Choose `textValuePolicy()`, `receiptSchemaPolicy()`, or `strictRecursivePolicy()` by comparing the old security semantics, then lock the choice with policy-boundary tests.
