# M5.21-114 NIM Runtime Binding Required Fields Closed List Audit - 2026-06-08

## Scope

This wave hardens the M5.21-75 runtime source guard's validation of the M5.21-73 runtime binding report. The source guard now rejects a digest-consistent runtime binding contract that extends `requiredFutureRuntimeEvidenceDigestFields` with an unreviewed future evidence field.

Touched production file:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java`

Touched test:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` now requires `runtimeBindingContract.requiredFutureRuntimeEvidenceDigestFields` to exactly equal the source-owned runtime evidence field list.
- Added a regression that appends `forgedFutureRuntimeEvidenceDigest` to the nested runtime binding contract, recomputes `runtimeBindingContractDigest`, and still expects the source guard to reject the report.

## Why This Matters

M5.21-113 closed top-level source guard mirrors. A neighboring proof surface remained in the upstream runtime binding report: source guard validation only required the future runtime evidence list to contain known fields. A forged report could therefore add extra future evidence names while keeping the contract digest consistent.

For a top-tier Agent, future evidence field names are not caller-extensible. They define what a future release path must bind before a real write can run. Adding a field without reviewed code can confuse downstream proof semantics or let future code treat a forged field as intentional release evidence.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: runtime binding required-evidence fields are now exact, preventing unreviewed release-proof expansion.
- Agent architecture lens: the source guard now treats upstream runtime binding required fields as a source-owned contract, not an extensible list.
- Test architecture lens: the forged report recomputes `runtimeBindingContractDigest`, so rejection proves closed-list validation rather than stale checksum detection.
- Learning lens: future proof slots are part of the safety protocol. They should evolve through reviewed code and tests, not caller JSON.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest#sourceGuard_shouldRejectDigestConsistentRuntimeBindingExtraFutureEvidenceField" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
```

Final verification passed:

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

- Continue closing required-evidence lists in adjacent release decision and code release switch contracts.
- Continue release-binding proof design without opening writes.
- If future evidence fields need to evolve, introduce a versioned contract change with reviewed tests and docs.
