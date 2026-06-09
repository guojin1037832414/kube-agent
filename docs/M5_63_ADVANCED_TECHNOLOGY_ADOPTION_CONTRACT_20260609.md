# M5.63 Advanced Technology Adoption Contract

Date: 2026-06-09

## Purpose

M5.63 turns the owner's "introduce all advanced technologies" requirement into a backend-owned adoption contract:

```text
latest Agent technology
        |
        +-- stable mainline: buildable, testable, auditable, recoverable now
        |
        +-- compatibility matrix: advanced direction that needs evidence before runtime
```

The new endpoint is:

```text
GET /api/agent/observability/top-tier/advanced-technology-adoption-contract
```

It is admin-only, read-only, contract-only, and fail-closed. It does not upgrade dependencies, bind new external runtimes, call LLMs, execute Tools, call kube-manager, open MCP `tools/call`, mutate memory, write audit, or touch NIM / HPC / Slurm / BCM Phase 2 scope.

## What Enters The Stable Mainline

- Java / Spring remains the Phase 1 control plane because the project needs typed security, audit, Tool authority boundaries, tests, recovery memory, and long-term maintainability.
- Spring AI 1.1.x remains the verified model/tool access layer while Spring AI 2.x is tracked in compatibility work.
- `SafeToolExecutor`, HITL guard, protected parameter filtering, and durable audit evidence remain the real execution boundary.
- Deterministic eval workbench, trace sets, gate bundles, and review artifacts remain the release quality path.
- Memory/RAG continues contract-first: readiness, citation, source digest, durable lifecycle, and eval gate before runtime retrieval.
- MCP remains manifest/governance first until runtime `tools/call` can be bound to identity, consent, HITL, audit, eval, rate limits, and `SafeToolExecutor`.

## What Enters The Compatibility Matrix

- Java 21 / 25 / 26 toolchains, including virtual threads and structured concurrency validation.
- Spring Boot 4 / Spring Framework 7 migration.
- Spring AI 2.x API migration.
- OpenAI Responses/Agents-style tools, tracing, handoffs, and guardrails mapped to local contracts.
- Full MCP runtime server / broker, including `tools/list`, `tools/call`, structured output, consent, and rate limits.
- OpenTelemetry GenAI semantic conventions mapped through stable internal `atlas.agent.*` fields.
- A2A / Agent artifact provenance.
- Hybrid retrieval, GraphRAG, rerankers, and multi-vector stores.

## Teaching Point

顶级 Agent 的“先进”不是盲目堆版本号。真正先进的是采用闸门：

- 先有 source-owned contract，再有 runtime binding。
- 先有 identity / tenant / privacy proof，再有外部暴露。
- 先有 trace / audit / replay，再有危险能力。
- 先有 deterministic eval 和 reviewed trace evidence，再有 release blocking 或 prompt influence。
- 先有 Vue read model，再有任何 runtime control button。

This keeps Phase 1 top-tier while still allowing the project to learn and adopt the newest Agent ecosystem in a controlled way.

## Verification

Implemented and verified with:

```powershell
mvn -q "-Dtest=AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
