# M5.21-77 NIM runtime source guard binding static contract audit

> Scope: this wave adds a source-level regression contract for the M5.21-76 source-guard binding. It does not change runtime behavior, call kube-manager `8100`, bind an HTTP client, or execute any write.

## Background

M5.21-76 made the M5.21-75 `codeReleaseSwitchRuntimeSourceGuardReport` mandatory for both current shells:

- `NimCreateStateMachineSupport`
- `NimCreateDurableWriteExecutorSupport`

The next failure mode to guard against is future code drift. A later edit could accidentally remove the report field, skip validation, stop checking the source guard digest, or treat source guard success as write-release permission. M5.21-77 catches those regressions at source level.

## Delivered Changes

- Added `M521NimRuntimeSourceGuardBindingContractTest`.
- The contract reads production source files only.
- It asserts the state machine still:
  - declares `codeReleaseSwitchRuntimeSourceGuardReport`
  - calls `validateCodeReleaseSwitchRuntimeSourceGuardReport`
  - emits `codeReleaseSwitchRuntimeSourceGuardReportRequired=true`
  - emits `codeReleaseSwitchRuntimeSourceGuardAcceptedForRelease=false`
  - keeps source guard / backend readback / storage backfill release flags false
  - cross-checks durable executor `sourceGuardMatrixDigest`
  - exposes fail-closed blocker codes for missing, invalid, forged-release, and secret-bearing source guard reports
- It asserts the durable executor still:
  - reads `safeInput.codeReleaseSwitchRuntimeSourceGuardReport()`
  - calls `validateCodeReleaseSwitchRuntimeSourceGuardReport`
  - scans the source guard report for secret material
  - emits `codeReleaseSwitchRuntimeSourceGuardReportRequired=true`
  - recomputes `sourceGuardMatrixDigest`
  - binds the same M5.21-72 switch contract digest and audit-context digest
  - keeps source guard / backend readback / storage backfill release flags false
  - keeps legal shell HOLD blockers instead of release permission
- It statically rejects runtime shortcuts:
  - `System.getenv`
  - `System.getProperty`
  - `@Value`
  - Spring component/controller/bean injection
  - real HTTP client binding
  - Elasticsearch / `ISysLogService` / `sys_log`
  - kube-manager `8100`
  - any direct `result.put(..., true)` for write/release success fields

## Security Boundary

No production code path changed in this wave. The static contract strengthens existing boundaries:

- no real `8100`
- no real `POST /api/{orgId}/deployment`
- no HTTP client
- no Spring registration
- no storage/sys_log/ES side effect
- no write-success true flag

## Verification

Passed:

```bash
mvn -q "-Dtest=M521NimRuntimeSourceGuardBindingContractTest" test
mvn -q "-Dtest=M521NimRuntimeSourceGuardBindingContractTest,M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest,NimCreateDurableWriteExecutorSupportTest,NimCreateStateMachineSupportTest" test
git diff --check
```

## Learning Note

Source-level contracts are useful when the dangerous regression is simple to search for but expensive to discover at runtime. Here the contract teaches a durable rule: the source guard report must stay mandatory and fail-closed, and no static/runtime shortcut may convert it into write permission.
