# M5.21-138 NIM validation result migration plan maps closed audit

## 本轮范围

本轮关闭的是 `NimCreateDurableAuditValidationResultMigrationSupport` 生产、并被 `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 消费的完整 `migrationPlan` map。

这是证明对象契约加固，不是功能放行。本轮不实现真实 receipt validator、真实 validation result、真实 release decision、code release switch、runtime write path、durable audit writer，也不执行 NIM deployment POST。

## 为什么要做

`migrationPlanDigest` 只能证明调用方提交的 map 是“自洽的”：map 改了以后，只要重新计算 digest，就仍然能得到一个匹配的新 digest。

它不能证明 map 里的每一个 key 都已经经过安全评审，也不能证明新增 key 可以成为未来 migration / release 语义。

在本轮之前，probe-binding migration consumer 会自己解释 `migrationPlan` 里的若干已知 nested map。这个模式有一个隐蔽风险：如果调用方或未来未审查代码往 `migrationPlan` 里追加一个新的 authority-shaped key，然后重新计算 `migrationPlanDigest`，旧 consumer 可能因为“只看已知字段”而放过这个扩展。

更稳的模式是 producer-owned canonical equality：

- 生产者拥有完整协议形状。
- report 暴露足够的 source identity 和 digest 字段，用来重建标准对象。
- consumer 先校验 source identity / digest chain / HOLD 边界，再要求实际对象与生产者 canonical helper 的输出完全相等。

## 代码变更

### `NimCreateDurableAuditValidationResultMigrationSupport`

- 新增顶层来源身份字段：
  - `sourceOrganizationId`
  - `sourceUserId`
  - `sourceUsername`
- 新增 `migrationPlanFromReport(...)`，作为 producer-owned canonical reconstruction helper。
- 将真实输出 plan 与 report 重建 plan 共用同一个 digest/identity builder，保证“生产时形状”和“消费方重建形状”一致。

### `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`

- `validateMigrationReport(...)` 现在要求 migration report 的来源身份与当前可信 audit/principal 匹配。
- `migrationPlanContractValid(...)` 不再本地逐项解释 nested map，而是收敛为：
  - `migrationPlan` 非空；
  - source digest 字段匹配；
  - source audit digest 匹配；
  - source identity 匹配；
  - `migrationPlan.equals(NimCreateDurableAuditValidationResultMigrationSupport.migrationPlanFromReport(migrationReport))`。
- 保留前置安全门：
  - report 名称、执行模式和 HOLD 状态；
  - 所有真实执行、真实 validation result、真实 release decision、release/write 成功状态必须为 false；
  - `migrationPlanDigestAlgorithm` 与 `migrationPlanDigest` 校验；
  - `blockedBy` 只允许预期的 implementation hold；
  - 与 probe binding report 的 cross-binding digest chain；
  - caller release evidence rejection；
  - forged claim rejection；
  - secret material detection。

## 测试设计

### 正向契约测试

`NimCreateDurableAuditValidationResultMigrationSupportTest` 新增断言：

- report 输出 `sourceOrganizationId/sourceUserId/sourceUsername`；
- `migrationPlan` 完全等于 `migrationPlanFromReport(report)`。

### digest-consistent forgery 回归

`NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest` 新增多组伪造场景：先篡改 `migrationPlan`，再重新计算 `migrationPlanDigest`，最后仍要求下游 fail closed。

覆盖的漂移点：

- 顶层额外 key；
- `trustedIdentityBinding`；
- `migrationSequence`；
- `validationResultContract`；
- `validationResultContract.currentTemplate`；
- `releaseDecisionContract`；
- `releaseDecisionContract.currentTemplate`；
- `legacyCompatibilityPolicy`；
- `releaseCredentialRules`；
- `failureContract`；
- `failureContract.failureStatuses`；
- `forbiddenShortcuts`。

## 验证

- `git diff --check`
- `mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest" test`
- `mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest" test`
- `mvn -q test`
- Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.
- 对变更 support 文件做静态扫描，未发现真实网络、真实存储、真实写入集成；命中仅来自注释、禁用说明和 secret detector 文案。

## HOLD 边界

本轮继续保持：

- 不访问真实 `8100`。
- 不调用真实 NIM service HTTP。
- 不发送 Authorization header。
- 不写 durable audit。
- 不执行 `POST /api/{orgId}/deployment`。
- 不创建 validation result signer。
- 不创建 release decision signer。
- 不实现 code release switch。
- 不打开 runtime write behavior。
- 不写 Elasticsearch / `ISysLogService` / `sys_log`。
- `nim_create` 仍是 HOLD/mock-first。

## 学习总结

顶级 Agent 的安全链路不能把 proof map 当成普通 JSON。只要一个 map 会被下游消费，并且未来可能影响 validation / release / write path，它就是协议对象。

digest 是必要的完整性绑定，但不是语义授权。真正的语义授权来自：

- 生产者源码拥有完整 canonical shape；
- consumer 做 exact equality；
- digest-consistent forgery 测试证明重新计算 hash 也不能绕过；
- 文档和审查记录说明为什么这个 proof object 可以被信任。

这一波的核心教学点是：不要让 downstream consumer 手写“我现在认识的几个字段”。让 producer 拥有完整协议，让 consumer 只接受 producer 的标准形状。这样未来任何新增 migration 语义都必须经过代码、测试、文档和审查，而不是悄悄混进 release path。
