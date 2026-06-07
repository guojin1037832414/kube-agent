# M5.21 第五十五批 NIM durable audit writer 接口规格契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditWriterInterfaceSpecSupport`、`NimCreateDurableAuditWriterInterfaceSpecSupportTest`、M5.21-54 dedicated writer boundary
> 约束: 本批只定义未来 `NimDurableAuditWriter` 的接口规格；不创建真实 Java 接口，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-54 已经把未来 dedicated durable audit writer 的服务端边界和测试替身契约固化下来，但它仍然只是 boundary/test-double plan。本批继续 contract-first，先定义未来 `NimDurableAuditWriter` 的接口请求、响应、方法、失败语义和 test double 规则。

关键原则仍然不变:

- 接口规格不是接口实现。
- 测试替身不是 durable success。
- 当前不能声明 storage available、pre-write persisted、post-write persisted 或 durable receipt issued。

## 多专家会诊

- Backend/API 专家:
  - 未来接口应以 `NimDurableAuditWriteRequest` 输入、`NimDurableAuditWriteResult` 输出为核心。
  - 真实方法边界应拆成 storage probe、pre-write intent、post-write result、receipt assembly。
  - 当前只定义规格，不创建 Spring Bean，也不接入 `sys_log` writer。
- Security/RBAC 专家:
  - 接口请求必须只接受服务端可信输入，禁止调用方 header、Authorization、真实 API Key、自报身份。
  - 响应在真实实现前只能返回 HOLD/失败语义，不能返回 `DURABLE_RECORDED`。
  - 任何 forged success claim 或 secret-shaped input 都必须 fail-closed。
- Agent 架构专家:
  - 接口规格把“未来实现要怎么长”变成可测试知识，帮助后续开发避免口头约定漂移。
  - spec 必须复核 M5.21-54 boundary digest，不能凭空构造 writer 接口。
- Test 架构专家:
  - 正向用例应生成 request/response/method/failure/test-double 规格，但保持 `IMPLEMENTATION_HOLD`。
  - 缺少 boundary report 必须拒绝。
  - 伪造 storage/persistence/receipt success 必须拒绝。
  - secret 泄漏必须在生成 interface spec 前拒绝。
- Documentation/Learning 专家:
  - 本批学习重点是 contract-first interface design: 先把接口输入、输出、失败、测试替身边界写成可复算契约，再谈真实实现。

## 变更摘要

- 新增 `NimCreateDurableAuditWriterInterfaceSpecSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `dedicatedAuditWriterBoundaryReport`
  - 输出:
    - `durableAuditWriterInterfaceSpec=NIM_CREATE_DURABLE_AUDIT_WRITER_INTERFACE_SPEC`
    - `executionMode=DURABLE_AUDIT_WRITER_INTERFACE_SPEC_CONTRACT_ONLY`
    - `interfaceSpecState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureInterface=NimDurableAuditWriter`
    - `requestType=NimDurableAuditWriteRequest`
    - `responseType=NimDurableAuditWriteResult`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `storageAvailable=false`
    - `preWritePersisted=false`
    - `postWritePersisted=false`
    - `durableReceiptCanBeIssued=false`
- 正向输入生成 `interfaceSpec`:
  - `requestContract`
  - `responseContract`
  - `operationMethods`
  - `failureContract`
  - `testDoubleRules`
  - trusted identity binding
  - upstream boundary/writer/gate digest binding
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditWriterInterfaceSpecSupportTest`:
  - 验证接口规格生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 boundary report 时 `DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY`。
  - 验证伪造 boundary success claim 时 `DURABLE_AUDIT_WRITER_INTERFACE_FORGED_SUCCESS_CLAIM`。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_WRITER_INTERFACE_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实 Java writer 接口、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- `interfaceSpec` 不是 durable writer result，也不是 release credential。
- 当前正确状态仍是 `storageAvailable=false`、`preWritePersisted=false`、`postWritePersisted=false`、`durableReceiptCanBeIssued=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 在 interface spec 之后设计 typed receipt/ack schema，但仍保持纯数据、contract-first。
3. 后续真实实现只能在 reviewed server-side `NimDurableAuditWriter` 边界内接入 probe、pre-write ack、post-write ack 和 receipt assembly。
