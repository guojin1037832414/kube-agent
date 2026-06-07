# M5.21-76 NIM code release switch runtime source guard report binding audit

> Scope: this wave binds the M5.21-75 runtime source-guard report into the current state-machine and durable-executor shells. It does not open a real switch, issue a release credential, call kube-manager `8100`, write `sys_log`, or execute `POST /api/{orgId}/deployment`.

## Background

M5.21-75 defined a source-guard matrix that distinguishes planning evidence from release evidence. M5.21-76 makes that matrix mandatory input for the two current write-shell boundaries:

- `NimCreateStateMachineSupport`
- `NimCreateDurableWriteExecutorSupport`

The key invariant remains unchanged: a valid source-guard report is required guard evidence, not a release credential.

## Delivered Changes

- `NimCreateDurableWriteExecutorSupport`
  - Added `WriteExecutionInput.codeReleaseSwitchRuntimeSourceGuardReport`.
  - Validates the M5.21-75 report before accepting a controlled handoff/request-spec pair.
  - Recomputes `sourceGuardMatrixDigest` from `sourceGuardContract`.
  - Requires the source guard to bind the same M5.21-72 `codeReleaseSwitchContractDigest` and audit-context digest.
  - Emits `codeReleaseSwitchRuntimeSourceGuardReportRequired=true`.
  - Emits `sourceGuardMatrixDigest`, `sourceRuntimeBindingContractDigest`, `sourceGuardInstalled=false`, `candidateSourceEvidenceAuthoritative=false`, `backendQuerySourceAllowedForRelease=false`, and `sysLogBackfillSourceAllowed=false`.
  - Keeps legal input in `IMPLEMENTATION_HOLD` with two blocker codes:
    - `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`
    - `CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD`
- `NimCreateStateMachineSupport`
  - Added `ReadinessRequest.codeReleaseSwitchRuntimeSourceGuardReport`.
  - Validates the M5.21-75 report independently.
  - Validates that the durable executor report echoes the same source guard digest and runtime-binding digest.
  - Emits `codeReleaseSwitchRuntimeSourceGuardReportRequired=true`.
  - Emits `codeReleaseSwitchRuntimeSourceGuardAcceptedForRelease=false`.
  - Keeps legal full-shell evaluation `HELD` with three HOLD blockers:
    - `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`
    - `CODE_RELEASE_SWITCH_CONTRACT_REPORT_IMPLEMENTATION_HOLD`
    - `CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD`
- Tests
  - Extended `NimCreateDurableWriteExecutorSupportTest`.
  - Extended `NimCreateStateMachineSupportTest`.
  - Added coverage for missing source guard, tampered digest, forged source release claims, and secret-bearing source guard reports.

## Rejected Source Guard Shortcuts

Both shells fail closed when the source guard report is missing, malformed, digest-tampered, or claims release authority through any of these fields:

- `sourceGuardInstalled=true`
- `candidateSourceEvidenceAuthoritative=true`
- `callerParamSourceAllowed=true`
- `llmJsonSourceAllowed=true`
- `environmentVariableSourceAllowed=true`
- `runtimeFlagSourceAllowed=true`
- `stateMachineBooleanSourceAllowed=true`
- `durableExecutorSuccessSourceAllowed=true`
- `backendQuerySourceAllowedForRelease=true`
- `sysLogBackfillSourceAllowed=true`
- `releaseDecisionContractReportSourceAllowed=true`
- `validationResultContractReportSourceAllowed=true`
- non-empty `acceptedSourcesForCurrentRelease`
- `writePermitted=true`
- `writeExecutionAllowed=true`
- `writeExecuted=true`
- non-empty `deploymentId`, `deploymentUid`, or `writeResult`

## Security Boundary

M5.21-76 keeps all production side effects closed:

- no HTTP client
- no Spring Bean, Controller, or Tool registration
- no Elasticsearch
- no `ISysLogService`
- no `sys_log` write
- no kube-manager `8100` call
- no real deployment POST
- no `writePermitted=true`
- no `writeExecutionAllowed=true`
- no `realHttpExecutionAllowed=true`
- no `writeAttempted=true`
- no `writeExecuted=true`
- no `postWriteReadinessTriggered=true`

`POST /api/{orgId}/deployment` remains only a documented future side-effect string/path template. `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## Expert Notes

- Backend/API: safe local `8100` read/query tests remain allowed for scoped read tools, but not as write-release authorization for `nim_create`.
- Security/RBAC: the state machine and durable executor both re-check source guard evidence. No caller-visible field or runtime flag can open the switch.
- Agent architecture: this wave prevents an Agent planner from confusing "a valid-looking report" with "a valid release source".
- Test architecture: the legal executor shell now has `inputAccepted=true` and two HOLD blockers. Invalid source guard input is `REJECTED`.
- Learning: source validation and value validation are separate. A top-tier Agent needs both, plus digest binding at every boundary.

## Verification

Targeted verification passed:

```bash
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest" test
mvn -q "-Dtest=NimCreateStateMachineSupportTest" test
mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest,NimCreateStateMachineSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest,M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
```

Final closure verification passed:

```bash
mvn -q test
git diff --check
```

Static boundary scans passed on:

- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`

Only expected context strings remained: a future implementation note mentioning `KubeManagerHttpClient`, and documented future `POST /api/{orgId}/deployment` text. No active real network, Spring, `8100`, storage, `sys_log`, or success-true shortcut was introduced.

Full test note: `model.onnx` download timed out and Atlas degraded to L1 embedding mode during full Maven test execution; Maven exited 0.

## Next Step

The next safe NIM slice should continue toward reviewed real implementation boundaries without opening writes yet. Good candidates are:

- strengthen static source-level contract tests for the M5.21-76 binding, or
- continue a dedicated durable audit writer/probe implementation.

Do not open `nim_create` until trusted policy, durable audit writer, durable write executor, post-write readiness, release decision, code switch, runtime binding, and source guard all have reviewed real implementations.
