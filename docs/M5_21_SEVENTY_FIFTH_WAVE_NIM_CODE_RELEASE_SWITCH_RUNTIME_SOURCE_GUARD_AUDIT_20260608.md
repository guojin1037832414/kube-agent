# M5.21-75 NIM code release switch runtime source guard audit

> Scope: this wave defines a runtime source-guard matrix after M5.21-73. It does not open a real code switch, issue a release credential, call kube-manager `8100`, write storage, or execute `POST /api/{orgId}/deployment`.

## Background

M5.21-72 defines the code release switch value contract. M5.21-73 defines how the state machine and durable executor must bind that switch at runtime. M5.21-74 wires the M5.21-72 report into the current state-machine and executor shells.

The remaining risk is source confusion: a future implementation might accidentally treat a convenient source as an open switch, for example:

- `nimCreateReleased=true`
- caller/LLM JSON with `codeReleaseSwitchDigestVerified=true`
- environment variables or runtime flags
- state-machine `writePermitted=true`
- durable executor success or `deploymentId`
- backend readback/query results
- `sys_log` or Elasticsearch backfill rows
- M5.21-72/73 contract reports by themselves

M5.21-75 makes those source families machine-readable and testable.

## Delivered Changes

- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`.
  - Consumes the M5.21-73 runtime-binding report.
  - Recomputes `runtimeBindingContractDigest`.
  - Emits `sourceGuardMatrix` with accepted planning sources, forbidden release sources, and the only future authoritative source family.
  - Keeps `acceptedSourcesForCurrentRelease=[]`, `sourceGuardInstalled=false`, and every current write-release path closed.
- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest`.
  - Builds the real M5.21-52..73 evidence chain.
  - Rejects missing or tampered runtime-binding reports.
  - Rejects forged candidate source evidence.
  - Rejects backend readback and storage backfill as release sources.
  - Rejects secret-bearing inputs without echoing the secret.
- Added `M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest`.
  - Scans production source for future environment/property based release-switch shortcuts.
  - Asserts the M5.21-75 support class keeps dangerous source families non-authoritative.
  - Asserts the current state machine still treats `nimCreateReleased` as a legacy boolean that is not authoritative alone.

## Source Guard Matrix

Planning-only sources:

- `M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT`
- `M5.21-73_RUNTIME_BINDING_REPORT`

Forbidden release sources:

- `CALLER_PARAMS_OR_LLM_JSON`
- `ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG`
- `LEGACY_NIM_CREATE_RELEASED_BOOLEAN`
- `STATE_MACHINE_WRITE_PERMITTED_BOOLEAN`
- `DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID`
- `BACKEND_QUERY_OR_READBACK_RESULT`
- `SYS_LOG_OR_ELASTICSEARCH_BACKFILL`
- `RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY`

Future required authoritative source family:

- `REVIEWED_SERVER_OWNED_OPEN_SWITCH`

That future source still requires code review, test evidence, security approval, rollback plan, change window, release decision digest, validation result digest, controlled body/request/handoff digests, audit receipt id, and server-derived idempotency key.

Dangerous field names now tracked by the matrix:

- `nimCreateReleased`
- `codeReleaseSwitchContractReportAccepted`
- `codeReleaseSwitchContractReportAcceptedForRelease`
- `serverOwnedCodeReleaseSwitchAccepted`
- `realCodeReleaseSwitchOpened`
- `codeReleaseSwitchDigestVerified`
- `switchState`
- `codeReleaseSwitchStatus`
- `runtimeFlag`
- `runtimeReleaseFlag`
- `runtimeFlagOverrideAllowed`
- `runtimeToggleOverrideAllowed`
- `environmentOverride`
- `environmentReleaseOverride`
- `environmentVariableOverrideAllowed`
- `writePermitted`
- `writePermittedCanBeTrueNow`
- `writeExecutionAllowed`
- `writeExecutionAllowedNow`
- `releaseEligible`
- `releaseCredential`
- `releaseCredentialIssued`
- `releaseDecisionAccepted`
- `releaseDecisionDigestVerified`
- `validationResultDigestVerified`
- `stateMachineReleaseBound`
- `durableExecutorReleaseBound`
- `writeAttempted`
- `writeExecuted`
- `postWriteReadinessTriggered`
- `deploymentId`
- `deploymentUid`
- `writeResult`

## Security Boundary

M5.21-75 keeps all write-release flags false:

- `sourceGuardInstalled=false`
- `candidateSourceEvidenceAuthoritative=false`
- `callerParamSourceAllowed=false`
- `environmentVariableSourceAllowed=false`
- `runtimeFlagSourceAllowed=false`
- `stateMachineBooleanSourceAllowed=false`
- `durableExecutorSuccessSourceAllowed=false`
- `backendQuerySourceAllowedForRelease=false`
- `sysLogBackfillSourceAllowed=false`
- `acceptedSourcesForCurrentRelease=[]`

No production side-effect boundary was added:

- no HTTP client
- no Spring Bean, Controller, or Tool registration
- no Elasticsearch
- no `ISysLogService`
- no `sys_log` write
- no real kube-manager `8100`
- no real `POST /api/{orgId}/deployment`

## Expert Notes

- Backend/API: safe local `8100` query/read testing remains allowed for scoped read tools, but readback evidence cannot become a write-release source for `nim_create`.
- Security/RBAC: the future open switch must be server-owned and digest-bound. Caller-visible fields and operational toggles remain forgeable.
- Agent architecture: the matrix prevents a common Agent failure mode where a planner treats a diagnostic fact or prior step success as a permission credential.
- Test architecture: the new source-level test catches future env/property shortcuts before they become runtime behavior.
- Learning: top-tier write agents need not only gates, but also source taxonomy. A fact is not safe merely because it appears in the right shape.

## Verification

Targeted verification passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest,M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest" test
```

Final closure verification passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest,M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
mvn -q test
git diff --check
```

Static closure also passed:

- production-boundary scan on the new source-guard support class
- true/success/release shortcut scan on the new source-guard support class
- static secret scan; matches were existing authentication docs or test sentinels, not real credentials

Full test note: `model.onnx` download timed out and Atlas degraded to L1 embedding mode; Maven exited 0.

## Next Step

The next safe NIM slice is either to bind this M5.21-75 source-guard matrix into the current state-machine/executor shells as required HOLD evidence, or to continue toward a real dedicated durable audit writer/probe implementation. `nim_create` must remain HOLD until trusted policy, durable audit writer, durable write executor, post-write readiness, release decision, code switch, runtime binding, and source guard all have reviewed real implementations.
