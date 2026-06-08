# M5.21-106 NIM Durable Idempotency Derivation Binding Contract Audit - 2026-06-08

## Scope

This wave hardens the NIM write-chain idempotency proof across handoff generation, durable executor validation, and state-machine validation.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`

## What Changed

- `NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKey(...)` is now package-visible and reused as the canonical derivation rule.
- Added `serverDerivedIdempotencyKeyFromHandoffEvidence(...)` so downstream durable executor validation can recompute the same key from handoff source evidence plus request spec digest.
- `NimCreateDurableWriteExecutorSupport` now rejects a handoff report whose idempotency key is format-valid but not server-derived from the bound evidence.
- `NimCreateStateMachineSupport` now recomputes the idempotency key while validating handoff plans and execution attempt specs.
- Tests now forge digest-consistent reports with syntactically valid fake idempotency keys and assert they fail closed.

## Why This Matters

The previous contract checked that idempotency keys matched the expected prefix/length and were equal across report fields. That is useful, but it does not prove the key was derived from the intended audit/request evidence.

This wave upgrades the key from a trusted-looking string into a recomputable proof:

- request id
- conversation id
- user id
- organization id
- audit receipt id
- audit event digest
- request spec digest

All downstream validators can now prove the key is bound to the same evidence chain that produced the write request.

## Multi-Expert Review Notes

- Backend/API lens: no HTTP client, kube-manager `8100`, or deployment POST was added.
- Security/RBAC lens: a caller or forged report cannot pick an arbitrary valid-looking idempotency key.
- Agent architecture lens: idempotency becomes part of the durable write proof chain, not a loose retry string.
- Test architecture lens: forged tests recompute affected digests, proving rejection is from derivation mismatch.
- Learning lens: retry safety and authority safety meet at the idempotency key; strong Agents verify the derivation source.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateStateMachineSupportTest" test
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

- Further bind handoff audit receipt drift in durable executor validation.
- Continue release-binding proof design without opening writes.
- Consider a shared write-chain proof helper only if another wave shows real duplication pressure.
