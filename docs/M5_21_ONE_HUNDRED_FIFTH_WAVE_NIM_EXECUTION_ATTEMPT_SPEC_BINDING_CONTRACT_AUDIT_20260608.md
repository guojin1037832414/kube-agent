# M5.21-105 NIM Execution Attempt Spec Binding Contract Audit - 2026-06-08

## Scope

This wave hardens the durable write executor contract shell and the state-machine validator around `executionAttemptSpec`.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`

## What Changed

- `executionAttemptSpec` now carries value-copied `requestSpec`, `body`, and `executionHandoffPlan`.
- `NimCreateDurableWriteExecutorSupport` now emits `executionAttemptSpecDigestAlgorithm` and `executionAttemptSpecDigest`.
- The state machine now recomputes the attempt-spec digest and verifies the copied request/body/handoff content matches upstream evidence.
- Request specs, handoff plans, idempotency handoff, pre-write audit handoff, post-write readiness handoff, retry policy, and attempt specs now have closed key-set contracts.
- The attempt-spec body is revalidated with the same DeploymentDTO body contract, including nested protected-context rejection.

## Why This Matters

Before this wave, the durable executor shell and state machine strongly bound digest strings, but `executionAttemptSpec` was still mostly a scalar digest index. That is safe while the executor is a HOLD shell, but a future implementation could accidentally consume extra or drifted fields from the attempt spec.

The new contract makes the future execution input explicit:

- exact shape
- value-copied evidence
- digest over the full attempt mirror
- request/body/handoff equality checks at the state-machine boundary

This teaches a core Agent write-path pattern: do not let a future executor interpret an open parameter bag. Define the exact evidence shape, copy it by value, then verify content and digest at the next boundary.

## Multi-Expert Review Notes

- Backend/API lens: still no HTTP client wiring, no `8100`, and no deployment POST.
- Security/RBAC lens: extra fields cannot smuggle authority/context through request spec, handoff plan, or attempt spec maps.
- Agent architecture lens: execution input is now a closed proof object rather than a loose collection of digest references.
- Test architecture lens: forged regressions recompute affected digests, so rejection proves closed-shape/content validation rather than accidental digest mismatch.
- Learning lens: closed schemas and value-copy boundaries are easier to reason about than permissive maps in high-risk Agent write paths.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest,NimCreateStateMachineSupportTest" test
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

- Bind and re-derive the durable executor idempotency key from audit context, audit receipt, and request spec digest inside the durable executor validator.
- Consider extracting closed-shape validators into a shared write-chain schema helper only after another wave proves the duplication is creating real maintenance cost.
- Continue release-binding proof design without opening writes.
