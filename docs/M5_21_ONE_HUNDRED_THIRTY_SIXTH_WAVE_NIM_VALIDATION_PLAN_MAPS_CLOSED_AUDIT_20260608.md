# M5.21-136 NIM validation plan maps closed audit

## Summary

This wave closes the `validationPlan` proof object produced by `NimCreateDurableAuditReceiptValidationGateSupport`.

Before this wave, downstream consumers accepted a digest-consistent `validationPlan` and then locally interpreted selected nested fields. That made the plan vulnerable to digest-consistent semantic drift: a caller or future code path could append an authority-shaped field, recompute `validationPlanDigest`, and still pass if the current consumer did not inspect that new field.

After this wave, the validation plan is producer-owned:

- `NimCreateDurableAuditReceiptValidationGateSupport` emits `sourceOrganizationId`, `sourceUserId`, and `sourceUsername`.
- `NimCreateDurableAuditReceiptValidationGateSupport.validationPlanFromReport(...)` reconstructs the canonical whole `validationPlan` from report-level source fields.
- `NimCreateDurableAuditValidationResultMigrationSupport` requires exact equality with that canonical plan.
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` requires exact equality with that canonical plan.

## Changed Files

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest.java`

## Contract Boundary Closed

Closed proof object:

- `validationPlan`

Canonical producer helper:

- `NimCreateDurableAuditReceiptValidationGateSupport.validationPlanFromReport(...)`

Report-level reconstruction fields:

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

- `trustedIdentityBinding`
- `validationSequence`
- `requiredEvidence`
- `requiredEvidence.storageProbeReceipt`
- `requiredEvidence.preWriteDurableAck`
- `requiredEvidence.postWriteDurableAck`
- `requiredEvidence.durableReceipt`
- `releaseDecisionTemplate`
- `failureContract`
- `forbiddenShortcuts`

## Consumer Changes

`NimCreateDurableAuditValidationResultMigrationSupport` now validates:

- validation gate report status and HOLD boundary
- `validationPlanDigest == digestFor(validationPlan)`
- source digest fields match the validation plan
- source audit digest matches the current audit context
- source identity fields match the current audit/principal
- `validationPlan.equals(NimCreateDurableAuditReceiptValidationGateSupport.validationPlanFromReport(validationGateReport))`

`NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` applies the same exact canonical validation before accepting the validation gate report for probe-result binding.

## Tests Added

Positive producer test:

- Gate report now asserts `sourceOrganizationId`, `sourceUserId`, and `sourceUsername`.
- Gate report now asserts `validationPlan` equals `validationPlanFromReport(report)`.

Digest-consistent drift tests:

- top-level `validationPlan` extra key
- `trustedIdentityBinding` extra key
- `requiredEvidence` extra key
- `requiredEvidence.storageProbeReceipt` extra key
- `requiredEvidence.preWriteDurableAck` extra key
- `requiredEvidence.postWriteDurableAck` extra key
- `requiredEvidence.durableReceipt` extra key
- `validationSequence` list drift
- `releaseDecisionTemplate` extra key
- `failureContract` extra key
- `forbiddenShortcuts` list drift

Each forged input recomputes `validationPlanDigest`, proving rejection is based on closed producer-owned semantics rather than stale digest mismatch.

## Verification

Commands run:

```powershell
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationGateSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest" test
git diff --check
```

Result:

- Targeted tests passed.
- `git diff --check` passed.

## Security Invariant

This wave does not add:

- real `8100` writes or reads
- real NIM service HTTP calls
- Authorization header sending
- durable audit writes
- deployment POSTs
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

`validationPlanDigest` proves object integrity, not semantic approval. If a downstream consumer accepts a digest-consistent superset, then a new unreviewed key can become future release authority.

The stronger pattern is:

1. The producer owns the full shape.
2. The producer exposes a canonical reconstruction helper.
3. Every current consumer accepts only exact equality with that helper.
4. Tests forge digest-consistent drift and require fail-closed rejection.

This turns a JSON-like map into a reviewed protocol object.
