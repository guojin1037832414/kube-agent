# M5.21-127 NIM State-Machine Requirement Failure/Shortcut Lists Closed Audit - 2026-06-08

## Scope

This wave closes the proof vocabulary emitted by `NimCreateStateMachineReleaseDecisionRequirementSupport` itself:

- `stateMachineRequirementPlan.failureContract.failureStatuses`
- `stateMachineRequirementPlan.forbiddenShortcuts`

M5.21-126 made the upstream release decision gate vocabulary exact before the state-machine requirement consumes it. This wave turns the state-machine requirement plan's own failure and forbidden-shortcut vocabulary into source-owned closed lists so future state-machine or durable-executor consumers have a stable contract to validate.

Touched production file:

- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupport.java`

Touched test:

- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupportTest.java`

## What Changed

- Added package-private source-owned helper lists:
  - `stateMachineRequirementFailureStatuses()`
  - `stateMachineRequirementForbiddenShortcuts()`
- `stateMachineFailureContract()` now emits the helper list instead of an inline `List.of(...)`.
- `stateMachineForbiddenShortcuts()` now delegates to the source-owned helper list.
- The positive state-machine requirement test now asserts exact equality for:
  - `releaseDecisionGateReportAcceptedRequiredCompanionSignals`
  - `stateMachineRequirementPlan.failureContract.failureStatuses`
  - `stateMachineRequirementPlan.forbiddenShortcuts`

## Why This Matters

The state-machine requirement plan is still a HOLD contract, but it is close to the future write-release boundary. Its failure statuses and forbidden shortcuts are not ordinary explanatory text. They describe how future release wiring must fail closed and which shortcuts must remain forbidden.

If these lists stay as private inline literals and tests only check a few booleans, future code can accidentally add a new status or shortcut without reviewing downstream meaning. By making the lists source-owned helpers now, later consumers can validate exact equality instead of inventing their own partial checks.

Chinese learning note:

- 顶级 Agent 的 release path 不应该等到真实写入上线之后才整理协议词汇。
- 越靠近 `writePermitted` 和 `realHttpExecutionAllowed`，越应该提前把 failure vocabulary 与 shortcut vocabulary 变成源代码拥有的闭合契约。
- 这不是过度设计，而是在为未来真实状态机接入预铺安全轨道：以后新增一个失败状态或禁止捷径，必须同步生产代码、测试和文档。

## Multi-Expert Review Notes

- State-machine producer lens: the state-machine requirement plan now owns its future failure and shortcut vocabulary as reusable package-private helpers.
- Security lens: no new authority was granted; the change only makes HOLD vocabulary harder to drift.
- Test architecture lens: the positive path now checks exact list shape, not only selected fields or `contains(...)`.
- Agent architecture lens: future downstream state-machine binding can depend on the same helper lists instead of re-encoding protocol strings.
- Learning lens: producer-side closed lists are useful even before a downstream consumer exists, because they prevent protocol drift at the moment the future consumer is added.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
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

- Continue scanning `stateMachineRequirementPlan` future evidence maps for exact key-set validation needs before they become release criteria.
- If a downstream consumer starts accepting `stateMachineRequirementPlan`, require exact equality for the newly exposed helper lists and add digest-consistent forged extra-list regressions.
- Keep real durable audit writer, receipt validator, release signer, code switch, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
