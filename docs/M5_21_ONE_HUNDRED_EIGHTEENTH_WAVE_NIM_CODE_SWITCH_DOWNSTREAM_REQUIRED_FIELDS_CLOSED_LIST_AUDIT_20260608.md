# M5.21-118 NIM Code Switch Downstream Required Fields Closed List Audit - 2026-06-08

## Scope

This wave hardens two downstream consumers of the M5.21-72 code release switch contract report:

- `NimCreateStateMachineSupport`
- `NimCreateDurableWriteExecutorSupport`

Both consumers now reject a digest-consistent code switch contract that extends `requiredFutureEvidenceDigestFields` with an unreviewed future proof slot.

Touched production files:

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

- State-machine code switch validation now requires the upstream `codeReleaseSwitchContract.requiredFutureEvidenceDigestFields` list to exactly match the source-owned switch evidence list.
- Durable executor code switch validation now applies the same exact closed-list check before accepting the switch contract report.
- Added regressions that append `forgedCodeSwitchFutureEvidenceDigest` to the nested code switch contract, recompute `codeReleaseSwitchContractDigest`, and still expect downstream rejection.

## Why This Matters

M5.21-115 already closed this list at the runtime binding boundary. However, the state machine and durable executor still used subset checks for their own write-chain evidence needs. That meant a digest-consistent but unreviewed code-switch proof slot could flow into lower runtime planning surfaces even though the switch contract itself remained HOLD.

A top-tier Agent cannot let downstream runtime code treat proof-slot taxonomies as extensible metadata. The list describes exactly which future server-owned evidence must exist before a real write can ever proceed. New proof slots require reviewed source code, tests, documentation, and release governance.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: both downstream runtime consumers now reject unreviewed code-switch proof slots even when the switch contract digest is recomputed.
- Agent architecture lens: state-machine and executor now consume the same source-owned switch proof taxonomy instead of local subsets.
- Test architecture lens: forged reports recompute `codeReleaseSwitchContractDigest`, proving rejection comes from closed-list validation rather than stale checksum detection.
- Learning lens: downstream components should not only ask "do the fields I need exist?" They should also ask "is this exactly the reviewed protocol shape?"

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest#stateMachine_shouldRejectTamperedCodeReleaseSwitchContractDigest+stateMachine_shouldRejectDigestConsistentCodeSwitchExtraFutureEvidenceField,NimCreateDurableWriteExecutorSupportTest#executorShell_shouldRejectDigestConsistentCodeSwitchExtraFutureEvidenceField" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
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

- Continue closing remaining runtime-source or readiness target lists that still use superset acceptance.
- Continue release-binding proof design without opening writes.
- Later, factor shared source-owned proof-list constants once the proof taxonomy stabilizes across all runtime consumers.
