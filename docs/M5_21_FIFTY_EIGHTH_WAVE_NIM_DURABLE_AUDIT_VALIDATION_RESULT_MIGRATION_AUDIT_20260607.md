# M5.21 第五十八批 NIM durable audit validation result 迁移蓝图契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditValidationResultMigrationSupport`、`NimCreateDurableAuditValidationResultMigrationSupportTest`、M5.21-57 receipt validation gate
> 约束: 本批只定义未来强类型 validation result / release decision 的迁移蓝图；不创建真实 DTO/Bean，不创建真实 validator，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-57 已经定义了未来 `NimDurableAuditReceiptValidator` 的 validation gate。它能生成验证顺序和 required evidence，但仍然不是 `PASS`。

顶级 Agent 的危险点在这里会继续升级：如果未来只把 `validationStatus=PASS`、`releaseEligible=true` 或一个名叫 `releaseDecision` 的对象塞进链路，Agent 就可能把伪造对象误当成放行凭证。因此本批继续 contract-first，把未来真正放行前必须出现的两个强类型对象先固定下来：

- `NimDurableAuditReceiptValidationResult`
- `NimDurableAuditReleaseDecision`

当前它们都只是 future-only 合同，不是实例，不是 release credential。

## 多专家会诊

- Backend/API 专家:
  - migration plan 只能消费 M5.21-57 的 validation gate report。
  - 未来 DTO / release gate 必须在服务端边界生成，不能由 Tool 入参提供。
  - 当前不能注册 Spring Bean、不能接真实 writer / validator / HTTP client。
- Security/RBAC 专家:
  - 任意 caller-supplied `validationResult` / `releaseDecision`，即使是空对象，也必须 fail-closed。
  - 旧 `auditReceipt.releaseEligible=true` 只能作为迁移风险记录，不能作为写执行权限。
  - release decision 必须绑定 trusted principal、validation result digest、code release switch。
- Agent 架构专家:
  - schema、validation gate、validation result、release decision 是四层不同的证据。
  - plan 层只能表达未来规则，不能被 Agent 当成事实或凭证。
  - 后续 state machine 和 durable executor 需要从 legacy audit receipt flag 迁移到 release decision digest。
- Test 架构专家:
  - 正例只允许 `IMPLEMENTATION_HOLD`，并验证所有 release 字段为 false。
  - 负例覆盖缺少 gate report、伪造 pass、空 validation result、digest 篡改、principal 不匹配、secret 泄漏。
- Documentation/Learning 专家:
  - 本批学习重点是 release credential 迁移模型：不是“有字段就通过”，而是“服务端签发、可复算、可绑定、可拒绝”。

## 变更摘要

- 新增 `NimCreateDurableAuditValidationResultMigrationSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditReceiptValidationGateReport`
  - 输出:
    - `durableAuditValidationResultMigrationPlan=NIM_CREATE_DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_PLAN`
    - `executionMode=DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_CONTRACT_ONLY`
    - `migrationPlanState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureValidationResult=NimDurableAuditReceiptValidationResult`
    - `futureReleaseDecision=NimDurableAuditReleaseDecision`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `validationStatus=NOT_RUN_UNTIL_REAL_RECEIPT`
    - `durableReceiptValidationPassed=false`
    - `durableReceiptAccepted=false`
    - `releaseEligible=false`
    - `releaseDecisionAccepted=false`
    - `releaseCredentialIssued=false`
    - `writeExecutionAllowed=false`
    - `legacyAuditReceiptReleaseFlagTrusted=false`
- 正向输入生成 `migrationPlan`:
  - `migrationSequence`
  - `validationResultContract`
  - `releaseDecisionContract`
  - `legacyCompatibilityPolicy`
  - `releaseCredentialRules`
  - `failureContract`
  - `forbiddenShortcuts`
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditValidationResultMigrationSupportTest`:
  - 验证 migration plan 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 M5.21-57 validation gate report 时 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY`。
  - 验证伪造 `validationResult` / `releaseDecision` / legacy `auditReceipt.releaseEligible` 时 `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_FORGED_RELEASE_CLAIM`。
  - 验证空的 caller-supplied `validationResult` 也被拒绝。
  - 验证篡改 `validationPlanDigest` 会拒绝。
  - 验证 trusted principal 与 gate report 绑定不一致会拒绝。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实 Java DTO、validator、release gate、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- migration plan 不是 validation result，不是 release decision，也不是 release credential。
- 旧 `auditReceipt.releaseEligible` 不再被视为可信放行来源；后续真实开放前必须迁移到 server-issued release decision。
- 当前正确状态仍是:
  - `realValidationResultCreated=false`
  - `realReleaseDecisionCreated=false`
  - `storageProbeReceiptValidated=false`
  - `preWriteDurableAckValidated=false`
  - `postWriteDurableAckValidated=false`
  - `digestChainValidated=false`
  - `trustedPrincipalValidated=false`
  - `durableReceiptValidationPassed=false`
  - `releaseEligible=false`
  - `writeExecutionAllowed=false`
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`，仅 Windows line-ending warnings。
  - secret-pattern 静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client、`java.net` 或 `POST /api/{orgId}/deployment` 依赖。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 后续可以把 state machine / durable write executor 的未来 release 证据从 legacy audit receipt flag 迁移到 `NimDurableAuditReleaseDecision` digest。
3. 真正开放写执行前，必须实现 reviewed server-side `NimDurableAuditReceiptValidator`、真实 typed evidence、server-issued release decision 和 code release switch。
