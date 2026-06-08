# M5.21-133 NIM Release Decision Contract Maps Closed Audit - 2026-06-08

## Scope

This wave closes `releaseDecisionContract` as consumed by the NIM code release switch contract boundary.

The closed object includes:

- top-level release decision contract fields
- `validationResultBinding`
- `stateMachineBinding`
- `durableExecutorBinding`
- `allowPrerequisites`
- `currentTemplate`
- `failureContract`
- `forbiddenShortcuts`
- `requiredFutureEvidenceDigestFields`

This remains HOLD/mock-first protocol hardening. It does not issue a real release decision or open a real code release switch.

## Why This Matters

`releaseDecisionContractDigest` only proves that a contract object was hashed. It does not prove that every nested key is reviewed authority.

A forged-but-digest-consistent report can append a new future key, recompute `releaseDecisionContractDigest`, and still satisfy any validator that only checks a subset of known fields. That is dangerous at the code release switch boundary because this boundary is close to future write execution.

The safer design is producer ownership: the release decision producer builds the canonical contract, and the code release switch accepts only exact equality with that canonical object.

## Implementation

- `NimCreateDurableAuditReleaseDecisionContractSupport`
  - Emits `sourceOrganizationId`, `sourceUserId`, and `sourceUsername` on the release decision report.
  - Adds package-private `releaseDecisionContractFromReport(...)`.
  - Refactors contract construction through a shared digest-aware builder so producer output and downstream canonical reconstruction use the same shape.

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport`
  - Replaces partial nested-map validation for `releaseDecisionContract`.
  - Requires source audit digest, trusted principal digest, source identity fields, source proof digests, and exact canonical contract equality.
  - Removes dead local validators that reinterpreted upstream maps field by field.

## Regression Tests

- `NimCreateDurableAuditReleaseDecisionContractSupportTest`
  - Positive path asserts source identity fields are emitted.
  - Positive path asserts `releaseDecisionContract` equals `releaseDecisionContractFromReport(report)`.

- `NimCreateDurableAuditCodeReleaseSwitchContractSupportTest`
  - Adds a reusable digest-consistent mutation helper.
  - Forges contract drift, recomputes `releaseDecisionContractDigest`, and still expects fail-closed rejection for:
    - top-level extra key
    - `validationResultBinding` extra key
    - `stateMachineBinding` extra key
    - `durableExecutorBinding` extra key
    - `allowPrerequisites` value drift
    - `currentTemplate` extra key
    - `failureContract` extra key
    - `forbiddenShortcuts` list drift
    - existing extra future evidence field drift

## Verification

```powershell
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditReleaseDecisionContractSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest" test
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

The main lesson is that whole producer-owned proof object equality beats downstream hand interpretation.

A digest-consistent superset can pass hash recomputation, so downstream consumers should not ask only "do the fields I currently know about look correct?" Near a release/write boundary, the better question is "is this exactly the contract the producer's reviewed code knows how to issue?"

## Next Recommended Slice

Continue scanning release-adjacent proof objects for partial nested-map interpretation. The next high-value area is validation-result evidence and release-decision downstream maps that are still checked as local field subsets instead of source-owned canonical proof objects.
