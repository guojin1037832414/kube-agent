# M5.21-107 NIM Durable Handoff Source Evidence Binding Contract Audit - 2026-06-08

## Scope

This wave hardens durable executor handoff validation so a handoff report cannot drift away from the request spec adapter evidence while remaining internally digest-consistent.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- Durable executor handoff validation now cross-checks handoff source evidence against the trusted request spec report.
- The bound fields are `sourceAuditReceiptId`, `sourceAuditEventDigest`, `sourceRequestId`, `sourceConversationId`, `sourceUserId`, and `organizationId`.
- Added `handoffSourceEvidenceMatchesRequestSpecReport(...)` to keep the cross-report proof explicit and local to durable executor validation.
- Added a forged-report regression where the handoff audit receipt evidence is changed, nested `preWriteAuditHandoff` is changed, the server-derived idempotency key is recomputed from the forged evidence, and `handoffDigest` is recomputed.
- The durable executor still rejects that digest-consistent forged handoff with `WRITE_EXECUTION_HANDOFF_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR`.

## Why This Matters

M5.21-106 made the idempotency key recomputable from handoff evidence. That closed forged-key attacks, but a separate class remained important to document and test: a forged handoff could be made internally self-consistent by changing its own source audit evidence and recomputing the idempotency key and handoff digest.

This wave makes the durable executor prove that the handoff evidence still belongs to the same upstream request spec adapter evidence. The executor now rejects a handoff that is self-consistent but no longer chained to the trusted request/audit source.

## Multi-Expert Review Notes

- Backend/API lens: no HTTP client, kube-manager `8100`, or deployment POST was added; this is pure validation hardening.
- Security/RBAC lens: audit receipt identity cannot be swapped inside handoff evidence after the request spec has been compiled.
- Agent architecture lens: durable write proofs need cross-boundary evidence binding, not only per-object digest checks.
- Test architecture lens: the regression recomputes the affected digest and idempotency proof so the rejection proves source-chain mismatch, not stale checksum detection.
- Learning lens: a top-tier Agent distinguishes "the object is internally consistent" from "the object is trusted because it is still bound to the same upstream chain."

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest" test
```

Passed:

```bash
git diff --check
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch, `ISysLogService`, or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue release-binding proof design without opening writes.
- Consider sharing cross-report evidence binding helpers only if future waves introduce real duplication pressure.
- Expand durable executor/state-machine forged-report regressions around other source-chain fields if new handoff evidence containers are added.
