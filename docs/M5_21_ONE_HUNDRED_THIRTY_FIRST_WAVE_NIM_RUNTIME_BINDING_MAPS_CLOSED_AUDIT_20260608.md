# M5.21-131 NIM Runtime Binding Maps Closed Audit - 2026-06-08

## Scope

This wave closes runtime binding maps emitted by the M5.21-73 runtime binding contract and consumed by the runtime source guard:

- `runtimeBindingContract.stateMachineRuntimeBinding`
- `runtimeBindingContract.durableExecutorRuntimeBinding`

The source guard now requires producer-owned exact binding maps from `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`.

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport` now exposes source-owned helper maps:
  - `codeReleaseSwitchStateMachineRuntimeBinding(...)`
  - `codeReleaseSwitchStateMachineRuntimeBindingFromRuntimeReport(...)`
  - `codeReleaseSwitchDurableExecutorRuntimeBinding(...)`
  - `codeReleaseSwitchDurableExecutorRuntimeBindingFromRuntimeReport(...)`
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` now validates both runtime binding maps with exact equality against those helpers.
- The runtime binding positive regression now asserts the emitted maps exactly equal the producer helpers.
- Added a digest-consistent forged runtime binding regression that appends authority-shaped fake keys, recomputes `runtimeBindingContractDigest`, and still expects source-guard rejection.

## Why This Matters

Runtime binding maps are the bridge between a reviewed code release switch contract and future runtime consumers. A source guard that only checks known fields can still accept a digest-consistent map with extra fields such as `fallbackToRuntimeBindingReportAcceptedAllowed`. Those fields might later be misread as reviewed runtime release authority.

Chinese learning note:

- runtime binding map 是 runtime source guard 的输入协议，不是普通说明字段。
- digest 只能证明对象被重新计算过，不能证明新 key 已经过安全评审。
- 当某个 map 会被下游边界用来判断“未来能否安装 runtime binding”时，必须由 producer 拥有完整 shape，由 consumer 做 exact validation。
- 顶级 Agent 的安全边界要提前防止“现在只是 HOLD，将来再说”的字段漂移。

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No durable audit table write.
- No Elasticsearch write.
- No `ISysLogService` or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No source guard installation added.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue scanning release decision contract binding maps and validation result evidence bindings for producer-owned exact-map validation.
- Review `runtimeBindingContract.currentRuntimeTemplate`, `failureContract`, and `forbiddenShortcuts` for the same source-owned helper pattern if future consumers start depending on them.
- Keep real durable audit writer, receipt validator, release signer, code switch implementation, runtime source guard installation, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
