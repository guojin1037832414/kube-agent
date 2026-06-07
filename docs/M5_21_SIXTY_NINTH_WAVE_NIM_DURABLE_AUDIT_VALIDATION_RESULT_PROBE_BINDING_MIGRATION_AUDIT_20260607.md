# M5.21 第六十九批 NIM durable audit validation result probe binding migration 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`、`NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest`
> 约束: 本批只定义 future validation result / release decision migration 必须消费 M5.21-68 probe-result-binding report 的桥接契约；不创建真实 DTO/Bean/validator/result/decision/release gate，不绑定 HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-58 已经把 future `NimDurableAuditReceiptValidationResult` 和 `NimDurableAuditReleaseDecision` 从 receipt validation gate 中拆成独立迁移计划。M5.21-68 又补上了 receipt validation 必须先绑定 M5.21-67 storage probe result contract 的要求。

本批解决两者之间的关键断点：未来 validation result / release decision migration 不能只拿 M5.21-58 migration report 就继续向前，也不能把 validation gate、schema 或 caller evidence 当成 release evidence。它必须额外绑定 M5.21-68 的 `bindingPlanDigest` 和 `sourceProbeResultContractDigest`，让 probe-result-binding digest 进入 future validation result 与 release decision 的证据链。

## 本批交付

- 新增 `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`。
- 新增 `NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest`。
- 输入绑定:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `durableAuditReceiptValidationProbeResultBindingReport` from M5.21-68
  - `durableAuditValidationResultMigrationReport` from M5.21-58
  - optional `callerReleaseEvidence`，当前永远非权威
- 输出固定 fail-closed:
  - `executionMode=DURABLE_AUDIT_VALIDATION_RESULT_PROBE_BINDING_MIGRATION_CONTRACT_ONLY`
  - `migrationState=IMPLEMENTATION_HOLD|REJECTED`
  - `probeBindingRequiredBeforeValidationResult=true`
  - `legacyMigrationReportAloneAllowed=false`
  - `probeBindingBoundToValidationResultMigration=false`
  - `realValidationResultCreated=false`
  - `realReleaseDecisionCreated=false`
  - `durableReceiptValidationPassed=false`
  - `releaseEligible=false`
  - `writeExecutionAllowed=false`

## 契约要点

`NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 同时校验:

- M5.21-68 probe binding report 仍为 HOLD，且 `bindingPlanDigest` 可复算。
- M5.21-58 validation result migration report 仍为 HOLD，且 `migrationPlanDigest` 可复算。
- 两份上游报告绑定同一 `sourceAuditEventDigest`、`sourceReceiptSchemaDigest`、`sourceValidationPlanDigest`、`sourceInterfaceSpecDigest`、`sourceBoundaryPlanDigest`、`sourceWriterPlanDigest` 和 `sourceAvailabilityPlanDigest`。
- enhanced validation result contract 必须携带 `sourceProbeBindingPlanDigest`、`sourceProbeResultContractDigest`、`sourceMigrationPlanDigest`，并要求 `mustBindProbeResultBindingDigest=true`、`mustBindProbeResultContractDigest=true`、`mustBindPreWriteDurableAckDigest=true`、`mustBindPostWriteDurableAckDigest=true`。
- enhanced release decision contract 必须绑定 probe binding digest 和 validation result digest，并继续绑定 schema/validation plan digest、trusted principal、audit event 和 code release switch。
- M5.21-58 migration report、M5.21-68 binding report、caller release evidence、legacy `auditReceipt.releaseEligible=true`、caller supplied `validationResult` / `releaseDecision` / `probeResult` 均不能单独成为放行依据。

合法路径只生成 `enhancedMigrationPlan`，并给出 hold blocker:

- `VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有把 M5.21-58 migration plan 升级成真实 validation result。
- 本批没有生成真实 `NimDurableAuditReceiptValidationResult`、`NimDurableAuditReleaseDecision`、release credential 或 state-machine release。
- 本批没有让 `probeBindingBoundToValidationResultMigration`、`storageProbeResultBoundForValidation`、`durableReceiptValidationPassed`、`releaseEligible` 或 `writeExecutionAllowed` 变成 true。
- 本批新增源码级依赖扫描，防止 support 类绑定 Spring、HTTP、Elasticsearch、`ISysLogService`、`java.net` 或真实存储写调用。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
git diff --check
```

同时完成 production 边界 import / write-path 扫描、diff secret 扫描、H 盘 SHA256 同步校验、commit 和 push。全量测试备注：`model.onnx` 下载超时后 Atlas 降级到 L1 embedding mode，但 Maven 退出码为 0；这是当前本地环境可接受的降级测试路径，不是 M5.21-69 失败。

## HOLD 清单

- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 没有真实 `8100` 访问。
- 没有真实 `POST /api/{orgId}/deployment` 执行。
- 没有 Elasticsearch / `ISysLogService` / `sys_log` 写入。
- 没有 Spring Bean、Tool、Controller 或真实 HTTP client 注册。
- `callerReleaseEvidence` 当前永远非权威。
- M5.21-58 migration report 不能单独作为 validation result / release decision 的来源。

## 学习笔记

顶级 Agent 的写链路不能只证明“将来会有 validation result / release decision”。migration plan 只是迁移计划，不是验证事实；binding plan 也只是绑定要求，不是验证通过。真正可放行的路径必须让 server-issued validation result 绑定 probe result contract、probe binding digest、receipt/ack digest、trusted principal 和 audit event，再让 release decision 绑定 validation result digest、enhanced migration digest、code release switch 和 state-machine gate。

M5.21-69 的核心学习点是避免“中间计划对象漂移成权限凭证”。任何 future release 代码如果只消费 M5.21-58 的 `migrationPlanDigest`，没有同时消费 M5.21-68 的 `bindingPlanDigest`，都应该 fail closed。

## 下一步

- 继续定义 future server-issued validation result value type、release decision value type、canonical digest 与签发边界。
- 让后续 state-machine gate 明确消费 enhanced migration digest，而不是消费旧 migration report alone。
- 保持 NIM create 写链路 HOLD，直到 trusted policy、durable audit writer、probe result、pre/post ack、receipt validation、release decision、state-machine gate 和 code release switch 全部通过 review。
