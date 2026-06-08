# M5.21-128 NIM Code Switch Failure/Shortcut Lists Closed Audit - 2026-06-08

## Scope

This wave closes the proof vocabulary emitted by `NimCreateDurableAuditCodeReleaseSwitchContractSupport` and consumed by both current downstream runtime shells:

- `codeReleaseSwitchContract.failureContract.failureStatuses`
- `codeReleaseSwitchContract.forbiddenShortcuts`

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupportTest.java`

## What Changed

- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now exposes source-owned helper lists:
  - `codeReleaseSwitchFailureStatuses()`
  - `codeReleaseSwitchForbiddenShortcuts()`
- `switchFailureContract()` now emits `failureStatuses` from the source-owned helper list.
- `forbiddenShortcuts()` now delegates to the source-owned helper list.
- `NimCreateStateMachineSupport` now requires exact equality for both code-switch-owned lists before accepting the code switch contract report shape.
- `NimCreateDurableWriteExecutorSupport` now applies the same exact checks before accepting code switch evidence for the future durable executor shell.
- Added digest-consistent forged list regressions for both downstream consumers. The tests append fake future failure/shortcut values, recompute `codeReleaseSwitchContractDigest`, and still expect fail-closed rejection.

## Why This Matters

The code release switch is a high-authority future guard: it will eventually decide whether a reviewed build is allowed to move from HOLD contract into real write execution. That makes its failure statuses and forbidden shortcuts release-proof protocol vocabulary, not descriptive strings.

Closing only the state machine would leave the durable executor accepting a drifted proof object. Closing only the durable executor would leave the state machine vulnerable to the same drift. This wave treats the code switch report as a shared protocol object: every current downstream consumer must validate the same closed vocabulary.

Chinese learning note:

- 同一个 proof object 如果有多个下游，必须同步关闭所有当前消费者。
- Digest 一致只能说明对象被重新哈希过，不能说明新增词汇被代码审查过。
- code release switch 的禁止捷径是负面授权协议，不能被当成“说明文字”随意扩展。
- 顶级 Agent 的写放行链路应该让 version skew fail closed，而不是靠宽容解析维持表面兼容。

## Multi-Expert Review Notes

- State-machine lens: `NimCreateStateMachineSupport` now rejects digest-valid code switch contracts whose failure/shortcut vocabulary has been extended.
- Durable-executor lens: `NimCreateDurableWriteExecutorSupport` now applies the same source-owned vocabulary check before any future POST boundary could use the report.
- Producer lens: the code switch contract producer owns the closed failure and shortcut vocabularies in package-private helpers.
- Test lens: regressions recompute `codeReleaseSwitchContractDigest`, proving semantic list validation rather than stale-digest rejection.
- Learning lens: shared proof objects require shared downstream validation. Security is only as strong as the loosest current consumer.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
```

Final verification passed:

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

- Continue scanning shared proof objects for multiple consumers where one path still accepts subset, missing, or extra vocabulary.
- Review `openPrerequisites`, `currentTemplate`, and binding maps for future exact key-set closure if they become release criteria.
- Keep real durable audit writer, receipt validator, release signer, code switch implementation, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
