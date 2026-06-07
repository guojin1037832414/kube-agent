# M5.21-103 NIM Protected Context Detector Audit - 2026-06-08

## Scope

This wave centralizes protected-context detection for future NIM write-chain DTO/request bodies:

- `src/main/java/com/atlas/tool/impl/NimProtectedContextDetector.java`
- `src/main/java/com/atlas/tool/impl/NimCreateWriteBodyRebuilderSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateWriteRequestSpecAdapterSupport.java`

It does not change Tool execution protected-parameter filtering and does not open any write execution path.

## What Changed

- Added `NimProtectedContextDetector`.
- Moved the write body rebuilder and request-spec adapter away from local protected-context key lists.
- The detector normalizes key spelling by removing `_`, `-`, `.`, and spaces before lowercasing.
- The detector recursively scans nested maps and lists so allowlisted body containers such as `autoScaleConfig` and `commands` cannot smuggle `organizationId`, audit receipts, HITL confirmations, creation gates, readiness reports, or request-spec reports.
- Added `WRITE_BODY_CONTAINS_FORBIDDEN_CONTEXT` when a rebuilt body contains nested protected context after allowlist copying.
- Kept request-spec boundary blocker `WRITE_REQUEST_SPEC_CONTAINS_FORBIDDEN_SECRET_OR_CONTEXT` for final-body validation compatibility.

## Why This Matters

Protected context is not the same risk as secret material:

- Secret-material detection answers whether caller-visible structures carry credentials or credential-shaped values.
- Protected-context detection answers whether a future write DTO/request body carries tenant identity, request identity, audit/HITL evidence, or state-machine control evidence that must be bound outside the body.
- `ProtectedToolParameterFilter` remains the Tool execution boundary. `NimProtectedContextDetector` is narrower and exists only for the NIM write-chain DTO/request boundary.

This separation keeps the Agent easier to audit: a clean secret scan does not prove a body is free of forged authority/context.

## Multi-Expert Review Notes

- Backend/API lens: no controller, HTTP client, kube-manager `8100` call, storage, or deployment POST was added.
- Security/RBAC lens: nested `organization_id`, `conversation-id`, `auditReceipt`, and `write_request_spec_report` are now rejected before future request compilation.
- Agent architecture lens: write-chain context stripping is a dedicated safety primitive rather than a side effect of credential scanning.
- Test architecture lens: detector unit tests cover normalization and recursion; boundary tests prove both body rebuilding and request-spec compilation fail closed; a usage contract prevents local scanner drift.
- Learning lens: this wave documents a key Agent-design distinction: secrets, protected execution parameters, and write-chain authority/context evidence need separate proof boundaries.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimProtectedContextDetectorTest,NimProtectedContextDetectorUsageContractTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest" test
```

Passed:

```bash
git diff --check
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch, `ISysLogService`, or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue closing write-chain authority/context gaps before any real execution adapter is considered.
- Consider expanding protected-context drift contracts only after categorizing non-NIM call sites so Tool-parameter filtering and write-body filtering do not get merged accidentally.
