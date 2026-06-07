# M5.21-82 Default value prompt authority contract audit

> Scope: this wave extends the M5.21-81 default-value safety boundary into the
> LLM-visible ToolRegistry prompt. Defaults and optional parameter descriptions
> must not be interpreted as HITL, release, audit, identity, or write authority.

## Background

M5.21-81 installed a runtime/default-registry guard. The next risk surface is
the LLM-visible tool directory: parameter descriptions often say "default" or
"optional", and risk labels can say `requiresConfirmation=false`. Those phrases
are useful, but a model must not infer that a default value or a lack of extra
HITL equals authorization.

This wave keeps the design deliberately small. It does not expose `defaults.yml`
or concrete default values to the prompt. It adds a global prompt rule in
`ToolRegistry`, which is consumed by both AtlasBrain and ReAct.

## Delivered Changes

- Updated `ToolRegistry.buildSystemPromptForCurrentUser()`.
- Added a global rule: parameter descriptions that mention "默认/可选" are only
  form-draft/frontend-fill hints.
- The rule explicitly says defaults do not mean:
  - user confirmation
  - HITL pass
  - release approval
  - audit success
  - write authorization
  - real HTTP execution permission
- The rule also clarifies `requiresConfirmation=false`: it only means no extra
  HITL is required for that Tool, not that login, RBAC, tenant isolation, release
  gates, or backend authorization can be bypassed.
- The rule tells the model not to proactively generate auth, tenant, HITL,
  audit, release, or write-control fields in `Action.params`.
- Extended `ToolRegistryPromptContractTest`.
- Added `M521DefaultValuePromptAuthorityContractTest` as a source-level contract
  that prevents `ToolRegistry` prompt generation from importing/rendering
  `DefaultValueRegistry`, `DefaultValueApplier`, `IntentDefaults`, or
  `defaults.yml`.

## Expert Review

- Prompt/Agent reviewer: accepted. The prompt now says what the execution layer
  already enforces: defaults and optional fields are not authority.
- Security reviewer: accepted after follow-up. `requiresConfirmation=false` is
  clarified as "no extra HITL", not "no authorization".
- Defaults reviewer: accepted. The ToolRegistry prompt still does not render the
  default registry or concrete `defaults.yml` values.
- Learning reviewer: accepted. This wave teaches an important Agent pattern:
  model-facing guidance must distinguish ergonomics from authority.

## Security Boundary

This wave does not open any write path.

Still absent:

- no real kube-manager `8100` access
- no `POST /api/{orgId}/deployment`
- no HTTP client added to `NimCreateTool`
- no durable writer, storage probe, durable receipt, validation result, release
  decision, or code release switch
- no prompt rendering of `defaults.yml`
- no prompt rendering of `DefaultValueRegistry` or concrete default map values

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER +
requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=ToolRegistryPromptContractTest,M521DefaultValuePromptAuthorityContractTest,M521DefaultValueSafetyContractTest,M521NimCreateDefaultsIntentHoldContractTest" test
mvn -q "-Dtest=ToolRegistryPromptContractTest,ToolRegistryPermissionTest,M521DefaultValuePromptAuthorityContractTest,M521DefaultValueSafetyContractTest,M521NimCreateDefaultsIntentHoldContractTest,M513HitlFailClosedContractTest,HighRiskMutationToolHttpContractTest,SafeToolExecutorTest" test
git diff --check
mvn -q test
```

`git diff --check` reported only CRLF working-copy warnings. Full test note:
local `model.onnx` download timed out and Atlas degraded to L1 embedding mode,
but Maven exited 0.

## Learning Note

LLM-visible prompts are part of the Agent security boundary. A runtime guard is
necessary, but not enough: the model should also be taught not to invent
authority-shaped fields. Good Agent systems make unsafe interpretations both
hard to execute and hard to even propose.
