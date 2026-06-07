# M5.21-74 NIM code release switch contract report binding audit

> Scope: this wave wires the M5.21-72 `codeReleaseSwitchContractReport` back into `NimCreateStateMachineSupport` and `NimCreateDurableWriteExecutorSupport` as fail-closed contract evidence. It does not create a real code switch, open `nim_create`, call kube-manager `8100`, or execute `POST /api/{orgId}/deployment`.

## Delivered Changes

- Updated `NimCreateStateMachineSupport`.
  - Added `codeReleaseSwitchContractReport` to `ReadinessRequest`.
  - Recomputes the M5.21-72 `codeReleaseSwitchContractDigest`.
  - Rejects missing, tampered, forged-open, or secret-bearing switch reports.
  - Keeps a valid report as shape evidence only with an implementation HOLD blocker.
- Updated `NimCreateDurableWriteExecutorSupport`.
  - Added `codeReleaseSwitchContractReport` to `WriteExecutionInput`.
  - Requires the same switch contract report before accepting handoff/request-spec input.
  - Exposes `codeReleaseSwitchContractReportRequired=true` and `sourceCodeReleaseSwitchContractDigest`.
- Updated tests:
  - `NimCreateStateMachineSupportTest`
  - `NimCreateDurableWriteExecutorSupportTest`

## Security Boundary

M5.21-74 keeps all write-release flags false:

- `writePermitted=false`
- `writeExecutionAllowed=false`
- `realHttpExecutionAllowed=false`
- `realStorageTouched=false`
- `codeReleaseSwitchDigestVerified=false`
- `releaseDecisionDigestVerified=false`
- `validationResultDigestVerified=false`
- `writeAttempted=false`
- `writeExecuted=false`
- `postWriteReadinessTriggered=false`

No production side-effect boundary was added:

- no HTTP client
- no Spring Bean, Controller, or Tool registration
- no Elasticsearch
- no `ISysLogService`
- no `sys_log` write
- no real kube-manager `8100`
- no real `POST /api/{orgId}/deployment`

## Expert Notes

- Backend/API: the future write endpoint remains only `POST /api/{orgId}/deployment`; this wave does not bind or execute a client.
- Security/RBAC: `nimCreateReleased=true`, environment flags, runtime toggles, state-machine `writePermitted`, executor success, and caller-provided switch claims remain non-authoritative.
- Agent architecture: the state machine and durable executor now both require the same switch contract digest instead of trusting each other by boolean.
- Test architecture: tests build the real M5.21-52..72 evidence chain and assert missing/tampered/forged switch evidence fails closed.
- Learning: a top-tier Agent write path should not have a single magic release boolean. It should require digest-bound, independently rechecked release facts.

## Verification

Targeted verification passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
```

Required final verification for closure:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
mvn -q test
git diff --check
```

## Next Step

The next safe slice is to define a reviewed runtime switch acceptance/source-guard matrix or continue toward a real dedicated durable audit writer boundary. `nim_create` must remain HOLD until trusted policy, durable audit writer, durable write executor, post-write readiness, release decision, and code release switch runtime binding all have reviewed real implementations.
