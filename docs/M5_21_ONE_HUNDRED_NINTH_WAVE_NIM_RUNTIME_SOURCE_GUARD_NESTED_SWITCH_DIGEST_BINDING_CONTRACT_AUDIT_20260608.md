# M5.21-109 NIM Runtime Source Guard Nested Switch Digest Binding Contract Audit - 2026-06-08

## Scope

This wave hardens the NIM code release switch runtime source guard. It ensures the source guard does not accept a runtime binding report whose nested state-machine or durable-executor runtime binding points to a different code release switch contract digest, even when the outer `runtimeBindingContractDigest` is recomputed.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- Source guard validation now checks nested `stateMachineRuntimeBinding.sourceCodeReleaseSwitchContractDigest`.
- Source guard validation now checks nested `durableExecutorRuntimeBinding.sourceCodeReleaseSwitchContractDigest`.
- Both nested values must match the trusted runtime binding report's `sourceCodeReleaseSwitchContractDigest`.
- Added a forged-report regression that changes both nested switch digests and recomputes `runtimeBindingContractDigest`; source guard still rejects the report.

## Why This Matters

M5.21-108 bound runtime binding to nested release-decision evidence. This wave moves one layer downstream: runtime source guard must ensure the future state-machine gate and durable-executor gate are anchored to the exact same code release switch proof.

A digest-consistent runtime binding report is not enough if its nested consumers point to a different switch digest. Source governance must bind who can consume the switch proof, what digest they consume, and which sources are forbidden from becoming release credentials.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: state-machine and durable-executor release paths cannot drift to a different switch digest inside a recomputed runtime binding report.
- Agent architecture lens: source guard remains a governance layer, not a release issuer. It binds future consumers while keeping all current release sources empty.
- Test architecture lens: the forged regression recomputes the outer runtime binding digest, so rejection proves nested semantic binding validation rather than stale checksum detection.
- Learning lens: source guards protect both input source categories and downstream consumer alignment. A top-tier Agent proves every future consumer is reading the same release evidence.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
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

- Continue closing runtime-source and durable-executor release proof drift around source guard matrix digests.
- Consider closed-shape validation for source guard contracts if future executor-facing ambiguity appears.
- Continue release-binding proof design without opening writes.
