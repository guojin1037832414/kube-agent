# M5.21 第四十二批 NIM mock-first 审计写入 receipt 契约审计

> 日期: 2026-06-07 00:25 Asia/Shanghai
> 范围: `NimCreateAuditWriterSupport`、`NimCreateStateMachineSupport`、`NimCreateAuditWriterSupportTest`、`NimCreateStateMachineSupportTest`、`NimCreateAuditReadinessSupportTest`
> 约束: 只设计 mock-first 审计 writer receipt 契约；不连接真实数据库，不写真实审计表，不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature NIM 创建最终写入口仍是 `POST /api/{organizationId}/deployment`，属于高风险创建动作。
- M5.21-39 已把未来 NIM 创建状态机拆成 `policy -> gate -> HITL -> audit -> write -> readiness`。
- M5.21-40 已把 `auditContext` 建模为纯数据结构，但还没有“审计 writer 已经接收并持久化”的 receipt。
- M5.21-41 已把可信 license/RBAC/organization policy provider 拆出，证明 Tool 入参不能伪造可信事实。
- 因此，本批只补“审计上下文已经被 writer 接收”的边界，不直接做真实持久化。

## 多专家会议

- Backend/API 专家:
  - `auditContext` 是写入前材料，不等于审计已经落库。
  - 未来真实 writer 必须返回可追踪 `receiptId`、`eventDigest`、事件身份字段和持久化状态。
- Security/RBAC 专家:
  - mock receipt 不能被状态机当成放行凭据。
  - receipt 内不得出现 token、password、secret、真实 NGC/NIM API Key。
- Agent 架构专家:
  - receipt 是状态机的一等输入，让 Agent 清楚解释“缺 audit receipt”与“缺 audit context”的不同。
  - 这是生产级 Agent 常见模式: 先形成可审计意图，再拿 durable receipt，最后才允许执行副作用。
- Test 架构专家:
  - 单独测试 mock writer 的 receipt 形状和 fail-closed。
  - 状态机必须拒绝 `MOCK_CONTRACT_ONLY` receipt，只接受未来 durable receipt fixture。
- Documentation/Learning 专家:
  - 本批要明确教学重点: mock-first 是设计推进手段，不是生产放行证明。

## 变更摘要

- 新增 `NimCreateAuditWriterSupport`。
  - `buildMockReceipt(auditContext)` 输出:
    - `auditReceiptPrepared`
    - `receiptStatus=MOCK_PREPARED|REJECTED`
    - `sideEffect=NONE`
    - `storageMode=MOCK_CONTRACT_ONLY`
    - `durable=false`
    - `realStorageTouched=false`
    - `releaseEligible=false`
    - `requiredFutureStorage=DURABLE_AUDIT_LOG`
    - `receiptId`
    - `eventDigestAlgorithm=SHA-256`
    - `eventDigest`
    - 审计身份字段
    - `blockedBy`
  - 对未准备好的 audit context 返回 `REJECTED`。
  - 对含 token/API Key/secret 的 audit context 返回 `REJECTED`。
  - digest 只覆盖脱敏后的审计身份和 NIM 创建摘要字段。
- `NimCreateStateMachineSupport` 加严:
  - `ReadinessRequest` 新增 `auditReceipt`。
  - 真实写入前新增 `validateAuditReceipt(...)`。
  - 没有 receipt 返回 `AUDIT_RECEIPT_NOT_READY`。
  - mock receipt、非 durable receipt、身份字段不匹配或 digest 不合法返回 `AUDIT_RECEIPT_NOT_DURABLE`。
  - receipt 含 secret 返回 `AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET`。
  - 未来绿灯态 fixture 必须使用:
    - `receiptStatus=DURABLE_RECORDED`
    - `storageMode=DURABLE_AUDIT_LOG`
    - `durable=true`
    - `realStorageTouched=true`
    - `releaseEligible=true`
- 新增 `NimCreateAuditWriterSupportTest`。
- 更新 `NimCreateStateMachineSupportTest` 与 `NimCreateAuditReadinessSupportTest`，补齐 durable receipt fixture。

## 安全边界

- 本批不写真实审计表。
- 本批不新增 Tool，不新增 Controller，不调用真实 kube-manager。
- `MOCK_CONTRACT_ONLY` receipt 明确 `releaseEligible=false`，不能作为真实创建放行依据。
- 状态机仍要求代码级 `nimCreateReleased=true`；当前 `NimCreateTool` 仍是 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 本批只让未来真实 writer 的契约可测试，不开放任何副作用。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest" test`
  - `mvn -q "-Dtest=NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`

## 是否访问真实 8100

否。本批只使用纯单元测试和契约测试。

## 下一步建议

1. 设计创建后 readiness 只读执行器，严格消费 `NimCreateAuditReadinessSupport` 生成的 GET/派生步骤。
2. 后续再设计真实 durable audit writer 适配层，但必须先明确真实审计表/日志后端。
3. 在 policy、durable audit receipt、readiness executor 都完成后，再考虑服务端受控 `READY_FOR_SERVER_CONFIRMED_WRITE`。
