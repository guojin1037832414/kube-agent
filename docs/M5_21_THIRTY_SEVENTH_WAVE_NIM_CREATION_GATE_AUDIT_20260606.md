# M5.21 第三十七批 NIM 创建门禁与 HITL 卡片草案审计

> 日期: 2026-06-06
> 范围: `NimCreationGateSupport`、`NimDeploymentPreflightSupport`、`NimCreationGateSupportTest`、`NimDeploymentPreflightToolHttpContractTest`
> 约束: 只生成创建前门禁和 HITL 卡片草案；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - NIM 一键部署正式创建前会检查系统组织 / `SYS_ADMIN`，并检查 NVAIE license。
  - 创建链路依赖 repository、tag、`templateType=NIM` 模板、`mergeTemplate`、`formatApplication`。
  - 创建后还会回读 Deployment、检查入口 URL、轮询 NIM API health/model。
- mature `kube-manager` 后端:
  - `DeploymentController#createDeployment` 是唯一成熟创建入口: `POST /api/{organizationId}/deployment`。
  - Controller 强制 namespace 为 `ns{organizationId}`。
  - `DeploymentServiceImpl#createDeployment` 会禁止系统组织创建，执行资源校验、DB 插入、K8s Deployment/HPA/Service/Ingress 创建。
- 现有 `kube-agent`:
  - `nim_create` 仍是 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
  - `HitlGuard` 只信任服务端 `HitlConfirmation`，不信任 LLM/前端参数里的 confirmed/approved。
  - `SafeToolExecutor` 会过滤 token/orgId/userId 等伪造上下文字段，并在执行前调用 `HitlGuard`。

## 多专家会议

- Backend/API 专家:
  - 创建前门禁不能替代后端 create service 的资源校验；它只用于解释阻断原因和组织下一步。
  - 后端最终资源名由服务层生成，HITL 卡片应突出 `displayName`、image、templateId 和资源规格。
- Frontend/Product 专家:
  - 用户说“部署 NIM”时，Agent 应能给出一张清楚的确认卡片草案，而不是只说“当前不能创建”。
  - 确认项必须覆盖服务名、镜像、模板、GPU、网络、费用/配额、API Key 不保存。
- Security/RBAC 专家:
  - `approved/licenseValid/hitlConfirmed/safeToPost/sysAdmin` 等 Tool 入参均不可信，必须结构化标记为 ignored caller claims。
  - 任何卡片草案都不能产生服务端确认 marker；真正 marker 只能由 HITLController 在 confirmToken 校验后生成。
- Agent 架构专家:
  - `creationGate` 作为 Plan -> Preflight -> HITL -> Write 状态机中的“关闭门”节点，能让 LLM 明确下一步缺什么。
  - `futureWritePath.directUseOfPreviewAllowed=false` 和 `fallbackAllowedFromPreflight=false` 防止把预检草案直接塞进写 Tool。
- Test 架构专家:
  - 单测覆盖 ready CPU preview 仍关闭、伪造授权声明被忽略、缺 displayName 动态阻断。
  - HTTP 契约测试确认 preflight 仍只调用三段 GET，没有新增 POST。
- Documentation/Learning 专家:
  - 这是学习顶级 Agent 安全工程的关键模式：把“为什么不做”结构化成可观察、可测试、可逐步解除的门禁。

## 变更摘要

- 新增 `NimCreationGateSupport`。
  - 输出 `creationGate`:
    - `gateState=CLOSED`
    - `allowedToCreateNow=false`
    - `sideEffect=NONE`
    - `blockedBy`
    - `ignoredCallerClaims`
    - `requiredTrustedChecks`
    - `hitlCardDraft`
    - `futureWritePath`
    - `nextBestActions`
  - 固定阻断项:
    - `NIM_CREATE_TOOL_HOLD`
    - `NVAIE_LICENSE_NOT_VERIFIED`
    - `CALLER_ORG_POLICY_NOT_VERIFIED`
    - `HITL_CONFIRMATION_NOT_ISSUED`
    - `AUDIT_AND_STATUS_FLOW_NOT_READY`
  - 动态阻断项:
    - `DEPLOYMENT_BODY_PREVIEW_INCOMPLETE`
    - `DISPLAY_NAME_REQUIRED`
    - `GPU_MAP_UNRESOLVED`
    - `PREVIEW_SAFE_FLAG_INVALID`
- `NimDeploymentPreflightSupport` 在预检计划中新增 `creationGate`。
- 新增 `NimCreationGateSupportTest`。
- 扩展 `NimDeploymentPreflightToolHttpContractTest`，锁定 `creationGate` 返回且仍不写入。

## HITL 卡片草案

`hitlCardDraft` 当前仅用于展示和学习，不会创建服务。草案包含:

- `targetTool=nim_create`
- `operationType=CREATE`
- `requiresServerMarker=true`
- 关键字段:
  - `displayName`
  - `image`
  - `templateId`
  - `gpuSpec`
  - `gpuResolution`
  - `cpuLimits/memLimits`
  - `gpuPercentLimits/gpuMemLimits`
  - `network`
- 可编辑字段:
  - `displayName`
  - `gpuSpec`
  - `bandwidth`
  - `autoScaleConfig`
  - `networkExpose`
  - `expectedCostAcknowledgement`

## HOLD 清单

- `nim_create` 继续 HOLD，不接入 POST。
- NVAIE license 后端可信校验未完成。
- 系统组织 / SYS_ADMIN 禁止策略尚未在 Agent 执行链中完整实现。
- GPU map 解析只能由未来已审计受控编排提供，公开预检不信任用户传入的 `gpuMap`。
- HITL 卡片目前只是草案，不生成 `HitlConfirmation`。
- 审计日志、费用/配额确认、创建后 Deployment/Service/NIM readiness 轮询未完成。
- API Key 不由 Agent 生成、保存或展示。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`
  - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态 secret 扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用 mature 源码证据、现有 HITL/SafeToolExecutor 代码证据、mock HTTP client 和纯函数测试。

## 下一步建议

1. 设计 license / SYS_ADMIN / system org 的可信后端策略接口或现有配置读取证据。
2. 为 `nim_create` 未来状态机补契约测试: 没有 `creationGate` 通过、没有服务端 marker、没有审计上下文时永远不执行。
3. 设计创建后 readiness 只读轮询 Tool，仍不处理真实 API Key。
