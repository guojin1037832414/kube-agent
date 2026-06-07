# M5.21-90 NIM Validation / Probe Result Shared Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the next homogeneous NIM validation/probe-result evidence scanners to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationResultSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.java`
- `src/main/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetector.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

All four support classes previously used the same secret-material pattern:

- forbidden keys allow Boolean/Number state scalars but reject other non-blank values.
- recursive `Map` and `List` scanning.
- secret-looking string matching for `Bearer ...`, `apiKey=...`, `token=...`, cloud/API token shapes, and NGC/NVAIE API key markers.
- no documented forbidden-field-name exception.

That behavior is equivalent to `NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()`.

## What Changed

- Added `NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()` as an explicit no-exception policy for validation/probe-result evidence contracts.
- `NimCreateDurableAuditStorageProbeResultSupport` now uses the shared non-Boolean/Number detector for audit/principal/probe/receipt/caller evidence secret scanning.
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` now uses the same shared detector for probe-result binding inputs.
- `NimCreateDurableAuditReceiptValidationResultSupport` now uses the same shared detector for validation-result contract inputs.
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` now uses the same shared detector for enhanced migration inputs.
- The usage contract now protects these four classes from reintroducing local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, `secretBearingValue(...)`, or documented-field exception copies.
- Added direct detector tests proving `token=false` and `apiKey=0` remain allowed state scalars while nested secret objects and secret-like strings fail closed.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `STORAGE_PROBE_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `PROBE_RESULT_VALIDATION_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `VALIDATION_RESULT_PROBE_BINDING_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET`
- Local forged-success scanners stayed local. They guard caller-supplied claims such as `storageAvailable=true`, `validationStatus=PASS`, `releaseEligible=true`, `writeExecutionAllowed=true`, typed `validationResult`, typed `releaseDecision`, and similar evidence-source risks.
- No `receiptSchemaPolicy()` documented field-name exception was used.
- No strict recursive non-null policy was used.
- No storage probe, receipt validator, validation result signer, release decision, sys_log write, Elasticsearch call, `ISysLogService` call, or real durable writer implementation was added.

## Multi-Expert Review Notes

- Backend/API lens: this is a contract-shell refactor only. The classes still produce value contracts and migration plans; they do not call kube-manager or any storage service.
- Security/RBAC lens: secret leakage detection now shares one policy while forged validation/release claims remain separately guarded. This preserves the security distinction between "credential material" and "caller-forged success evidence".
- Agent architecture lens: naming the non-Boolean/Number policy makes the evidence-contract boundary teachable and reviewable. Later migrations must pick a policy by comparing semantics, not by convenience.
- Test architecture lens: direct detector tests lock the Boolean/Number allowance, and usage-contract tests lock the migrated call sites to the shared policy.
- Learning lens: top-tier Agent work is mostly precise boundary work. A shared helper is only safe when the old local semantics are proven equivalent and separately protected with drift tests.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest" test
git diff --check
mvn -q test
```

Full-test note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited successfully.

## Security Invariants

- No real `8100` access.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No storage probe implementation added.
- No durable writer/probe/receipt implementation added.
- No validation result, release decision, or code release switch implementation added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue migrating remaining NIM support shells only after comparing policy semantics.
- Keep classes with documented-field exceptions on `receiptSchemaPolicy()` or another explicit policy.
- Keep state-machine and write-body scanner migrations separate because they may mix protected-context filtering with secret-material detection.
