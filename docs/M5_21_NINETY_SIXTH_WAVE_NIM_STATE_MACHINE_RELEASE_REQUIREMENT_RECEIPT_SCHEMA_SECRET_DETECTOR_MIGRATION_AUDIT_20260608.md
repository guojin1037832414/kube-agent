# M5.21-96 NIM State-Machine Release Requirement Receipt-Schema Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the final documented-field exception scanner in the NIM release chain to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupportTest.java`

The previous local scanner allowed exact documented forbidden field names such as `Authorization`, `apiKey`, and `ngcApiKey` when they were used as schema or contract documentation, but still rejected real secret-looking values such as `Authorization=Bearer ...`.

## What Changed

- `NimCreateStateMachineReleaseDecisionRequirementSupport` now calls `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(..., receiptSchemaPolicy())`.
- The local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, `secretBearingValue(...)`, `isDocumentedForbiddenFieldName(...)`, and `normalizeKey(...)` copy was removed from the state-machine requirement support class.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now includes `NimCreateStateMachineReleaseDecisionRequirementSupport` in the receipt-schema policy support group.
- `NimCreateStateMachineReleaseDecisionRequirementSupportTest` now proves exact documented forbidden field names remain allowed while bearer-style secret material is rejected.

## What Did Not Change

- Blocker code stayed unchanged:
  - `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged release/write claim scanners stayed local. They guard caller-supplied authority and execution-source forgery, not credential material.
- Valid inputs still produce only `IMPLEMENTATION_HOLD`.
- No state-machine release binding, durable executor release binding, validation result signer, release decision signer, code release switch, kube-manager write, Elasticsearch write, or `sys_log` write was added.

## Policy Comparison

- Forbidden key with `Boolean` / `Number`: old scanner allowed it; `receiptSchemaPolicy()` still allows it.
- Forbidden key with non-blank string/object/list: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Normal key or list value with documented field-name string such as `Authorization`, `apiKey`, or `ngcApiKey`: old scanner allowed it; `receiptSchemaPolicy()` allows it.
- Normal key or list value with real secret-looking string such as `Authorization=Bearer abcdefghijklmnop`: old scanner rejected it; `receiptSchemaPolicy()` still rejects it.
- Nested `Map` and `List` structures remain recursively checked for secret material. This wave did not widen list-inside-list semantics.

## Multi-Expert Review Notes

- Backend/API lens: this is a support-class refactor and test update only. It does not register a Spring bean, controller, HTTP client, state-machine release implementation, or durable executor.
- Security/RBAC lens: credential leakage checks are centralized, while release/write authority forgery checks remain local to the state-machine requirement contract.
- Agent architecture lens: a clean secret scan is not release evidence. The state machine still requires future server-issued, digest-bound validation result and release decision evidence plus a code release switch before any write can be considered.
- Test architecture lens: usage contracts now prevent every receipt-schema documented-field support from reintroducing local blacklist drift.
- Learning lens: top-tier Agent safety separates "no secrets leaked" from "caller has authority." These are different proofs, and they should stay independently testable.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateStateMachineReleaseDecisionRequirementSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest" test
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

- Continue toward the reviewed durable writer/probe boundary only through small, test-backed contract slices.
- Keep state-machine forged-release/write scanners local unless a future shared authority-forgery helper is explicitly designed, named, reviewed, and tested separately from credential leakage detection.
