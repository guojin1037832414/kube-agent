# M5.49 Kube-Manager HTTP Outlet Health Summary

Date: 2026-06-09 Asia/Shanghai

## Scope

M5.49 adds an admin-only local health summary for the kube-manager HTTP outlet.

New endpoint:

- `GET /api/agent/observability/kube-manager/http-outlet/health-summary`

New backend contracts:

- `AgentKubeManagerHttpOutletHealthSummaryResponse`
- `AgentKubeManagerHttpOutletHealthSummaryService`

## Why This Matters

A top-tier Agent must explain the reliability boundary around its external
system calls. The kube-manager outlet already uses Resilience4j for read retry,
circuit breaking, and bulkhead isolation. M5.49 turns that policy into a safe
operator read model:

- backend configuration summary with base URL redacted
- effective read retry policy
- effective write policy showing no automatic write retry
- circuit breaker state and bounded metrics
- bulkhead concurrency state
- explicit safety and privacy proof

This is not a remote health probe. Opening the page cannot call kube-manager,
refresh a fallback token, inspect Authorization headers, or mutate circuit
breaker state.

## Security Boundary

The endpoint is admin-only, local-process-only, read-only, and summary-only.

It does not:

- call `KubeManagerHttpClient`
- call `RestClient`
- call kube-manager `8100`
- call `/api/login`
- execute Tools
- use an LLM
- make external network calls
- inspect or expose Bearer tokens
- expose token prefixes
- expose login username/password
- expose the raw backend base URL
- expose raw kube-manager paths, query strings, request bodies, response bodies, or exception bodies
- change circuit breaker or bulkhead state
- enable write retry

Read requests remain governed by `kubeManagerRead` retry + `kubeManager`
circuit breaker + `kubeManager` bulkhead. Write requests remain governed by
circuit breaker + bulkhead only. If a `kubeManagerWrite` retry instance exists
in configuration, the summary marks it as configured but inactive so operators
do not confuse configuration presence with effective write retry.

NIM / HPC / Slurm / BCM remain Phase 2 paused scope.

## Frontend Contract

Future `vue-kube-manager` observability pages should render:

- `status` and `statusReasons` for the headline.
- `backend` for redacted local configuration facts.
- `readPolicy` and `writePolicy` for effective retry semantics.
- `circuitBreaker` and `bulkhead` for current in-process resilience state.
- `safety` and `privacy` as the contract proof.

The page must not add a "ping kube-manager", "refresh token", "login", "reset
circuit breaker", or "enable write retry" action around this endpoint.

## Verification

Passed during implementation:

```powershell
mvn -q "-Dtest=AgentKubeManagerHttpOutletHealthSummaryServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

Final checkpoint verification should also include:

```powershell
mvn -q "-DskipTests" validate
git diff --check
```

## Recovery Notes

If work resumes from this checkpoint, continue from M5.49. The next likely
Phase 1 slices are:

- expose Resilience4j metrics in the frontend observability page
- define idempotency key design for future controlled writes
- continue durable audit retention/export enforcement
- continue database/search-backed audit index planning
- continue RAG/persistent Memory and read-only MCP schema adapter
- complete request/intent/plan/LLM/tool/HTTP/HITL/final-answer span mapping
