# M5.21-124 NIM Validation Gate Failure/Shortcut Lists Closed Audit - 2026-06-08

## Scope

本轮继续关闭 NIM durable audit validation gate 自己拥有的 proof vocabulary。M5.21-123 已经关闭 receipt schema 输入给 validation gate 的 failure/test-double 清单；本轮关闭 validation gate 输出给 validation result migration 的清单：

- `validationPlan.failureContract.failureStatuses`
- `validationPlan.forbiddenShortcuts`

这些清单会影响未来 `NimDurableAuditReceiptValidationResult` 和 `NimDurableAuditReleaseDecision` 如何解释 validation gate 的失败状态与禁止捷径，因此不能允许上游通过 digest-consistent validation plan 扩展词表。

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupportTest.java`

## What Changed

- `NimCreateDurableAuditReceiptValidationGateSupport` now owns source-controlled helper lists for:
  - `validationFailureStatuses()`
  - `validationForbiddenShortcuts()`
- The validation gate producer now emits those helpers instead of inline list literals.
- `NimCreateDurableAuditValidationResultMigrationSupport` now requires exact equality for both validation-gate-owned lists instead of subset/non-empty checks.
- Added regressions that append fake values such as `FUTURE_VALIDATION_SIGNER_NOT_READY` and `accepting validationPlanDigest as release decision`, recompute `validationPlanDigest`, and still expect migration planning to reject the validation gate report.

## Why This Matters

Validation gate 是未来 release decision 的上游证据来源。只检查 failure status “包含关键值”或 forbidden shortcut “非空”，会让未审查的词表进入后续迁移和放行判断。闭合清单让失败语义和禁止捷径只能从源代码审查进入系统。

学习点：hash/digest 只证明 validation plan 的内容被重新摘要了，不证明这些内容被项目语义接受。顶级 Agent 的写放行链路必须同时做 digest binding 和 source-owned exact vocabulary validation。

## Multi-Expert Review Notes

- Backend/API lens: no writer implementation, storage write, kube-manager client call, `8100` call, `sys_log`, `ISysLogService`, or Elasticsearch path was added.
- Security/RBAC lens: validation failure vocabulary and forbidden shortcuts are now source-owned protocol lists.
- Agent architecture lens: validation result migration no longer accepts gate report supersets that could smuggle future release shortcuts.
- Test architecture lens: regressions recompute `validationPlanDigest`, so the rejection proves semantic closed-list validation rather than stale-hash rejection.
- Learning lens: failure vocabularies and shortcut prohibitions are release-proof protocol surfaces, not extensible documentation.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest" test
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

- Close validation result migration's own failure/shortcut lists before downstream release decision gate consumes them.
- Consider exact `digestChainRules.rules` validation if validation rule rows become release criteria.
- Keep the real durable writer implementation HOLD until dedicated storage probe, two-phase audit persistence, receipt assembly, signer, release decision, and code switch are reviewed end to end.
