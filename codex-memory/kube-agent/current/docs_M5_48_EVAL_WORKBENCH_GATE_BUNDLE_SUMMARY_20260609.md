# M5.48 Eval Workbench Gate Bundle Summary

Date: 2026-06-09 Asia/Shanghai

## Scope

M5.48 adds a page-ready gate bundle summary model for the future `vue-kube-manager` eval workbench.

New endpoint:

- `GET /api/agent/observability/eval/workbench/gate-bundle-summary`

New backend contracts:

- `AgentEvalWorkbenchGateBundleSummaryResponse`
- `AgentEvalWorkbenchGateBundleSummaryService`

Updated contracts:

- `AgentEvalWorkbenchCapabilitiesService` now exposes `workbench-gate-bundle-summary`.
- `AgentEvalWorkbenchTraceSetDetailResponse` now exposes `workbenchGateBundleSummary` in endpoint templates.
- Promotion workflow and catalog patch review responses now expose `workbenchGateBundleSummary`.
- `ObservabilityController` now exposes the admin-only workbench summary endpoint.

## Why This Matters

The raw trace-set gate bundle is a compact CI artifact. A frontend workbench needs a safer operator summary:

- bundle summary
- trace-set gate rows
- CI artifact metadata
- blocker summary
- next actions
- endpoint templates
- workbench policy
- privacy proof

This lets Vue render release-gate status without accepting request trace IDs or turning a page view into a CI blocking switch.

The summary may expose curated trace ID anchors to admin users for drill-down. Those anchors are references to persisted redacted replay evidence; they are not raw principals, organizations, conversations, kube-manager endpoints, reasons, parameter values, replay timelines, or per-trace reports.

## Security Boundary

The new endpoint is admin-only, read-only, and summary-only.

It does not:

- accept caller-provided trace IDs
- mutate `observability/eval-trace-sets.json`
- write runtime catalog data
- enable CI blocking
- execute Tool code
- call kube-manager
- call an LLM
- make external network calls
- embed replay timeline payloads
- embed per-trace eval reports

Catalog promotion authority remains human Git review only. CI blocking remains disabled until reviewed real redacted trace evidence exists.

NIM / HPC / Slurm / BCM remain Phase 2 paused scope.

## Frontend Contract

The default Vue flow should render:

- `bundleSummary` for headline status.
- `traceSetGateRows` for the table.
- `ciArtifact` for artifact path and enablement condition.
- `blockerSummary` for why the bundle is not release-blocking.
- `nextActions` for operator workflow.

The page must not add a runtime "enable CI blocking" or "apply catalog patch" action.

## Verification

Passed during implementation:

```powershell
mvn -q "-Dtest=AgentEvalWorkbenchGateBundleSummaryServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchTraceSetDetailServiceTest,AgentEvalWorkbenchPromotionWorkflowServiceTest,AgentEvalWorkbenchCatalogPatchReviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

Final checkpoint verification also passed:

```powershell
mvn -q "-DskipTests" validate
git diff --check
```

## Recovery Notes

If work resumes from this checkpoint, continue from M5.48. The next likely Phase 1 slices are:

- Vue eval workbench integration across capability, overview, detail, promotion workflow, catalog patch review, and gate bundle summary contracts.
- Reviewed real redacted trace population workflow after human/Git review.
- CI transition planning from evidence-only (`ciBlockingEnabled=false`) to reviewed blocking mode.
- Durable audit retention/export enforcement and database/search-backed audit index planning.
