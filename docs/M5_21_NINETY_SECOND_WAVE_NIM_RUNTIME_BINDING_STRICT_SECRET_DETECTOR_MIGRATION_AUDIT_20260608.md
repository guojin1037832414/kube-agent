# M5.21-92 NIM Runtime Binding Strict Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates one strict runtime-binding scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest.java`

The old runtime-binding scanner treated any non-null value under a forbidden key as unsafe, including Boolean and Number values, and recursively scanned nested `Map` and `List` values. That behavior matches `NimForbiddenSecretMaterialDetector.strictRecursivePolicy()`.

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport` now uses `NimForbiddenSecretMaterialDetector.strictRecursivePolicy()` for secret-material scanning.
- The local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, and `secretBearingValue(...)` copy was removed from the runtime-binding support class.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now protects strict-recursive-policy classes from reintroducing local detector copies.
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest` now proves strict runtime-binding semantics by rejecting `token=false` and `secret=0` under nested runtime evidence.

## What Did Not Change

- Blocker code stayed unchanged:
  - `CODE_RELEASE_SWITCH_RUNTIME_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged runtime release claim scanners stayed local. They guard authority/source forgery such as `writePermitted=true`, `writeExecutionAllowed=true`, `switchState=OPEN_FOR_NIM_CREATE_WRITE_EXECUTION`, executor success, and runtime flag overrides.
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` was not functionally changed; it was added to the new strict-recursive usage-contract group because it already used the same shared policy.
- Classes with documented forbidden field-name exceptions were intentionally not migrated in this wave:
  - `NimCreateDurableAuditValidationResultMigrationSupport`
  - `NimCreateDurableAuditReceiptValidationGateSupport`
  - `NimCreateDurableAuditReleaseDecisionGateSupport`
  - `NimCreateStateMachineReleaseDecisionRequirementSupport`
- No runtime binding implementation, state-machine release, durable executor release, kube-manager write, storage write, or real HTTP side effect was added.

## Multi-Expert Review Notes

- Backend/API lens: this is a support-class refactor only. It does not register a Spring bean, HTTP client, Tool, controller, or durable writer.
- Security/RBAC lens: strict runtime-binding inputs stay stricter than non-Boolean/Number evidence contracts. Any non-null forbidden-key value remains unsafe because runtime release sources are caller-visible and non-authoritative.
- Agent architecture lens: the shared detector removes drift, but it does not become release evidence. Code switch runtime binding still remains HOLD until reviewed server-owned open-switch evidence exists.
- Test architecture lens: the usage contract prevents local blacklist drift, while the runtime-binding regression protects the strict Boolean/Number rejection boundary.
- Learning lens: policy choice is part of architecture. `strictRecursivePolicy()` is appropriate for runtime release-source evidence; documented schema/gate classes require separate policy comparison.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
```

Passed:

```bash
git diff --check
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine runtime release implementation added.
- No durable executor release implementation added.
- No code release switch implementation added.
- No durable writer/probe/receipt implementation added.
- No validation result signer or release decision signer added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Treat documented-field exception classes separately; they are not `strictRecursivePolicy()` candidates without a dedicated policy comparison.
- Continue strict-policy migrations only where old behavior recursively rejected any non-null forbidden-key value.
- Keep state-machine and write-body protected-context scanner migrations separate because they mix credential detection with protected context stripping and release-gate semantics.
