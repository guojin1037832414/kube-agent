# M5.21 第七十一批 NIM durable audit release decision 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditReleaseDecisionContractSupport`、`NimCreateDurableAuditReleaseDecisionContractSupportTest`
> 约束: 本批只定义 future `NimDurableAuditReleaseDecision` 的服务端签发契约；不创建真实 release decision，不接受真实 validation result，不签发 release credential，不修改状态机，不接入 durable executor，不注册 Tool/Spring Bean/Controller/HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-70 已经把 future `NimDurableAuditReceiptValidationResult` 固定为服务端签发事实，而不是 migration plan、caller JSON 或 legacy flag。本批继续推进下一层边界：future `NimDurableAuditReleaseDecision` 必须消费 M5.21-70 validation result contract，并且未来还必须绑定 server-issued validation result digest、release decision digest、代码级 release switch、状态机复核和 durable executor 写前复核。

这一步仍然不是放行。它的价值在于先把“什么才算 release decision”写成可测试契约，防止后续实现时把 M5.21-70 contract、`releaseDecisionGateReportAccepted=true`、legacy `auditReceipt.releaseEligible=true` 或 caller supplied `releaseDecision` 顺手升级为写权限。

## 多专家会诊

- Backend/API 专家:
  - 当前类保持 package-private contract support，不注册 Spring Bean、Controller 或 HTTP client。
  - `pathTemplate=/api/{orgId}/deployment` 只作为受控写链路上下文，不代表本批会执行 POST。
- Security/RBAC 专家:
  - release decision 必须来自未来 reviewed server-side issuer，caller release evidence 永远非权威。
  - `releaseDecisionGateReportAccepted`、`releaseDecisionGateDigestVerified`、`stateMachineCanSetWritePermittedNow` 等外部成功声明均按 forged release claim fail-closed。
- Agent 架构专家:
  - 本批把 validation fact 和 release decision 分成两层：validation result contract 不是 release credential，release decision 也必须再次绑定 code release switch 和 write-chain digests。
- Test 架构专家:
  - 正向路径只能返回 `IMPLEMENTATION_HOLD`。
  - 负向路径覆盖缺失/篡改 M5.21-70 report、伪造 release/write、caller evidence、secret 泄漏和禁止依赖扫描。
- Documentation/Learning 专家:
  - 本批学习重点是“放行事实必须服务端签发、可复核、可绑定、可拒绝”，不能让任何中间计划对象越权。

## 本批交付

- 新增 `NimCreateDurableAuditReleaseDecisionContractSupport`。
  - 输入:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditReceiptValidationResultContractReport` from M5.21-70
    - `callerReleaseEvidence`，当前永远非权威
  - 输出:
    - `durableAuditReleaseDecisionContract=NIM_CREATE_DURABLE_AUDIT_RELEASE_DECISION_CONTRACT`
    - `executionMode=DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_ONLY`
    - `releaseDecisionState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureReleaseDecision=NimDurableAuditReleaseDecision`
    - `futureValidationResult=NimDurableAuditReceiptValidationResult`
    - `serverIssuedReleaseDecisionRequired=true`
    - `callerReleaseEvidenceAuthoritative=false`
    - `serverIssuedValidationResultDigestRequired=true`
- 新增 `NimCreateDurableAuditReleaseDecisionContractSupportTest`。
- 新增本审计文档，并更新 changelog、wave index、项目记忆、会话进度和 v3.1 开发指南。

## 契约要点

future `NimDurableAuditReleaseDecision` 必须绑定:

- M5.21-70 `validationResultContractDigest`
- future server-issued `validationResultDigest`
- future server-issued `releaseDecisionDigest`
- `codeReleaseSwitchDigest`
- `bodyDigest`
- `requestSpecDigest`
- `handoffDigest`
- `auditReceiptId`
- `serverDerivedIdempotencyKey`
- source audit event digest
- trusted principal digest
- M5.21-69 enhanced migration digest
- M5.21-68 probe binding/result digests
- M5.21-67 probe executor digest
- M5.21-58 migration digest
- receipt schema / validation plan / writer interface / writer boundary / writer plan / availability plan digests

当前合法路径只会生成 `releaseDecisionContract` 和 `releaseDecisionContractDigest`，并返回 hold blocker:

- `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有创建真实 `NimDurableAuditReleaseDecision` 实例。
- 本批没有接受真实 `NimDurableAuditReceiptValidationResult`。
- 本批没有签发 release credential，没有绑定状态机 release，没有绑定 durable executor release。
- 本批没有让 `validationResultDigestVerified`、`validationResultContractDigestVerified`、`releaseDecisionDigestVerified`、`trustedPrincipalValidated`、`codeReleaseSwitchVerified`、`stateMachineReleaseBound`、`durableExecutorReleaseBound`、`releaseDecisionAccepted`、`releaseEligible`、`writePermitted` 或 `writeExecutionAllowed` 变为 true。
- 本批显式保持 `realStorageTouched=false`，并要求 M5.21-70 上游 report 也必须保持 `realStorageTouched=false`。
- 本批没有新增 Spring、HTTP、Elasticsearch、`ISysLogService`、Tool registry、Controller 或真实存储写依赖。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 拒绝场景

- 缺少 M5.21-70 validation result contract report:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_NOT_READY`
- M5.21-70 report digest 被篡改、HOLD 状态不符合、上游 digest 不一致或 contract digest 不可复算:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_INVALID_FOR_RELEASE_DECISION`
- caller 提供 release/validation/receipt/executor evidence:
  - `CALLER_RELEASE_EVIDENCE_NOT_AUTHORITATIVE`
- caller、audit context、principal 或 M5.21-70 report 声称 release/write/gate success:
  - `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_FORGED_RELEASE_CLAIM`
- 输入携带 Authorization、token、password、secret 或真实 NGC/NIM API Key:
  - `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_INPUT_CONTAINS_FORBIDDEN_SECRET`

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
git diff --check
```

同时完成 production boundary scan、static secret scan、H 盘 SHA256 同步校验、commit 和 push。
全量测试备注：本次本地 test profile 中 `model.onnx` 下载超时并降级到 L1 embedding mode，Maven 最终退出码为 0；这是当前环境可接受的降级测试路径，不代表 M5.21-71 失败。

## 是否访问真实 8100

否。本批只运行单元/契约测试和静态扫描，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 学习笔记

顶级 Agent 的写链路不能只看“有没有一个 releaseDecision 对象”。真正安全的问题是：这个 release decision 是谁签发的，绑定了哪个 validation result digest，是否绑定可信 principal，是否绑定 body/request/handoff/auditReceiptId/idempotencyKey，是否经过 code release switch，是否被状态机和 durable executor 在真正写前重新检查。

M5.21-71 的核心学习点是把 release credential boundary 固定下来。validation result 是“验证事实”，release decision 是“放行事实”，两者不能合并，也不能由 caller evidence、legacy flag、compatibility boolean 或 executor success 回填。

## 下一步建议

- 继续保持 `nim_create` HOLD。
- 后续实现 reviewed server-side validation issuer 后，再实现 release decision issuer。
- 真正开放写执行前，仍需完成 state-machine release gate、durable executor pre-POST re-check、code release switch 和真实 durable audit writer 的多轮审查。
