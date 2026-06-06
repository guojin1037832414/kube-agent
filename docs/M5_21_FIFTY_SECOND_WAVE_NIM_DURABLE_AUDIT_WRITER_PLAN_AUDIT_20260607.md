# M5.21 第五十二批 NIM durable audit writer 两阶段计划契约审计

> 日期: 2026-06-07 Asia/Shanghai  
> 范围: `NimCreateDurableAuditWriterPlanSupport`、`NimCreateDurableAuditWriterPlanSupportTest`、M5.21-51 `sys_log` 候选报告  
> 约束: 本批只设计专用 writer 计划；不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-51 已确认 mature `kube-manager` 有通用系统日志链路:

- `SaveLogAspect`
- `ISysLogService.saveLog(SysLog)`
- `SysLog`
- `Constant.ES_SYS_LOG_INDEX_NAME = sys_log`
- `SysLogController` 的 `GET /api/log` / `DELETE /api/log/all`
- `vue-kube-manager` 的 `/system/log`

但这些证据只能说明存在成熟持久化候选，不能说明 NIM 创建已经具备专用 durable audit writer。本批的目标就是把“候选存储证据”推进为“专用 writer 两阶段计划”，同时继续保持实现冻结。

## 多专家会诊

- Backend/API 专家:
  - NIM 创建不能只依赖通用 AOP request log；未来应有专用服务端 writer 边界。
  - writer 至少要写两条记录: pre-write intent 与 post-write result。
  - 两条记录都要绑定 `requestId`、`conversationId`、`userId`、`organizationId`、event digest、request spec digest 和服务端幂等键。
- Security/RBAC 专家:
  - username/orgId/userId 必须来自可信 `SERVER_SESSION_CONTEXT` principal。
  - Tool 入参、storage candidate report 或 request/handoff report 中出现 Authorization、token、password、secret、真实 API Key 形态时必须 fail-closed。
  - 任何自称 `durableReceiptCanBeIssued=true`、`releaseEligible=true`、`DURABLE_RECORDED` 的输入都不能被接受。
- Agent 架构专家:
  - writer plan 不是 receipt，也不是 release credential。
  - 当前只能返回 `writerState=IMPLEMENTATION_HOLD`，并明确 `durable=false`、`realStorageTouched=false`、`releaseEligible=false`。
  - storage availability gate 必须独立存在，避免 Elasticsearch disabled 时仍签发 durable receipt。
- Test 架构专家:
  - 正向用例应生成 pre/post 两阶段模板，但仍阻断。
  - 缺少 M5.21-51 storage candidate report 必须拒绝。
  - 伪造 durable/release 声明必须拒绝。
  - secret 泄漏必须在生成任何 record template 前拒绝。
- Documentation/Learning 专家:
  - 教学重点是把“审计上下文准备好”“审计存储候选存在”“专用 writer 已持久化”三件事拆开。
  - 顶级 Agent 的安全性来自逐段可复算证据，而不是相信一个中间对象的乐观字段。

## 变更摘要

- 新增 `NimCreateDurableAuditWriterPlanSupport`。
  - `plan(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditStorageReport`
    - 可选 `writeRequestSpecReport`
    - 可选 `writeExecutionHandoffReport`
  - 输出:
    - `durableAuditWriterPlan=NIM_CREATE_DURABLE_AUDIT_WRITER_PLAN`
    - `executionMode=DURABLE_AUDIT_WRITER_PLAN_CONTRACT_ONLY`
    - `writerState=IMPLEMENTATION_HOLD|REJECTED`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `preWriteRecordRequired=true`
    - `postWriteRecordRequired=true`
    - `storageAvailabilityGateRequired=true`
    - `durableReceiptCanBeIssued=false`
    - `writerPlan`
    - `blockedBy`
- 正向输入会生成 `writerPlan`:
  - `storageAvailabilityGate`
  - `trustedIdentityBinding`
  - `preWriteRecordTemplate`
  - `postWriteRecordTemplate`
  - `receiptIssuanceRule`
- 正向输入仍然阻断:
  - `DURABLE_AUDIT_STORAGE_CANDIDATE_IMPLEMENTATION_HOLD`
  - `DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD`
- 新增 `NimCreateDurableAuditWriterPlanSupportTest`:
  - 验证两阶段模板生成但保持 `IMPLEMENTATION_HOLD`。
  - 验证可选 request spec / handoff digest 与幂等键会被模板绑定。
  - 验证缺少 storage candidate report 时 `DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_NOT_READY`。
  - 验证伪造 durable/release claim 时拒绝。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_WRITER_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增 Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批不访问真实 `8100`，不执行 `POST /api/{orgId}/deployment`。
- `writerPlan` 不是 durable receipt；不能替代状态机所需的 `DURABLE_RECORDED + DURABLE_AUDIT_LOG`。
- `preWriteRecordTemplate` 与 `postWriteRecordTemplate` 都是未来记录模板，当前 `realStorageTouched=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 边界扫描未发现本批新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` import；命中项仅为文档/注释中的禁止项说明。
  - `mvn -q test`
- 全量测试备注: test profile 下 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元/契约测试，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 为真实 dedicated writer 设计受控接口和测试替身。
3. 设计 storage availability gate 的真实探测策略和失败语义。
4. 再推进 durable write executor 的真实边界设计: write-before audit、POST、write-after audit、readiness aftercare、失败回滚/补偿。
