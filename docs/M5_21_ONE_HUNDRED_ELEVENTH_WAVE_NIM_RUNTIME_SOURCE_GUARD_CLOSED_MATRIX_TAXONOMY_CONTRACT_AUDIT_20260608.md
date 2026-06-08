# M5.21-111 NIM Runtime Source Guard Closed Matrix Taxonomy Contract Audit - 2026-06-08

## Scope

This wave hardens the NIM runtime source guard matrix from "required rows are present" into a closed taxonomy. Downstream consumers must now reject a digest-consistent source guard report that appends a new release-capable row to both top-level `sourceGuardMatrix` and nested `sourceGuardContract.sourceGuardMatrix`.

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

- Added `closedSourceGuardMatrixValid(...)` to `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`.
- The closed validator rebuilds the authoritative M5.21-75 matrix for the trusted runtime binding digest and requires exact equality.
- Every row must remain fail-closed:
  - `authoritativeForReleaseNow=false`
  - `writePermittedAllowedNow=false`
  - `writeExecutionAllowedNow=false`
  - state-machine and durable-executor rechecks remain required
- `NimCreateStateMachineSupport` and `NimCreateDurableWriteExecutorSupport` now call this shared closed validator.
- Added digest-consistent forged-report regressions that append `FORGED_BACKEND_READBACK_RELEASE_SOURCE`, set release/write booleans to true, recompute `sourceGuardMatrixDigest`, and still expect rejection.

## Why This Matters

M5.21-110 bound top-level source guard mirrors to the nested contract. That still left a taxonomy risk: a forged report could make both mirrors agree while adding a new row that claims current release authority.

For an Agent that will eventually control real Kubernetes writes, source governance must be closed by default. A source guard matrix is not an extensible list from caller JSON. It is a server-owned taxonomy whose allowed rows, row order, release booleans, and runtime binding digest companion signal must all be fixed until reviewed code changes them.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: attacker-controlled readback, executor success, or deployment id evidence cannot become a new matrix source by appending a row and recomputing the digest.
- Agent architecture lens: source guard remains contract-only and fail-closed; no current source family is accepted for release authority.
- Test architecture lens: the forged regressions recompute `sourceGuardMatrixDigest`, so rejection proves closed taxonomy validation rather than stale checksum detection.
- Learning lens: a top-tier Agent treats safety taxonomies as closed contracts. Extension requires reviewed code, tests, docs, and a release decision, not a larger JSON list.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest#stateMachine_shouldRejectDigestConsistentRuntimeSourceGuardExtraReleaseMatrixRow" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest#executorShell_shouldRejectDigestConsistentRuntimeSourceGuardExtraReleaseMatrixRow" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
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

- Continue closed-shape validation for nested source guard contract maps if more mirrored fields become downstream-facing.
- Continue release-binding proof design without opening writes.
- When a real server-owned switch is eventually introduced, keep source taxonomies versioned and reviewed before any matrix row can change release authority.
