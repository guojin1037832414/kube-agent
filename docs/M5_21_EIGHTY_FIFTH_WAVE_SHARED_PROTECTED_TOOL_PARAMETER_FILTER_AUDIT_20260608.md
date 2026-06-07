# M5.21-85 Shared protected Tool parameter filter audit

> Scope: centralize protected Tool parameter filtering across ReAct,
> SafeToolExecutor, and execute_node so auth/tenant/HITL/audit/release/write
> control fields cannot drift between execution entrances.

## Background

M5.21-84 hardened ReAct so a high-risk Action is blocked before Tool execution
and forged control fields are stripped from `Action.params`. The remaining risk
was architectural drift: ReAct had a richer protected-field list than
`SafeToolExecutor`, while `execute_node` had another private recursive guard for
Plan parameters.

For a top-tier Agent, safety rules should not live as scattered copies. The same
service-owned control plane fields must be recognized consistently wherever
untrusted Tool parameters enter the system.

## Delivered Changes

- Added `ProtectedToolParameterFilter`.
- The shared filter recognizes auth/session/tenant context and control-plane
  fields including:
  - `token`, `accessToken`, `authToken`, `authorization`, `headers`, `cookie`
  - `organizationId`, `orgId`, `tenantId`, `conversationId`, `userId`
  - `confirmed`, `confirmation`, `hitlConfirmed`, `hitlApproved`
  - `auditReceipt`, `releaseDecision`, `releaseCredential`, `releaseApproved`
  - `writePermitted`, `writeAllowed`, `writeEnabled`, `writeAuthorized`
  - `writeExecutionAllowed`, `realHttpExecutionAllowed`, `releaseEligible`
  - `requiresConfirmation`, `operationType`, `httpMethod`, `apiEndpoints`
  - `nimCreateReleased`, `codeReleaseSwitch`, `codeReleaseSwitchDigestVerified`
- Normalized variants with `_`, `-`, `.`, spaces, and casing are also protected,
  for example `hitl_approved`, `release-approved`, `write_allowed`,
  `operation_type`, and `api.endpoints`.
- Updated `ReActEngine` to call the shared filter when merging initial context
  and LLM Action params.
- Updated `SafeToolExecutor` to call the shared filter when building trusted Tool
  params and when deciding which Plan params should be ignored as protected
  control fields instead of rejected as unknown business params.
- Updated `AtlasGraphConfig` execute_node to use the same shared filter for its
  pre-execution recursive fail-closed guard.
- Updated static safety contract tests that previously searched for local
  `PROTECTED_CONTEXT_PARAMS` lists.
- Added:
  - `ProtectedToolParameterFilterTest`
  - `ProtectedToolParameterFilterUsageContractTest`
- Extended `SafeToolExecutorTest` to prove forged HITL/audit/release/write
  fields are stripped in both Graph compatibility and PLAN_EXECUTE_NODE paths.

## Boundary Clarification

This filter is for Tool execution parameters, not for defaults.yml.

- Execution parameters may legitimately contain business secrets in some tools
  such as user creation password fields, so the execution filter should not
  blindly strip every key named `password` or every arbitrary business value.
- `DefaultValueSafety` remains the wider configuration/default-value guard,
  because defaults must never inject secrets or authority.
- The shared execution filter focuses on service-owned control plane authority:
  identity, tenant, session, HITL, audit, release, risk metadata, and write
  permission fields.

## Expert Review

- Agent architecture reviewer: accepted. A single shared filter reduces hidden
  drift between ReAct, Plan/Execute, and SafeToolExecutor.
- Security reviewer: accepted. SafeToolExecutor now strips forged control fields
  beyond context IDs, and execute_node still fails closed before automatic Plan
  execution.
- Test architecture reviewer: accepted. Behavior tests cover both runtime paths,
  and source contract tests prevent private blacklists from returning.
- Learning reviewer: accepted. This wave illustrates the distinction between
  configuration safety, prompt safety, and execution-boundary authority.

## Security Boundary

This wave does not open any write path.

Still absent:

- no real kube-manager `8100` access
- no `POST /api/{orgId}/deployment`
- no runtime write behavior opened by ReAct, SafeToolExecutor, or execute_node
- no service-side HITL bypass
- no durable writer, storage probe, durable receipt, validation result, release
  decision, or code release switch
- no Elasticsearch, `ISysLogService`, or `sys_log` write

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER +
requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=ProtectedToolParameterFilterTest,ProtectedToolParameterFilterUsageContractTest,SafeToolExecutorTest,ReActEngineHitlGuardContractTest,M4Px4ToolExecuteEntrypointContractTest,M42PlanExecuteSafetyContractTest" test
mvn -q "-Dtest=ProtectedToolParameterFilterTest,ProtectedToolParameterFilterUsageContractTest,SafeToolExecutorTest,ReActEngineHitlGuardContractTest,ReActEngineMultiStepE2ETest,ReActPromptBuilderRiskMetadataContractTest,ReActEventRiskMetadataTest,ToolRegistryPromptContractTest,M521DefaultValuePromptAuthorityContractTest,M521DefaultValueSafetyContractTest,M4Px4ToolExecuteEntrypointContractTest,M42PlanExecuteSafetyContractTest,M513HitlFailClosedContractTest,HighRiskMutationToolHttpContractTest" test
git diff --check
mvn -q test
```

`git diff --check` reported only CRLF working-copy warnings. Full test note:
local `model.onnx` download timed out and Atlas degraded to L1 embedding mode,
but Maven exited 0.

## Learning Note

A strong Agent does not let every layer invent its own idea of "protected".
Prompt rules can tell the model not to generate authority-shaped params, but
runtime boundaries must remove or reject those params even if the model does.
Centralizing the filter turns an implicit convention into a reusable security
primitive.
