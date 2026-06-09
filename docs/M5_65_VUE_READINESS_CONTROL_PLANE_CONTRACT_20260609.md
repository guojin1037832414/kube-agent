# M5.65 Vue Readiness Control Plane Contract

Date: 2026-06-09

## Purpose

M5.65 advances the first M5.64 roadmap step: `vue-readiness-control-plane`.

The new endpoint is:

```text
GET /api/agent/observability/top-tier/vue-readiness-control-plane
```

It gives `vue-kube-manager` a backend-owned, page-level binding contract for Phase 1 top-tier Agent readiness. The repository currently contains the backend project only, so this slice prepares the exact frontend read model without touching the external frontend workspace.

## What The Frontend Can Render

The response publishes seven dashboards:

1. `top-tier-command-center`
2. `advanced-technology-adoption`
3. `phase1-execution-roadmap`
4. `kube-manager-governance`
5. `memory-rag-readiness`
6. `eval-workbench`
7. `mcp-governance`

Each dashboard has a primary endpoint, render fields, read-only status, and `runtimeControlAllowed=false`.

## What The Frontend Must Not Render Yet

The contract explicitly forbids:

- kube-manager write retry enablement;
- kube-manager state-changing calls;
- MCP runtime `tools/call`;
- retrieval into prompt context;
- eval trace-set catalog mutation;
- CI blocking switches;
- durable receipt issuance;
- HITL triggers from readiness pages;
- backend dependency upgrades from UI;
- NIM / HPC / Slurm / BCM Phase 2 reopening.

## Teaching Point

顶级 Agent 的前端不是“把接口都放成按钮”。成熟做法是先让后端发布页面级 read model：哪些卡片能渲染、字段从哪里来、哪些状态代表 blocked/partial/ready、哪些按钮绝对不能出现。M5.65 把这个 UX 治理规则写成后端契约，为后续真正改 `vue-kube-manager` 做准备。

## Verification

Implemented and verified with:

```powershell
mvn -q "-Dtest=AgentVueReadinessControlPlaneServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
