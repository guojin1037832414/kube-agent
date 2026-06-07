# M5.21-91 NIM Release / Code Switch Shared Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the next release-control contract scanners to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionContractSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

Both support classes previously used the same no-exception secret-material pattern:

- forbidden keys allow Boolean/Number state scalars but reject other non-blank values.
- recursive `Map` and `List` scanning.
- secret-looking string matching for `Bearer ...`, `apiKey=...`, `token=...`, cloud/API token shapes, and NGC/NVAIE API key markers.
- no documented forbidden-field-name exception.

That behavior is equivalent to `NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()`.

## What Changed

- `NimCreateDurableAuditReleaseDecisionContractSupport` now uses the shared non-Boolean/Number detector for audit/principal/validation-result/caller release evidence secret scanning.
- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now uses the same shared detector for audit/principal/release-decision/caller switch evidence secret scanning.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now protects six non-Boolean/Number policy support classes from reintroducing local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, `secretBearingValue(...)`, or documented-field exception copies.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `DURABLE_AUDIT_CODE_RELEASE_SWITCH_INPUT_CONTAINS_FORBIDDEN_SECRET`
- `hasForgedReleaseClaim(...)` and `hasForgedSwitchClaim(...)` stayed local and recursive. They guard caller-forged release/switch success evidence such as `releaseEligible=true`, `writePermitted=true`, `writeExecutionAllowed=true`, `switchState=OPEN`, `releaseDecision=ALLOW_WRITE_EXECUTION`, typed `releaseCredential`, and write result claims.
- No `receiptSchemaPolicy()` documented field-name exception was used.
- No strict recursive non-null policy was used.
- No release decision signer, code release switch implementation, state-machine release, durable executor release, storage write, kube-manager write, or real HTTP side effect was added.

## Multi-Expert Review Notes

- Backend/API lens: this is a contract-shell refactor only. The release decision and code switch remain future value contracts, not runtime services.
- Security/RBAC lens: credential leakage remains shared and policy-bound, while release/switch forgery stays in purpose-built local guards.
- Agent architecture lens: release authorization remains multi-evidence and fail-closed. A shared detector cannot become a release credential.
- Test architecture lens: usage-contract source scanning protects the migrated classes from local detector drift.
- Learning lens: release controls are more than "no secrets leaked." A top-tier Agent also separates forged authority claims from credential leakage and keeps both audited.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
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
- No release decision signer added.
- No code release switch implementation added.
- No durable writer/probe/receipt implementation added.
- No validation result signer added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue remaining detector migrations only after policy comparison.
- Treat documented-field exception classes separately; they are candidates for `receiptSchemaPolicy()` only when the exception is intentional.
- Keep state-machine and write-body scanner migrations separate because they mix broader protected-context or release-gate semantics with secret-material detection.
