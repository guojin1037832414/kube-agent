# M5.51 Kube-Manager Write Idempotency Contract - 2026-06-09

## Purpose

M5.51 implements the generic server-derived idempotency-key contract required by the M5.50 write retry readiness gate.

New endpoint:

```text
GET /api/agent/observability/kube-manager/http-outlet/write-idempotency-contract
```

This endpoint is admin-only, local-process-only, read-only, and summary-only. It proves the derivation contract exists while also proving it is not yet bound to outbound kube-manager writes.

## Added Files

- `KubeManagerWriteIdempotencyKeyInput`
- `KubeManagerWriteIdempotencyKeyResult`
- `KubeManagerWriteIdempotencyKeyDeriver`
- `AgentKubeManagerWriteIdempotencyContractResponse`
- `AgentKubeManagerWriteIdempotencyContractService`
- `KubeManagerWriteIdempotencyKeyDeriverTest`
- `AgentKubeManagerWriteIdempotencyContractServiceTest`

## Contract

The deriver creates keys with:

```text
km-write-v1-{sha256(canonical server-side evidence)}
```

Required evidence:

- durable audit receipt id
- durable audit receipt digest
- request spec digest
- principal fingerprint
- organization fingerprint
- operation type
- HTTP method
- path template
- request body digest
- release evidence digest

The input deliberately has no caller-provided idempotency-key field. A caller, browser, prompt, or frontend cannot provide or override the key.

## Current Status

M5.51 status:

- `serverDerivedKeyContractExists=true`
- `boundToHttpOutlet=false`
- `callerProvidedIdempotencyKeyAccepted=false`
- `writeRetryEnabled=false`
- `httpHeaderInjectionEnabled=false`
- `runtimeEnableSwitchPresent=false`

M5.50 readiness was updated accordingly:

- `genericKubeManagerIdempotencyBoundaryExists=true`
- `genericKubeManagerIdempotencyBoundaryBoundToHttpOutlet=false`
- `serverDerivedIdempotencyKeyDeriverExists=true`
- `callerProvidedIdempotencyKeyAccepted=false`
- `readinessVerdict=NOT_READY` remains unchanged

## Safety Boundary

M5.51 does not:

- call kube-manager `8100`
- call `KubeManagerHttpClient`
- call `RestClient`
- inject any HTTP header
- call `/api/login` or fallback login
- execute Tools
- call LLMs or external services
- write audit evidence
- issue durable receipts
- mutate Resilience4j registries
- enable write retry
- expose raw idempotency keys, raw principal, raw organization, raw backend paths, raw request bodies, tokens, passwords, or Authorization headers

## Verification

Passed:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=KubeManagerWriteIdempotencyKeyDeriverTest,AgentKubeManagerWriteIdempotencyContractServiceTest,AgentKubeManagerWriteRetryReadinessServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## Learning Note

This is the first generic kube-manager write idempotency primitive. It is intentionally below the execution layer: define deterministic evidence binding first, observe it safely second, then later bind it into the HTTP outlet only after release review, operation allowlists, write verification, and eval evidence exist.

That order is important. If idempotency is treated as "caller sends a key", prompt injection or UI misuse can forge the retry boundary. A top-tier Agent derives the key from trusted server evidence.
