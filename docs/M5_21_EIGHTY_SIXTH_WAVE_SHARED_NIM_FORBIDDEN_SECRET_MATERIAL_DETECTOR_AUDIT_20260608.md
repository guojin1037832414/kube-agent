# M5.21-86 Shared NIM Forbidden Secret Material Detector Audit - 2026-06-08

## Scope

This wave introduces the first shared detector for NIM forbidden secret material while preserving the current `nim_create` HOLD / mock-first boundary.

Changed code:

- `src/main/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetector.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorTest.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

## Why This Matters

Multiple NIM support shells had local copies of the same forbidden secret-key and secret-looking-value logic. That duplication is risky because each shell protects a different contract boundary. Over time, one shell could learn to reject `Authorization=Bearer ...` while another still only checks `token`, or one could accidentally treat documented field names such as `Authorization` as real leaked secrets.

The new helper centralizes the common scanner but keeps call-site policy explicit. This is important for a top-tier Agent: shared safety infrastructure should reduce drift without flattening meaningful security semantics.

## Policy Matrix

| Policy | Intended Use | Forbidden key value behavior | Documented forbidden field names |
|---|---|---|---|
| `textValuePolicy()` | Existing `hasText(...)` style NIM checks | Any non-blank value under forbidden key is secret material | Not specially allowed |
| `receiptSchemaPolicy()` | Typed receipt/interface schema reports | Boolean/number values under forbidden key are not secret-bearing; non-blank text is | Allows literal field names like `Authorization`, `apiKey`, `ngcApiKey`, `callerProvidedUsername` inside lists |
| `strictRecursivePolicy()` | Runtime source guard / release-source evidence | Any non-null scalar under forbidden key is unsafe; nested maps/lists are scanned recursively | Not specially allowed |

The helper also detects secret-looking values such as:

- `Bearer ...`
- `apiKey=...`, `token=...`, `secret=...`, `password=...`, `authorization=...`
- `sk-*`, `AKIA*`, `AIza*`, `ghp_*`, and Slack `xox*` token shapes

## Multi-Expert Review Notes

- Backend/API lens: this change is purely in contract support code and shared core utility code. It does not bind HTTP clients, Spring beans, storage clients, `ISysLogService`, or any kube-manager write path.
- Security/RBAC lens: the detector remains fail-closed for real secret material and keeps runtime source guard stricter than receipt schema. Caller-provided release/write authority is not accepted.
- Agent architecture lens: this is a reusable safety primitive for future NIM contract shells; blocker codes and business state remain owned by each support class.
- Test architecture lens: direct helper tests lock down key normalization, recursive scanning, receipt-schema documented field-name compatibility, boolean/number compatibility, and strict runtime behavior. A source usage contract prevents the first migrated classes from reintroducing local blacklists.
- Documentation/learning lens: the main lesson is that deduplication in safety code must preserve policy differences explicitly. "One helper" should not mean "one flattened rule."

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorTest,NimForbiddenSecretMaterialDetectorUsageContractTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
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

The next safe migration slice can target more homogeneous classes that currently use the text-value policy:

- `NimCreateWriteRequestSpecAdapterSupport`
- `NimCreateWriteExecutionHandoffSupport`
- `NimCreateDurableWriteExecutorSupport`

Each follow-up should migrate a small group, keep blocker codes unchanged, and add usage contracts only for the classes actually migrated in that wave.
