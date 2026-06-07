# M5.21-95 NIM Release Decision Gate Receipt-Schema Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates one documented-field exception scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupportTest.java`

The old release-decision-gate scanner allowed documented forbidden field names such as `Authorization`, `apiKey`, and `ngcApiKey` when they appeared as schema/contract documentation, but still rejected real secret material such as `Authorization=Bearer ...`.

## What Changed

- `NimCreateDurableAuditReleaseDecisionGateSupport` now calls `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(..., receiptSchemaPolicy())` instead of carrying a local `FORBIDDEN_SECRET_KEYS` and `looksLikeSecretValue(...)` copy.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now includes `NimCreateDurableAuditReleaseDecisionGateSupport` in the receipt-schema policy support group.
- `NimCreateDurableAuditReleaseDecisionGateSupportTest` now proves the release decision gate allows exact documented forbidden field names while still rejecting real bearer-style secret material.

## What Did Not Change

- Blocker code stayed unchanged:
  - `DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged release/write claim scanners stayed local. They guard authority and execution-source forgery, not credential material.
- The release decision gate still remains `IMPLEMENTATION_HOLD` when inputs are otherwise valid.
- No real release decision, state-machine release binding, durable executor binding, code release switch, kube-manager write, Elasticsearch write, or `sys_log` write was added.

## Policy Comparison

- Forbidden key with `Boolean` / `Number`: old scanner allowed it; `receiptSchemaPolicy()` still allows it.
- Forbidden key with non-blank string/object/list: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Normal key or list value with documented field-name string such as `Authorization`, `apiKey`, or `ngcApiKey`: old scanner allowed it; `receiptSchemaPolicy()` allows it.
- Normal key or list value with real secret-looking string such as `Authorization=Bearer abcdefghijklmnop`: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Nested `Map` and `List` structures remain recursively checked the same way as before. List-inside-list behavior is unchanged and was not widened by this migration.

## Multi-Expert Review Notes

- Backend/API lens: this is a support-class refactor and test update only. It does not register a Spring bean, controller, HTTP client, state-machine release implementation, or durable executor.
- Security/RBAC lens: exact documented forbidden field names remain documentation-only; bearer/API-key/token/password/secret material fails closed.
- Agent architecture lens: release-decision gate still separates credential leakage checks from release/write authority checks. The shared detector is not release evidence.
- Test architecture lens: usage contracts now prevent the release decision gate from reintroducing local blacklist drift, while behavior tests lock the documented-field versus bearer-secret boundary.
- Parallel reviewer note: an independent read-only review confirmed equivalence for forbidden-key Boolean/Number handling, non-blank forbidden-key values, documented field-name strings, bearer rejection, and nested map/list scanning. It also confirmed that release/write forged-claim detection must remain local, and called out the usage-contract entry as the right static guard for this migration.
- Learning lens: release gates need two layers: no credential leaks and no caller-forged authority. They may block the same input, but the reasons and review evidence differ.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
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
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Migrate the final documented-field exception class only after comparing its old semantics with `receiptSchemaPolicy()`:
  - `NimCreateStateMachineReleaseDecisionRequirementSupport`
- Keep state-machine forged-release/write scanners local unless a future shared authority-forgery helper is explicitly designed and tested.
