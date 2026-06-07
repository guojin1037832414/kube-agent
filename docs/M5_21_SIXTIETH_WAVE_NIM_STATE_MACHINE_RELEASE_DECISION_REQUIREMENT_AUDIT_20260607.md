# M5.21 第六十批 NIM state machine release decision report requirement 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineReleaseDecisionRequirementSupport`、`NimCreateStateMachineReleaseDecisionRequirementSupportTest`、M5.21-59 release decision gate report
> 约束: 本批只定义未来 `NimCreateStateMachineSupport` 必须消费 release decision gate report 的契约壳；不修改真实状态机放行逻辑，不创建真实 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-59 已经把未来 `NimDurableAuditReleaseDecision` 回接 state machine / durable executor 的双重门禁计划固化下来。但并行专家审查指出一个关键缺口：`NimCreateStateMachineSupport.ReadinessRequest` 当前还没有 `releaseDecisionGateReport` 一类强输入要求。虽然现在 durable executor shell 仍会强制 HOLD，未来一旦 executor 实现，如果状态机没有先要求 release decision gate report，就可能只凭旧的 audit receipt / handoff / executor 链路进入放行路径。

因此本批新增一个独立 contract shell，先把未来状态机必须消费和复核的 release decision gate report 形状写成可测试契约，同时不改变真实状态机的现有行为。

## 本批交付

- 新增 `NimCreateStateMachineReleaseDecisionRequirementSupport`。
- 新增 `NimCreateStateMachineReleaseDecisionRequirementSupportTest`。
- 输入只包含:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `durableAuditReleaseDecisionGateReport` from M5.21-59
- 输出核心字段:
  - `stateMachineReleaseDecisionReportRequirement=NIM_CREATE_STATE_MACHINE_RELEASE_DECISION_REPORT_REQUIREMENT`
  - `executionMode=STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_CONTRACT_ONLY`
  - `requirementState=IMPLEMENTATION_HOLD|REJECTED`
  - `targetStateMachine=NimCreateStateMachineSupport`
  - `requiredFutureStateMachineInput=durableAuditReleaseDecisionGateReport`
  - `futureReadinessRequestField=releaseDecisionGateReport`
  - `releaseDecisionGateReportRequired=true`
- 当前仍保持:
  - `realStateMachineReleaseDecisionGateReportAccepted=false`
  - `releaseDecisionGateDigestVerified=false`
  - `validationResultDigestVerified=false`
  - `releaseDecisionDigestVerified=false`
  - `trustedPrincipalValidated=false`
  - `codeReleaseSwitchVerified=false`
  - `realReleaseDecisionLoaded=false`
  - `realReleaseDecisionAccepted=false`
  - `stateMachineReleaseGateImplemented=false`
  - `stateMachineReleaseBound=false`
  - `stateMachineReleaseDecisionRequirementBound=false`
  - `stateMachineCanSetWritePermittedNow=false`
  - `legacyAuditReceiptReleaseFlagTrusted=false`
  - `legacyAuditReceiptReleaseEligibleTrusted=false`
  - `fallbackToAuditReceiptReleaseEligibleAllowed=false`
  - `fallbackToCallerReleaseDecisionAllowed=false`
  - `fallbackToMigrationPlanAllowed=false`
  - `releaseEligible=false`
  - `writePermitted=false`
  - `writeExecutionAllowed=false`
  - `realHttpExecutionAllowed=false`

## 契约内容

合法输入会生成 `stateMachineRequirementPlan`，但仍被 `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD` 阻断。计划包括:

- `stateMachineRequirementSequence`
  - `require-release-decision-gate-report`
  - `recompute-release-decision-gate-plan-digest`
  - `bind-server-issued-validation-result`
  - `bind-server-issued-release-decision`
  - `bind-write-chain-digests`
  - `require-code-release-switch`
  - `keep-current-state-machine-denied`
- `requiredFutureStateMachineEvidence`
  - release decision gate report 必须服务端生成，并由状态机复算 gate plan digest。
  - validation result digest 必须存在，不能相信 caller-supplied `validationStatus`。
  - release decision digest 必须存在，且必须是未来 server-issued `ALLOW_WRITE_EXECUTION`。
  - write chain 必须绑定 `bodyDigest`、`requestSpecDigest`、`handoffDigest`、audit receipt id 和 server-derived idempotency key。
  - code release switch 必须独立存在，不能由 validation result 或 legacy audit receipt flag 推导。
- `stateMachineFieldMigration`
  - 明确未来 `ReadinessRequest` 需要新增 release decision gate report 输入。
  - 明确 legacy `auditReceipt.releaseEligible=true` 不再是可信放行来源。
- `failureContract`
  - 明确禁止 fallback 到 legacy audit receipt flag、migration plan、release decision gate plan、caller release decision 或 durable executor handoff。

## 负例覆盖

- 缺少 release decision gate report:
  - `RELEASE_DECISION_GATE_REPORT_NOT_READY_FOR_STATE_MACHINE`
- 篡改 gate plan digest 或 source audit event digest:
  - `RELEASE_DECISION_GATE_REPORT_INVALID_FOR_STATE_MACHINE`
- 伪造 release/write 成功:
  - `releaseDecision`
  - `validationResult`
  - `auditReceipt.releaseEligible=true`
  - `writePermitted=true`
  - `writeExecutionAllowed=true`
  - `writeExecuted=true`
  - `deploymentId`
  - `postWriteReadinessTriggered=true`
  - 返回 `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM`
- 空对象 `releaseDecision={}` 也会被视为 caller-supplied release decision 并拒绝。
- secret 泄漏:
  - `Authorization`
  - `token`
  - `password`
  - `secret`
  - `ngcApiKey`
  - `nvaieApiKey`
  - 返回 `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET`

## 安全结论

- 本批没有修改 `NimCreateStateMachineSupport.ReadinessRequest` 或真实状态机放行条件。
- 本批没有新增真实 Java release decision、validator、release gate、Spring Bean、Tool、Controller、HTTP client、Elasticsearch writer 或 `ISysLogService` 依赖。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- requirement plan 不是 release decision，不是 release credential，也不是状态机放行结果。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按现有设计降级到 L1 embedding mode；Maven 退出码为 0，本批契约与回归测试通过。

本轮最终收尾还执行了边界 import 扫描、secret 扫描、H 盘同步校验和 git push，确认本批没有新增真实写执行链路或密钥材料。

## 学习笔记

顶级 Agent 的状态机不能只问“上游有没有说可以写”，而要问“我能不能独立复算上游证据链”。本批把这个原则落到一个更细的点上：未来状态机必须直接消费 release decision gate report，并复核 gate plan digest、validation result digest、release decision digest、trusted principal、write chain digest 和 code release switch。

换句话说，release decision gate report 是未来状态机输入契约，不是今天的放行令牌。现在继续 HOLD，正是为了给后续真实写执行留出可审计、可解释、可教学的安全边界。
