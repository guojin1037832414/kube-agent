# M5.21-110 NIM Runtime Source Guard Matrix Digest Binding Contract Audit - 2026-06-08

## Scope

This wave hardens downstream NIM runtime source guard consumers. It ensures the state machine and durable write executor do not accept a source guard report whose top-level mirror fields differ from the nested `sourceGuardContract`, even when `sourceGuardMatrixDigest` is recomputed.

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

- `NimCreateStateMachineSupport` now checks that top-level `sourceGuardMatrix` equals `sourceGuardContract.sourceGuardMatrix`.
- `NimCreateDurableWriteExecutorSupport` now performs the same matrix mirror check before accepting source guard evidence for the executor shell.
- Both downstream validators now bind top-level and nested source digest fields:
  - `sourceRuntimeBindingContractDigest`
  - `sourceCodeReleaseSwitchContractDigest`
  - `sourceAuditEventDigest`
  - `trustedPrincipalDigest`
- Both validators also bind mirrored planning-source and dangerous-field lists to the nested contract.
- Added digest-consistent forged-report regressions that recompute `sourceGuardMatrixDigest` after drifting nested matrix rows or nested source digest fields.

## Why This Matters

M5.21-109 ensured the runtime source guard generator rejects nested runtime binding switch digest drift. This wave hardens the downstream consumers of that guard.

The source guard report intentionally exposes some values twice: top-level fields for easy downstream checks, and nested `sourceGuardContract` fields for digest-bound contract evidence. A forged report could make the nested contract internally digest-consistent while leaving the top-level mirror to tell a different story, or vice versa.

This wave makes the state machine and durable executor reject that split-brain evidence. Digest consistency now proves the nested contract was recomputed, while mirror binding proves the top-level report and nested contract still describe the same source guard evidence.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: forged matrix taxonomy or source digest drift cannot become accepted guard evidence merely by recomputing `sourceGuardMatrixDigest`.
- Agent architecture lens: source guard remains contract-only and fail-closed; it binds future evidence consumers without issuing release authority.
- Test architecture lens: the regressions are digest-consistent forgeries, so rejection proves semantic mirror validation rather than stale checksum detection.
- Learning lens: top-tier Agents validate both cryptographic shape and semantic source identity. A digest over a nested object is necessary, but downstream consumers must also prove their top-level mirrors are not lying.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest#stateMachine_shouldRejectDigestConsistentRuntimeSourceGuardMatrixDrift+stateMachine_shouldRejectDigestConsistentRuntimeSourceGuardContractSourceDigestDrift" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest#executorShell_shouldRejectDigestConsistentRuntimeSourceGuardMatrixDrift+executorShell_shouldRejectDigestConsistentRuntimeSourceGuardContractSourceDigestDrift" test
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

- Continue closed-shape validation for source guard contract maps if additional mirror fields become executor-facing.
- Continue release-binding proof design without opening writes.
- When the real server-owned switch is eventually designed, require the same mirror-binding discipline before any POST path can observe release authority.
