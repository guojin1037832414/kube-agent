# M5.21-87 NIM Write Chain Shared Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the next homogeneous NIM write-chain contract shells to the shared forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateWriteRequestSpecAdapterSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

Both support classes previously used the same pattern:

- forbidden key check with `hasText(value)`.
- recursive `Map` and `List` scanning.
- secret-looking string matching for `Bearer ...`, `apiKey=...`, `token=...`, cloud/API token shapes, and NGC/NVAIE API key markers.

That is equivalent to `NimForbiddenSecretMaterialDetector.textValuePolicy()`.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `WRITE_REQUEST_SPEC_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `WRITE_REQUEST_SPEC_CONTAINS_FORBIDDEN_SECRET_OR_CONTEXT`
  - `WRITE_EXECUTION_HANDOFF_INPUT_CONTAINS_FORBIDDEN_SECRET`
- The request spec adapter still has its own protected body-context scanner because that logic is different from secret material detection.
- No receipt-schema documented-field-name compatibility was added to these classes.
- No strict runtime-source non-null policy was applied to these classes.

## Multi-Expert Review Notes

- Backend/API lens: this is a contract-shell refactor only. The future endpoint remains `POST /api/{orgId}/deployment`, but no HTTP client is bound and no kube-manager call is made.
- Security/RBAC lens: secret material rejection remains recursive and fail-closed. Caller headers, Authorization, API keys, and caller idempotency keys remain forbidden.
- Agent architecture lens: the shared detector now covers four representative NIM support classes, reducing future drift in write-chain pre-execution gates.
- Test architecture lens: the existing request spec and handoff tests still prove blocker codes and state-machine acceptance semantics. New nested/list-carried secret regression tests prove the shared detector still reaches the same blocker codes. The usage contract now blocks local duplicate secret scanners in all migrated classes.
- Documentation/learning lens: this wave demonstrates the safe follow-up pattern after creating a shared safety primitive: migrate small homogeneous groups, preserve policy names, and keep business blockers local.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteExecutionHandoffSupportTest" test
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

Next small slices can migrate more text-policy classes, for example:

- `NimCreateDurableWriteExecutorSupport`
- `NimCreateDurableAuditWriterPlanSupport`
- `NimCreateDurableAuditWriterInterfaceSpecSupport`

Each migration should first compare policy differences and only use `textValuePolicy()` when the existing behavior is equivalent.
