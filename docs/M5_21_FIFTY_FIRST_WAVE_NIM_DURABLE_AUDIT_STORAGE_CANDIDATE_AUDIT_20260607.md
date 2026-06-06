# M5.21 第五十一批 NIM durable audit storage 候选契约审计

> 日期: 2026-06-07 06:31 Asia/Shanghai  
> 范围: `NimCreateDurableAuditStorageSupport`、`NimCreateDurableAuditStorageSupportTest`、成熟 `kube-manager` 系统日志证据  
> 约束: 只识别成熟持久化候选并固化 mock-first 替换边界；不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- `kube-manager` 已存在通用系统日志链路:
  - `SaveLogAspect` 包围带 `@Operation` 的 `/api` 请求。
  - `SysLog` 是 `@Document(indexName = Constant.ES_SYS_LOG_INDEX_NAME)`。
  - `Constant.ES_SYS_LOG_INDEX_NAME = "sys_log"`。
  - `ISysLogService.saveLog(SysLog)` 在 Elasticsearch 启用时调用 `ElasticsearchTemplate.save(sysLog)`。
  - `SysLogController` 暴露 `GET /api/log` 和 `DELETE /api/log/all`，且搜索/清空为 `SYS_ADMIN_ONLY`。
- `vue-kube-manager` 已存在系统日志页面:
  - 路由 `/system/log`。
  - `src/api/log.js` 使用 `GET /api/log` 查询，`DELETE /api/log/all` 清空。
- 这些证据说明 mature 项目有可持久化、可查询的系统操作日志能力，但它是通用 request log，不是 NIM 创建前专用 durable audit receipt。

## 多专家会诊

- Backend/API 专家:
  - `sys_log` 字段可以映射组织、用户、模块、描述、URI、params、body、start/end、success、trace。
  - `SaveLogAspect` 默认会记录请求参数和 `@RequestBody`，未来 NIM writer 不能直接复用 raw params/body，必须写脱敏摘要。
- Security/RBAC 专家:
  - `SysLogController` 查询/清空是 SYS_ADMIN_ONLY，说明日志敏感。
  - 未来 writer 必须从可信 session principal 获取 username/orgId/userId，不能使用 Tool 入参自报身份。
  - Authorization、token、password、secret 和真实 API Key 形态必须在生成 storage plan 前 fail-closed。
- Agent 架构专家:
  - durable audit storage 候选报告不是 durable receipt，不能设置 `DURABLE_RECORDED`。
  - 当前只能输出 `candidateFit=PARTIAL_FIT_NEEDS_DEDICATED_NIM_AUDIT_WRITER` 和 `IMPLEMENTATION_HOLD`。
- Test 架构专家:
  - 正向输入应生成脱敏 `sysLogFieldMapping`，但 `realStorageTouched=false`、`durable=false`、`releaseEligible=false`。
  - 缺少可信 principal 或 secret 泄漏必须 `REJECTED`，不能生成 storage plan。
- Documentation/Learning 专家:
  - 本批教学重点是“找到成熟持久化能力 ≠ 已完成专用审计 writer”；顶级 Agent 要把证据适配层和语义缺口都写进测试。

## 变更摘要

- 新增 `NimCreateDurableAuditStorageSupport`。
  - `prepare(...)` 消费:
    - `auditContext`
    - `trustedPrincipalSnapshot`
  - 输出:
    - `durableAuditStorage=NIM_CREATE_DURABLE_AUDIT_STORAGE_CANDIDATE`
    - `executionMode=DURABLE_AUDIT_STORAGE_CANDIDATE_CONTRACT_ONLY`
    - `storageState=IMPLEMENTATION_HOLD|REJECTED`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `candidateIndex=sys_log`
    - `candidateEntity=com.cgm.kube.system.entity.SysLog`
    - `candidateSaveService=ISysLogService.saveLog(SysLog)`
    - `candidateWriter=SaveLogAspect`
    - `storagePlan`
    - `semanticGaps`
    - `blockedBy`
- 正向输入会生成 `storagePlan.sysLogFieldMapping`:
  - `organizationId`
  - `username`
  - `module=NIM_CREATE_AUDIT`
  - `description=nim_create pre-write audit intent`
  - `uri=/api/{orgId}/deployment`
  - 脱敏 params/body 摘要
  - `ip/start/end` 占位
  - `success=PRE_WRITE_INTENT_RECORDED_NOT_DEPLOYMENT_RESULT`
- 正向输入仍然阻断:
  - `DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED`
- 新增 `NimCreateDurableAuditStorageSupportTest`:
  - 验证 sys_log 候选映射与 `IMPLEMENTATION_HOLD`。
  - 验证缺少可信 principal 时 `TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY`。
  - 验证 secret 泄漏时 `DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET`。

## 安全边界

- 本批没有新增真实持久化实现，没有注入 `ElasticsearchTemplate`、`ISysLogService` 或 kube-manager client。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- `sys_log` 只是候选 durable storage 证据；当前报告不能替代 `DURABLE_RECORDED + DURABLE_AUDIT_LOG` receipt。
- 当前 `NimCreateAuditWriterSupport.buildMockReceipt(...)` 仍不是 release-eligible。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,M510ArchitectureBoundaryTest" test`
  - `mvn -q "-Dtest=NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - 本批边界扫描仅命中文档/字符串证据，没有新增真实 `ElasticsearchTemplate`、`ISysLogService`、HTTP client 或 `java.net` 依赖。
  - `mvn -q test`
- 全量测试备注: test profile 下 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只读取本地成熟项目源码作为证据，并运行纯单元/架构测试。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 设计专用 NIM durable audit writer 接口，明确 pre-write intent 与 post-write result 两阶段记录。
3. 为真实 writer 增加存储可用性门禁，避免 Elasticsearch disabled 时仍签发 durable receipt。
4. 后续再把 `NimCreateAuditWriterSupport` 从 mock receipt 演进为受控 adapter，但必须先有真实 writer 测试替身和回滚策略。
