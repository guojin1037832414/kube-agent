# M5.21 第四十八批 NIM 写执行交接与幂等契约审计

> 日期: 2026-06-07 05:54 Asia/Shanghai
> 范围: `NimCreateWriteExecutionHandoffSupport`、`NimCreateStateMachineSupport`、NIM create 写链路相关测试
> 约束: 只新增 mock-first/纯函数写执行交接契约；不注册 Tool，不新增 Controller，不持有 HTTP client，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 一键部署最终会由 kube-manager 处理 Deployment 创建。
- M5.21-46 已经把 POST body 重建合同化，M5.21-47 已经把 `POST /api/{orgId}/deployment` request spec 合同化。
- 但 request spec 仍不是执行许可。如果未来 durable write executor 直接消费 request spec，就还缺少:
  - 服务端派生幂等键；
  - pre-write durable audit receipt handoff；
  - write 后 readiness executor handoff；
  - executor 边界、重试策略、digest 复核和 secret 防泄漏契约。
- 因此本批新增 request spec adapter 与未来 durable write executor 之间的 fail-closed handoff 层。

## 多专家会诊

- Backend/API 专家:
  - handoff plan 必须固定表达未来写入目标 `POST /api/{orgId}/deployment`，但当前 `networkAccess=NOT_PERFORMED`。
  - resolved path 必须由 trusted orgId 派生，不能来自 Tool 入参。
- Security/RBAC 专家:
  - 幂等键必须服务端从 audit/request spec 证据派生，禁止调用方提供。
  - handoff 必须绑定 durable audit receipt、body digest 和 request spec digest。
  - caller header、Authorization、真实 NGC/NIM API Key、token/password/secret 继续 fail-closed。
- Agent 架构专家:
  - `writeExecutionHandoffReport` 是 future durable writer 前的独立关卡，不是 writer 实现，也不是 release credential。
  - handoff 只声明 `FUTURE_DURABLE_WRITE_EXECUTOR`，不能持有或调用 HTTP client。
- Test 架构专家:
  - 状态机绿色路径必须同时携带 body rebuild、request spec、execution handoff 和 READY readiness report。
  - 缺 handoff、伪造 digest、receipt/spec 绑定不一致、secret 泄漏都要阻断。
- Documentation/Learning 专家:
  - 本批教学重点是“执行交接也要合同化”：顶级 Agent 在真正副作用之前要有幂等、审计、后置观测的完整证据链。

## 变更摘要

- 新增 `NimCreateWriteExecutionHandoffSupport`。
  - `prepare(...)` 消费:
    - `creationGate`
    - `auditContext`
    - `auditReceipt`
    - `writeBodyRebuildReport`
    - `writeRequestSpecReport`
  - 输出:
    - `writeExecutionHandoff=NIM_CREATE_WRITE_EXECUTION_HANDOFF`
    - `executionMode=WRITE_EXECUTION_HANDOFF_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeExecutionPrepared`
    - `futureExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
    - `realHttpExecutionAllowed=false`
    - `preWriteAuditRequired=true`
    - `idempotencyRequired=true`
    - `idempotencyKeySource=SERVER_DERIVED_FROM_AUDIT_AND_REQUEST_SPEC`
    - `idempotencyKey=nim-create-<32 hex>`
    - `callerIdempotencyKeyAllowed=false`
    - `executionHandoffPlan`
    - `handoffDigest`
    - `blockedBy`
- `executionHandoffPlan` 固定表达:
  - `target=deployment-create`
  - `method=POST`
  - `backendEndpoint=POST /api/{orgId}/deployment`
  - `pathTemplate=/api/{orgId}/deployment`
  - `resolvedPath=/api/{trustedOrgId}/deployment`
  - `futureExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
  - `requestSpecDigest`
  - `bodyDigest`
  - `idempotency`
  - `preWriteAuditHandoff`
  - `postWriteReadinessHandoff`
  - `retryPolicy`
- `NimCreateStateMachineSupport` 加强:
  - `ReadinessRequest` 新增 `writeExecutionHandoffReport`，并保留旧构造器兼容缺失 handoff 的负例测试。
  - 输出新增 `writeExecutionHandoffRequired=true`。
  - 缺少 handoff 报告返回 `WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY`。
  - 合约不合法、digest/receipt/request spec/audit identity 不匹配返回 `WRITE_EXECUTION_HANDOFF_REPORT_CONTRACT_INVALID`。
  - 报告中出现 forbidden secret 返回 `WRITE_EXECUTION_HANDOFF_REPORT_CONTAINS_FORBIDDEN_SECRET`。
  - 状态机会复算 `handoffDigest`，并确认 handoff plan 绑定 request spec digest 与 body digest。
- 新增 `NimCreateWriteExecutionHandoffSupportTest`。
- 更新 `NimCreateStateMachineSupportTest`、`NimCreateWriteRequestSpecAdapterSupportTest`、`NimCreateWriteBodyRebuilderSupportTest`、`NimCreateAuditReadinessSupportTest` 的未来绿色 fixture。

## 安全边界

- 本批没有新增任何真实 HTTP 调用、Controller、Tool 注册或后台执行器。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- handoff 报告不是 release credential，不能替代 trusted policy、HITL、durable audit receipt、body rebuild、request spec、READY readiness executor 或 release switch。
- 幂等键只能服务端派生，调用方提供的 `idempotencyKey`、`idempotency`、`writeExecutionHandoffReport` 等声明都会被状态机忽略。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateAuditReadinessSupportTest" test`
  - `mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 静态密钥扫描: 只命中文档说明和测试哨兵假值，未发现真实密钥。
  - `mvn -q test`

## 是否访问真实 8100

否。本批只运行纯单元测试、mock-first 契约测试和静态架构/HTTP contract 测试。

## 下一步建议

1. 继续保持 `nim_create` HOLD，直到真实 durable audit writer、真实 trusted policy reader、真实 durable write executor 和 release switch 都完成审计。
2. 下一批可以设计 future durable write executor 的离线 contract shell，但仍不执行真实 POST。
3. 也可以先识别真实 durable audit log 表/服务，减少后续 executor 接入时的不确定性。
