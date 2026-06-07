# M5.21 第六十一批 NIM state machine secret coverage 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineReleaseDecisionRequirementSupportTest`
> 约束: 本批只增强 M5.21-60 的 secret 泄漏测试证据；不修改真实状态机放行逻辑，不创建 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-60 已经把未来状态机必须消费 `durableAuditReleaseDecisionGateReport` 的契约壳固化下来。多专家复核确认该壳保持 contract-only，但指出一个低风险增强点：secret 泄漏测试只直接覆盖了 `auditContext.Authorization`，虽然实现已经递归扫描 `auditContext`、`trustedPrincipalSnapshot` 和 `durableAuditReleaseDecisionGateReport`，测试证据还不够宽。

本批把这个建议转成可执行测试，让安全边界不仅“看起来递归”，而是被用例证明覆盖所有输入面。

## 本批交付

- 更新 `NimCreateStateMachineReleaseDecisionRequirementSupportTest`。
- 新增 `stateMachineRequirement_shouldRejectSecretLeakageAcrossAllInputsAndNestedEvidence`。
- 覆盖输入:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `durableAuditReleaseDecisionGateReport`
- 覆盖位置:
  - 顶层字段
  - 嵌套 map
  - list item 中的嵌套 map
- 覆盖 forbidden keys:
  - `token`
  - `password`
  - `secret`
  - `Authorization`
  - `ngcApiKey`
  - `nvaieApiKey`

## Fail-Closed 断言

每个 secret 泄漏场景都必须返回:

- `requirementState=REJECTED`
- `inputAccepted=false`
- `stateMachineRequirementPlanPrepared=false`
- 空 `stateMachineRequirementPlan`
- `writePermitted=false`
- `writeExecutionAllowed=false`
- `realHttpExecutionAllowed=false`
- blocker `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET`

同时断言 rejected input 不会继续进入 positive HOLD path:

- 不应出现 `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有修改 `NimCreateStateMachineReleaseDecisionRequirementSupport` 生产代码。
- 本批没有修改 `NimCreateStateMachineSupport` 或真实状态机放行条件。
- 本批没有新增 HTTP client、Elasticsearch writer、`ISysLogService`、Spring Bean、Tool 或 Controller。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 测试值使用 `redacted-test-value`，不向仓库写入真实或仿真的 credential-shaped token。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按现有设计降级到 L1 embedding mode；Maven 退出码为 0，本批测试增强与回归测试通过。

本轮最终收尾还执行了边界 import 扫描、secret 扫描、H 盘同步校验和 git push，确认本批没有新增真实写执行链路或密钥材料。

## 学习笔记

顶级 Agent 的安全测试不能只证明一个 happy blocker。一个 release gate 有多少输入面，测试就应该覆盖多少输入面；否则未来重构时，很容易只保住最显眼的路径，却让嵌套证据或旁路 metadata 带入敏感材料。

本批的学习点是：安全契约要同时验证“危险字段被拒绝”和“拒绝后不会落入任何可继续执行的 HOLD/计划路径”。这能帮助后续真实状态机接入 release decision gate report 时，继续保持 secret redaction 与 fail-closed 的工程肌肉记忆。
