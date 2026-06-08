# M5.21-132 NIM Release Gate Contract Maps Closed Audit - 2026-06-08

## Scope

This wave closes two upstream contract maps consumed by the NIM release decision gate:

- `migrationPlan.validationResultContract`
- `migrationPlan.releaseDecisionContract`

Both maps are still HOLD/mock-first protocol definitions. They do not create a real validation result, release decision, release credential, durable audit write, deployment POST, or runtime write.

## Why This Matters

`migrationPlanDigest` proves that a migration plan object is internally stable, but it does not prove that every future authority-shaped key is allowed. A caller or unreviewed integration can append a new key and recompute the digest.

For release-adjacent maps, partial field checks are too weak. The downstream release gate must accept only the producer-owned canonical map.

## Implementation

- `NimCreateDurableAuditValidationResultMigrationSupport`
  - Added package-private canonical helpers:
    - `validationResultContractFromMigrationReport(...)`
    - `releaseDecisionContractFromMigrationReport(...)`
  - Both helpers share the same producer-owned map construction used by the migration plan itself.

- `NimCreateDurableAuditReleaseDecisionGateSupport`
  - Replaced partial validation of `validationResultContract` and `releaseDecisionContract`.
  - The release gate now requires exact equality with the producer helpers.

## Regression Tests

- `NimCreateDurableAuditValidationResultMigrationSupportTest`
  - Positive path now asserts both migration-plan contract maps equal the producer helpers.

- `NimCreateDurableAuditReleaseDecisionGateSupportTest`
  - Added digest-consistent forged map regression.
  - The test appends fake nested contract keys, recomputes `migrationPlanDigest`, and still expects fail-closed rejection.

## Verification

```powershell
mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
git diff --check
mvn -q test
```

All commands passed.

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariant

- `nim_create` remains HOLD/mock-first.
- No real `8100` call was made.
- No real NIM service HTTP call was added.
- No Authorization header sending was added.
- No durable audit write was added.
- No deployment POST was added.
- No validation result signer, release decision signer, code release switch implementation, runtime source guard installation, Elasticsearch, `ISysLogService`, or `sys_log` write was added.

## Teaching Note

The main lesson is producer ownership of security protocol maps. A downstream consumer should not hand-interpret an upstream JSON contract with a small set of known fields. It should compare against the producer's canonical map, so schema expansion must pass through reviewed code, regression tests, and documentation.

## Next Recommended Slice

Multi-expert review identified the next higher-priority closure point: `releaseDecisionContract` as consumed by `NimCreateDurableAuditCodeReleaseSwitchContractSupport`.

Recommended next maps/lists to close:

- `validationResultBinding`
- `stateMachineBinding`
- `durableExecutorBinding`
- `allowPrerequisites`
- `currentTemplate`
- `forbiddenShortcuts`
