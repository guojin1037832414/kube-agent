# M5.55 Kube-Manager HTTP Outlet Governance Workbench Overview

Date: 2026-06-09

## Summary

M5.55 adds a Vue-ready, admin-only governance overview for the kube-manager HTTP outlet:

```text
M5.49 health summary
M5.50 write retry readiness
M5.51 idempotency contract
M5.52 operation safety contract
M5.53 retry governance contract
M5.54 release gate contract
        |
        v
AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService
        |
        v
GET /api/agent/observability/kube-manager/http-outlet/governance-workbench/overview
```

The overview is a control-plane/read-model layer for future `vue-kube-manager` pages. It helps operators see the whole write-governance chain without opening any runtime write authority.

## Delivered

- Added `AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse`.
- Added `AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/kube-manager/http-outlet/governance-workbench/overview
```

- Added six governance cards:
  - `http-outlet-health`
  - `write-retry-readiness`
  - `write-idempotency-contract`
  - `write-operation-safety-contract`
  - `write-retry-governance-contract`
  - `write-release-gate-contract`
- Added a recommended frontend workflow:

```text
governance-workbench-overview
http-outlet-health-summary
write-retry-readiness
write-idempotency-contract
write-operation-safety-contract
write-retry-governance-contract
write-release-gate-contract
eval-workbench-gate-bundle-summary
human-release-review-before-runtime-binding
```

## Runtime State

Current expected M5.55 state:

- `workbenchStatus=WRITE_GOVERNANCE_NOT_READY`
- `httpOutletStatus=READY`
- `writeReadinessVerdict=NOT_READY`
- `releaseGateOpen=false`
- `writeRetryEnabled=false`
- `automaticWriteRetryAllowed=false`
- `governanceCardCount=6`
- `blockingCardCount=5`
- `boundRuntimeContractCount=0`
- `runtimeReleaseGateOpenCount=0`
- `runtimeRetryableFailureClassCount=0`
- `automaticCompensationPolicyCount=0`

The health card can be `INFO/READY` because local GET/read resilience is configured. The five write-governance cards remain blocking because they are intentionally contract-defined-but-not-bound.

## Security Boundary

M5.55 does not add:

- kube-manager calls
- `KubeManagerHttpClient` binding
- `RestClient`
- `executeWrite`
- Tool execution
- HITL invocation
- LLM calls
- external calls
- audit writes
- durable receipt issuance
- durable storage mutation
- HTTP header injection
- readback execution
- release switch opening
- Resilience4j registry mutation
- runtime enable switch
- compensation execution
- write retry enablement
- NIM / HPC / Slurm / BCM Phase 2 work

The endpoint is admin-only, local-process-only, read-only, overview-only, and frontend-navigation-only.

## Privacy Boundary

The overview aggregates privacy proofs from the existing read models. It does not expose:

- raw base URL
- raw backend path
- raw endpoint
- Authorization header
- token or token prefix
- login password
- raw principal
- raw organization
- raw request body
- raw response body
- raw release evidence
- raw receipt

## Learning Note

顶级 Agent 的前端工作台不应该靠 Vue 自己拼安全语义，也不应该给高风险能力做“按钮优先”的 UI。

更成熟的路线是：

1. 后端先把每个危险能力拆成稳定契约。
2. 每个契约都明确 `defined`、`bound`、`runtime-enabled` 的区别。
3. Vue 只渲染后端给出的治理状态和下一步动作。
4. 任何真正的写权限、重试权限、release switch 都必须来自新的代码发布、测试、审计和人工/Git review，而不是页面参数、prompt 文本或 caller-provided flags。

M5.55 的意义是把 M5.49-M5.54 的分散证据聚合成一个可教学、可恢复、可前端消费的控制面。它让一期 Agent 更接近“顶级系统”，同时仍然保持 fail-closed。

## Verification

Passed:

```powershell
mvn -q "-Dtest=AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewServiceTest,AgentKubeManagerHttpOutletHealthSummaryServiceTest,AgentKubeManagerWriteRetryReadinessServiceTest,AgentKubeManagerWriteIdempotencyContractServiceTest,AgentKubeManagerWriteOperationSafetyContractServiceTest,AgentKubeManagerWriteRetryGovernanceContractServiceTest,AgentKubeManagerWriteReleaseGateContractServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
