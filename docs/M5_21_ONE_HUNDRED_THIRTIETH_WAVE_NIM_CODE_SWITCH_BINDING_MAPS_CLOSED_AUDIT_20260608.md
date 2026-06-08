# M5.21-130 NIM Code Switch Binding Maps Closed Audit - 2026-06-08

## Scope

This wave closes code release switch binding maps emitted by the M5.21-72 proof object and consumed by current downstream boundaries:

- `codeReleaseSwitchContract.releaseDecisionBinding`
- `codeReleaseSwitchContract.stateMachineBinding`
- `codeReleaseSwitchContract.durableExecutorBinding`

Current consumers now require producer-owned exact binding maps:

- `NimCreateStateMachineSupport`
- `NimCreateDurableWriteExecutorSupport`
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now exposes source-owned helper maps:
  - `codeReleaseSwitchReleaseDecisionBinding(...)`
  - `codeReleaseSwitchReleaseDecisionBindingFromSwitchReport(...)`
  - `codeReleaseSwitchStateMachineBinding()`
  - `codeReleaseSwitchDurableExecutorBinding()`
- The code switch producer now delegates its private binding builders to those helper maps.
- State-machine and durable-executor consumers now require exact equality for the relevant code-switch binding maps.
- Runtime binding validation now requires exact equality for release-decision, state-machine, and durable-executor binding maps.
- Added digest-consistent forged binding-map regressions that append authority-shaped extra keys, recompute `codeReleaseSwitchContractDigest`, and still expect fail-closed rejection.

## Why This Matters

Binding maps are not ordinary metadata. They explain how a future code release switch must connect release decisions, validation results, state-machine release, and durable executor execution. If a caller or unreviewed integration can append fields such as `fallbackToStateMachineWritePermittedAllowed`, that field might later be mistaken for a reviewed release path.

Digest consistency does not solve this class of bug. A mutated binding map can be re-hashed. The consumer must also prove that the binding map exactly matches the producer-owned protocol shape.

Chinese learning note:

- binding map 是组件之间的授权合同，不是说明性 JSON。
- release decision binding 里有动态 digest 字段，所以 exact 校验不能简单硬编码；需要能从上游 release decision report 或 code switch report 重建同一份标准 map。
- state machine 和 durable executor binding 即使当前只是 HOLD，也必须先关闭 key-set，避免未来写放行逻辑读取未审查字段。
- 顶级 Agent 的安全工程要避免“现在没用到所以没关系”的字段漂移，因为未来代码最容易从这些字段开始误用。

## Verification

Passed:

```bash
git diff --check
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest" test
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
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue closing runtime binding maps emitted by `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`.
- Review release decision contract binding maps and validation result evidence bindings for the same producer-owned exact-map pattern.
- Keep real durable audit writer, receipt validator, release signer, code switch implementation, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
