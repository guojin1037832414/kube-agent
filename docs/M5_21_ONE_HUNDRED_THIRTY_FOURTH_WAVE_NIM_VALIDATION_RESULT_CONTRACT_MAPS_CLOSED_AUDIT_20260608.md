# M5.21-134 NIM Validation Result Contract Maps Closed Audit - 2026-06-08

## Scope

This wave closes `validationResultContract` as consumed by the NIM release decision contract boundary.

The closed object includes:

- top-level validation result contract fields
- `trustedIdentityBinding`
- `evidenceBinding`
- `currentTemplate`
- `passPrerequisites`
- `failureContract`
- `forbiddenShortcuts`
- `requiredFutureEvidenceDigestFields`

This remains HOLD/mock-first protocol hardening. It does not create a real validation result or issue a real release decision.

## Why This Matters

`validationResultContractDigest` only proves that the contract object was hashed. It does not prove that every nested key is reviewed validation authority.

A forged-but-digest-consistent report can append a new future key, recompute `validationResultContractDigest`, and still satisfy a validator that only checks a subset of known fields. That is dangerous at the release decision boundary because release decision is the next proof layer toward future write execution.

The safer design is producer ownership: the validation result producer builds the canonical contract, and the release decision consumer accepts only exact equality with that canonical object.

## Implementation

- `NimCreateDurableAuditReceiptValidationResultSupport`
  - Emits `sourceOrganizationId`, `sourceUserId`, and `sourceUsername` on the validation result report.
  - Adds package-private `validationResultContractFromReport(...)`.
  - Refactors contract construction through a shared digest-aware builder so producer output and downstream canonical reconstruction use the same shape.
  - Rebuilds `evidenceBinding` from source digests plus trusted principal digest instead of letting downstream consumers reinterpret that nested map.

- `NimCreateDurableAuditReleaseDecisionContractSupport`
  - Replaces partial nested-map validation for `validationResultContract`.
  - Requires source audit digest, trusted principal digest, source identity fields, source proof digests, and exact canonical contract equality.
  - Removes dead local validators that reinterpreted upstream validation result maps field by field.

## Regression Tests

- `NimCreateDurableAuditReceiptValidationResultSupportTest`
  - Positive path asserts source identity fields are emitted.
  - Positive path asserts `validationResultContract` equals `validationResultContractFromReport(report)`.

- `NimCreateDurableAuditReleaseDecisionContractSupportTest`
  - Adds a reusable digest-consistent mutation helper.
  - Forges contract drift, recomputes `validationResultContractDigest`, and still expects fail-closed rejection for:
    - top-level extra key
    - `trustedIdentityBinding` extra key
    - `evidenceBinding` extra key
    - `currentTemplate` extra key
    - `passPrerequisites` value drift
    - `failureContract` extra key
    - `forbiddenShortcuts` list drift
    - existing extra future evidence field drift

## Verification

```powershell
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationResultSupportTest,NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditReleaseDecisionGateSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
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

The main lesson is that hash self-consistency is not semantic authority.

A validation result contract sits directly upstream of release decision. If the release decision layer hand-interprets only the fields it currently understands, then a digest-consistent superset can quietly introduce future validation authority. The release decision layer should instead ask: "is this exactly the validation result contract that the producer's reviewed code knows how to issue?"

## Next Recommended Slice

Continue scanning release-adjacent proof maps and validation result / migration plan local hand-interpretation points. Good next candidates are places where downstream code still accepts non-empty maps, required subset fields, or `contains(...)` checks instead of producer-owned exact equality.
