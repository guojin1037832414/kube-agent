# M5.21-121 NIM Writer Interface Spec Required Lists Closed Audit - 2026-06-08

## Scope

本轮加固 NIM durable audit writer interface spec 的请求/响应证明列表。上一轮 M5.21-120 已经把 typed receipt schema 自身的 `requiredFields` 做成闭表；本轮继续处理上游 interface spec：

- `requestContract.requiredFields`
- `responseContract.requiredFutureSuccessFields`

这两个列表定义未来真实 `NimDurableAuditWriter` 的请求输入和成功响应证据槽位。下游 typed receipt schema 不能只验证自己当前读取的子集，必须拒绝 digest-consistent 的扩展列表。

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterInterfaceSpecSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupportTest.java`

## What Changed

- `NimCreateDurableAuditWriterInterfaceSpecSupport` now owns source-controlled helper lists:
  - `requestRequiredFields()`
  - `responseRequiredFutureSuccessFields()`
- The interface spec producer now emits those helpers instead of inline list literals.
- `NimCreateDurableAuditReceiptSchemaSupport` now validates:
  - `requestContract.requiredFields` exact equality against `requestRequiredFields()`
  - `responseContract.requiredFutureSuccessFields` exact equality against `responseRequiredFutureSuccessFields()`
- Added a regression that appends fake future fields to both interface spec lists, recomputes `interfaceSpecDigest`, and still expects receipt schema planning to reject the report.

## Why This Matters

The writer interface spec is the upstream contract for the future durable audit writer. If a caller or buggy upstream report can append `futureCallerProofEnvelope` or `futureReceiptSignerEvidence` while recomputing `interfaceSpecDigest`, downstream code may later treat those fields as reviewed protocol slots.

A top-tier Agent write path must distinguish:

- object integrity: the report hash matches the object;
- protocol authority: the object shape and proof lists are source-owned and reviewed.

Hash consistency alone is not protocol authority. Closed list validation prevents a digest-valid interface spec from silently expanding the future write-release proof surface.

## Multi-Expert Review Notes

- Backend/API lens: no new kube-manager controller, `8100` call, HTTP client behavior, writer interface implementation, `sys_log` writer, or Elasticsearch writer was added.
- Frontend/product lens: no `vue-kube-manager` page behavior changed; the mature NIM UI remains evidence only, not a write authorization source.
- Security/RBAC lens: extra future request/response evidence slots are rejected even when `interfaceSpecDigest` is recomputed.
- Agent architecture lens: upstream interface spec proof lists and downstream typed receipt schema validation now evolve together through shared source helpers.
- Test architecture lens: the regression mutates only proof-list fields and recomputes the interface digest, proving the rejection is semantic rather than a stale-hash artifact.
- Learning lens: downstream gates should validate the full upstream protocol shape they rely on, not merely the fields they currently read.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
```

Full verification for this wave should include:
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

- Continue scanning remaining interface/spec/failure/test-double proof lists that still use subset checks.
- Consider exact-list validation for failure status lists and forbidden assertion lists where they are release-proof protocol fields rather than explanatory diagnostics.
- Keep real durable writer implementation HOLD until storage probe, pre-write, post-write, receipt assembly, signer, release decision, and code switch are reviewed end to end.
