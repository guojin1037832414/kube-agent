# M5.21-125 NIM Validation Result Migration Failure/Shortcut Lists Closed Audit - 2026-06-08

## Scope

This wave closes the proof vocabulary owned by `NimCreateDurableAuditValidationResultMigrationSupport` before downstream release planning consumes it:

- `migrationPlan.failureContract.failureStatuses`
- `migrationPlan.forbiddenShortcuts`

The previous wave closed validation-gate-owned lists before M5.21-58 migration planning. This wave closes the M5.21-58 migration plan itself, then verifies every current consumer that treats the migration plan as upstream evidence.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest.java`

## What Changed

- `NimCreateDurableAuditValidationResultMigrationSupport` now exposes source-owned helper lists:
  - `migrationFailureStatuses()`
  - `migrationForbiddenShortcuts()`
- The producer emits those helpers instead of private inline list literals.
- `NimCreateDurableAuditReleaseDecisionGateSupport` requires exact equality for both migration-plan-owned lists before preparing release gate planning.
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` applies the same exact checks, closing the adjacent M5.21-69 consumer.
- Tests forge digest-consistent migration plans by appending fake future entries, recomputing `migrationPlanDigest`, and still expecting fail-closed rejection.

## Why This Matters

`migrationPlanDigest` only proves the current migration plan object was hashed. It does not prove the object is semantically approved.

Failure statuses and forbidden shortcuts look like strings, but in this write-release chain they are protocol vocabulary. A future developer could accidentally make one of these strings authoritative, for example by treating a new failure status as signer readiness or treating a shortcut description as a release credential rule. Therefore the vocabulary must evolve through reviewed source code, tests, and docs, not through a digest-consistent JSON append.

Learning point: top-tier Agent safety needs both digest binding and source-owned semantic validation. Hashes protect object integrity; closed lists protect protocol authority.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client path, `8100` call, deployment POST, storage write, `sys_log`, `ISysLogService`, or Elasticsearch path was added.
- Security/RBAC lens: migration failure vocabulary and forbidden shortcuts are now source-owned protocol lists, not caller-extensible metadata.
- Agent architecture lens: both current migration-plan consumers now validate the same upstream protocol shape before preparing downstream release planning.
- Test architecture lens: regressions recompute `migrationPlanDigest`, so rejection proves semantic closed-list validation rather than stale-digest rejection.
- Learning lens: multi-consumer contracts must be closed at every consumer. Hardening only the most obvious consumer can leave a side path open.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest" test
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

- Continue scanning M5.21-58/M5.21-69 downstream proof fields for any remaining subset or non-empty list checks.
- Consider exact validation of rule-row lists if a future release decision starts consuming rule descriptions as protocol criteria.
- Keep real durable audit writer, receipt validator, release signer, code switch, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
