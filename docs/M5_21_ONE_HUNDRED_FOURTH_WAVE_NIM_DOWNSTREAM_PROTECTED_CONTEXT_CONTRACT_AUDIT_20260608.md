# M5.21-104 NIM Downstream Protected Context Contract Audit - 2026-06-08

## Scope

This wave extends the shared NIM write-chain protected-context detector into downstream body contract validators:

- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`
- `src/test/java/com/atlas/tool/impl/NimProtectedContextDetectorUsageContractTest.java`

It does not scan handoff evidence containers such as idempotency, audit receipt handoff, or readiness handoff. Those are expected evidence channels. The hardened scope is specifically DeploymentDTO body copies carried by body reports, request specs, and execution attempt specs.

## What Changed

- Downstream `writeBodyContractValid(...)` validators now include `!NimProtectedContextDetector.containsProtectedContext(body)`.
- The state machine rejects digest-consistent body/request-spec reports whose body carries nested protected context.
- The execution handoff rejects digest-consistent body/request-spec inputs whose body carries nested protected context.
- The durable executor contract shell rejects digest-consistent request-spec and handoff evidence whose request body carries nested protected context.
- The usage contract now requires state machine, handoff, and durable executor support classes to keep using the shared protected-context detector for write body validation.

## Why This Matters

M5.21-103 hardened the earliest write-body and request-spec compilation boundaries. M5.21-104 hardens the downstream reviewers that consume those reports.

This matters because a future bug or malicious test fixture could forge a digest-consistent intermediate report. Downstream layers should still fail closed if the embedded DeploymentDTO body contains nested tenant identity, audit/HITL evidence, readiness evidence, or request-spec reports.

## Multi-Expert Review Notes

- Backend/API lens: no controller, HTTP client, kube-manager `8100` call, storage, or deployment POST was added.
- Security/RBAC lens: the body contract now has defense in depth across state machine, handoff, and durable executor shell.
- Agent architecture lens: each boundary independently verifies the same body safety invariant instead of assuming the previous component was honest.
- Test architecture lens: regressions recompute body/request/handoff digests so the failure proves protected-context validation, not accidental digest mismatch.
- Learning lens: strong Agent systems treat reports as evidence to verify, not truth to trust. Digest binding and content policy both matter.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateStateMachineSupportTest,NimProtectedContextDetectorUsageContractTest" test
mvn -q "-Dtest=NimProtectedContextDetectorUsageContractTest" test
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

- Continue hardening downstream report validators only where the embedded structure is a DeploymentDTO body.
- Avoid merging protected-context body validation with Tool-parameter filtering or secret-material detection; they are separate proof boundaries.
