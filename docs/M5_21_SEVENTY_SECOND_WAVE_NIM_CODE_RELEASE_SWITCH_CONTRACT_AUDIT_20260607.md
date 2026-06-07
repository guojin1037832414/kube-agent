# M5.21 第七十二批 NIM code release switch 契约审计

> 日期: 2026-06-07 Asia/Shanghai
> 范围: `NimCreateDurableAuditCodeReleaseSwitchContractSupport`、`NimCreateDurableAuditCodeReleaseSwitchContractSupportTest`
> 约束: 本批只定义 future `NimCreateDurableAuditCodeReleaseSwitch` 的 reviewed/server-owned 契约；不创建真实开关，不打开 release switch，不创建 release decision，不接受真实 validation result，不签发 release credential，不修改状态机，不接入 durable executor，不注册 Tool/Spring Bean/Controller/HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-71 已经把 future `NimDurableAuditReleaseDecision` 固定为服务端签发放行事实。但顶级 Agent 的真实写链路还需要最后一道独立条件：代码级 release switch。没有这层，未来实现者可能把 validation result digest 和 release decision digest 都接好以后，忘记要求“这段写路径代码本身已经通过 reviewed switch 开启”。

本批把 code release switch 提前写成契约：它必须绑定 M5.21-71 `releaseDecisionContractDigest`、未来 release/validation digest、可信 principal、写链路 digest，以及代码审查、测试证据、安全批准、回滚计划和变更窗口 digest。它仍然不打开真实开关。

## 多专家会诊

- Backend/API 专家:
  - 当前类保持 package-private contract support，不注册 Spring Bean、Controller 或 HTTP client。
  - `pathTemplate=/api/{orgId}/deployment` 只作为受控写链路上下文，不代表本批会执行 POST。
- Security/RBAC 专家:
  - code switch 必须是 server-owned reviewed artifact，caller、环境变量、runtime flag 和 legacy config flag 都不能成为放行来源。
  - `switchState=OPEN`、`codeReleaseSwitchDigestVerified=true`、`writePermitted=true` 等输入都按 forged open claim fail-closed。
- Agent 架构专家:
  - 本批把 validation fact、release fact 和 code switch fact 分成三层，避免把 release decision 误当成“代码路径已批准”。
- Test 架构专家:
  - 正向路径只能返回 `IMPLEMENTATION_HOLD`。
  - 负向路径覆盖缺失/篡改 M5.21-71 report、伪造开关打开、caller override、secret 泄漏和禁止依赖扫描。
- Documentation/Learning 专家:
  - 本批学习重点是 release switch 不是配置布尔值，而是带审查证据和 digest 绑定的服务端发布事实。

## 本批交付

- 新增 `NimCreateDurableAuditCodeReleaseSwitchContractSupport`。
  - 输入:
    - `auditContext`
    - `trustedPrincipalSnapshot`
    - `durableAuditReleaseDecisionContractReport` from M5.21-71
    - `callerSwitchEvidence`，当前永远非权威
  - 输出:
    - `durableAuditCodeReleaseSwitchContract=NIM_CREATE_DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT`
    - `executionMode=DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_ONLY`
    - `switchState=IMPLEMENTATION_HOLD|REJECTED`
    - `futureCodeReleaseSwitch=NimCreateDurableAuditCodeReleaseSwitch`
    - `serverOwnedCodeReleaseSwitchRequired=true`
    - `reviewedCodeSwitchDigestRequired=true`
    - `callerSwitchEvidenceAuthoritative=false`
- 新增 `NimCreateDurableAuditCodeReleaseSwitchContractSupportTest`。
- 新增本审计文档，并更新 changelog、wave index、项目记忆、会话进度和 v3.1 开发指南。

## 契约要点

future `NimCreateDurableAuditCodeReleaseSwitch` 必须绑定:

- M5.21-71 `releaseDecisionContractDigest`
- M5.21-70 `validationResultContractDigest`
- future server-issued `validationResultDigest`
- future server-issued `releaseDecisionDigest`
- future `codeReleaseSwitchDigest`
- `codeReviewDigest`
- `testEvidenceDigest`
- `securityApprovalDigest`
- `rollbackPlanDigest`
- `changeWindowDigest`
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

当前合法路径只会生成 `codeReleaseSwitchContract` 和 `codeReleaseSwitchContractDigest`，并返回 hold blocker:

- `DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有创建真实 code release switch。
- 本批没有打开真实 release switch。
- 本批没有创建真实 `NimDurableAuditReleaseDecision` 或 `NimDurableAuditReceiptValidationResult`。
- 本批没有签发 release credential，没有绑定状态机 release，没有绑定 durable executor release。
- 本批没有让 `codeReleaseSwitchDigestVerified`、`codeReviewDigestVerified`、`testEvidenceDigestVerified`、`releaseDecisionDigestVerified`、`validationResultDigestVerified`、`trustedPrincipalValidated`、`stateMachineReleaseBound`、`durableExecutorReleaseBound`、`releaseEligible`、`writePermitted` 或 `writeExecutionAllowed` 变为 true。
- 本批显式保持 `realStorageTouched=false`，并要求 M5.21-71 上游 report 也必须保持 `realStorageTouched=false`。
- 本批没有新增 Spring、HTTP、Elasticsearch、`ISysLogService`、Tool registry、Controller 或真实存储写依赖。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 拒绝场景

- 缺少 M5.21-71 release decision contract report:
  - `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_NOT_READY`
- M5.21-71 report digest 被篡改、HOLD 状态不符合、上游 digest 不一致或 contract digest 不可复算:
  - `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_INVALID_FOR_CODE_SWITCH`
- caller 提供 code switch、环境变量、runtime flag 或审查 evidence:
  - `CALLER_CODE_RELEASE_SWITCH_EVIDENCE_NOT_AUTHORITATIVE`
- caller、audit context、principal 或 M5.21-71 report 声称 switch open、release/write success:
  - `DURABLE_AUDIT_CODE_RELEASE_SWITCH_FORGED_OPEN_CLAIM`
- 输入携带 Authorization、token、password、secret 或真实 NGC/NIM API Key:
  - `DURABLE_AUDIT_CODE_RELEASE_SWITCH_INPUT_CONTAINS_FORBIDDEN_SECRET`

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditStorageProbeExecutorSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
mvn -q test
git diff --check
```

同时完成 production boundary scan、static secret scan、H 盘 SHA256 同步校验、commit 和 push。
全量测试备注：本次本地 test profile 中 `model.onnx` 下载超时并降级到 L1 embedding mode，Maven 最终退出码为 0；这是当前环境可接受的降级测试路径，不代表 M5.21-72 失败。

## 是否访问真实 8100

否。本批只运行单元/契约测试和静态扫描，不访问真实 kube-manager、NIM 服务或 Elasticsearch。

## 学习笔记

release switch 很容易被误解成“一个配置布尔值”。顶级 Agent 里它应该是独立的服务端发布事实：谁审过、测过、批准过、如何回滚、在哪个变更窗口开放、绑定到哪个 release decision 和写链 digest。只有这样，未来 `writePermitted=true` 才不是一个随手改出来的 flag，而是多层证据链的结果。

M5.21-72 的核心学习点是把代码发布治理纳入 Agent 写链路。validation result 证明“证据有效”，release decision 证明“可以放行”，code release switch 证明“这段真实写路径代码已被审查并允许执行”。三层缺一不可。

## 下一步建议

- 继续保持 `nim_create` HOLD。
- 后续可以定义 state-machine / durable executor 对 code release switch digest 的最终回接契约。
- 真正开放写执行前，仍需完成真实 validation issuer、release decision issuer、server-owned code switch issuer、state-machine release gate、durable executor pre-POST re-check 和真实 durable audit writer 的多轮审查。
