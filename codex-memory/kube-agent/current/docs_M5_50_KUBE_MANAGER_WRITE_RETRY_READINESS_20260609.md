# M5.50 Kube-Manager Write Retry Readiness Contract - 2026-06-09

## Purpose

M5.50 adds the kube-manager write retry readiness contract after the M5.49 HTTP outlet health summary.

New endpoint:

```text
GET /api/agent/observability/kube-manager/http-outlet/write-retry-readiness
```

This is an admin-only, local-process-only, read-only, summary-only observability endpoint. It does not enable write retry and does not call kube-manager. Its job is to turn a dangerous future reliability capability into a machine-readable, testable, frontend-ready, and teachable safety protocol.

## Added Files

- `AgentKubeManagerWriteRetryReadinessResponse`
- `AgentKubeManagerWriteRetryReadinessService`
- `AgentKubeManagerWriteRetryReadinessServiceTest`

## Response Semantics

The current verdict is intentionally fail-closed:

- `readinessVerdict=NOT_READY`
- `readyForControlledWriteRetry=false`
- `writeRetryEnabled=false`
- `automaticWriteRetryAllowed=false`

Even if a Resilience4j `kubeManagerWrite` retry instance exists, the response can only describe it as `configuredButInactive=true`. A configured retry bean is not the same as an effective write retry path.

## Future Required Evidence

Future controlled write retry cannot be considered until these requirements are implemented and bound into the HTTP outlet:

- Server-derived idempotency key bound to audit receipt, request spec, principal, organization, and operation.
- Durable prewrite receipt before the first attempt.
- HITL and release evidence for high-risk state-changing operations.
- Read-after-write verification or an equivalent deterministic status check.
- Bounded retry predicate limited to safe failure classes, bounded attempts, backoff, and jitter.
- Operation allowlist and RBAC/tenant evidence.
- Compensation or rollback guidance plus replay/eval regression evidence.
- CI gate and admin-only operator observability.

## Current Evidence And Gaps

Already present:

- GET read retry through `kubeManagerRead`.
- WRITE circuit breaker and bulkhead only.
- High-risk durable prewrite gate foundation.
- Admin audit query.
- Replay timeline.
- Eval gate bundle.

Still missing:

- Generic kube-manager idempotency boundary.
- Generic write operation allowlist.
- Retry predicate bound to write failure classes.
- Post-write readback contract.
- Release/HITL evidence bound to the HTTP outlet.
- Compensation policy.
- Runtime enable switch, intentionally absent.

## Safety Boundary

The endpoint must remain:

- Admin-only at URL and method levels.
- GET-only, with no request body.
- No caller-supplied trace, idempotency, release, retry, or write-control flags.
- No `KubeManagerHttpClient` call.
- No `RestClient` call.
- No kube-manager `8100` access.
- No `/api/login`, token refresh, or fallback login.
- No Tool execution.
- No LLM or external service call.
- No audit write and no durable receipt issuance.
- No Retry/CircuitBreaker/Bulkhead registry mutation.
- No write retry enablement.
- No raw base URL, Authorization, token, password, backend path, request body, response body, or exception body exposure.

## Verification

Passed:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=AgentKubeManagerWriteRetryReadinessServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## Learning Note

Top-tier Agent engineering does not mean opening every powerful capability immediately. It means turning dangerous capabilities into observable, auditable, replayable, evaluable, and fail-closed protocols before they ever enter the execution path.

Write retry is especially sensitive because it can amplify side effects. Without idempotency, durable audit, HITL, readback verification, and compensation design, one user action can become multiple real state changes. M5.50 is therefore successful precisely because write retry remains off and the path to future enablement is explicit.
