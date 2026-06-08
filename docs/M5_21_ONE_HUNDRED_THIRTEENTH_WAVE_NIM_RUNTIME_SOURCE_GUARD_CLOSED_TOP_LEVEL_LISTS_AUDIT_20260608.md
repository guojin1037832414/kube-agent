# M5.21-113 NIM Runtime Source Guard Closed Top-Level Lists Audit - 2026-06-08

## Scope

This wave hardens the top-level NIM runtime source guard report lists. Downstream consumers must now reject a source guard report that extends top-level `forbiddenReleaseSources`, even when the nested `sourceGuardContract` and `sourceGuardMatrixDigest` remain valid.

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

- Added shared `closedSourceGuardReportListsValid(...)` to `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`.
- The closed validator requires exact top-level lists for:
  - `acceptedSourcesForCurrentRelease`
  - `contractShapeSourcesAcceptedForPlanning`
  - `forbiddenReleaseSources`
  - `dangerousReleaseCredentialFieldNames`
- `NimCreateStateMachineSupport` and `NimCreateDurableWriteExecutorSupport` now require this top-level list validator before accepting runtime source guard evidence.
- Added regressions that append `FORGED_TOP_LEVEL_FORBIDDEN_SOURCE_EXTENSION` to top-level `forbiddenReleaseSources` while leaving the nested contract and digest valid; both downstream consumers now reject it.

## Why This Matters

M5.21-112 closed the nested contract shape. A remaining top-level mirror risk existed because `forbiddenReleaseSources` used a "required values are present" check. That allowed a forged report to add extra top-level source taxonomy names while preserving the nested contract digest.

For a top-tier Agent, top-level mirror fields are not informal prose. They are part of the proof object that future code may read. If they are extensible by caller JSON, a future consumer can accidentally treat unreviewed taxonomy additions as intentional policy.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: top-level source taxonomy lists are now exact, preventing unreviewed mirror-list expansion from becoming future policy surface.
- Agent architecture lens: state-machine and durable-executor validators share one authoritative list validator from the source guard support class.
- Test architecture lens: the forged report leaves nested contract/digest valid, so rejection proves top-level list closure rather than nested checksum detection.
- Learning lens: mirrors can become authority if future code reads them. Treat mirrored proof fields as closed contracts, not comments.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest#stateMachine_shouldRejectDigestConsistentRuntimeSourceGuardTopLevelExtraForbiddenSource,NimCreateDurableWriteExecutorSupportTest#executorShell_shouldRejectDigestConsistentRuntimeSourceGuardTopLevelExtraForbiddenSource" test
```

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

- Continue closing adjacent proof surfaces that still use "contains required fields" instead of exact source-owned shape.
- Continue release-binding proof design without opening writes.
- Keep future top-level mirrors versioned if the report schema intentionally evolves.
