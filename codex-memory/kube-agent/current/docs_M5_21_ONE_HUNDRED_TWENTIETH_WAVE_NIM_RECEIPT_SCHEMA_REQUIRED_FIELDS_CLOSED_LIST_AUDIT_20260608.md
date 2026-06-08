# M5.21-120 NIM Receipt Schema Required Fields Closed List Audit - 2026-06-08

## Scope

本轮加固 NIM durable audit typed schema 的 `requiredFields` 协议。目标是让 storage probe receipt、pre-write durable ack、post-write durable ack 和 final durable receipt 的必填字段列表成为源码拥有的闭表，而不是调用方可以扩展的 JSON 列表。

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupportTest.java`

## What Changed

- `NimCreateDurableAuditReceiptSchemaSupport` now owns closed helper lists:
  - `storageProbeRequiredFields()`
  - `durableAckRequiredFields(String requiredPreviousDigestField)`
  - `durableReceiptRequiredFields()`
- The receipt schema producer now emits those source-owned lists for:
  - `storageAvailabilityProbeReceiptSchema.requiredFields`
  - `preWriteDurableAckSchema.requiredFields`
  - `postWriteDurableAckSchema.requiredFields`
  - `durableAuditReceiptSchema.requiredFields`
- `NimCreateDurableAuditReceiptValidationGateSupport` now requires exact equality for all four nested `requiredFields` lists.
- `NimCreateDurableAuditStorageProbeResultSupport` now rejects storage probe schemas whose `requiredFields` list is a digest-consistent superset.

## Why This Matters

`requiredFields` 不是普通展示字段。它定义未来 durable audit receipt / ack 必须携带哪些证据字段，也可能成为后续 release gate、write executor 或 receipt signer 的安全判断依据。

如果只使用 `contains(...)` 或 `containsAll(...)`，攻击者或错误上游可以追加一个看起来像未来证据槽位的字段，再重新计算 schema digest。这样的对象在哈希上自洽，但协议语义已经漂移。顶级 Agent 的安全协议不能允许这种“自洽但未审查”的扩展。

本轮把这些列表改成闭表校验：新字段必须先经过源码实现、测试、文档和发布治理，不能由调用方 JSON 直接扩展。

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager controller, HTTP client, `8100` call, `sys_log` writer, Elasticsearch writer, or deployment POST was added.
- Frontend/product lens: this does not change `vue-kube-manager` NIM page behavior; it only hardens backend Agent future write-release evidence.
- Security/RBAC lens: digest consistency is not treated as authority. Extra receipt evidence slots are rejected even when the top-level schema digest is recomputed.
- Agent architecture lens: durable audit receipt schema is now a protocol contract, not caller-extensible metadata.
- Test architecture lens: regressions mutate nested `requiredFields`, recompute `schemaDigest`, and still expect downstream rejection.
- Learning lens: hashes prove object integrity after hashing. They do not prove that an object shape is reviewed. A top-tier Agent combines digest binding with closed schema validation.

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest" test
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch write.
- No `ISysLogService` or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue scanning remaining proof lists that still accept supersets.
- Consider a later wave for `NimCreateDurableAuditWriterInterfaceSpecSupport` request/response contract lists.
- Keep future real durable audit writer work inside the dedicated writer boundary, with storage probe, pre-write, post-write, and receipt issuance reviewed separately.
