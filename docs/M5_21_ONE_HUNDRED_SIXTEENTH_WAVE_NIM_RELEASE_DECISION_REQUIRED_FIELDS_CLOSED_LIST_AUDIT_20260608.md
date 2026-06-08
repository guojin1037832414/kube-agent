# M5.21-116 NIM Release Decision Required Fields Closed List Audit - 2026-06-08

## Scope

This wave hardens the M5.21-72 code release switch contract consumer of the M5.21-71 release decision contract report. The code release switch contract now rejects a digest-consistent release decision contract that extends `requiredFutureEvidenceDigestFields` with an unreviewed future proof slot.

Touched production file:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java`

Touched test:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now requires the upstream `releaseDecisionContract.requiredFutureEvidenceDigestFields` list to exactly match the source-owned release decision evidence list.
- Added a regression that appends `forgedReleaseDecisionFutureEvidenceDigest` to the nested release decision contract, recomputes `releaseDecisionContractDigest`, and still expects code release switch contract rejection.

## Why This Matters

M5.21-115 closed the switch contract required-field list at the runtime binding boundary. The adjacent upstream boundary was still accepting a release decision contract whose future evidence slots contained all known fields plus extra names. That means a digest-consistent but unreviewed release-decision proof taxonomy could flow into switch contract planning.

For a top-tier Agent, release decision proof slots define what future server-owned evidence must exist before any real write can run. They must evolve through reviewed code, tests, and docs, not caller-shaped JSON that happens to carry a fresh digest.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: release decision evidence slots are now closed before code release switch planning can consume them.
- Agent architecture lens: the code release switch consumer now treats the release decision contract as an exact protocol instead of an extensible field list.
- Test architecture lens: the forged report recomputes `releaseDecisionContractDigest`, so rejection proves closed-list validation rather than stale checksum detection.
- Learning lens: proof taxonomies are not comments. If downstream code may branch on a future proof field, that field must be owned by reviewed source code.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest#codeReleaseSwitch_shouldRejectDigestConsistentReleaseDecisionExtraFutureEvidenceField" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
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

- Close the adjacent M5.21-71 release decision contract consumer of validation-result required fields.
- Continue closing any remaining future-proof field lists that still use superset acceptance.
- Continue release-binding proof design without opening writes.
