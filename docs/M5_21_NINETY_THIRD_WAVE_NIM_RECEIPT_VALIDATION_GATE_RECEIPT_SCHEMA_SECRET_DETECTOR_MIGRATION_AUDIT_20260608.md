# M5.21-93 NIM Receipt Validation Gate Receipt-Schema Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates one documented-field exception scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetector.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorTest.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupportTest.java`

The old receipt-validation-gate scanner allowed documented forbidden field names such as `Authorization`, `apiKey`, and `ngcApiKey` when they appeared as schema/interface field-name documentation, but still rejected real secret material such as `Authorization=Bearer ...`.

## What Changed

- `NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()` now applies its documented-field-name allowlist to both direct string values and list string values.
- `NimCreateDurableAuditReceiptValidationGateSupport` now calls `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(..., receiptSchemaPolicy())` instead of carrying a local `FORBIDDEN_SECRET_KEYS` and `looksLikeSecretValue(...)` copy.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now has an explicit receipt-schema-policy group for:
  - `NimCreateDurableAuditReceiptSchemaSupport`
  - `NimCreateDurableAuditReceiptValidationGateSupport`
- `NimForbiddenSecretMaterialDetectorTest` now covers direct scalar documented field names, not only list values.
- `NimCreateDurableAuditReceiptValidationGateSupportTest` now proves the gate allows documented forbidden field names while still rejecting real bearer-style secret material.

## What Did Not Change

- Blocker code stayed unchanged:
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged validation/success claim scanners stayed local. They guard authority and evidence-source forgery, not credential material.
- The validation gate still remains `IMPLEMENTATION_HOLD` when inputs are otherwise valid.
- No receipt validator implementation, storage probe, durable writer, release decision signer, code release switch, kube-manager write, Elasticsearch write, or `sys_log` write was added.

## Policy Comparison

- Forbidden key with `Boolean` / `Number`: old scanner allowed it; `receiptSchemaPolicy()` still allows it.
- Forbidden key with non-blank string/object/list: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Normal key with documented field-name string such as `Authorization`, `apiKey`, or `ngcApiKey`: old scanner allowed it; `receiptSchemaPolicy()` now explicitly allows it for direct strings and list values.
- Normal key with real secret-looking string such as `Authorization=Bearer abcdefghijklmnop`: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Nested `Map` and `List` structures remain recursively checked the same way as before. List-inside-list behavior is unchanged and was not widened by this migration.

## Multi-Expert Review Notes

- Backend/API lens: this is a pure support-class refactor and policy-boundary test update. It does not add Spring wiring, controllers, HTTP clients, or storage writers.
- Security/RBAC lens: documented field names are interface documentation, not credentials. Exact names are allowed; values containing bearer/API-key/token/password/secret material fail closed.
- Agent architecture lens: the shared detector reduces blacklist drift while preserving policy names at call sites. `receiptSchemaPolicy()` is intentionally different from `nonBooleanNumberValuePolicy()` and `strictRecursivePolicy()`.
- Test architecture lens: usage contracts now prevent both receipt-schema classes from reintroducing local detector copies, while behavior tests lock scalar/list documented-field semantics and bearer rejection.
- Parallel reviewer note: an independent semantic review agreed the migration is equivalent for forbidden-key scalar handling, direct/list documented field names, bearer rejection, and nested map/list scanning, and emphasized preserving forged validation/success claim scanners.
- Learning lens: this is a useful Agent safety pattern: credential-leak checks and forged-authority checks should be separate helpers with separate tests, even when both block the same caller-visible input.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
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
  - `NimCreateDurableAuditValidationResultMigrationSupport`
  - `NimCreateDurableAuditReleaseDecisionGateSupport`
  - `NimCreateStateMachineReleaseDecisionRequirementSupport`
- Consider a separate focused test for unchanged list-inside-list behavior only if a future schema contract begins carrying nested list field-name groups.
