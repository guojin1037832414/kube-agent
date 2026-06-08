# M5.21-129 NIM Code Switch Template/Prerequisites Closed Audit - 2026-06-08

## Scope

This wave closes two structured maps inside the M5.21-72 code release switch proof object:

- `codeReleaseSwitchContract.currentTemplate`
- `codeReleaseSwitchContract.openPrerequisites`

Current downstream consumers now validate the exact producer-owned maps:

- `NimCreateStateMachineSupport`
- `NimCreateDurableWriteExecutorSupport`
- `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest.java`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now exposes producer-owned helper maps:
  - `codeReleaseSwitchCurrentTemplate()`
  - `codeReleaseSwitchOpenPrerequisites()`
- The internal `currentSwitchTemplate()` and `openPrerequisites()` builders delegate to those helpers.
- The state machine, durable executor, and runtime binding contract validation now require exact equality for both maps.
- The producer test now asserts the emitted maps exactly match the helper maps.
- Added digest-consistent forged nested-map regressions. The tests append fake future release-shaped keys into `currentTemplate` or `openPrerequisites`, recompute `codeReleaseSwitchContractDigest`, and still expect fail-closed rejection.

## Why This Matters

`currentTemplate` and `openPrerequisites` look like normal nested maps, but they sit on the write-release path. A future engineer might add fields such as `writePermittedApprovedByTemplate` or `durableExecutorRecheckWaived` and accidentally teach a downstream component to treat those fields as authority.

Digest consistency alone cannot prevent this. An attacker or unreviewed integration can mutate the map and recompute `codeReleaseSwitchContractDigest`. Exact map validation forces every authority-shaped field to be added through reviewed source code, tests, docs, and release governance.

Chinese learning note:

- `currentTemplate` 是当前 HOLD 状态模板，不是调用方可扩展的状态包。
- `openPrerequisites` 是未来开关打开前必须满足的条件集合，不是可被 JSON 动态追加的选项表。
- 对 release proof object 来说，map 的 key-set 本身就是协议。只检查几个当前用到的 key，会给未来字段留下“潜伏授权”空间。
- 顶级 Agent 的安全边界要同时验证 digest、字段值、字段集合、来源和下游一致性。

## Multi-Expert Review Notes

- Producer lens: the code switch contract producer now owns the authoritative template and prerequisite maps in source-controlled helpers.
- State-machine lens: the state machine rejects digest-valid code switch reports whose template or prerequisite maps contain unreviewed authority-shaped extensions.
- Durable-executor lens: the durable executor applies the same exact map checks before any future real POST boundary could consume the switch report.
- Runtime-binding lens: runtime binding setup also rejects mutated code switch maps, preventing a drifted contract from becoming downstream runtime evidence.
- Test lens: regressions recompute `codeReleaseSwitchContractDigest`, proving schema closure rather than stale-digest rejection.
- Learning lens: maps near release authority should be treated as closed protocol schemas, not ad hoc bags of fields.

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

- Continue scanning binding maps that are consumed by multiple downstream boundaries, especially runtime binding maps and release decision bindings.
- Prefer producer-owned exact helper maps or exact key-set validators for any proof object that can become release criteria.
- Keep real durable audit writer, receipt validator, release signer, code switch implementation, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
