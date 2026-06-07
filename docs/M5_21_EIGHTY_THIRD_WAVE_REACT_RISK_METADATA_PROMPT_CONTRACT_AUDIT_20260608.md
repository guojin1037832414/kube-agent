# M5.21-83 ReAct risk metadata prompt contract audit

> Scope: align the ReAct system prompt with ToolRegistry risk metadata so the
> model treats `operationType`, `httpMethod`, and `requiresConfirmation` as
> action-gating safety signals before it proposes tool calls.

## Background

M5.12 introduced compact ToolRegistry risk labels:
`operationType`, `httpMethod`, and `requiresConfirmation`. M5.13 added
execution-layer HITL fail-closed behavior, and M5.21-82 clarified that defaults
and optional fields are not authority.

The remaining prompt gap was in `ReActPromptBuilder`: the high-risk rule still
focused on keyword examples such as delete/scale/permission changes. That was
useful but incomplete. A top-tier Agent should not rely on keyword matching
when the tool catalog already exposes explicit risk metadata.

## Delivered Changes

- Updated `ReActPromptBuilder` high-risk rules.
- The ReAct prompt now says the ToolRegistry risk labels are authoritative
  prompt-level risk hints.
- Tools with `operationType=CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER` or
  `requiresConfirmation=true` must not be called directly by ReAct; the model
  must output Mode C and ask for HITL.
- The rule explicitly says completed parameters, default value backfill,
  optional fields, and natural-language "confirmation" do not replace
  server-side HITL.
- The prompt tells the model not to proactively generate control-plane fields
  such as `token`, `orgId`, `userId`, `confirmed`, `hitlConfirmed`,
  `approval`, `auditReceipt`, `releaseDecision`, or `writePermitted`.
- `operationType=PLACEHOLDER` or `httpMethod=NONE` is now described as a
  non-open real backend execution path. The model must not claim create/delete/
  submit/change success for those tools.
- Keyword-based high-risk examples remain as a supplementary signal.
- Added `ReActPromptBuilderRiskMetadataContractTest`.
- The new contract covers READ, CREATE, UPDATE, DELETE, ACTION, and PLACEHOLDER
  risk labels, including an embedded test-only UPDATE tool so future UPDATE
  behavior is guarded before a production UPDATE tool appears.

## Expert Review

- Prompt/Agent reviewer: accepted. The ReAct prompt now follows the metadata
  already exposed by ToolRegistry instead of relying only on keyword examples.
- Security reviewer: accepted. Execution-layer HITL remains the hard guard, but
  the prompt now reduces unsafe proposals before the executor has to reject
  them.
- Test reviewer: accepted. The new contract uses representative real tools plus
  a small embedded UPDATE contract tool to avoid fragile assertions.
- Learning reviewer: accepted. This wave teaches an important Agent pattern:
  structured tool metadata should drive both execution safety and model-facing
  reasoning instructions.

## Security Boundary

This wave does not open any write path.

Still absent:

- no real kube-manager `8100` access
- no `POST /api/{orgId}/deployment`
- no runtime write behavior added to ReAct
- no HTTP client added to `NimCreateTool`
- no durable writer, storage probe, durable receipt, validation result, release
  decision, or code release switch
- no Elasticsearch, `ISysLogService`, or `sys_log` write

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER +
requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=ReActPromptBuilderRiskMetadataContractTest,ReActPromptBuilderGpuCreateContractTest,ReActPromptBuilderPodDiagnosticContractTest,ToolRegistryPromptContractTest,M521DefaultValuePromptAuthorityContractTest,M513HitlFailClosedContractTest" test
git diff --check
mvn -q "-Dtest=ReActPromptBuilderRiskMetadataContractTest,ReActPromptBuilderGpuCreateContractTest,ReActPromptBuilderPodDiagnosticContractTest,ToolRegistryPromptContractTest,ToolRegistryPermissionTest,M513HitlFailClosedContractTest,HighRiskMutationToolHttpContractTest,SafeToolExecutorTest" test
mvn -q test
```

`git diff --check` reported only CRLF working-copy warnings. Full test note:
local `model.onnx` download timed out and Atlas degraded to L1 embedding mode,
but Maven exited 0.

## Learning Note

Prompt rules are not just wording. In an Agent system, they are the model-facing
part of the safety architecture. Runtime HITL guards still make the final
decision, but the model should be taught to recognize risk before it proposes an
unsafe `Action`. Metadata-driven safety is more robust than keyword-only safety
because it follows the tool contract even when natural language phrasing changes.
