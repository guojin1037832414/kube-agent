# M5.21 第四十一批 NIM 可信策略提供器契约审计

> 日期: 2026-06-07 00:04 Asia/Shanghai
> 范围: `NimTrustedPolicyProviderSupport`、`NimTrustedPolicyProviderSupportTest`、`NimCreationGateSupport`、`NimCreateStateMachineSupport`
> 约束: 只把后端可信链路读取到的 license/RBAC/organization 事实转换为策略快照；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - `isSysOrg = checkPermission(['SYS_ADMIN']) || organizationId === 100001`。
  - `handleCreate(row)` 先拒绝系统组织成员创建 NIM，再检查 `isLicenseValid`。
  - `isLicenseValid` 来自 store 中的 NVAIE license 过期时间，不来自创建表单字段。
- mature `vue-kube-manager/src/store/modules/user.js`:
  - `getInfo()` 可信保存 `roles`、`organizationId`、`id` 等当前登录用户事实。
  - `getLicenseInfo()` 根据是否 `SYS_ADMIN` 读取 admin/user license 信息，并写入 `nvaieLicense`。
- mature `kube-manager/src/main/java/com/cgm/kube/base/Constant.java`:
  - `ORGANIZATION_ID_SYS = 100001`。
  - `ORGANIZATION_ID_PUB = 100003`，但 NIM 创建阻断重点是 system org `100001`。
- mature `kube-manager/src/main/java/com/cgm/kube/client/service/impl/DeploymentServiceImpl.java`:
  - create 路径会拒绝系统组织用户创建 Deployment。
- mature `kube-manager/src/main/java/com/cgm/kube/system/service/impl/SysLicenseServiceImpl.java`:
  - `checkNavieLicense(token, until)` 使用 `LicenseUtils.genNvaieSha(String.valueOf(until))` 校验 NVAIE license。

## 多专家会议

- Backend/API 专家:
  - provider 的输入应是后端可信事实，而不是 Tool params。
  - 可信事实至少包含当前 userId、当前 organizationId、当前 roles、NVAIE license 校验状态和审计证据。
- Frontend/Product 专家:
  - 需要复刻 mature 前端的两个阻断: `SYS_ADMIN` 和 `organizationId=100001`。
  - `ORG_ADMIN` 不等于 `SYS_ADMIN`，普通组织管理员应允许进入后续确认链路。
- Security/RBAC 专家:
  - 调用方自报的 `licenseValid`、`role`、`organizationId`、`trustedPolicySource` 全部必须忽略。
  - 缺少证据时不能猜测为失败或通过，应回到 `UNVERIFIED`，让门禁继续保持关闭。
- Agent 架构专家:
  - `NimTrustedPolicySnapshot` 是 policy fact snapshot，不是授权凭证。
  - provider report 要暴露 ignored caller claims，方便 ReAct/审计解释为什么用户自报字段无效。
- Test 架构专家:
  - 正例覆盖普通组织 + 非 SYS_ADMIN + license valid。
  - 负例覆盖 system org、SYS_ADMIN、invalid license、缺少证据、缺少 user/org。
- Documentation/Learning 专家:
  - 本批是学习 Agent “可信事实来源”和“调用方声明隔离”的关键模式，必须在代码注释和审计文档中明确。

## 变更摘要

- 新增 `NimTrustedPolicyProviderSupport`。
  - 新增 `TrustedPolicyFacts`，表达后端可信链路读取到的:
    - `organizationId`
    - `callerRoles`
    - `callerUserId`
    - `nvaieLicenseVerified`
    - `nvaieLicenseValid`
    - `source`
    - `evidence`
  - 新增 `TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION`。
  - `buildSnapshot(...)` 只在事实完整、来源可信、证据覆盖 license/role/organization、license 已校验时返回 authoritative snapshot。
  - 普通组织、非 `SYS_ADMIN`、有效 NVAIE license 返回 `TRUSTED_PASSED`。
  - `organizationId=100001`、`SYS_ADMIN`、license invalid 返回 `TRUSTED_BLOCKED`。
  - 缺少可信来源、证据、userId、organizationId、roles 或 license verified 时返回 `UNVERIFIED`。
- 新增 `buildProviderReport(...)`。
  - 输出 `provider=NIM_TRUSTED_POLICY_PROVIDER`。
  - 输出 `sideEffect=NONE`。
  - 输出 `protectedFromCallerParams=true`。
  - 输出 `ignoredCallerClaims`，记录被忽略的调用方自报字段。
- 加严 `NimCreationGateSupport` 与 `NimCreateStateMachineSupport` 的 ignored caller claim 识别:
  - `organizationId/orgId`
  - `roles/callerRoles`
  - `nvaieLicenseVerified`
  - `trustedPolicySource`
  - `authoritative`
  - `trustedPolicySnapshot`
- 新增 `NimTrustedPolicyProviderSupportTest`。
- 扩展 `NimCreationGateSupportTest` 与 `NimCreateStateMachineSupportTest`，锁定更多伪造字段只进入 ignored caller claims。

## 安全边界

- 本批不新增真实 `nim_create` 写入能力。
- 本批不新增 HTTP 调用，不访问真实 `8100`。
- 本批不从 `nim_deployment_preflight` 公开入参生成 trusted pass。
- `TRUSTED_PASSED` 仍不能打开创建门禁；还需要服务端 HITL、审计上下文、readiness 计划、受控 body 重建和 release 开关。
- `TrustedPolicyFacts` 不接收 API Key、token、password、secret。
- `SYS_ADMIN` 与 `ORG_ADMIN` 必须区分，不能复用 `UserPermissionContext.isAdmin()` 的宽泛 admin 判断作为 NIM 创建准入。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimCreateStateMachineSupportTest" test`

## 是否访问真实 8100

否。本批只使用 mature 源码证据和纯单元测试。

## 下一步建议

1. 设计 mock-first NIM audit writer 接口，先只做契约测试，不连接真实持久化。
2. 设计创建后 readiness 只读执行器，严格消费 `NimCreateAuditReadinessSupport` 生成的 GET/派生步骤。
3. 在 policy、audit、readiness 都接上真实 provider 后，再考虑服务端受控 `READY_FOR_SERVER_CONFIRMED_WRITE`。
