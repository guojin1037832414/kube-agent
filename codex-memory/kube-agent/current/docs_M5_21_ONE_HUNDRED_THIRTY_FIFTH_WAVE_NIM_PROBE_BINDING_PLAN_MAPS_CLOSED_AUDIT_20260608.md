# M5.21-135 NIM Probe Binding Plan Maps Closed Audit - 2026-06-08

## Scope

This wave closes `bindingPlan` as consumed by the NIM validation-result probe-binding migration boundary.

The closed object includes:

- top-level probe binding plan fields
- `trustedIdentityBinding`
- `requiredBindingEvidence`
- nested `storageProbeResultContract`
- nested `receiptValidationGate`
- nested `futureStorageProbeReceipt`
- `validationSequencePatch`
- `currentDecisionTemplate`
- `failureContract`
- `forbiddenShortcuts`

This remains HOLD/mock-first protocol hardening. It does not create a real probe result, validation result, release decision, or write-release credential.

## Why This Matters

`bindingPlanDigest` proves that a binding-plan object was hashed. It does not prove that every key inside the plan has reviewed protocol meaning.

A forged-but-digest-consistent report can append a future authority-shaped field, recompute `bindingPlanDigest`, and pass any downstream validator that checks only known nested fields. That is risky because M5.21-68 `bindingPlan` is the bridge that makes validation-result migration depend on storage probe result binding.

The safer design is producer ownership: the probe-result binding producer builds the canonical `bindingPlan`, and the probe-binding migration consumer accepts only exact equality with that canonical object.

## Implementation

- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport`
  - Emits `sourceOrganizationId`, `sourceUserId`, and `sourceUsername` on the binding report.
  - Adds package-private `bindingPlanFromReport(...)`.
  - Refactors binding plan construction through a shared digest-aware builder so producer output and downstream canonical reconstruction use the same shape.

- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`
  - Replaces partial nested-map validation for `bindingPlan`.
  - Requires source digest fields, source audit digest, trusted source identity fields, and exact canonical binding plan equality.
  - Removes the downstream hand-interpretation of `trustedIdentityBinding`, `requiredBindingEvidence`, nested evidence maps, and `failureContract`.

## Regression Tests

- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest`
  - Positive path asserts source identity fields are emitted.
  - Positive path asserts `bindingPlan` equals `bindingPlanFromReport(report)`.

- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest`
  - Adds a reusable digest-consistent mutation helper.
  - Forges binding-plan drift, recomputes `bindingPlanDigest`, and still expects fail-closed rejection for:
    - top-level extra key
    - `trustedIdentityBinding` extra key
    - `requiredBindingEvidence` extra key
    - nested `storageProbeResultContract` extra key
    - `currentDecisionTemplate` extra key
    - `failureContract` extra key
    - `forbiddenShortcuts` list drift

## Verification

```powershell
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest,NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest,NimCreateDurableAuditValidationResultMigrationSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest" test
git diff --check
mvn -q test
```

All commands passed.

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariant

- `nim_create` remains HOLD/mock-first.
- No real `8100` call was made.
- No real NIM service HTTP call was added.
- No Authorization header sending was added.
- No durable audit write was added.
- No deployment POST was added.
- No validation result signer, release decision signer, code release switch implementation, runtime source guard installation, Elasticsearch, `ISysLogService`, or `sys_log` write was added.

## Teaching Note

The main lesson is that an intermediate proof bridge is still a protocol object, not metadata.

M5.21-68 `bindingPlan` is not the final validation result, but it shapes whether future validation result migration is allowed to depend on storage probe evidence. If downstream code hand-interprets only selected nested fields, then a digest-consistent superset can quietly introduce new migration authority. Producer-owned exact equality keeps that authority visible in code review, tests, and docs.

## Next Recommended Slice

Continue scanning validation-result probe-binding migration and earlier receipt-validation inputs for remaining local map interpretation. Good candidates are `validationPlan` and `probeResultContract` consumers that still validate nested maps field by field instead of using producer-owned canonical reconstruction helpers.
