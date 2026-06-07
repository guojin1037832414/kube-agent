# M5.21 第六十四批 NIM accepted boolean non-authoritative contract 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineReleaseDecisionRequirementSupport`、`NimCreateStateMachineReleaseDecisionRequirementSupportTest`
> 约束: 本批只把 `releaseDecisionGateReportAccepted` 的非权威属性固化成机器可读契约；不修改真实状态机放行逻辑，不创建 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-63 已经为 `releaseDecisionGateReportAccepted=true` 增加 `CONTRACT_INPUT_SHAPE_ONLY` scope，明确它只表示 contract shell 接受了输入形状。多专家复核后的剩余风险是：旧 boolean 仍然存在，未来实现者如果只读取这个字段，仍可能绕过 scope、digest 和 code release switch 证据。

本批继续保持 fail-closed，只把“旧 boolean 是兼容字段、非权威、不得单独消费”写成输出字段、计划字段和测试断言。

## 本批交付

- 新增顶层输出字段:
  - `releaseDecisionGateReportAcceptedFieldIsCompatibilityOnly=true`
  - `releaseDecisionGateReportAcceptedIsAuthoritative=false`
  - `releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed=false`
  - `releaseDecisionGateReportAcceptedRequiredCompanionSignals`
- 扩展 `stateMachineFieldMigration`:
  - `currentReleaseDecisionGateReportAcceptedAuthoritative=false`
  - `releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed=false`
  - `releaseDecisionGateReportAcceptanceScopeRequired=true`
- 扩展 `failureContract` 和 `forbiddenShortcuts`:
  - 禁止 fallback 到 `releaseDecisionGateReportAccepted`
  - 增加 `RELEASE_DECISION_GATE_REPORT_ACCEPTED_FLAG_NOT_AUTHORITATIVE`
  - 明确禁止把 `releaseDecisionGateReportAccepted=true` 当作 release approval

## 语义约束

`releaseDecisionGateReportAccepted` 现在只能作为兼容/可读性字段存在。任何未来真实状态机放行代码都必须同时验证:

- `releaseDecisionGateReportAcceptanceScope=CONTRACT_INPUT_SHAPE_ONLY`
- `realStateMachineReleaseDecisionGateReportAccepted=false` 在当前合同壳中仍保持 false
- `releaseDecisionGateDigestVerified=false` 在当前合同壳中仍保持 false
- `releaseDecisionDigestVerified=false` 在当前合同壳中仍保持 false
- `stateMachineCanSetWritePermittedNow=false` 在当前合同壳中仍保持 false

也就是说，当前合同壳没有任何字段可以作为 release credential；`accepted=true` 不是 `writePermitted=true` 的前置充分条件。

## 安全结论

- 本批没有修改 `NimCreateStateMachineSupport` 或真实状态机放行条件。
- 本批没有新增 HTTP client、Elasticsearch writer、`ISysLogService`、Spring Bean、Tool 或 Controller。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 当前仍保持 `writePermitted=false`、`writeExecutionAllowed=false`、`realHttpExecutionAllowed=false`。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按现有设计降级到 L1 embedding mode；Maven 退出码为 0，本批非权威兼容字段契约与回归测试通过。

本轮最终收尾还执行了边界 import 扫描、secret 扫描、H 盘同步校验和 git push，确认本批没有新增真实写执行链路或密钥材料。

## 学习笔记

顶级 Agent 的权限链路里，兼容字段是常见技术债。比删除旧字段更稳的做法，是给它增加机器可读的非权威标记，并在 failure contract 里禁止 fallback。这样未来实现者即使看到旧 boolean，也会被测试和文档引导到正确的证据链: scope、digest、trusted principal、code release switch、durable executor 二次校验。
