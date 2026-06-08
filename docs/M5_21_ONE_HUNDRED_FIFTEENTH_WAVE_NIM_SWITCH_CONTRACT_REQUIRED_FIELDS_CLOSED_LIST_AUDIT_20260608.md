# M5.21-115 NIM Switch Contract Required Fields Closed List Audit - 2026-06-08

## Scope

This wave hardens the M5.21-73 runtime binding consumer of the M5.21-72 code release switch contract report. Runtime binding now rejects a digest-consistent switch contract that extends `requiredFutureEvidenceDigestFields` with an unreviewed future proof slot.

Touched production file:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java`

Touched test:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport` now requires the upstream `codeReleaseSwitchContract.requiredFutureEvidenceDigestFields` list to exactly match the source-owned switch evidence list.
- The consumer-side required switch evidence list was aligned with the M5.21-72 producer list, including `sourceAuditEventDigest` and `trustedPrincipalDigest`.
- Added a regression that appends `forgedSwitchFutureEvidenceDigest` to the nested switch contract, recomputes `codeReleaseSwitchContractDigest`, and still expects runtime binding rejection.

## Why This Matters

M5.21-114 closed the runtime binding report after it was produced. A neighboring upstream surface remained open: runtime binding accepted a switch contract whose required future proof slots contained all known fields plus extra names. That allowed a caller-shaped contract to carry digest-consistent but unreviewed future proof semantics.

For a top-tier Agent, future release proof slots are part of the safety protocol. Downstream consumers must not treat extra field names as harmless metadata, because future code could interpret those names as intentional authority or release evidence.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: required switch evidence slots are now closed at the runtime binding boundary, blocking proof-slot expansion before state-machine or durable-executor binding.
- Agent architecture lens: the runtime binding consumer now validates the producer-owned switch contract as an exact protocol, not as an extensible list.
- Test architecture lens: the forged report recomputes `codeReleaseSwitchContractDigest`, so rejection proves closed-list validation rather than stale checksum detection.
- Learning lens: digest consistency is necessary but not sufficient. A proof object also needs a reviewed closed schema and exact field taxonomy.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest#runtimeBinding_shouldRejectDigestConsistentSwitchContractExtraFutureEvidenceField" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest" test
```

Final verification passed:

```bash
git diff --check
mvn -q test
```

Full Maven note: the first full run hit the 120s tool timeout while tests were still running; a second run with a longer timeout passed. Local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

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

- Close the adjacent M5.21-72 `CodeReleaseSwitchContractSupport` consumer of release-decision required fields.
- Close the adjacent M5.21-71 release decision contract consumer of validation-result required fields.
- Continue release-binding proof design without opening writes.
