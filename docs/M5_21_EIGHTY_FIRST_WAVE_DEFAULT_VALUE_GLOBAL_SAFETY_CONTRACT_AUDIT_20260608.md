# M5.21-81 Default value global safety contract audit

> Scope: this wave generalizes the M5.21-80 "defaults are not authorization"
> rule from `nim_create` metadata into the shared default-value infrastructure.

## Background

M5.21-80 locked `defaults.yml` and `intents.yml` around `nim_create`, but the
broader `DefaultValueRegistry` path still needed a global guard. A top-tier
Agent should treat form defaults as low-authority UX hints. They can make a
draft easier to fill, but they must never inject credentials, tenant/principal
identity, HITL state, audit receipts, release decisions, HTTP endpoint choices,
or write execution claims.

The important design choice in this wave is placement: the guard sits inside
`IntentDefaults`, the value object used by `DefaultValueRegistry`. This means
YAML-loaded defaults, reflection/test-injected defaults, and future registry
construction paths all cross the same boundary.

## Delivered Changes

- Added `DefaultValueSafety`.
- `IntentDefaults` now sanitizes every parameter map during construction.
- Protected keys are normalized before matching, so variants such as
  `writePermitted`, `write_permitted`, `write-permitted`, and `write.permitted`
  resolve to the same protected control key.
- The protected set includes reviewed near-synonym variants such as
  `accessToken`, `authToken`, `clientSecret`, `secretKey`, `targetOrgId`,
  `humanConfirmed`, `hitlApproved`, `writeAllowed`, `releaseApproved`,
  `trustedPolicySource`, `writeBodyRebuildReport`, `readinessExecutionReport`,
  `success`, `executed`, and `authoritative`.
- Nested `Map` and `List` default values are sanitized recursively.
- `DefaultValueRegistryTest` now proves protected control defaults are never
  filled, while legitimate form defaults continue to apply.
- Added `M521DefaultValueSafetyContractTest`.
- The contract parses current `defaults.yml` and recursively rejects protected
  control keys under every intent.
- The contract asserts current reviewed form defaults survive sanitization, so
  the guard does not accidentally break `deploy_create_instance`, `nim_create`,
  `distributed_create`, `user_create`, or `storage_create`.
- The contract proves legal business form defaults such as `user_create.role`
  remain allowed.
- Added a Chinese safety note to `defaults.yml` explaining that defaults are
  form drafts only and must not declare auth, identity, HITL, audit, release,
  endpoint, write-permission, or success-state fields.

## Protected Boundary

The global protected-key list covers these categories:

- Auth and secret fields: token, authorization/header material, password,
  secret, credentials, cookies, sessions, API keys.
- Tenant and principal fields: org/user/tenant/organization identity.
- HITL fields: confirmation and approval claims.
- HTTP/write/release fields: method, endpoint, side effect, write permission,
  real HTTP execution, release eligibility, release decision, validation result.
- Audit and source-switch fields: receipt, durable executor report, code release
  switch, source guard, backend/sys_log source claims.
- Strategy/self-claims: sysadmin/system-org/license/fallback/deployment success.

This wave intentionally does not block ordinary business form fields such as
`role`, because current `defaults.yml` legitimately uses `user_create.role=user`.
Authorization must be enforced by permission and policy layers, not by allowing
or disallowing a UI form draft field name.

## Expert Review

- Defaults infrastructure reviewer: accepted. The guard is installed at the
  `IntentDefaults` boundary, so the registry cannot apply unsafe defaults even
  if future YAML or test injection attempts to add them.
- Security reviewer: accepted after follow-up. Initial review asked for common
  near-synonym coverage and recursive config scanning. Both were added before
  commit.
- Product/frontend reviewer: accepted. Existing `defaults.yml` still supports
  expected form hints for deploy, NIM draft, distributed jobs, RBAC user role,
  and storage creation.
- Agent architecture reviewer: accepted. The rule is low-authority metadata in,
  server-owned evidence out; no default value can become a release credential.
- Learning reviewer: accepted. This wave captures a reusable Agent pattern:
  configuration can improve ergonomics, but authority must be produced by typed,
  trusted, auditable runtime evidence.

## Security Boundary

This wave does not open `nim_create` or any other write path.

Still absent:

- no real kube-manager `8100` access
- no `POST /api/{orgId}/deployment`
- no HTTP client added to `NimCreateTool`
- no durable writer, storage probe, durable receipt, validation result, release
  decision, or code release switch
- no Elasticsearch, `ISysLogService`, or `sys_log` write
- no default-injected `writePermitted`, `writeExecutionAllowed`,
  `realHttpExecutionAllowed`, `releaseEligible`, or `safeToPost`

`nim_create` remains `httpMethod=NONE + apiEndpoints={} + PLACEHOLDER +
requiresConfirmation=true`.

## Verification

Passed:

```bash
mvn -q "-Dtest=DefaultValueRegistryTest,M521DefaultValueSafetyContractTest,M521NimCreateDefaultsIntentHoldContractTest" test
mvn -q "-Dtest=DefaultValueRegistryTest,M521DefaultValueSafetyContractTest,M521NimCreateDefaultsIntentHoldContractTest,M513HitlFailClosedContractTest,HighRiskMutationToolHttpContractTest" test
git diff --check
mvn -q test
```

`git diff --check` reported only CRLF working-copy warnings. Full test note:
local `model.onnx` download timed out and Atlas degraded to L1 embedding mode,
but Maven exited 0. No real write path was opened; Spring test context may
initialize `KubeManagerHttpClient` with `baseUrl=http://localhost:8100`, but
this wave did not perform real `8100` write access.

## Learning Note

Default values are a convenience layer, not an evidence layer. In an Agent
system, the safe pattern is:

1. Let defaults fill only harmless form draft fields.
2. Strip anything that looks like identity, confirmation, audit, release, or
   write control.
3. Require server-owned, typed, digest-bound evidence before side effects can
   ever be considered.
