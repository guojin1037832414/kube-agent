# M5.21-122 NIM Writer Interface Failure/Test-Double Lists Closed Audit - 2026-06-08

## Scope

本轮继续加固 NIM durable audit writer interface spec。M5.21-121 已经关闭了 request/response proof slots；本轮关闭同一个 interface spec 内的失败语义和 test-double 禁止成功声明列表：

- `failureContract.failureStatuses`
- `testDoubleRules.forbiddenSuccessClaims`

这些列表会影响未来真实 durable writer、receipt schema、validation gate 和 release decision 对失败状态与测试替身的理解，因此不能让调用方通过 digest-consistent JSON 扩展。

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterInterfaceSpecSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupportTest.java`

## What Changed

- `NimCreateDurableAuditWriterInterfaceSpecSupport` now owns:
  - `failureStatuses()`
  - `testDoubleForbiddenSuccessClaims()`
- The interface spec producer now emits these helper lists instead of inline list literals.
- `NimCreateDurableAuditReceiptSchemaSupport` now requires exact equality for:
  - `failureContract.failureStatuses`
  - `testDoubleRules.forbiddenSuccessClaims`
- Added a regression that appends fake values such as `FUTURE_SIGNER_NOT_READY` or `releaseEligible=true`, recomputes `interfaceSpecDigest`, and still expects typed receipt schema planning to reject the report.

## Why This Matters

Failure statuses define what kinds of failure the future durable writer is allowed to report. Test-double forbidden claims define which success-like claims a mock boundary may never produce. Both are part of the write-release proof protocol.

If these lists are only checked by subset, a digest-consistent interface spec could add an unreviewed future status or forbidden-claim variant. Downstream code may later interpret those extra values as reviewed authority. Closed equality keeps the protocol surface source-owned.

## Multi-Expert Review Notes

- Backend/API lens: no writer implementation, storage write, kube-manager client call, `8100` call, `sys_log`, `ISysLogService`, or Elasticsearch path was added.
- Frontend/product lens: no `vue-kube-manager` NIM workflow changed.
- Security/RBAC lens: failure semantics and test-double success blockers are now treated as protocol lists, not extensible caller diagnostics.
- Agent architecture lens: mock/test-double boundaries remain incapable of introducing future release vocabulary by JSON extension.
- Test architecture lens: regressions recompute `interfaceSpecDigest`, so failure proves semantic closed-list validation rather than stale-hash rejection.
- Learning lens: not all diagnostic-looking strings are harmless. In Agent write paths, failure/status vocabularies can become release gates and should be source-owned.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
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

- Continue to receipt-schema-owned failure/test-double proof lists.
- Consider exact operation-method row validation if future side-effect method names become release criteria.
- Keep the real durable writer implementation HOLD until dedicated storage probe, two-phase audit persistence, receipt assembly, signer, release decision, and code switch are reviewed end to end.
