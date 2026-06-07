# M5.21-89 NIM Durable Audit Storage Shared Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the next homogeneous NIM durable audit storage and probe-boundary input scanners to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageAvailabilityGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDedicatedDurableAuditWriterBoundarySupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

All four support classes previously used the same secret-material pattern:

- forbidden key check with `hasText(value)`.
- recursive `Map` and `List` scanning.
- secret-looking string matching for `Bearer ...`, `apiKey=...`, `token=...`, cloud/API token shapes, and NGC/NVAIE API key markers.

That behavior is equivalent to `NimForbiddenSecretMaterialDetector.textValuePolicy()`.

## What Changed

- `NimCreateDurableAuditStorageSupport` now uses the shared text-value detector for audit/principal input secret scanning.
- `NimCreateDurableAuditStorageAvailabilityGateSupport` now uses the same shared detector for audit/principal/writer-plan input secret scanning.
- `NimCreateDurableAuditStorageProbeExecutorSupport` now uses the same shared detector for audit/principal/gate/boundary/probe-snapshot input secret scanning.
- `NimCreateDedicatedDurableAuditWriterBoundarySupport` now uses the same shared detector for audit/principal/writer-plan/availability-gate input secret scanning.
- The usage contract now protects eleven migrated support classes from reintroducing local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, `secretBearingValue(...)`, or documented-field exception copies.
- Added nested/list-carried secret regression tests for all four migrated support classes.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `STORAGE_PROBE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `DEDICATED_AUDIT_WRITER_BOUNDARY_INPUT_CONTAINS_FORBIDDEN_SECRET`
- `NimCreateDurableAuditStorageProbeExecutorSupport` and `NimCreateDedicatedDurableAuditWriterBoundarySupport` still keep their local forged-success recursive scanners. Those scanners detect different evidence-source and success-claim risks, so they were intentionally not folded into the secret-material detector.
- No receipt-schema documented-field-name policy was applied.
- No strict recursive non-null policy was applied.
- No storage probe, sys_log write, Elasticsearch call, `ISysLogService` call, or real durable writer implementation was added.

## Multi-Expert Review Notes

- Backend/API lens: this is a contract-shell refactor only. Mature `sys_log` and `ISysLogService.saveLog(SysLog)` remain evidence for future design, not runtime dependencies.
- Security/RBAC lens: recursive secret rejection remains fail-closed, while forged success claims remain separately guarded by purpose-built scanners.
- Agent architecture lens: shared safety primitives now cover a larger contiguous slice of the NIM durable audit/storage write-readiness chain, reducing drift before any real side-effect boundary is implemented.
- Test architecture lens: usage-contract scanning prevents local detector copies from returning. Nested/list regression tests prove the shared detector still reaches each original business blocker.
- Learning lens: do not mix "secret material" and "forged success evidence" into one generic helper. Shared utilities are safest when their policy boundary is precise.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateDurableAuditStorageSupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageProbeExecutorSupportTest" test
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
- Prioritize classes that use `secretBearingValue(...)` if their semantics can be matched exactly by `strictRecursivePolicy()` or another explicit detector policy.
- Keep state-machine and write-body scanner migrations separate because they may mix protected-context filtering with secret-material detection.
