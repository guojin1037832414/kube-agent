# M5.21 第四十六批 NIM 受控写入 body 重建契约审计

> 日期: 2026-06-07 04:45 Asia/Shanghai
> 范围: `NimCreateWriteBodyRebuilderSupport`、`NimCreateStateMachineSupport`、`NimCreateWriteBodyRebuilderSupportTest`、NIM 状态机相关测试
> 约束: 只新增 mock-first/纯函数 body 重建契约；不注册 Tool，不新增 Controller，不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 一键部署最终仍落到 Deployment 创建 DTO，但前端预览/合并体不等于生产写入凭据。
- M5.21-39 已要求 `POST body` 必须来自 `SERVER_REBUILT_FROM_AUDITED_NIM_STATE`，但之前状态机只检查 provenance 字符串。
- M5.21-42 已把 durable audit receipt 作为未来写入前置条件。
- M5.21-44/45 已把 readiness 执行报告和 HTTP request spec 与写入放行隔离。
- 因此本批补上“受控重建报告”这一层，让未来写链必须拿到可检查的白名单 DeploymentDTO，而不是直接复用 `deploymentBodyPreview.bodyDraft`。

## 多专家会诊

- Backend/API 专家:
  - 重建体必须对齐 `POST /api/{orgId}/deployment` 的 DeploymentDTO 形状，但本批不执行 POST。
  - `organizationId/userId/conversationId/token` 等执行上下文字段不得进入 body。
- Security/RBAC 专家:
  - body 重建必须绑定 durable audit receipt，mock receipt 或身份不匹配都不能放行。
  - body/report/input 中不得出现 `Authorization`、`token`、`password`、`secret` 或真实 NGC/NIM API Key。
- Agent 架构专家:
  - `writeBodyProvenance` 是标签，`writeBodyRebuildReport` 才是状态机可验证的一等输入。
  - preview 仍然是学习/解释材料，不能升级为 release credential。
- Test 架构专家:
  - 单独测试 rebuilder 的白名单、digest、secret fail-closed、unsafe identity 字段拒绝。
  - 状态机未来绿灯 fixture 必须显式携带 rebuilder report。
- Documentation/Learning 专家:
  - 本批教学重点是“不要相信中间态对象”，生产 Agent 需要把每一段放行证据变成可测试契约。

## 变更摘要

- 新增 `NimCreateWriteBodyRebuilderSupport`。
  - `rebuild(...)` 消费:
    - `creationGate`
    - `deploymentBodyPreview`
    - `auditContext`
    - `auditReceipt`
  - 输出:
    - `writeBodyRebuilder=NIM_CREATE_WRITE_BODY_REBUILDER`
    - `executionMode=CONTROLLED_BODY_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeBodyPrepared`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `writeBodyProvenance=SERVER_REBUILT_FROM_AUDITED_NIM_STATE`
    - `directPreviewReuseAllowed=false`
    - `previewBodyReferenceUsed=false`
    - `fieldWhitelistApplied=true`
    - `protectedContextStripped=true`
    - `body`
    - `bodyDigestAlgorithm=SHA-256`
    - `bodyDigest`
    - `sourceAuditReceiptId/sourceAuditEventDigest`
    - `blockedBy`
- body 白名单只允许 DeploymentDTO 业务字段，例如:
  - `name/displayName/image/templateId`
  - CPU/Memory/GPU/resource 字段
  - `replicas`
  - 网络/端口/带宽字段
  - `autoScaleConfig`
  - `enableSecondNetwork`
- body 重建前置校验:
  - `creationGate` 必须已进入 `READY_FOR_SERVER_CONFIRMED_WRITE`；
  - trusted policy 必须为 `TRUSTED_PASSED`；
  - `deploymentBodyPreview.bodyComplete=true` 且 `safeToPost=false`；
  - audit context 必须是完整 `NIM_CREATE_REQUEST`；
  - audit receipt 必须是 `DURABLE_RECORDED + DURABLE_AUDIT_LOG`；
  - audit receipt 身份字段必须与 audit context 匹配。
- `NimCreateStateMachineSupport` 加严:
  - `ReadinessRequest` 新增 `writeBodyRebuildReport`；
  - 状态机输出新增 `writeBodyRebuildRequired=true`；
  - 缺少重建报告返回 `WRITE_BODY_REBUILD_REPORT_NOT_READY`；
  - report 合约不满足或未绑定 audit receipt 返回 `WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID`；
  - report 含 secret 返回 `WRITE_BODY_REBUILD_REPORT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateWriteBodyRebuilderSupportTest`。
- 更新状态机、审计 readiness、审计 writer、readiness adapter 相关 fixture，确保未来绿灯路径必须显式携带受控 body 重建报告。

## 安全边界

- 本批没有新增任何真实 HTTP 客户端或 HTTP 调用。
- 本批不访问真实 `8100`，不调用真实 `POST /api/{orgId}/deployment`。
- 重建报告 `releaseCredential=false`，不能绕过状态机、HITL、audit receipt 或 readiness executor。
- preview 仍保持 `safeToPost=false`；rebuilder 只从 preview 中复制白名单字段，并重新生成独立 body。
- protected context 和 secret 字段不会进入 body。
- `nim_create` 仍是 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimCreateReadinessHttpAdapterSupportTest" test`
  - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateStateMachineSupportTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - real secret-pattern static scan: 0 matches after replacing one historical docs example key in `docs/v3.1/DEVELOPMENT_GUIDE.md` with a non-secret placeholder.
  - `mvn -q test`

全量测试说明: test profile 中 embedding model download 仍会因网络超时降级，最终 Maven 测试通过。

## 是否访问真实 8100

否。本批只运行纯单元测试、mock-first 契约测试和 ArchUnit 架构边界测试。

## 下一步建议

1. 识别真实 durable audit writer 后端落点，设计 writer adapter 替换 mock receipt。
2. 继续保持 `nim_create` HOLD，直到 release 开关、真实 writer、真实策略读取和真实执行层都完成审计。
3. 后续可以把 rebuilder 输出交给一个仍然 fail-closed 的 POST request spec adapter，而不是直接接真实 HTTP。
