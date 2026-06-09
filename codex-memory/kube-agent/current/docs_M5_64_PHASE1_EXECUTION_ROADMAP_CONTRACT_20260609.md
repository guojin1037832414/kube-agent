# M5.64 Phase 1 Execution Roadmap Contract

Date: 2026-06-09

## Purpose

M5.64 turns the latest Phase 1 plan into a backend-owned roadmap contract:

```text
top-tier Phase 1 goal
        |
        +-- current backend read models
        |
        +-- ordered execution steps
        |
        +-- dependency gates and do-not-start-yet boundaries
```

The new endpoint is:

```text
GET /api/agent/observability/top-tier/phase1-execution-roadmap
```

It is admin-only, read-only, roadmap-only, and fail-closed. It does not run evals, mutate trace sets, bind retrieval, call LLMs, execute Tools, invoke `SafeToolExecutor`, invoke HITL, call kube-manager, open MCP `tools/call`, write audit, issue durable receipts, write memory, upgrade dependencies, or touch NIM / HPC / Slurm / BCM Phase 2 scope.

## Execution Order

The roadmap publishes eight ordered Phase 1 steps:

1. `vue-readiness-control-plane`
2. `reviewed-eval-trace-evidence`
3. `release-blocking-eval-gates`
4. `memory-rag-eval-suite-binding`
5. `durable-memory-store-binding`
6. `retrieval-runtime-binding`
7. `mcp-runtime-safe-call-plane`
8. `agent-handoff-and-a2a-provenance`

## Dependency Gates

The roadmap keeps advanced technology adoption disciplined:

- `admin-auth-required`
- `safe-tool-executor-only`
- `trace-audit-replay-required`
- `eval-before-runtime`
- `vue-read-model-before-control`
- `kube-manager-write-authority-closed`
- `phase2-domain-pause`

## Teaching Point

顶级 Agent 的路线图不能只存在于聊天记录里。它应该成为后端契约，让前端、测试、文档、恢复记忆和后续开发都引用同一份顺序表。

M5.64 的核心学习点是：先进技术不是“同时打开所有能力”，而是先把能力排序、门禁、证据和禁止项写成可测试契约。Vue 先消费只读状态；eval 先拥有 reviewed trace evidence；Memory/RAG 先通过 source digest、lifecycle 和 eval gate；MCP runtime 和多 Agent handoff 只能在身份、同意、HITL、审计、限流、eval 和 `SafeToolExecutor` 全部闭环后进入运行时。

## Current Official Version Signals

Checked on 2026-06-09:

- Spring Boot official docs list `4.0.6` and `3.5.14` as stable lines, so Boot 4 belongs in the compatibility/migration lane while the current mainline stays buildable.
- Spring AI official docs list `1.1.7` as stable and `2.0.0-RC1` as preview; Spring AI Memory/RAG remains in Phase 1, while a 2.x migration needs a separate gate.
- MCP official tools spec defines `tools/list` and `tools/call`, and explicitly calls for human-in-the-loop safety around tool invocation; this matches the roadmap's safe call plane.
- OpenTelemetry GenAI semantic conventions are still marked `Development`, so kube-agent keeps stable internal `atlas.agent.*` fields and maps outward through a compatibility layer.

## Verification

Implemented and verified with:

```powershell
mvn -q "-Dtest=AgentPhase1ExecutionRoadmapServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
