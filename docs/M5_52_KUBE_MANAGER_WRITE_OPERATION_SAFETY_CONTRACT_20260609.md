# M5.52 Kube-Manager Write Operation Safety Contract

> Date: 2026-06-09
> Scope: Phase 1 generic kube-manager Agent Core
> Status: contract defined, not bound to runtime writes

## Goal

M5.52 closes the next safe prerequisite before any future kube-manager write retry can be considered:

- write operation allowlist contract
- RBAC / tenant evidence contract
- post-write readback contract

This milestone does not make writes executable. It defines the shape of the evidence that future code must satisfy before a write can be retried, verified, or released.

## Delivered

- Added `KubeManagerWriteOperationAllowlistEntry`.
- Added `KubeManagerPostWriteReadbackContract`.
- Added `KubeManagerWriteSafetyContractCatalog`.
- Added `AgentKubeManagerWriteOperationSafetyContractResponse`.
- Added `AgentKubeManagerWriteOperationSafetyContractService`.
- Added admin-only `GET /api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract`.
- Updated write retry readiness evidence:
  - `genericWriteOperationAllowlistExists=true`
  - `genericWriteOperationAllowlistBoundToHttpOutlet=false`
  - `genericWriteOperationAllowlistEnforcedByHttpOutlet=false`
  - `operationRbacEvidenceContractExists=true`
  - `operationRbacEvidenceBoundToHttpOutlet=false`
  - `runtimeRetryEligibleWriteOperationCount=0`
  - `callerProvidedAllowlistEntryAccepted=false`
  - `postWriteReadbackContractExists=true`
  - `postWriteReadbackBoundToHttpOutlet=false`
  - `postWriteReadbackExecutorExists=false`
  - `postWriteReadbackExecutedByReadinessEndpoint=false`
  - `postWriteReadbackAcceptsCallerClaims=false`
  - `postWriteReadbackCanOpenReleaseSwitch=false`
  - `phase2NimHpcSlurmBcmWriteOperationsExcluded=true`

## Architecture

```text
KubeManagerWriteSafetyContractCatalog
    |
    | owns source-of-truth review-only entries
    v
AgentKubeManagerWriteOperationSafetyContractService
    |
    | admin-only read model
    v
GET /api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract
    |
    | contract defined, not bound
    v
Operator / future Vue observability page
```

The catalog is intentionally static and source-owned. It does not scan `ToolRegistry`, `tool/impl`, or kube-manager runtime routes. That prevents existing high-risk Tools from being silently promoted into a write allowlist.

## Safety Boundary

M5.52 is read-only and local-process-only:

- no kube-manager call
- no `KubeManagerHttpClient` binding
- no `RestClient`
- no `executeWrite`
- no readback execution
- no HTTP header injection
- no idempotency header binding
- no audit write
- no durable receipt issuance
- no Tool execution
- no LLM call
- no external call
- no resilience registry mutation
- no runtime enable switch
- no write retry enablement
- no caller-provided allowlist entry
- no caller success claim for post-write readback

NIM / HPC / Slurm / BCM remain Phase 2 paused scope. M5.52 only defines generic tenant-scoped operation classes.

## Learning Notes

Top-tier Agent engineering is not "turn on powerful writes quickly." It is the discipline of turning future dangerous abilities into explicit, typed, tested, observable contracts before any runtime authority exists.

Write retry is especially dangerous because it can amplify side effects. Before retry can ever be bound to real kube-manager writes, the system needs:

- server-derived idempotency key
- durable prewrite receipt
- operation allowlist
- RBAC and tenant evidence
- HITL / release evidence
- bounded retry predicate
- post-write readback
- compensation policy
- redacted replay and eval gate evidence

M5.50 defined the fail-closed readiness surface. M5.51 defined server-derived idempotency. M5.52 defines the operation allowlist/RBAC/readback safety catalog. The project is deliberately moving from "can we call an API?" toward "can we prove this Agent is allowed, observable, recoverable, and evaluable?"

## Advanced Technology Position

The newest Agent ecosystem points in the same direction:

- OpenAI Agents SDK emphasizes tools, handoffs, guardrails, and tracing.
- MCP standardizes tool/resource integration, but authorization and tool safety must still be enforced by the host system.
- OpenTelemetry GenAI semantic conventions are useful for future span naming, but they are still development-stage and should stay behind compatibility layers.
- Durable execution systems such as Temporal are valuable for long-running, crash-resumable agent workflows, but Phase 1 should first finish local durable audit and release evidence contracts.

Therefore the project continues to use Java/Spring as the core control plane while importing the best ideas from these systems as tested contracts: guardrails, traceability, MCP compatibility, durable evidence, and eval gates.

## Verification

Passed:

```powershell
mvn -q "-DskipTests" validate
mvn -q "-Dtest=KubeManagerWriteSafetyContractCatalogTest,AgentKubeManagerWriteOperationSafetyContractServiceTest,AgentKubeManagerWriteRetryReadinessServiceTest,AgentKubeManagerWriteIdempotencyContractServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

`git diff --check` is run during final verification before commit.
