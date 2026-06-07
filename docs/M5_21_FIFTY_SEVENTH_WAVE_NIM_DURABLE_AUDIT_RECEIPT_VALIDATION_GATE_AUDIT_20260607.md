# M5.21 第五十七批 NIM durable audit receipt validation gate 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditReceiptValidationGateSupport`、`NimCreateDurableAuditReceiptValidationGateSupportTest`、M5.21-56 typed ack/receipt schema
> 约束: 本批只定义未来真实 typed ack/receipt 出现后必须通过的校验门规则；不创建真实 validator，不创建真实 Java value type，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-56 已经定义了四类未来 typed evidence:

- `StorageAvailabilityProbeReceipt`
- `PreWriteDurableAck`
- `PostWriteDurableAck`
- `DurableAuditReceipt`

但顶级 Agent 不能只因为“未来 receipt 字段存在”就放行写执行。真实世界里，最危险的错误是把 mock receipt、caller-supplied ack、字段名相似的对象或 plan/schema report 当成 durable success。

本批继续 contract-first，新增 receipt validation gate，先把未来真实 ack/receipt 必须通过的校验规则固化下来。当前仍然不校验真实 receipt，也不产生 validation pass。

## 多专家会诊

- Backend/API 专家:
  - validation gate 应是未来 `NimDurableAuditReceiptValidator` 的服务端规则边界。
  - 当前只生成 validation plan，不注册 validator Bean，不接触 `sys_log` 或真实 writer。
  - 未来验证顺序必须是 schema digest -> storage probe receipt -> pre-write ack -> post-write ack -> final receipt。
- Security/RBAC 专家:
  - validation pass 只能来自未来真实服务端 validator，不可信任 Tool 入参、schema report 或 test double。
  - 任意 `validationStatus=PASS`、`releaseEligible=true`、`writeExecutionAllowed=true`、typed receipt/ack 实例都必须 fail-closed。
  - trusted principal 必须绑定 `SERVER_SESSION_CONTEXT`，禁止 caller-supplied identity。
- Agent 架构专家:
  - receipt validation gate 是 release 前最后一层 durable audit evidence gate 的规则蓝图。
  - 它把 schema 和未来真实 evidence 之间的验证要求变成可测试知识，而不是口头约定。
- Test 架构专家:
  - 正向用例应生成 validation plan，但保持 `IMPLEMENTATION_HOLD`。
  - 缺少 typed schema report 必须拒绝。
  - 伪造 validation pass、release decision 或 typed receipt 必须拒绝。
  - secret 泄漏必须在生成 validation plan 前拒绝。
- Documentation/Learning 专家:
  - 本批学习重点是 release credential 的验证模型: evidence 不仅要存在，还要能重算、能绑定、能证明顺序和身份。

## 变更摘要

- 新增 `NimCreateDurableAuditReceiptValidationGateSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditReceiptAckSchemaReport`
  - 输出:
    - `durableAuditReceiptValidationGate=NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_GATE`
    - `executionMode=DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_CONTRACT_ONLY`
    - `gateState=IMPLEMENTATION_HOLD|REJECTED`
    - `validationGateState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureValidator=NimDurableAuditReceiptValidator`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `validationStatus=NOT_RUN_UNTIL_REAL_RECEIPT`
    - `durableReceiptValidationPassed=false`
    - `durableReceiptAccepted=false`
    - `releaseEligible=false`
    - `writeExecutionAllowed=false`
- 正向输入生成 `validationPlan`:
  - `validationSequence`
  - `requiredEvidence`
  - `releaseDecisionTemplate`
  - `failureContract`
  - `forbiddenShortcuts`
  - trusted identity binding
  - M5.21-56 schema digest 与上游 interface/boundary/writer/gate digest 绑定
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditReceiptValidationGateSupportTest`:
  - 验证 validation plan 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 schema report 时 `DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY`。
  - 验证伪造 validation pass / typed receipt / release decision 时 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_FORGED_PASS_CLAIM`。
  - 验证空的 caller-supplied `validationResult` 也被拒绝。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实 Java validator/value type、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- validation plan 不是 validation pass，不是 durable receipt，也不是 release credential。
- 当前正确状态仍是 `storageAvailable=false`、`storageProbeReceiptValidated=false`、`preWriteDurableAckValidated=false`、`postWriteDurableAckValidated=false`、`digestChainValidated=false`、`trustedPrincipalValidated=false`、`durableReceiptValidationPassed=false`、`releaseEligible=false`、`writeExecutionAllowed=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q test`。
  - `mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 后续可以设计 future validation result DTO / value type migration plan，但仍保持 contract-only。
3. 真实开放写执行前，必须有 reviewed server-side `NimDurableAuditReceiptValidator` 返回可复核 validation pass，并由 durable write executor gate 再次绑定。
