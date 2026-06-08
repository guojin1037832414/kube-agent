# M5.21-108 NIM Code Release Switch Runtime Release-Decision Binding Contract Audit - 2026-06-08

## Scope

This wave hardens the NIM code release switch runtime binding contract. It ensures runtime binding does not accept a code switch contract whose nested `releaseDecisionBinding` has drifted away from the trusted switch report evidence, even when the outer `codeReleaseSwitchContractDigest` is recomputed.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest.java`

Touched docs:

- `CHANGELOG.md`
- `docs/M5_21_WAVE_INDEX_20260606.md`
- `docs/PROJECT_MISSION_AND_MEMORY.md`
- `docs/SESSION_PROGRESS_20260606_M521_29.md`
- `docs/v3.1/DEVELOPMENT_GUIDE.md`

## What Changed

- Runtime binding validation now extracts `codeReleaseSwitchContract.releaseDecisionBinding`.
- The nested binding must match the switch report on:
  - `sourceReleaseDecisionContractDigest`
  - `sourceValidationResultContractDigest`
  - all source proof digests
  - `trustedPrincipalDigest`
- The nested binding must keep future release/validation digest requirements enabled and caller release decision acceptance disabled.
- Added a forged-report regression that changes nested `releaseDecisionBinding.sourceReleaseDecisionContractDigest` and recomputes `codeReleaseSwitchContractDigest`; the runtime binding report is still rejected.

## Why This Matters

M5.21-107 focused on durable executor handoff evidence binding. This wave continues the same proof-chain idea toward the release path.

A future runtime release gate will depend on the code switch contract, and that contract depends on a release decision contract. It is not enough for the switch contract object to have a recomputed digest. The downstream runtime binding layer must also verify that the nested release-decision binding still points to the same trusted evidence chain.

This is the difference between "the object is digest-consistent" and "the release proof inside the object is still semantically anchored."

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Security/RBAC lens: a forged switch report cannot smuggle a different release-decision contract digest inside nested binding evidence.
- Agent architecture lens: release authority remains layered: validation result, release decision, code switch, state-machine check, and durable executor re-check are separate proof boundaries.
- Test architecture lens: the regression recomputes the outer switch contract digest, so rejection proves nested semantic binding validation rather than stale digest detection.
- Learning lens: top-tier Agents verify both cryptographic shape and semantic source binding at every downstream boundary.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest" test
```

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
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

- Continue binding code release switch runtime source guard evidence to runtime binding nested proof fields.
- Consider closed-shape validation for runtime binding contract maps if future waves show executor-facing ambiguity.
- Continue release-binding proof design without opening writes.
