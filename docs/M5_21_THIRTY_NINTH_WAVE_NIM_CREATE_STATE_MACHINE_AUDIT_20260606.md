# M5.21 第三十九批 NIM 创建状态机安全契约审计

> 日期: 2026-06-06
> 范围: `NimCreateStateMachineSupport`、`NimCreateTool`、`NimCreateStateMachineSupportTest`、`HighRiskMutationToolHttpContractTest`
> 约束: 只固化未来 `nim_create` 写入状态机契约；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放真实 NIM 创建。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - NIM 一键部署不是直接 Pod 创建，而是 repository/tag/template/DeploymentDTO 编排。
  - `handleCreate` 会先拒绝系统组织 / `SYS_ADMIN`，再检查 NVAIE license。
  - 前端创建前需要用户确认 NIM 服务名、镜像、模板资源和网络/费用影响。
- mature `kube-manager` 后端:
  - tenant 创建入口是 `POST /api/{organizationId}/deployment`，后端强制命名空间为 `ns{organizationId}`。
  - `DeploymentServiceImpl#createDeployment` 阻止系统组织创建普通 Deployment。
  - NVAIE license 校验来自 `SysLicenseServiceImpl#checkNavieLicense` 的服务端逻辑。
- 现有 `kube-agent`:
  - M5.21-35 已完成 NIM repository/tag/template 只读预检。
  - M5.21-36 已完成 `safeToPost=false` 的 DeploymentDTO 离线预览。
  - M5.21-37 已完成 `creationGate` 与 HITL 卡片草案，但 gate 仍为 `CLOSED`。
  - M5.21-38 已完成 `NimTrustedPolicySnapshot`，但公开 preflight 仍只能返回 `UNVERIFIED`。

## 多专家会议

- Backend/API 专家:
  - `nim_create` 最终写入应走 tenant `POST /api/{orgId}/deployment`，但 body 必须由受控后端状态机重新构建，不能直接复用预检草案。
  - 真实写入前必须能证明创建门禁、policy、HITL、审计和 readiness 都来自服务端执行链。
- Frontend/Product 专家:
  - Agent 不应只说“暂不支持”，而应告诉用户下一步缺什么: license/RBAC、HITL、审计、readiness 或 release 开关。
  - NIM API Key 必须作为强安全边界: Agent 不生成、不保存、不展示真实 key。
- Security/RBAC 专家:
  - `confirmed/hitlConfirmed/approved/safeToPost/licenseValid/sysAdmin` 等入参不能授权。
  - `trustedPolicySnapshot` 必须是 `TRUSTED_PASSED + authoritative=true + protectedFromCallerParams=true`。
  - `HitlConfirmation` 必须 target 精确匹配 `nim_create`。
- Agent 架构专家:
  - 该批把 Plan -> Preflight -> Gate -> HITL -> Audit -> Write -> Readiness 的未来链路固化为状态机。
  - `directPreviewReuseAllowed=false` 与 `fallbackWriteAllowed=false` 可以防止 LLM 从预检跳步到写 Tool。
- Test 架构专家:
  - 用纯函数测试覆盖 fail-closed 场景，不依赖 Spring、不访问 8100、不调用真实 POST。
  - 保留一个“全部未来条件齐全”的绿灯态，证明状态机不是永久封死，而是要求条件完整。
- Documentation/Learning 专家:
  - 这一批适合作为学习 Agent 工程安全的例子: 把未来写入的必要条件编码，而不是只写 TODO。

## 变更摘要

- 新增 `NimCreateStateMachineSupport`。
  - 当前 placeholder 可调用 `evaluateCurrentPlaceholderHold(params)`，返回结构化 HOLD。
  - 未来受控写链可调用 `evaluate(ReadinessRequest)`，在全部条件满足时才返回 `writePermitted=true`。
  - 输出字段:
    - `stateMachine=NIM_CREATE_WRITE_GUARD`
    - `state=HELD` / `READY_FOR_CONTROLLED_WRITE`
    - `writePermitted`
    - `sideEffect=NONE`
    - `nextSideEffectIfExecuted=POST /api/{orgId}/deployment`
    - `blockedBy`
    - `ignoredCallerClaims`
    - `requiredStages`
    - `directPreviewReuseAllowed=false`
    - `fallbackWriteAllowed=false`
    - `apiKeyPolicy=NEVER_GENERATE_STORE_OR_DISPLAY`
- `NimCreateTool` 仍然 fail-closed，但失败结果新增 `data.stateMachine`。
- `HighRiskMutationToolHttpContractTest` 锁定 `nim_create` 不调用 HTTP client，且返回状态机保护信息。
- 新增 `NimCreateStateMachineSupportTest` 覆盖以下场景:
  - 当前占位态缺少全部写入前置条件。
  - 调用方伪造确认、license、safeToPost、fallback 字段被忽略。
  - policy/gate 未打开时阻断。
  - 预览 `safeToPost=true` 或直接复用 preview body 时阻断。
  - `HitlConfirmation` target 不匹配时阻断。
  - 审计/readiness 中出现 token/API Key 等敏感字段时阻断。
  - 全部未来条件齐全时才进入 `READY_FOR_CONTROLLED_WRITE`。

## 状态机门槛

真实 `nim_create` 未来只有在以下条件全部满足时才可能写入:

1. `nim_create` 代码级 release 开关已审计打开。
2. `creationGate.gateState=READY_FOR_SERVER_CONFIRMED_WRITE`。
3. `creationGate.allowedToCreateNow=true`。
4. `trustedPolicySnapshot.snapshotState=TRUSTED_PASSED`。
5. `trustedPolicySnapshot.authoritative=true`。
6. `trustedPolicySnapshot.protectedFromCallerParams=true`。
7. `deploymentBodyPreview.bodyComplete=true`，但 `safeToPost=false` 必须保持不变。
8. `HitlConfirmation` 来自服务端，且 `target=nim_create`。
9. 审计上下文包含 `requestId/conversationId/userId/organizationId` 和 `NIM_CREATE_REQUEST`。
10. 写入 body 来源是 `SERVER_REBUILT_FROM_AUDITED_NIM_STATE`，不是 `PREVIEW_BODY_DIRECT_REUSE`。
11. readiness 计划声明 `pollOnly=true`，且 API Key 策略为 `NEVER_GENERATE_STORE_OR_DISPLAY`。
12. 不允许 fallback 到 `deploy_create_instance` 或其它写 Tool。

## 安全边界

- 本批不新增 HTTP endpoint，不新增真实写 Tool，不访问真实 `8100`。
- `nim_create` 注解保持:
  - `httpMethod=NONE`
  - `operationType=PLACEHOLDER`
  - `requiresConfirmation=true`
- 状态机输出是解释和契约，不是执行授权。
- 即使未来测试中构造了 `READY_FOR_CONTROLLED_WRITE`，当前生产 Tool 仍不会 POST。
- 任何 Tool 入参中的 `confirmed`、`hitlConfirmed`、`approved`、`safeToPost`、`licenseValid`、`sysAdmin`、`fallbackTool` 都只记录为 ignored caller claim。
- 审计和 readiness 结构中出现 token、password、secret、API Key 类字段会被阻断。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,HighRiskMutationToolHttpContractTest" test`
  - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 真实密钥形态静态扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用纯状态机测试和 mock HTTP 契约测试。

## 下一步建议

1. 设计 `NimTrustedPolicyProvider`，从真实后端上下文填充 `NimTrustedPolicySnapshot`。
2. 设计审计上下文对象，明确 requestId、conversationId、userId、organizationId 与审计事件序列。
3. 设计创建后 readiness 只读轮询 Tool，继续保证不生成、不保存、不展示真实 API Key。
4. 在以上都完成后，再考虑把 `creationGate` 从 `CLOSED` 演进到服务端受控的 `READY_FOR_SERVER_CONFIRMED_WRITE`。
