# M5.21 第五十三批 NIM durable audit storage 可用性门禁计划契约审计

> 日期: 2026-06-07 Asia/Shanghai  
> 范围: `NimCreateDurableAuditStorageAvailabilityGateSupport`、`NimCreateDurableAuditStorageAvailabilityGateSupportTest`、M5.21-52 writer plan  
> 约束: 本批只设计 storage availability gate 计划；不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-52 已经把 `sys_log` 候选证据推进为 NIM 专用 durable audit writer 的两阶段计划，明确未来需要:

- pre-write intent
- post-write result
- trusted principal binding
- receipt issuance rule
- storage availability gate

但 M5.21-52 仍只是 writer plan。它不能证明 Elasticsearch/sys_log 当前可用，也不能证明任何记录已经持久化。本批继续把 storage availability gate 的语义做成纯契约计划，防止未来在存储不可用时仍签发 `DURABLE_RECORDED`。

## 多专家会诊

- Backend/API 专家:
  - 可用性门禁必须在 pre-write intent 前完成。
  - 真实实现至少要确认服务端持久化 client 启用、`sys_log` backing storage 可达、专用 writer 记录契约可写、并拿到 durable ack 或 read-after-write 证据。
- Security/RBAC 专家:
  - availability gate 仍必须绑定可信 `SERVER_SESSION_CONTEXT` principal。
  - 调用方或上游报告不能自称 `storageAvailable=true`、`availabilityStatus=AVAILABLE` 或 `DURABLE_RECORDED`。
  - Authorization、token、password、secret、真实 API Key 形态必须在生成 probe plan 前 fail-closed。
- Agent 架构专家:
  - gate plan 不是 probe executor，更不是 storage result。
  - 当前输出必须保持 `storageProbeExecuted=false`、`storageAvailable=false`、`availabilityStatus=UNKNOWN_UNTIL_REAL_PROBE`。
  - writer plan、storage candidate report、mock receipt 都不能作为 fallback release credential。
- Test 架构专家:
  - 正向用例应生成 probe plan，但仍 `IMPLEMENTATION_HOLD`。
  - 缺少 writer plan report 必须拒绝。
  - 伪造 available/success claim 必须拒绝。
  - secret 泄漏必须在生成 probe plan 前拒绝。
- Documentation/Learning 专家:
  - 本批教学重点是把“计划可用性检查”和“真实可用性探测结果”拆开。
  - 顶级 Agent 的 durable receipt 必须来自真实可复核证据，不来自 optimistic plan 字段。

## 变更摘要

- 新增 `NimCreateDurableAuditStorageAvailabilityGateSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditWriterPlanReport`
  - 输出:
    - `durableAuditStorageAvailabilityGate=NIM_CREATE_DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE`
    - `executionMode=DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE_CONTRACT_ONLY`
    - `gateState=IMPLEMENTATION_HOLD|REJECTED`
    - `storageProbeExecuted=false`
    - `storageAvailable=false`
    - `availabilityStatus=UNKNOWN_UNTIL_REAL_PROBE`
    - `durableReceiptCanBeIssued=false`
    - `availabilityPlan`
    - `blockedBy`
- 正向输入会生成 `availabilityPlan`:
  - `probeSteps`
  - `failurePolicy`
  - `receiptPrerequisites`
  - `trustedIdentityBinding`
- 正向输入仍然阻断:
  - `STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditStorageAvailabilityGateSupportTest`:
  - 验证 probe plan 生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证缺少 writer plan report 时 `DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY`。
  - 验证伪造 available claim 时 `STORAGE_AVAILABILITY_GATE_FORGED_SUCCESS_CLAIM`。
  - 验证 secret 泄漏时 `STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增 Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- `availabilityPlan` 不是 storage probe result；不能替代 `DURABLE_RECORDED + DURABLE_AUDIT_LOG`。
- `storageAvailable=false` 是当前正确状态，因为真实探测尚未实现。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界扫描未发现本批新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import。
  - `mvn -q test`
- 全量测试备注: test profile 下 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 设计真实 dedicated audit writer boundary 与测试替身。
3. 在真实 writer 内实现 availability probe executor，而不是让 Tool 或 LLM 直接声明存储可用。
4. 后续再串联 pre-write durable ack、POST 结果、post-write durable ack 和 readiness aftercare。
