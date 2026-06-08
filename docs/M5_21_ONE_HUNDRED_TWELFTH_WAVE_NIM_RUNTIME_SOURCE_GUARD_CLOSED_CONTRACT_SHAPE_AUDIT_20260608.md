# M5.21-112 NIM Runtime Source Guard Closed Contract Shape Audit - 2026-06-08

## Scope

This wave hardens the nested NIM runtime source guard contract from value checks into a closed-shape contract. Downstream consumers must now reject a digest-consistent source guard report when the nested `sourceGuardContract` gains an unexpected field, even if `sourceGuardMatrixDigest` is recomputed.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- Added shared `closedSourceGuardContractValid(...)` to `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`.
- The closed validator requires the nested `sourceGuardContract` to have the exact expected key set:
  - boundary and target metadata
  - source runtime binding / switch / audit / trusted principal digests
  - current release scope and empty current release source list
  - planning source list
  - dangerous release credential field list
  - source guard matrix
  - acceptance rules
  - failure contract
  - forbidden shortcuts
- The validator also checks exact nested values for `acceptanceRules`, `failureContract`, `forbiddenShortcuts`, planning sources, dangerous fields, and the closed M5.21-75 source guard matrix.
- `NimCreateStateMachineSupport` and `NimCreateDurableWriteExecutorSupport` now call the shared closed contract validator while validating runtime source guard evidence.
- Added digest-consistent forged-report regressions that add `contractShapeExtensionPolicy` to the nested contract, recompute `sourceGuardMatrixDigest`, and still expect state-machine / durable-executor rejection.

## Why This Matters

M5.21-111 closed the matrix taxonomy. A remaining risk was that a forged report could leave all known fields valid, recompute the nested contract digest, and add a new contract field that future code might accidentally treat as authority or policy.

For a top-tier Agent, safety contracts must fail closed not only on bad values, but also on unreviewed shape expansion. The contract is server-owned evidence, not caller-extensible JSON. New keys require reviewed code, tests, docs, and an intentional release wave.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: unreviewed nested contract fields can no longer ride along inside a digest-consistent source guard contract.
- Agent architecture lens: downstream state-machine and durable-executor validators now share one authoritative closed contract validator.
- Test architecture lens: regressions recompute `sourceGuardMatrixDigest`, so rejection proves closed-shape validation rather than stale checksum detection.
- Learning lens: a mature Agent treats proof objects as typed contracts. A hash proves integrity of the object you saw; it does not prove that object shape is safe unless the shape is also closed.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
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

- Continue closed-shape validation around adjacent source guard report top-level lists that currently use `containsAll(...)` where exact shape may be more appropriate.
- Continue release-binding proof design without opening writes.
- When a real server-owned switch is eventually introduced, version source guard contract shapes so new keys are released intentionally.
