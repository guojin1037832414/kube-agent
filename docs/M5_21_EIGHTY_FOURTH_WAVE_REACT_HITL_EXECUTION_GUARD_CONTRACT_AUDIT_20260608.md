# M5.21-84 ReAct HITL execution guard contract audit

> Scope: harden the ReAct execution layer so high-risk Actions remain
> fail-closed even if the LLM ignores the prompt, and make HITL blocks visible
> as complete audit timeline events.

## Background

M5.21-83 aligned the ReAct prompt with ToolRegistry risk metadata. That is the
model-facing part of the safety boundary. A top-tier Agent also needs execution
evidence: if the model still outputs a high-risk `Action`, the runtime must
block before `BaseTool.execute(...)`, record a structured Observation, and keep
the event stream auditable.

Current `ReActEngine` already called `HitlGuard.verify(...)` before tool
execution. This wave turns that behavior into an explicit E2E contract and
improves the blocked-event timeline.

## Delivered Changes

- Updated `ReActEngine`.
- When `HitlGuard` blocks a ReAct Action, the engine now emits:
  - `tool_done(success=false)` with the same risk metadata used by `tool_start`
  - `observation` containing the structured `HITL_CONFIRMATION_REQUIRED` result
  - `error` with the human-readable fail-closed message
- The blocked Observation is still written to `ReActMemory`, allowing the next
  LLM turn to summarize the safety block instead of retrying the same Action.
- Extended ReAct Action parameter cleanup:
  - `token`
  - `organizationId` / `orgId`
  - `conversationId`
  - `userId`
  - `confirmed`
  - `hitlConfirmed`
  - `approval`
  - `auditReceipt`
  - `releaseDecision`
  - `writePermitted`
  - `writeExecutionAllowed`
  - `realHttpExecutionAllowed`
  - `releaseEligible`
- Added normalized-key filtering for common underscore/dash/case variants such
  as `hitl_approved`, `release-approved`, and `write_allowed`.
- Added `ReActEngineHitlGuardContractTest`.
- The new E2E test scripts an LLM that attempts to call a CREATE tool directly
  and smuggles forged confirmation/audit/release/write fields. The test proves:
  - the Tool is never executed
  - the blocked ReAct step is marked unsuccessful
  - the Observation contains `HITL_CONFIRMATION_REQUIRED`
  - trusted `organizationId` from initial context is preserved
  - forged control fields from Action params are stripped
  - the event timeline includes `tool_start`, `tool_done(false)`,
    `observation`, and `error`
  - risk metadata is present without leaking `apiEndpoints`

## Expert Review

- Agent architecture reviewer: accepted. Prompt safety and execution safety now
  reinforce each other with a runnable ReAct E2E proof.
- Security reviewer: accepted. HITL remains fail-closed, and caller-supplied
  confirmation/audit/release/write-control fields are stripped before execution.
- Observability reviewer: accepted. Blocked ReAct Actions now produce a complete
  timeline instead of only an error event.
- Learning reviewer: accepted. This wave illustrates the Agent pattern:
  "prompt asks the model not to do it; runtime proves it cannot happen."

## Security Boundary

This wave does not open any write path.

Still absent:

- no real kube-manager `8100` access
- no `POST /api/{orgId}/deployment`
- no runtime write behavior opened by ReAct
- no service-side HITL bypass
- no HTTP client added to `NimCreateTool`
- no durable writer, storage probe, durable receipt, validation result, release
  decision, or code release switch
- no Elasticsearch, `ISysLogService`, or `sys_log` write

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER +
requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=ReActEngineHitlGuardContractTest,ReActEngineMultiStepE2ETest,ReActPromptBuilderRiskMetadataContractTest,M513HitlFailClosedContractTest" test
mvn -q "-Dtest=ReActEngineHitlGuardContractTest,ReActEngineMultiStepE2ETest,ReActEventRiskMetadataTest,ReActPromptBuilderRiskMetadataContractTest,ToolRegistryPromptContractTest,M513HitlFailClosedContractTest,SafeToolExecutorTest" test
git diff --check
mvn -q test
```

`git diff --check` reported only CRLF working-copy warnings. Full test note:
local `model.onnx` download timed out and Atlas degraded to L1 embedding mode,
but Maven exited 0.

## Learning Note

Good Agent safety is layered. The prompt should teach the model to avoid unsafe
Actions, but the runtime must assume the model can still be wrong. For
high-risk Tool calls, the authoritative safety boundary is service-side
metadata plus HITL verification before `execute(...)`. The event timeline should
also make that block visible, because auditability is part of safety.
