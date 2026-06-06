# M5.21 第三十八批 NIM 可信策略快照审计

> 日期: 2026-06-06
> 范围: `NimTrustedPolicySnapshot`、`NimCreationGateSupport`、`NimCreationGateSupportTest`
> 约束: 只把 NVAIE license / SYS_ADMIN / system org 检查建模为可信策略快照；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - `Create Service` 按钮在 `isSysOrg` 时禁用。
  - `handleCreate` 会先拒绝系统组织 / `SYS_ADMIN`，再检查 `isLicenseValid`。
  - `isSysOrg` 来源是 `checkPermission(['SYS_ADMIN']) || organizationId === 100001`。
  - NVAIE license 来源是 store 中的 `nvaieLicense` 过期时间，而不是创建表单参数。
- mature `kube-manager` 后端:
  - `DeploymentController#createDeployment` 是 tenant 创建入口: `POST /api/{organizationId}/deployment`，并强制 `namespace=ns{organizationId}`。
  - `DeploymentServiceImpl#createDeployment` 通过当前登录用户判断，禁止系统组织创建普通 Deployment。
  - `SysDeploymentController#createDeployment` 是 `SYS_ADMIN_ONLY` 站点级入口，和 tenant NIM 创建语义不同。
  - `SysLicenseServiceImpl#checkNavieLicense` 使用服务端 token/until 计算 NVAIE license 有效性。
- 现有 `kube-agent`:
  - `nim_deployment_preflight` 目前是敏感只读预检，不应从 Tool 入参信任 `licenseValid/isSysOrg/sysAdmin/role`。
  - M5.21-37 已经把这些调用方自报字段记录为 `ignoredCallerClaims`，但尚未给未来可信后端检查留下结构化接口。

## 多专家会议

- Backend/API 专家:
  - License 和组织/角色检查必须来自后端可信上下文，不能从 LLM 参数或前端 JSON 中推断。
  - 在未接真实后端读取前，可以先用纯 value object 固化返回结构，避免后续状态机边界漂移。
- Frontend/Product 专家:
  - 前端当前给用户的是“不能创建”的 toast；Agent 应能解释是未校验、可信通过还是可信失败。
  - 即使策略通过，也应该继续显示 `nim_create`、HITL、审计/readiness 的剩余门禁。
- Security/RBAC 专家:
  - `trustedPolicySnapshot` 必须声明 `protectedFromCallerParams=true`。
  - 公开 preflight 默认只能返回 `UNVERIFIED`，防止调用方通过 `licenseValid=true` 伪造通过。
  - 可信失败要变成显式 blocker，而不是和未校验混在一起。
- Agent 架构专家:
  - 这是从 explanation gate 进入 state-machine gate 的关键中间层: 让未来真实 `nim_create` 可以消费同一套 policy snapshot。
  - `TRUSTED_PASSED` 仍不能打开 gate；它只移除 license/RBAC 未校验 blocker。
- Test 架构专家:
  - 单测覆盖默认未校验、伪造自报仍未校验、可信通过仍关闭、可信失败有 blocker。
  - 本批只跑纯函数和 mock HTTP 契约，不访问真实 8100。
- Documentation/Learning 专家:
  - 该批适合作为学习 Agent 安全工程的模式: 把“安全检查来源”建模，而不是只写一句“以后要检查”。

## 变更摘要

- 新增 `NimTrustedPolicySnapshot`。
  - 默认 `unverified()` 用于公开 `nim_deployment_preflight`。
  - `fromTrustedChecks(...)` 仅供未来受控后端编排在完成可信读取后传入。
  - 输出字段:
    - `snapshotState`: `UNVERIFIED` / `TRUSTED_PASSED` / `TRUSTED_BLOCKED`
    - `authoritative`
    - `source`
    - `protectedFromCallerParams=true`
    - `nvaieLicense`
    - `callerOrgPolicy`
    - `evidence`
- `NimCreationGateSupport` 新增重载，允许受控路径传入 `NimTrustedPolicySnapshot`。
- 公开四参 `buildCreationGate(...)` 默认使用 `NimTrustedPolicySnapshot.unverified()`。
- `creationGate` 新增 `trustedPolicySnapshot`。
- blocker 行为调整:
  - 未校验时继续返回 `NVAIE_LICENSE_NOT_VERIFIED` 和 `CALLER_ORG_POLICY_NOT_VERIFIED`。
  - 可信失败时返回 `NVAIE_LICENSE_TRUSTED_CHECK_FAILED` 和 `CALLER_ORG_POLICY_TRUSTED_CHECK_FAILED`。
  - 可信通过时移除 license/RBAC 未校验 blocker，但仍保留 `NIM_CREATE_TOOL_HOLD`、`HITL_CONFIRMATION_NOT_ISSUED`、`AUDIT_AND_STATUS_FLOW_NOT_READY`。
- `nextBestActions` 会根据 policy snapshot 给出下一步: 先做可信校验、修复 license、切换执行上下文等。

## 安全边界

- 本批不新增 Tool，不新增 HTTP endpoint，不读取真实 license，不读取真实组织/用户。
- `trustedPolicySnapshot` 是结构化契约，不是授权凭证。
- `TRUSTED_PASSED` 仍不能让 `allowedToCreateNow=true`。
- `nim_create` 继续 `PLACEHOLDER_HOLD`，真实写入必须等待:
  - 后端可信 policy provider；
  - 服务端 HITL marker；
  - 审计日志；
  - readiness 轮询；
  - 严禁 API Key 生成/保存/展示；
  - 最终 `SafeToolExecutor + HitlGuard` 统一执行。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`
  - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态 secret 扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只读 mature 源码证据，并使用纯函数测试。

## 下一步建议

1. 设计 `NimTrustedPolicyProvider`，从可信后端上下文读取 NVAIE license、当前用户角色、当前组织类型。
2. 为未来 `nim_create` 状态机补契约: policy 未通过、无 `HitlConfirmation`、无审计上下文时永不执行。
3. 设计创建后 readiness 只读轮询，继续保证 Agent 不生成、不保存、不展示真实 API Key。
