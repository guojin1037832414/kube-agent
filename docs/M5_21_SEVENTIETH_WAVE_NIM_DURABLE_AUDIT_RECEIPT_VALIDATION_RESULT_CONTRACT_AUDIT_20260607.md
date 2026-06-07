# M5.21 第七十波 NIM durable audit receipt validation result 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditReceiptValidationResultSupport`、`NimCreateDurableAuditReceiptValidationResultSupportTest`
> 约束: 本批只定义 future `NimDurableAuditReceiptValidationResult` 的服务端签发契约；不创建真实 validation result，不运行 validator，不创建 release decision，不注册 Tool/Spring Bean/Controller/HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-58 定义了 future validation result / release decision 的迁移蓝图，M5.21-68 要求 receipt validation 必须先绑定 server-issued storage probe result contract，M5.21-69 又把 M5.21-68 的 probe-result-binding digest 接入 validation result / release decision migration。

本批继续向前推进一小步：不是生成真实 PASS，而是把 future `NimDurableAuditReceiptValidationResult` 自身的 value contract 固定下来。它必须消费 M5.21-69 的 enhanced migration report，并把所有上游 digest 和未来 typed receipt/ack 证据要求写进契约。这样后续真正实现 server-side issuer 时，不能只拿一个 migration plan 或 caller evidence 就声称验证通过。

## 多专家会诊

- Backend/API 专家:
  - 当前类保持 package-private contract support，不注册 Spring Bean，不暴露 Controller，不绑定真实 kube-manager client。
  - `pathTemplate=/api/{orgId}/deployment` 只作为受控写链路上下文，不代表本批会执行 POST。
- Security/RBAC 专家:
  - caller-supplied `validationResult`、`releaseDecision`、legacy `auditReceipt.releaseEligible=true`、`validationStatus=PASS` 均视为 forged pass claim。
  - trusted principal 必须来自 `SERVER_SESSION_CONTEXT`，并与 audit context 的 `organizationId/userId` 一致。
- Agent 架构专家:
  - 本批把 future validation result 视为“服务端签发事实”，而不是“计划对象”或“调用方声明”。
  - `legacyMigrationReportAloneAllowed=false` 继续防止 M5.21-58 migration plan 漂移成 release credential。
- Test 架构专家:
  - 正向路径只能返回 `IMPLEMENTATION_HOLD`。
  - 负向路径覆盖缺失/篡改 M5.21-69 report、伪造成功声明、caller evidence、secret 泄漏和禁止依赖扫描。
- Documentation/Learning 专家:
  - 本批学习重点是 credential boundary：顶级 Agent 要区分“规则”“计划”“绑定要求”“服务端签发事实”“放行决策”五层对象，不能让中间层越权。

## 本批交付

- 新增 `NimCreateDurableAuditReceiptValidationResultSupport`。
  - 输入:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `validationResultProbeBindingMigrationReport` from M5.21-69
    - `callerValidationEvidence`，当前永远非权威
  - 输出:
    - `durableAuditReceiptValidationResultContract=NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT`
    - `executionMode=DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_ONLY`
    - `validationResultState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureValidationResult=NimDurableAuditReceiptValidationResult`
    - `futureReleaseDecision=NimDurableAuditReleaseDecision`
    - `serverIssuedValidationResultRequired=true`
    - `callerValidationEvidenceAuthoritative=false`
    - `legacyMigrationReportAloneAllowed=false`
- 新增 `NimCreateDurableAuditReceiptValidationResultSupportTest`。
- 新增本审计文档，并更新 changelog、wave index、项目记忆、会话进度和 v3.1 开发指南。

## 契约要点

future `NimDurableAuditReceiptValidationResult` 必须绑定:

- M5.21-69 `enhancedMigrationPlanDigest`
- M5.21-68 `sourceProbeBindingPlanDigest`
- M5.21-68 `sourceProbeResultContractDigest`
- M5.21-67 `sourceProbeExecutorPlanDigest`
- M5.21-58 `sourceMigrationPlanDigest`
- receipt schema / validation plan / writer interface / writer boundary / writer plan / availability plan digests
- source audit event digest
- trusted principal digest
- future typed storage probe receipt digest
- future pre-write durable ack digest
- future post-write durable ack digest
- future final durable receipt digest

契约还显式列出未来 server-issued validation result 必须携带的 digest 字段名:

- `storageProbeReceiptDigest`
- `preWriteDurableAckDigest`
- `postWriteDurableAckDigest`
- `durableReceiptDigest`
- `trustedPrincipalDigest`
- `sourceAuditEventDigest`

当前合法路径只会生成 `validationResultContract` 和 `validationResultContractDigest`，并返回 hold blocker:

- `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有创建真实 `NimDurableAuditReceiptValidationResult` 实例。
- 本批没有创建真实 `NimDurableAuditReleaseDecision`、release credential 或 state-machine release。
- 本批没有让 `enhancedMigrationDigestVerified`、`probeBindingDigestVerified`、`probeResultContractDigestVerified`、`storageProbeReceiptValidated`、`preWriteDurableAckValidated`、`postWriteDurableAckValidated`、`digestChainValidated`、`trustedPrincipalValidated`、`durableReceiptValidated`、`validationPassed`、`releaseEligible` 或 `writeExecutionAllowed` 变为 true。
- 本批显式保持 `realStorageTouched=false`，并要求 M5.21-69 上游 report 也必须保持 `realStorageTouched=false`。
- 本批没有新增 Spring、HTTP、Elasticsearch、`ISysLogService`、Tool registry、Controller 或真实存储写依赖。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 拒绝场景

- 缺少 M5.21-69 enhanced migration report:
  - `VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_NOT_READY`
- M5.21-69 report digest 被篡改、HOLD 状态不符合、上游 digest 不一致或 enhanced plan 不可复算:
  - `VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT`
- 单独传入 M5.21-58 legacy migration report、篡改 `sourceProbeExecutorPlanDigest`，或上游声称 `realStorageTouched=true`:
  - `VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT`
- caller 提供 validation/release/receipt evidence:
  - `CALLER_VALIDATION_EVIDENCE_NOT_AUTHORITATIVE`
- caller、audit context、principal 或 migration report 声称 PASS/release/write success:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM`
- 输入携带 Authorization、token、password、secret 或真实 NGC/NIM API Key:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET`

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationResultSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
git diff --check
```

同时完成 production boundary scan、static secret scan、H 盘 SHA256 同步校验、commit 和 push。

全量测试备注：本地 test profile 中 `model.onnx` 下载超时后按既有机制降级到 L1 embedding mode，Maven 最终退出码为 0；这是当前环境可接受的降级测试路径，不是 M5.21-70 失败。

## 是否访问真实 8100

否。本批只运行单元/契约测试和静态扫描，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 学习笔记

一个顶级 Agent 的写链路不能只问“有没有一个叫 validationResult 的对象”。真正安全的问题是：这个对象是谁签发的、绑定了哪些上游 digest、是否可复算、是否绑定可信 principal、是否绑定真实 durable receipt/ack、是否仍被 code release switch 和 state machine 复核。

M5.21-70 的核心学习点是把 future server-issued fact 的边界提前固定。这样后续实现真实 issuer 时，代码必须满足契约，而不是把任意 plan/report/flag 顺手升级成 PASS。

## 下一步建议

- 继续保持 `nim_create` HOLD。
- 后续可以定义 future `NimDurableAuditReleaseDecision` value contract，要求它绑定 M5.21-70 `validationResultContractDigest` 和未来 server-issued validation result digest。
- 真正开放写执行前，仍需完成 reviewed server-side validation issuer、release decision issuer、state-machine gate、durable executor 和 code release switch。
