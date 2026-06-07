# M5.21 第五十四批 NIM 专用 durable audit writer 边界与测试替身契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDedicatedDurableAuditWriterBoundarySupport`、`NimCreateDedicatedDurableAuditWriterBoundarySupportTest`、M5.21-52 writer plan、M5.21-53 storage availability gate
> 约束: 本批只设计 dedicated writer boundary 与 test double contract；不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-52 已经把 NIM durable audit writer 规划成两阶段写入:

- pre-write intent
- post-write result
- trusted principal binding
- receipt issuance rule
- storage availability gate

M5.21-53 又把 storage availability gate 独立成未来 probe plan，并明确当前仍然是:

- `storageProbeExecuted=false`
- `storageAvailable=false`
- `availabilityStatus=UNKNOWN_UNTIL_REAL_PROBE`
- `durableReceiptCanBeIssued=false`

本批继续向前一步，但仍不做真实 I/O。目标是把未来真实 `NimDurableAuditWriter` 的服务端边界和单元测试替身契约固化下来，防止后续开发把 mock/test double 的“形状正确”误读成“真实存储已成功”。

## 多专家会诊

- Backend/API 专家:
  - dedicated writer boundary 必须是未来唯一允许接入 `sys_log` 持久化的服务端边界。
  - 当前类不能注册 Tool/Controller，也不能持有 HTTP/ES 写入客户端。
  - 未来真实顺序必须是 `probe -> pre-write durable ack -> POST -> post-write durable ack -> receipt`。
- Security/RBAC 专家:
  - 边界必须继续绑定 `SERVER_SESSION_CONTEXT` principal，不能相信调用方自报 username/orgId/userId。
  - 上游报告和调用方输入不得自称 `storageAvailable=true`、`preWritePersisted=true`、`postWritePersisted=true` 或 `DURABLE_RECORDED`。
  - Authorization、token、password、secret、真实 API Key 形态必须在生成 boundary plan 前 fail-closed。
- Agent 架构专家:
  - boundary plan 是未来实现规范，不是执行结果。
  - test double contract 只能断言输入契约、digest 绑定、顺序和 fail-closed 行为。
  - test double 明确禁止断言真实 storage success 或 durable receipt。
- Test 架构专家:
  - 正向用例应产生 `writerBoundaryPlan` 和 `testDoubleContract`，但保持 `IMPLEMENTATION_HOLD`。
  - 缺失 writer plan / availability gate 必须拒绝。
  - 伪造 storage/persistence/receipt success claim 必须拒绝。
  - secret 泄漏必须在任何 boundary plan 产生前拒绝。
- Documentation/Learning 专家:
  - 本批学习重点是区分三层对象:
    - plan/boundary contract
    - test double contract
    - real durable writer result
  - 顶级 Agent 的 release credential 只能来自真实可复核证据，不能来自 mock 或计划字段。

## 变更摘要

- 新增 `NimCreateDedicatedDurableAuditWriterBoundarySupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditWriterPlanReport`
    - `storageAvailabilityGateReport`
  - 输出:
    - `dedicatedAuditWriterBoundary=NIM_CREATE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY`
    - `executionMode=DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_TEST_DOUBLE_CONTRACT_ONLY`
    - `writerBoundaryState=IMPLEMENTATION_HOLD|REJECTED`
    - `testDoubleName=NIM_CREATE_DEDICATED_DURABLE_AUDIT_WRITER_TEST_DOUBLE`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `realStorageTouched=false`
    - `storageProbeExecuted=false`
    - `storageAvailable=false`
    - `preWritePersisted=false`
    - `postWritePersisted=false`
    - `durableReceiptCanBeIssued=false`
- 正向输入生成 `writerBoundaryPlan`:
  - `boundaryRequirement=SERVER_SIDE_DEDICATED_DURABLE_AUDIT_WRITER_REQUIRED`
  - `futureInterface=NimDurableAuditWriter`
  - `writeMode=PROBE_THEN_PRE_WRITE_THEN_POST_WRITE`
  - `evidenceBinding`
  - `trustedIdentityBinding`
  - `operationOrder`
  - `currentImplementationState`
  - `receiptReleaseRule`
- 正向输入生成 `testDoubleContract`:
  - 只允许断言 contract accepted/rejected、future operation order、digest/trusted identity binding、fail-closed blockers。
  - 明确禁止断言 `storageAvailable=true`、`storageProbeExecuted=true`、`preWritePersisted=true`、`postWritePersisted=true`、`receiptStatus=DURABLE_RECORDED`、`storageMode=DURABLE_AUDIT_LOG`、`realStorageTouched=true`。
- 正向输入仍然阻断:
  - `DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDedicatedDurableAuditWriterBoundarySupportTest`:
  - 验证 boundary/test double contract 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 writer plan report / availability gate report 时 fail-closed。
  - 验证伪造 storage/persistence/receipt success claim 时 fail-closed。
  - 验证 secret 泄漏时在生成 boundary plan 前 fail-closed。

## 安全边界

- 本批没有新增 Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- `writerBoundaryPlan` 不是 storage probe result，`testDoubleContract` 也不是 release credential。
- 当前正确状态仍是 `storageAvailable=false`、`preWritePersisted=false`、`postWritePersisted=false`、`durableReceiptCanBeIssued=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界 import 扫描未发现新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import。
  - `mvn -q test`
- 全量测试备注: test profile 中 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 设计真实 dedicated writer 的接口形状，但仍先做 contract-first / test-first。
3. 后续真实实现只能在 dedicated writer 服务端边界内接入 storage probe、`sys_log` writer 和 durable ack。
4. 再向后串联 pre-write ack、POST executor、post-write ack、readiness aftercare 和最终 release switch。
