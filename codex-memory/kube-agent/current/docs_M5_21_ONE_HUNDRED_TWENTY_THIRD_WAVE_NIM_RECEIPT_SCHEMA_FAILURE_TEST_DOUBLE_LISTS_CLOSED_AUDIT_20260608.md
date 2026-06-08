# M5.21-123 NIM Receipt Schema Failure/Test-Double Lists Closed Audit - 2026-06-08

## Scope

本轮继续加固 NIM durable audit typed ack/receipt schema 的闭合契约。M5.21-120 已经关闭 typed schema 的 `requiredFields` 清单，M5.21-121/122 已经关闭上游 writer interface spec 的 required/failure/test-double 清单；本轮关闭 receipt schema 自己输出并由 validation gate 消费的清单：

- `typedSchema.failureContract.failureStatuses`
- `typedSchema.testDoubleRules.mustNotReturnTypeInstances`
- `typedSchema.testDoubleRules.forbiddenSuccessClaims`

这些字段看起来像诊断信息，但未来会影响 durable receipt validator、release decision 和 code release switch 对失败状态、测试桩边界、成功声明的解释，因此必须由源代码显式持有，不能允许调用方通过 digest-consistent JSON 扩展。

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupportTest.java`

## What Changed

- `NimCreateDurableAuditReceiptSchemaSupport` now owns source-controlled helper lists for:
  - `receiptFailureStatuses()`
  - `receiptTestDoubleMustNotReturnTypes()`
  - `receiptTestDoubleForbiddenSuccessClaims()`
- The typed receipt schema producer now emits those helpers instead of inline list literals.
- `NimCreateDurableAuditReceiptValidationGateSupport` now requires exact equality for the three lists above instead of subset-style `contains(...)` checks.
- Added a regression that appends fake values such as `FUTURE_SIGNER_NOT_READY`, `ForgedDurableReceiptTestDouble`, and `releaseEligible=true`, recomputes `schemaDigest`, and still expects validation gate planning to reject the schema report.

## Why This Matters

闭合清单是 Agent 写路径里非常重要的安全手法：只校验“包含关键值”会留下协议扩展空间，攻击者或未来未审查调用方可以添加额外状态或测试桩声明，并让 digest 看起来仍然一致。精确相等意味着协议词表只能从代码审查进入系统，而不是从上游 JSON 输入进入系统。

本轮也保留了一个学习点：digest 证明“这份 JSON 没被无声篡改”，但不证明“这份 JSON 的语义被项目接受”。语义接受仍然需要源代码级白名单。

## Multi-Expert Review Notes

- Backend/API lens: no writer implementation, storage write, kube-manager client call, `8100` call, `sys_log`, `ISysLogService`, or Elasticsearch path was added.
- Security/RBAC lens: receipt failure vocabulary and test-double boundaries are now source-owned protocol lists.
- Agent architecture lens: validation gate no longer accepts schema report supersets that could smuggle release vocabulary into future gates.
- Test architecture lens: regressions recompute `schemaDigest`, so the rejection proves semantic closed-list validation rather than stale-hash rejection.
- Learning lens: status lists, type lists, and forbidden-claim lists should be treated as protocol surfaces in Agent write workflows, not harmless metadata.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest" test
```

Final verification passed:

```bash
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch write.
- No `ISysLogService` or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Close validation-gate-owned failure/shortcut lists with the same source-owned equality pattern.
- Consider exact `digestChainRules.rules` validation if future rule rows become release criteria.
- Keep the real durable writer implementation HOLD until dedicated storage probe, two-phase audit persistence, receipt assembly, signer, release decision, and code switch are reviewed end to end.
