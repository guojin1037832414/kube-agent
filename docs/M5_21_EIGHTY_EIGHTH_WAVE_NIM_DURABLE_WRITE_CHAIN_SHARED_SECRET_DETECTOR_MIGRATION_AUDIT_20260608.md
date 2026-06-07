# M5.21-88 NIM Durable Write Chain Shared Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the remaining text-policy NIM durable write-chain input scanners to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterPlanSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterInterfaceSpecSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

All three support classes previously used the same local pattern:

- forbidden key check with `hasText(value)`.
- recursive `Map` and `List` scanning.
- secret-looking string matching for `Bearer ...`, `apiKey=...`, `token=...`, cloud/API token shapes, and NGC/NVAIE API key markers.

That behavior is equivalent to `NimForbiddenSecretMaterialDetector.textValuePolicy()`.

## What Changed

- `NimCreateDurableWriteExecutorSupport` now uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` for executor input secret scanning.
- `NimCreateDurableAuditWriterPlanSupport` now uses the same shared text-value policy for audit writer plan inputs.
- `NimCreateDurableAuditWriterInterfaceSpecSupport` now uses the same shared text-value policy for interface-spec inputs.
- The usage contract now protects seven migrated support classes from reintroducing local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, `secretBearingValue(...)`, or documented-field exception copies.
- Added nested/list-carried secret regression tests for the durable executor and durable audit writer plan.
- Added interface-spec policy boundary tests:
  - generated `requestContract.forbiddenFields` still documents forbidden field names.
  - caller/input metadata may include bare documented field names that the previous text scanner allowed.
  - secret-like metadata such as `Authorization=Bearer ...` is rejected.
  - numeric forbidden-key values such as `token=123` are rejected to prevent accidental migration to the looser receipt-schema policy.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `DURABLE_WRITE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `DURABLE_AUDIT_WRITER_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `DURABLE_AUDIT_WRITER_INTERFACE_INPUT_CONTAINS_FORBIDDEN_SECRET`
- No receipt-schema documented-field-name policy was applied to these input scanners.
- No strict recursive non-null policy was applied to these input scanners.
- The future endpoint remains `POST /api/{orgId}/deployment`, but this wave does not bind an HTTP client and does not execute it.

## Multi-Expert Review Notes

- Backend/API lens: this is a contract-shell refactor only. The mature kube-manager write endpoint remains modeled as future evidence, not invoked.
- Security/RBAC lens: recursive secret rejection remains fail-closed. Caller headers, Authorization, API keys, caller idempotency keys, and secret-looking strings remain forbidden.
- Agent architecture lens: the NIM write-chain now uses one shared detector for request spec, execution handoff, durable executor, writer plan, interface spec, receipt schema, and runtime source guard use cases, with policy differences explicit at call sites.
- Test architecture lens: usage-contract scanning is now the drift guard. Focused regression tests prove the shared detector still reaches the same blocker codes and that interface-spec documentation fields are not confused with runtime secret values.
- Learning lens: shared safety primitives should be migrated in homogeneous policy groups. The policy name is part of the architecture; choosing `textValuePolicy()` versus `receiptSchemaPolicy()` is a security decision, not a cosmetic refactor.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableWriteExecutorSupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest" test
git diff --check
mvn -q test
```

Full-test note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited successfully.

## Security Invariants

- No real `8100` access.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No durable writer/probe/receipt implementation added.
- No validation result, release decision, or code release switch implementation added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue migrating any remaining NIM support shells with duplicate secret detectors only after confirming their policy is equivalent.
- Prefer `textValuePolicy()` for input scanners that previously used forbidden-key `hasText(value)` semantics.
- Keep `receiptSchemaPolicy()` only for schema/report contexts where documented forbidden field names must be represented without treating the bare names as leaked credentials.
