# M5.21-117 NIM Validation Result Required Fields Closed List Audit - 2026-06-08

## Scope

This wave hardens the M5.21-71 release decision contract consumer of the M5.21-70 validation result contract report. The release decision contract now rejects a digest-consistent validation result contract that extends `requiredFutureEvidenceDigestFields` with an unreviewed future proof slot.

Touched production file:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionContractSupport.java`

Touched test:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionContractSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- `NimCreateDurableAuditReleaseDecisionContractSupport` now requires the upstream `validationResultContract.requiredFutureEvidenceDigestFields` list to exactly match the source-owned validation result evidence list.
- Added a regression that appends `forgedValidationResultFutureEvidenceDigest` to the nested validation result contract, recomputes `validationResultContractDigest`, and still expects release decision contract rejection.

## Why This Matters

M5.21-116 closed the release decision proof taxonomy at the code release switch boundary. The adjacent upstream boundary still accepted a validation result contract whose future evidence slots contained all known fields plus extra names. A caller-shaped or compromised intermediate report could therefore introduce a digest-consistent but unreviewed validation-result proof slot before release decision planning.

For a top-tier Agent, validation-result proof slots define the server-owned evidence that must be checked before any release decision can ever authorize a write. Those slots must evolve through reviewed source code, tests, and docs, not caller-shaped JSON with a recomputed digest.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: validation result evidence slots are now closed before release decision planning can consume them.
- Agent architecture lens: the release decision consumer now treats the validation result contract as an exact protocol instead of an extensible field list.
- Test architecture lens: the forged report recomputes `validationResultContractDigest`, so rejection proves closed-list validation rather than stale checksum detection.
- Learning lens: digest consistency proves an object was rehashed; it does not grant authority to add future proof fields.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest#releaseDecision_shouldRejectDigestConsistentValidationResultExtraFutureEvidenceField" test
```

Final verification passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest" test
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

- Continue closing any remaining future-proof field lists that still use superset acceptance.
- Continue release-binding proof design without opening writes.
- Later, design a server-owned validation result signer only after durable receipts and release governance are implemented.
