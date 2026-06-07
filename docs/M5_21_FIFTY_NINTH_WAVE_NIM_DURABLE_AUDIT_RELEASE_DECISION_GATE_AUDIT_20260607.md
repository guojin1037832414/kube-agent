# M5.21 第五十九批 NIM durable audit release decision gate 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditReleaseDecisionGateSupport`、`NimCreateDurableAuditReleaseDecisionGateSupportTest`、M5.21-58 validation result migration plan
> 约束: 本批只定义未来 `NimDurableAuditReleaseDecision` 回接 state machine / durable write executor 的门禁计划；不创建真实 release decision，不修改真实状态机放行逻辑，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-58 已经把未来 `NimDurableAuditReceiptValidationResult` 和 `NimDurableAuditReleaseDecision` 的迁移蓝图固化下来，但它仍然不是 release credential。

下一层风险是：即使未来有 release decision，如果 state machine 或 durable executor 只相信一个上游字段，而没有重新绑定 digest、身份、写链和代码级 release switch，仍可能发生伪造放行。因此本批新增 release decision gate plan，把未来放行前的最终回接规则写清楚。

## 多专家会诊

- Backend/API 专家:
  - release decision gate 只能消费 M5.21-58 migration report。
  - 当前不能把真实 `NimDurableAuditReleaseDecision` 注入状态机，也不能修改真实 release switch。
  - state machine 和 durable executor 都必须在未来重新校验 release decision digest。
- Security/RBAC 专家:
  - caller-supplied `validationResult` / `releaseDecision`，即使空对象，也必须 fail-closed。
  - legacy `auditReceipt.releaseEligible=true` 不能单独放行。
  - release decision 必须绑定 trusted principal、validation result digest、audit event digest 和 code release switch。
- Agent 架构专家:
  - release decision gate 是“最终写执行门禁”的计划层，不是执行层。
  - 未来 state machine 与 durable executor 需要共同消费同一个 server-issued release decision，而不是各自相信局部证据。
  - 写链证据必须包括 `bodyDigest`、`requestSpecDigest`、`handoffDigest`、audit receipt id/event digest 和 server-derived idempotency key。
- Test 架构专家:
  - 正例只允许生成 gate plan 并保持 `IMPLEMENTATION_HOLD`。
  - 负例覆盖缺少 migration report、伪造 release decision、空对象、legacy release flag、digest 篡改、principal mismatch、executor 成功自称、secret 泄漏。
- Documentation/Learning 专家:
  - 本批学习重点是“双重回接”: release decision 不是只给 state machine 看，也要给 durable executor 在真正 POST 前复核。

## 变更摘要

- 新增 `NimCreateDurableAuditReleaseDecisionGateSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditValidationResultMigrationReport`
  - 输出:
    - `durableAuditReleaseDecisionGate=NIM_CREATE_DURABLE_AUDIT_RELEASE_DECISION_GATE`
    - `executionMode=DURABLE_AUDIT_RELEASE_DECISION_GATE_CONTRACT_ONLY`
    - `gateState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureStateMachineGate=NimCreateStateMachineReleaseDecisionGate`
    - `futureDurableExecutorGate=NimDurableWriteExecutorReleaseDecisionGate`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `releaseDecision=DENY_UNTIL_SERVER_VALIDATION_RESULT`
    - `releaseEligible=false`
    - `releaseDecisionAccepted=false`
    - `releaseCredentialIssued=false`
    - `writePermitted=false`
    - `writeExecutionAllowed=false`
    - `realHttpExecutionAllowed=false`
- 正向输入生成 `releaseDecisionGatePlan`:
  - `gateSequence`
  - `requiredFutureEvidence`
  - `stateMachineBindingPlan`
  - `durableExecutorBindingPlan`
  - `currentDenyTemplate`
  - `failureContract`
  - `forbiddenShortcuts`
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_RELEASE_DECISION_GATE_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditReleaseDecisionGateSupportTest`:
  - 验证 gate plan 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 M5.21-58 migration report 时 `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY`。
  - 验证伪造 `releaseDecision` / `validationResult` / legacy `auditReceipt.releaseEligible` 时 `DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM`。
  - 验证空的 caller-supplied `releaseDecision` 也被拒绝。
  - 验证篡改 `migrationPlanDigest` 会拒绝。
  - 验证 trusted principal 与 migration report 绑定不一致会拒绝。
  - 验证 executor 自称 `writeExecuted` / `deploymentId` / `postWriteReadinessTriggered` 会拒绝。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实 Java release decision、validator、release gate、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- release decision gate plan 不是 release decision，不是 validation result，也不是 release credential。
- 当前正确状态仍是:
  - `realReleaseDecisionLoaded=false`
  - `realReleaseDecisionAccepted=false`
  - `validationResultDigestVerified=false`
  - `releaseDecisionDigestVerified=false`
  - `stateMachineReleaseBound=false`
  - `durableExecutorReleaseBound=false`
  - `legacyAuditReceiptReleaseFlagTrusted=false`
  - `releaseEligible=false`
  - `writePermitted=false`
  - `writeExecutionAllowed=false`
  - `realHttpExecutionAllowed=false`
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`，仅 Windows line-ending warnings。
  - secret-pattern 静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client、`java.net` 或 `POST /api/{orgId}/deployment` 依赖。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 后续可以让 state machine 增加 release decision gate report 的未来必需字段，但仍必须保持当前 shell 为 HOLD。
3. 真正开放写执行前，必须实现 server-issued validation result、server-issued release decision、release switch、state machine 回接和 durable executor 复核。
