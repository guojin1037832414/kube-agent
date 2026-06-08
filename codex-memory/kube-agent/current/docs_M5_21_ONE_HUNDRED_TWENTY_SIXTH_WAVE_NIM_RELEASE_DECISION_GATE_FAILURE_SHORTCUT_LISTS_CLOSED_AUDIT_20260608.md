# M5.21-126 NIM Release Decision Gate Failure/Shortcut Lists Closed Audit - 2026-06-08

## Scope

This wave closes the proof vocabulary owned by `NimCreateDurableAuditReleaseDecisionGateSupport` before the state-machine release requirement consumes the release gate report:

- `releaseDecisionGatePlan.failureContract.failureStatuses`
- `releaseDecisionGatePlan.forbiddenShortcuts`

M5.21-124 closed validation-gate vocabulary. M5.21-125 closed validation-result-migration vocabulary. This wave closes the next downstream protocol layer: release decision gate vocabulary as consumed by the future state-machine release requirement.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupportTest.java`

## What Changed

- `NimCreateDurableAuditReleaseDecisionGateSupport` now exposes source-owned helper lists:
  - `releaseGateFailureStatuses()`
  - `releaseGateForbiddenShortcuts()`
- The release gate producer emits those helpers instead of private inline list literals.
- `NimCreateStateMachineReleaseDecisionRequirementSupport` now requires exact equality for both release-gate-owned lists instead of `contains(...)`.
- The producer positive test asserts that the emitted lists match the source-owned helper lists.
- The downstream regression appends fake future values, recomputes `releaseDecisionGatePlanDigest`, and still expects the state-machine requirement to reject the gate report.

## Why This Matters

The release decision gate report is closer to a future write-release boundary than the earlier migration plan. If a downstream consumer accepts extra failure statuses or forbidden shortcuts by `contains(...)`, an unreviewed value can become latent release semantics later.

This wave keeps the release gate vocabulary source-owned and versioned with code. That creates a deliberate fail-closed behavior for version skew: a report with missing, reordered, or extra protocol values is rejected until producer and consumer are reviewed and deployed together.

Learning point: compatibility is not always safety. In an Agent write-release chain, strict producer/consumer protocol coupling is often the safer choice because the alternative is accepting unreviewed authority-shaped vocabulary.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client path, `8100` call, deployment POST, storage write, `sys_log`, `ISysLogService`, or Elasticsearch path was added.
- Security/RBAC lens: release gate failure vocabulary and forbidden shortcuts are now source-owned release protocol lists.
- Agent architecture lens: the state-machine release requirement cannot consume a digest-valid release gate plan whose protocol vocabulary has been extended outside reviewed source code.
- Test architecture lens: the regression recomputes `releaseDecisionGatePlanDigest`, so rejection proves semantic exact-list validation rather than stale-digest rejection.
- Learning lens: version skew between producer and consumer should fail closed for release-proof protocol lists.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
```

Final verification passed:

```bash
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch write.
- No `ISysLogService` or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue scanning downstream release requirement and state-machine proof fields for remaining subset or non-empty list checks.
- Consider closing state-machine release requirement's own `failureContract.failureStatuses` and `forbiddenShortcuts` if they become consumed by the state machine or durable executor.
- Keep real durable audit writer, receipt validator, release signer, code switch, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
