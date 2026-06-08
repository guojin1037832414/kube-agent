# M5.21-139 NIM enhanced migration plan maps closed audit

## 本轮范围

本轮关闭的是 `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` 生产、并被
`NimCreateDurableAuditReceiptValidationResultSupport` 消费的完整 `enhancedMigrationPlan` map。

这仍然是证明对象契约加固，不是写入放行。本轮不实现真实 receipt validator、真实 validation result、真实 release decision、code release switch、runtime write path、durable audit writer，也不执行 NIM deployment POST。

## 为什么要做

`enhancedMigrationPlanDigest` 只能证明调用方提交的 `enhancedMigrationPlan` 和 digest 彼此一致。
如果调用方篡改 plan 后重新计算 digest，普通 hash 校验仍然会通过。

在本轮之前，receipt validation result consumer 会本地解释 `enhancedMigrationPlan` 中的一批 nested map：

- `trustedIdentityBinding`
- `probeBindingRequirement`
- `enhancedValidationResultContract`
- `enhancedValidationResultContract.currentTemplate`
- `enhancedReleaseDecisionContract`
- `enhancedReleaseDecisionContract.currentTemplate`
- `failureContract`

这种模式容易漏掉新增的 authority-shaped key。更稳的模式是 producer-owned canonical equality：

- producer 拥有完整协议形状；
- report 暴露足够的 source identity 和 digest 字段；
- consumer 先保留 HOLD、false-state、digest、blockedBy、secret、forged-claim 等前置安全门；
- consumer 最后要求实际 `enhancedMigrationPlan` 完全等于 producer helper 从 report 重建出的标准对象。

## 代码变更

### `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`

- 新增顶层来源身份字段：
  - `sourceOrganizationId`
  - `sourceUserId`
  - `sourceUsername`
- 新增 `enhancedMigrationPlanFromReport(...)` 作为 producer-owned canonical reconstruction helper。
- 将真实输出 plan 和 report 重建 plan 合并到同一套 digest/identity builder，避免两条路径形状漂移。

### `NimCreateDurableAuditReceiptValidationResultSupport`

- `validateEnhancedMigrationReport(...)` 现在要求 migration report 的 source identity 与当前 audit/principal 匹配。
- `enhancedMigrationPlanValid(...)` 保留 source digest、source audit digest、trusted identity binding 和 digest algorithm 检查。
- 删除本地嵌套解释器，改为：
  - `enhancedMigrationPlan` 非空；
  - source digest 字段匹配；
  - source audit digest 匹配；
  - source identity 匹配；
  - `enhancedMigrationPlan.equals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.enhancedMigrationPlanFromReport(report))`。

## 测试设计

### 正向契约测试

`NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest` 现在断言：

- report 输出 `sourceOrganizationId/sourceUserId/sourceUsername`；
- `enhancedMigrationPlan` 完全等于 `enhancedMigrationPlanFromReport(report)`。

### digest-consistent forgery 回归

`NimCreateDurableAuditReceiptValidationResultSupportTest` 新增多组伪造场景：先篡改 `enhancedMigrationPlan`，再重新计算 `enhancedMigrationPlanDigest`，最后仍要求 receipt validation result fail closed。

覆盖的漂移点：

- 顶层额外 key；
- `trustedIdentityBinding`；
- `probeBindingRequirement`；
- `enhancedValidationResultContract`；
- `enhancedValidationResultContract.currentTemplate`；
- `enhancedReleaseDecisionContract`；
- `enhancedReleaseDecisionContract.currentTemplate`；
- `migrationSequencePatch`；
- `currentDecisionTemplate`；
- `failureContract`；
- `failureContract.failureStatuses`；
- `forbiddenShortcuts`。

## 已验证

- `mvn -q "-Dtest=NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest" test`
- `git diff --check`
- `mvn -q "-Dtest=NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest" test`
- `mvn -q test`

完整测试仍出现本地 `model.onnx` 下载超时导致 L1 embedding 降级的已知提示，但 Maven 退出码为 0。

## HOLD 边界

本轮继续保持：

- 不访问真实 `8100`；
- 不调用真实 NIM service HTTP；
- 不发送 Authorization header；
- 不写 durable audit；
- 不执行 `POST /api/{orgId}/deployment`；
- 不创建 validation result signer；
- 不创建 release decision signer；
- 不实现 code release switch；
- 不打开 runtime write behavior；
- 不写 Elasticsearch / `ISysLogService` / `sys_log`；
- `nim_create` 仍是 HOLD/mock-first。

## 学习总结

`enhancedMigrationPlan` 是 validation result 生成前的关键桥接协议。它本身不会产生 PASS，但它定义了未来 validation result 和 release decision 必须绑定哪些上游证据。

顶级 Agent 的安全链路不能满足于“hash 对上了”。hash 只能证明对象没有在不知道 digest 的情况下被替换，不能证明对象中的新字段已被授权。真正稳的做法是：

- producer 拥有完整 canonical shape；
- consumer 保留外层 fail-closed 安全门；
- consumer 对 proof object 做 exact equality；
- 测试使用 digest-consistent forgery 证明“重新计算 digest”也不能绕过语义闭环。

这轮的教学点是：越靠近 validation / release / write path 的 map，越不能把它当普通 JSON。它是协议对象，必须由生产者拥有完整形状，由消费者接受完整形状，而不是局部理解几个字段。
