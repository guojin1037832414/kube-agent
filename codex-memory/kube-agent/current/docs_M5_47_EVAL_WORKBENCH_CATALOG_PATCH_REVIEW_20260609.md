# M5.47 Eval Workbench Catalog Patch Review Model

Date: 2026-06-09 Asia/Shanghai

## Scope

M5.47 adds a page-ready catalog patch review model for the future `vue-kube-manager` eval workbench.

New endpoint:

- `POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review`

New backend contracts:

- `AgentEvalWorkbenchCatalogPatchReviewResponse`
- `AgentEvalWorkbenchCatalogPatchReviewService`

Updated contracts:

- `AgentEvalWorkbenchCapabilitiesService` now exposes `workbench-catalog-patch-review`.
- `AgentEvalWorkbenchTraceSetDetailResponse` now exposes `workbenchCatalogPatchReview` in endpoint templates.
- `ObservabilityController` now exposes the admin-only workbench review endpoint.

## Why This Matters

The raw catalog patch proposal is a backend evidence artifact. A frontend workbench needs a safer operator contract:

- sanitized patch operations
- trace delta
- candidate gate summary
- review checklist
- next actions
- endpoint templates
- workbench policy
- privacy proof

This lets Vue render a Git-review page without treating a JSON Patch proposal as an auto-apply action.

## Security Boundary

The new endpoint is admin-only and review-only.

It does not:

- mutate `observability/eval-trace-sets.json`
- apply JSON Patch
- write runtime catalog data
- execute Tool code
- call kube-manager
- call an LLM
- make external network calls
- embed replay timeline payloads
- embed per-trace eval reports

Catalog promotion authority remains human Git review only.

NIM / HPC / Slurm / BCM remain Phase 2 paused scope.

## Frontend Contract

The response keeps two layers separate:

- `proposal`: raw review-only backend artifact for advanced evidence inspection.
- `patchOperations`: sanitized UI rows for the default review page.

The default Vue flow should render `patchOperations`, `traceDelta`, `candidateGateSummary`, `reviewChecklist`, and `nextActions`. It should not add an "apply patch" runtime action. If a patch is accepted, the operator should copy the proposal into normal Git review, merge it, then regenerate the trace-set gate bundle.

## Verification

Passed during implementation:

```powershell
mvn -q "-Dtest=AgentEvalWorkbenchCatalogPatchReviewServiceTest,AgentEvalWorkbenchPromotionWorkflowServiceTest,AgentEvalWorkbenchTraceSetDetailServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

Final checkpoint verification also passed:

```powershell
mvn -q "-DskipTests" validate
git diff --check
```

## Recovery Notes

If work resumes from this checkpoint, continue from M5.47. The next likely Phase 1 slices are:

- Vue eval workbench integration against capability, overview, detail, promotion workflow, and catalog patch review contracts.
- Reviewed catalog patch UX that defaults to sanitized rows and never performs runtime catalog mutation.
- Real redacted trace population workflow after human/Git review, so CI can later move from evidence-only to blocking mode.
- Durable audit retention/export enforcement and search/database-backed audit index planning.
