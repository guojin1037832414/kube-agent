# M5.21-80 NIM create defaults / intent HOLD contract audit

> Scope: this wave hardens the metadata layer around `nim_create`. `defaults.yml` and
> `intents.yml` may keep UI/form draft values, but those values must never become HITL evidence,
> release evidence, write permission, or a kube-manager HTTP shortcut.

## Background

M5.21-79 hardened the public `NimCreateTool` entry so it no longer owns an unused
`KubeManagerHttpClient` dependency. The next small risk surface was metadata drift:
`defaults.yml` still contains useful NIM form defaults, and `intents.yml` contains NIM intent
parameter defaults.

Those defaults are acceptable as draft/form hints:

- `gpuPercentLimits=100`
- `replicas=1`
- `enableWebSsh=true`

The safety requirement is stricter: metadata defaults cannot imply `safeToPost`, HITL
confirmation, approval, release eligibility, write permission, HTTP endpoint selection, principal
identity, or durable audit success.

## Delivered Changes

- Added `M521NimCreateDefaultsIntentHoldContractTest`.
- The contract parses `src/main/resources/defaults.yml` with SnakeYAML and asserts
  `defaults.nim_create` contains only form draft keys.
- The contract parses `src/main/resources/intents.yml` and asserts `nim_create` intent parameters
  do not expose forbidden control-plane or release keys.
- The contract executes `NimCreateTool` after applying `nim_create` defaults and forged caller
  claims, proving the public Tool still returns `UNSUPPORTED_BACKEND_OPERATION`, `state=HELD`,
  `writePermitted=false`, and `sideEffect=NONE`.
- The contract locks `NimCreateTool` out of `@WithDefaults`, `DefaultValueApplier`, and
  `DefaultValueRegistry` while it remains a placeholder.
- `NimCreateStateMachineSupport` now records additional forged caller claims as ignored:
  `writeExecutionAllowed`, `realHttpExecutionAllowed`, `releaseEligible`, `releaseDecision`,
  `releaseCredential`, `releaseCredentialIssued`, `validationResult`, `nimCreateReleased`,
  `codeReleaseSwitch`, `codeReleaseSwitchOpened`, `codeReleaseSwitchDigest`,
  `sourceGuardInstalled`, `backendQuerySourceAllowedForRelease`, and
  `sysLogBackfillSourceAllowed`.

## Expert Review

- Defaults-path reviewer: accepted. `DefaultValueRegistry.apply()` only fills missing/null keys;
  `DefaultValueAspect` only fires for `@WithDefaults`; no production `@WithDefaults` use is wired
  into `NimCreateTool`.
- Security reviewer: accepted. Defaults and intents now have an explicit forbidden control-key
  contract.
- Agent architecture reviewer: accepted. Metadata remains descriptive/draft-only; execution
  authority stays in server-owned state-machine evidence.
- Learning reviewer: accepted. This wave captures an important Agent lesson: configuration and
  intent metadata can guide forms, but must not authorize side effects.

## Security Boundary

This wave does not open `nim_create`.

Still absent:

- no real `POST /api/{orgId}/deployment`
- no real kube-manager `8100` access
- no HTTP client in `NimCreateTool`
- no `@WithDefaults` binding on `NimCreateTool`
- no durable writer, storage probe, durable receipt, validation result, release decision, or code
  release switch
- no Elasticsearch, `ISysLogService`, or `sys_log` write

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER + requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=M521NimCreateDefaultsIntentHoldContractTest,M521NimCreateToolEntryStaticContractTest,HighRiskMutationToolHttpContractTest,DefaultValueRegistryTest,M513HitlFailClosedContractTest" test
mvn -q "-Dtest=M521NimCreateDefaultsIntentHoldContractTest,M521NimCreateToolEntryStaticContractTest,M521NimRuntimeSourceGuardBindingContractTest,M521NimDurableAuditWriterProbeBoundaryStaticContractTest,NimCreateStateMachineSupportTest,HighRiskMutationToolHttpContractTest,DefaultValueRegistryTest,M513HitlFailClosedContractTest" test
git diff --check
mvn -q test
```

Full test note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but
Maven exited 0. This remains an accepted degraded-test-path signal, not an M5.21-80 failure.

## Learning Note

A top-tier Agent should treat metadata as a low-authority layer. `defaults.yml` and `intents.yml`
can make the UI and LLM interface easier to use, but they are not proof that a human approved an
action, not proof that durable audit succeeded, and not permission to call a write endpoint.
