# M5.21-94 NIM Validation Result Migration Receipt-Schema Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates one documented-field exception scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupportTest.java`

The old migration-plan scanner allowed documented forbidden field names such as `Authorization`, `apiKey`, and `ngcApiKey` when they appeared as interface/schema documentation, but still rejected real secret material such as `Authorization=Bearer ...`.

## What Changed

- `NimCreateDurableAuditValidationResultMigrationSupport` now calls `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(..., receiptSchemaPolicy())` instead of carrying a local `FORBIDDEN_SECRET_KEYS` and `looksLikeSecretValue(...)` copy.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now includes `NimCreateDurableAuditValidationResultMigrationSupport` in the receipt-schema policy support group.
- `NimCreateDurableAuditValidationResultMigrationSupportTest` now proves the migration plan allows exact documented forbidden field names while still rejecting real bearer-style secret material.

## What Did Not Change

- Blocker code stayed unchanged:
  - `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged validation/release claim scanners stayed local. They guard authority and evidence-source forgery, not credential material.
- The migration plan still remains `IMPLEMENTATION_HOLD` when inputs are otherwise valid.
- No real validation result, release decision, receipt validator, storage probe, durable writer, release switch, kube-manager write, Elasticsearch write, or `sys_log` write was added.

## Policy Comparison

- Forbidden key with `Boolean` / `Number`: old scanner allowed it; `receiptSchemaPolicy()` still allows it.
- Forbidden key with non-blank string/object/list: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Normal key or list value with documented field-name string such as `Authorization`, `apiKey`, or `ngcApiKey`: old scanner allowed it; `receiptSchemaPolicy()` allows it.
- Normal key or list value with real secret-looking string such as `Authorization=Bearer abcdefghijklmnop`: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Nested `Map` and `List` structures remain recursively checked the same way as before. List-inside-list behavior is unchanged and was not widened by this migration.

## Multi-Expert Review Notes

- Backend/API lens: this is a pure support-class refactor and test update. It does not register a Spring bean, controller, HTTP client, DTO writer, or storage adapter.
- Security/RBAC lens: exact documented forbidden field names remain documentation-only; bearer/API-key/token/password/secret material fails closed.
- Agent architecture lens: `receiptSchemaPolicy()` remains distinct from `nonBooleanNumberValuePolicy()` because migration-plan inputs still carry schema/validation documentation containers where literal field names can appear.
- Test architecture lens: usage contracts now prevent the migration support from reintroducing local blacklist drift, while behavior tests lock the documented-field versus bearer-secret boundary.
- Parallel reviewer note: an independent read-only review confirmed equivalence for forbidden-key Boolean/Number handling, non-blank forbidden-key values, documented field-name strings, bearer rejection, and nested map/list scanning. It also confirmed that `hasForgedValidationOrReleaseClaim(...)`, caller evidence/decision key checks, success-boolean checks, documentation-container skips, forged blockers, and ignored caller claims must stay local.
- Learning lens: no-secret and no-forged-release are different safeguards. A top-tier Agent should keep shared credential detection separate from local authority/source validation.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
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
- No receipt validator implementation added.
- No storage probe implementation added.
- No durable writer/probe/receipt implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Migrate the remaining documented-field exception classes only after comparing their old semantics with `receiptSchemaPolicy()`:
  - `NimCreateDurableAuditReleaseDecisionGateSupport`
  - `NimCreateStateMachineReleaseDecisionRequirementSupport`
- Keep release-decision-gate and state-machine requirement forged-release scanners local unless a future shared authority-forgery helper is explicitly designed and tested.
