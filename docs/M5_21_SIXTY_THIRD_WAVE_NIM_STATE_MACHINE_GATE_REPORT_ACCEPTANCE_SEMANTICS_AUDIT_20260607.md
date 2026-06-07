# M5.21 第六十三批 NIM state machine gate report acceptance semantics 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineReleaseDecisionRequirementSupport`、`NimCreateStateMachineReleaseDecisionRequirementSupportTest`
> 约束: 本批只澄清 `releaseDecisionGateReportAccepted` 的语义；不修改真实状态机放行逻辑，不创建 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-60 引入 `releaseDecisionGateReportAccepted=true` 表示合法输入的 gate report 形状被 contract shell 接受，并可以生成未来状态机要求计划。多专家复核指出这个字段存在一个低风险语义误读点：未来实现者可能把 `accepted=true` 错读成“真实状态机已经接受 release decision gate report，可以进入放行”。

本批不改变任何 release 行为，只给该 boolean 增加显式 scope 和两个 fail-closed 解释字段，让契约输出自己说明“接受了什么、没有接受什么”。

## 本批交付

- 新增输出字段:
  - `releaseDecisionGateReportAcceptanceScope=CONTRACT_INPUT_SHAPE_ONLY|NOT_ACCEPTED`
  - `releaseDecisionGateReportAcceptanceIsRealStateMachineRelease=false`
  - `releaseDecisionGateReportAcceptanceCanEnableWrite=false`
- 更新测试:
  - 合法输入断言 `CONTRACT_INPUT_SHAPE_ONLY`
  - 缺失 gate report 的 rejected 输入断言 `NOT_ACCEPTED`
  - 两条路径都断言不是真实状态机 release，也不能启用写执行

## 语义约束

当 `releaseDecisionGateReportAccepted=true` 时，只能表示:

- M5.21-59 gate report 的合同输入形状被本 contract shell 接受。
- 可以生成 `stateMachineRequirementPlan`，用于描述未来状态机接入要求。

它绝不表示:

- 真实 `NimCreateStateMachineSupport` 已经接受 release decision gate report。
- release decision gate digest 已经由状态机验证。
- validation result / release decision digest 已经验证。
- `writePermitted` 可以为 true。
- durable write executor 可以执行真实 POST。

## 安全结论

- 本批没有修改 `NimCreateStateMachineSupport` 或真实状态机放行条件。
- 本批没有新增 HTTP client、Elasticsearch writer、`ISysLogService`、Spring Bean、Tool 或 Controller。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 当前仍保持:
  - `realStateMachineReleaseDecisionGateReportAccepted=false`
  - `releaseDecisionGateDigestVerified=false`
  - `validationResultDigestVerified=false`
  - `releaseDecisionDigestVerified=false`
  - `stateMachineCanSetWritePermittedNow=false`
  - `writePermitted=false`
  - `writeExecutionAllowed=false`
  - `realHttpExecutionAllowed=false`

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按现有设计降级到 L1 embedding mode；Maven 退出码为 0，本批语义澄清与回归测试通过。

本轮最终收尾还执行了边界 import 扫描、secret 扫描、H 盘同步校验和 git push，确认本批没有新增真实写执行链路或密钥材料。

## 学习笔记

顶级 Agent 的状态机设计里，boolean 字段尤其危险。`accepted`、`verified`、`ready`、`eligible` 这类词如果没有范围限定，很容易在后续演进中被复用成更强的权限含义。

本批的学习点是：对安全关键 boolean，要显式写出 scope。`releaseDecisionGateReportAccepted=true` 只能是 `CONTRACT_INPUT_SHAPE_ONLY`，不能成为 release credential，也不能成为状态机真实放行条件。
