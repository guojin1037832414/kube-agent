# M5.46 Eval Workbench Promotion Workflow Result Model

Date: 2026-06-09 Asia/Shanghai

## Scope

M5.46 adds a page-ready promotion workflow result model for the future `vue-kube-manager` eval workbench.

New endpoint:

- `POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/promotion-workflow`

New backend contracts:

- `AgentEvalWorkbenchPromotionWorkflowResponse`
- `AgentEvalWorkbenchPromotionWorkflowService`

Updated contracts:

- `AgentEvalWorkbenchCapabilitiesService` now exposes `workbench-promotion-workflow`.
- `AgentEvalWorkbenchTraceSetDetailResponse` now exposes `workbenchPromotionWorkflow` in endpoint templates.
- `ObservabilityController` now exposes the admin-only workbench wrapper endpoint.

## Why This Matters

The raw promotion workflow artifact is excellent backend evidence, but a frontend workbench needs a page contract:

- UI steps
- patch summary
- candidate gate summary
- next actions
- endpoint templates
- workbench policy
- privacy proof

This prevents Vue pages from reconstructing release-governance semantics by stitching together raw discovery, review, patch, replay, and eval endpoints.

## Security Boundary

The new endpoint is admin-only and wrapper-only.

It may run the existing redacted promotion workflow, but it does not:

- mutate `observability/eval-trace-sets.json`
- write runtime catalog data
- execute Tool code
- call kube-manager
- call an LLM
- make external network calls
- embed replay timeline payloads
- embed per-trace eval reports

Catalog promotion authority remains human Git review only.

NIM / HPC / Slurm / BCM remain Phase 2 paused scope.

## Verification

Passed:

```powershell
mvn -q "-Dtest=AgentEvalWorkbenchPromotionWorkflowServiceTest,AgentEvalWorkbenchTraceSetDetailServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## Recovery Notes

If work resumes from this checkpoint, continue from M5.46. The next likely Phase 1 slices are:

- Vue eval workbench integration against capability / overview / detail / workbench promotion workflow contracts.
- Reviewed catalog patch UX around `workbenchPromotionWorkflow.patchSummary`.
- Real redacted trace population workflow after human/Git review, so CI can later move from evidence-only to blocking mode.
- Durable audit retention/export enforcement and search/database-backed audit index planning.
