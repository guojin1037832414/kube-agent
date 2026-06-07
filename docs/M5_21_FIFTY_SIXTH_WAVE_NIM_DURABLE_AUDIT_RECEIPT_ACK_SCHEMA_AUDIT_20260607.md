# M5.21 第五十六批 NIM durable audit typed ack/receipt schema 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditReceiptSchemaSupport`、`NimCreateDurableAuditReceiptSchemaSupportTest`、M5.21-55 durable writer interface spec
> 约束: 本批只定义未来 storage probe receipt、pre-write ack、post-write ack 和 durable receipt 的类型化 schema；不创建真实 Java value type，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-55 已经把未来 `NimDurableAuditWriter` 的 request/response/method/failure/test-double 规格固定下来，但还没有回答一个关键问题:

当真实 writer 未来实现后，哪些 receipt/ack 才能证明 durable audit 已经完成？

本批继续 contract-first，先定义未来四类 typed evidence 的 schema:

- `StorageAvailabilityProbeReceipt`
- `PreWriteDurableAck`
- `PostWriteDurableAck`
- `DurableAuditReceipt`

当前仍然不能产生任何真实 ack/receipt 实例。schema 是学习和实现规范，不是 durable result。

## 多专家会诊

- Backend/API 专家:
  - schema 必须继续绑定 M5.21-55 `interfaceSpecDigest`，不能凭空定义 receipt。
  - 未来真实顺序必须保持 `probe -> pre-write ack -> post-write ack -> receipt`。
  - ack 不能只表示“调用 save 成功”，必须代表 durable evidence 或 read-after-write proof。
- Security/RBAC 专家:
  - typed ack/receipt 只能由未来真实服务端 `NimDurableAuditWriter` 签发。
  - 调用方输入、plan、boundary、interface spec、mock/test double 都不得自报 typed ack/receipt。
  - 空的 `preWriteDurableAck` / `durableReceipt` 对象也必须视为 forged claim 并 fail-closed。
- Agent 架构专家:
  - 顶级 Agent 的 release credential 必须来自可复核证据链，而不是“字段存在”。
  - digest chain 必须绑定同一个 audit event、interface spec 和可信服务端 principal。
- Test 架构专家:
  - 正向用例应生成 schema，但保持 `IMPLEMENTATION_HOLD`。
  - 缺少 interface spec 必须拒绝。
  - 伪造 typed ack/receipt 或 storage success 必须拒绝。
  - secret 泄漏必须在生成 schema 前拒绝。
- Documentation/Learning 专家:
  - 本批学习重点是区分 schema、ack instance 和 release credential。
  - schema 让未来真实实现有严格字段表、失败状态和 digest 链，而不是靠口头约定。

## 变更摘要

- 新增 `NimCreateDurableAuditReceiptSchemaSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditWriterInterfaceSpecReport`
  - 输出:
    - `durableAuditReceiptAckSchema=NIM_CREATE_DURABLE_AUDIT_RECEIPT_ACK_SCHEMA`
    - `executionMode=DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_CONTRACT_ONLY`
    - `schemaState=IMPLEMENTATION_HOLD|REJECTED`
    - `storageProbeReceiptType=StorageAvailabilityProbeReceipt`
    - `preWriteAckType=PreWriteDurableAck`
    - `postWriteAckType=PostWriteDurableAck`
    - `durableReceiptType=DurableAuditReceipt`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `storageProbeReceiptIssued=false`
    - `preWriteDurableAckIssued=false`
    - `postWriteDurableAckIssued=false`
    - `durableReceiptIssued=false`
- 正向输入生成 `typedSchema`:
  - `storageAvailabilityProbeReceiptSchema`
  - `preWriteDurableAckSchema`
  - `postWriteDurableAckSchema`
  - `durableAuditReceiptSchema`
  - `digestChainRules`
  - `currentResponseTemplate`
  - `failureContract`
  - `testDoubleRules`
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditReceiptSchemaSupportTest`:
  - 验证 typed ack/receipt schema 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 interface spec 时 `DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_NOT_READY`。
  - 验证伪造 typed ack/receipt success claim 时 `DURABLE_AUDIT_RECEIPT_SCHEMA_FORGED_SUCCESS_CLAIM`。
  - 验证空的 caller-supplied `preWriteDurableAck` 也被拒绝。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_RECEIPT_SCHEMA_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实 Java writer/value type、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- typed schema 不是 ack instance，不是 durable receipt，也不是 release credential。
- 当前正确状态仍是 `storageAvailable=false`、`preWritePersisted=false`、`postWritePersisted=false`、`storageProbeReceiptIssued=false`、`preWriteDurableAckIssued=false`、`postWriteDurableAckIssued=false`、`durableReceiptIssued=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 在 typed schema 之后，可继续设计 future Java value type migration plan，但仍保持 contract-only。
3. 后续真实实现只能在 reviewed server-side `NimDurableAuditWriter` 边界内签发 typed ack/receipt，并且必须通过真实 durable evidence 证明。
