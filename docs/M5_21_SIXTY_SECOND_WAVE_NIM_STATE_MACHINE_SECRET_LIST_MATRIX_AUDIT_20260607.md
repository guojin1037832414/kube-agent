# M5.21 第六十二批 NIM state machine secret list matrix 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineReleaseDecisionRequirementSupportTest`
> 约束: 本批只补齐 M5.21-61 的 list-item secret 测试矩阵；不修改真实状态机放行逻辑，不创建 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-61 已经把 secret 泄漏测试扩展到三个输入面，并覆盖顶层字段、嵌套 map 和 list item。多专家复核确认方向正确，同时指出一个细节：list item 覆盖只落在 `durableAuditReleaseDecisionGateReport` 上。若目标是输入面 x 嵌套形状的矩阵证明，还应让 `auditContext` 与 `trustedPrincipalSnapshot` 也各自拥有 list item 场景。

本批补齐这个矩阵，让每个状态机 gate 输入都证明可以拒绝 list-carried secret metadata。

## 本批交付

- 更新 `NimCreateStateMachineReleaseDecisionRequirementSupportTest`。
- 新增 list item secret leak cases:
  - `auditContext.callerEvents[].token`
  - `trustedPrincipalSnapshot.sessionEvidence[].password`
- 既有 M5.21-61 case 继续覆盖:
  - `durableAuditReleaseDecisionGateReport.diagnosticEvents[].token`

## Fail-Closed 断言

三个输入面的 list item secret 泄漏都必须返回:

- `requirementState=REJECTED`
- `inputAccepted=false`
- `stateMachineRequirementPlanPrepared=false`
- 空 `stateMachineRequirementPlan`
- `writePermitted=false`
- `writeExecutionAllowed=false`
- `realHttpExecutionAllowed=false`
- blocker `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET`
- 不进入 `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有修改 `NimCreateStateMachineReleaseDecisionRequirementSupport` 生产代码。
- 本批没有修改 `NimCreateStateMachineSupport` 或真实状态机放行条件。
- 本批没有新增 HTTP client、Elasticsearch writer、`ISysLogService`、Spring Bean、Tool 或 Controller。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 测试值继续使用 `redacted-test-value`，不向仓库写入真实或仿真的 credential-shaped token。

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

安全测试的质量不只看“有没有测到某个 blocker”，还要看覆盖矩阵是否能对应真实攻击面。list-carried metadata 是 Agent 系统常见的旁路形态：调用方可能不在顶层字段塞 token，而是把它放进事件、证据、历史记录或诊断列表里。

本批的学习点是：当一个 release gate 消费多个输入对象时，每个输入对象都应该被当成独立攻击面来测试。矩阵越清楚，未来重构越不容易把安全边界误删。
