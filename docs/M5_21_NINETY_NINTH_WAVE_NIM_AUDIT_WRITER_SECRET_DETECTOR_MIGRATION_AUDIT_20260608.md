# M5.21-99 NIM Audit Writer Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the mock-first NIM audit writer receipt shell from its local secret-key scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateAuditWriterSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateAuditWriterSupportTest.java`

The audit writer remains a mock-first receipt contract only. It does not write a database table, does not call kube-manager `8100`, does not call a real NIM service, does not emit a durable receipt, and does not authorize `nim_create`.

## What Changed

- `NimCreateAuditWriterSupport` now delegates audit-context secret-material scanning to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(...)`.
- The writer uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` because audit context must not carry real credential material or secret-like strings.
- The local `FORBIDDEN_SECRET_KEYS` copy and local recursive scanner were removed.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now prevents the audit writer from reintroducing local detector drift.
- Audit writer tests now prove `Authorization: Bearer ...`, plain `Authorization: present`, `token=123`, and nested/list-carried `Authorization=Bearer ...` reject with `AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET`.

## Policy Comparison

- Old writer behavior rejected non-blank values under `apiKey`, `ngcApiKey`, `nvaieApiKey`, `token`, `secret`, and `password`.
- The shared text-value policy keeps those rejections and also covers additional forbidden key variants such as `Authorization`, `authHeader`, `bearerToken`, and keys ending in API-key/token/secret/password/authorization.
- The shared detector also rejects secret-looking string values outside forbidden keys, including Bearer headers, `token=...`, `secret=...`, `password=...`, cloud key shapes, GitHub tokens, Slack tokens, NGC/NVAIE markers, and API-key assignments.
- This is an intentional security hardening for audit context, not a purely equivalent refactor. It may reject caller metadata that includes raw header examples, placeholder-like header text, or real-looking values; such metadata should be redacted before reaching the audit writer.
- Compatibility risk: non-secret business fields whose names end in `token`, `secret`, `password`, `authorization`, or `apiKey`, plus schema snippets containing `Authorization=Bearer ...` or cloud/API-token-shaped strings, now fail closed as `AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET`.
- Schema/interface documentation that must list exact forbidden field names belongs in receipt-schema/report contexts that explicitly use `receiptSchemaPolicy()`, not in audit writer input and not through an audit-writer allowlist.
- Nested `Map` and `List` values remain recursively scanned.

## What Did Not Change

- Blocker code stayed unchanged:
  - `AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET`
- Valid sanitized audit context still produces a mock receipt with `receiptStatus=MOCK_PREPARED`.
- Invalid or secret-bearing audit context still produces `receiptStatus=REJECTED`.
- Mock receipts remain non-durable and non-release-eligible.
- No durable audit writer was implemented.
- No real storage, HTTP, or NIM execution path was added.
- No deployment `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Multi-Expert Review Notes

- Backend/API lens: no controller, bean, HTTP client, `8100` integration, or persistence boundary changed.
- Security/RBAC lens: this migration expands secret-material coverage for audit metadata, which is safer for a future durable audit trail.
- Agent architecture lens: clean audit context is only a precondition for receipt preparation; it is not release authority.
- Test architecture lens: the static usage contract now catches local blacklist drift, while writer regressions cover both forbidden keys and free-form secret-like strings.
- Learning lens: audit evidence should never preserve raw credentials. A top-tier Agent should redact first, then audit sanitized facts.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateAuditWriterSupportTest,NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test
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
- No real API key generation, storage, display, or forwarding.
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

- Continue scanning the remaining older NIM support shells with local detector copies only after explicit old/new policy comparison.
- Next high-value safety slice: resolve the known durable executor/source-guard acceptance-shape mismatch before any future real write boundary design.
