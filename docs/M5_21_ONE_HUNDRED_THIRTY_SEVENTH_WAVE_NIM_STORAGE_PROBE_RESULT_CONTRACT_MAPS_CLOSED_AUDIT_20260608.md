# M5.21-137 NIM storage probe result contract maps closed audit

## Summary

This wave closes the `probeResultContract` proof object produced by `NimCreateDurableAuditStorageProbeResultSupport`.

Before this wave, `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` verified `probeResultContractDigest`, then locally interpreted selected nested fields in `probeResultContract`. That left a subtle drift path: a caller-shaped report could append authority-looking keys, recompute `probeResultContractDigest`, and pass any consumer that only checked the fields it already knew.

After this wave, the storage probe result contract is producer-owned:

- `NimCreateDurableAuditStorageProbeResultSupport` emits `sourceOrganizationId`, `sourceUserId`, and `sourceUsername`.
- `NimCreateDurableAuditStorageProbeResultSupport.probeResultContractFromReport(...)` reconstructs the canonical whole `probeResultContract` from report-level source fields.
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` requires exact equality with that canonical contract.

## Changed Files

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest.java`

## Contract Boundary Closed

Closed proof object:

- `probeResultContract`

Canonical producer helper:

- `NimCreateDurableAuditStorageProbeResultSupport.probeResultContractFromReport(...)`

Report-level reconstruction fields:

- `sourceProbeExecutorPlanDigest`
- `sourceReceiptSchemaDigest`
- `sourceInterfaceSpecDigest`
- `sourceBoundaryPlanDigest`
- `sourceWriterPlanDigest`
- `sourceAvailabilityPlanDigest`
- `sourceAuditEventDigest`
- `sourceOrganizationId`
- `sourceUserId`
- `sourceUsername`

Canonical nested maps and lists now owned by the producer:

- `evidenceBinding`
- `trustedIdentityBinding`
- `requiredFutureFields`
- `currentTemplate`
- `passPrerequisites`
- `failureModel`
- `failureModel.failureStatuses`

## Consumer Changes

`NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` now validates:

- storage probe result report status and HOLD boundary
- `probeResultContractDigest == digestFor(probeResultContract)`
- source digest fields match the contract evidence binding
- source audit digest matches the current audit context
- source identity fields match the current audit/principal
- `probeResultContract.equals(NimCreateDurableAuditStorageProbeResultSupport.probeResultContractFromReport(probeResultReport))`

The consumer still keeps report-level HOLD, no-side-effect, and no-forged-success checks. The important change is that nested contract semantics are no longer redefined by the consumer.

## Tests Added

Positive producer test:

- Storage probe result report now asserts `sourceOrganizationId`, `sourceUserId`, and `sourceUsername`.
- Storage probe result report now asserts `probeResultContract` equals `probeResultContractFromReport(report)`.

Digest-consistent drift tests:

- top-level `probeResultContract` extra key
- `evidenceBinding` extra key
- `trustedIdentityBinding` extra key
- `requiredFutureFields` list drift
- `currentTemplate` extra key
- `passPrerequisites` extra key
- `failureModel` extra key
- `failureModel.failureStatuses` list drift

Each forged input recomputes `probeResultContractDigest`, proving rejection is based on closed producer-owned semantics rather than stale digest mismatch.

## Verification

Commands run:

```powershell
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest" test
git diff --check
mvn -q test
```

Result:

- Targeted tests passed.
- `git diff --check` passed.
- Full Maven passed. The full test run still logged the known local `model.onnx` download timeout and degraded to L1 embedding mode, but Maven exited 0.

## Security Invariant

This wave does not add:

- real `8100` reads or writes
- real NIM service HTTP calls
- Authorization header sending
- durable audit writes
- deployment POSTs
- real storage probe result issuance
- real storage probe receipt issuance
- real receipt validator
- real validation result signer
- real release decision signer
- code release switch implementation
- runtime write behavior
- Elasticsearch access
- `ISysLogService`
- `sys_log` writes

`nim_create` remains HOLD/mock-first.

## Teaching Note

`probeResultContractDigest` proves that a specific contract object was hashed consistently. It does not prove that every key inside that object is reviewed authority.

The stronger Agent engineering pattern is:

1. The producer owns the full proof-object shape.
2. The producer exposes a canonical reconstruction helper.
3. The consumer verifies source digest and identity anchors.
4. The consumer accepts only exact equality with the producer-owned helper.
5. Tests mutate the proof object, recompute the digest, and still require fail-closed rejection.

中文学习总结：在顶级 Agent 的写放行链路里，JSON Map 不能被当成“说明文字”。只要一个 Map 会被下游用来决定未来能不能继续靠近写执行，它就是协议对象。协议对象必须由生产者拥有完整形状，由消费者做精确等值校验；否则今天看似无害的扩展字段，明天就可能变成绕过审查的隐式授权。
