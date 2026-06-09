# M5.71 Memory/RAG Trace-Set Curation Contract

## Goal

M5.71 adds an admin-only read model for the three Memory/RAG trace-set evidence lanes created in M5.70.

The endpoint answers a precise question:

```text
Are the Memory/RAG trace-set catalog rows, policy latches, suite runtime latch,
and reviewed-evidence gaps visible enough for Vue and Git review?
```

It does not run evals, query raw audit, mutate trace-set catalogs, accept caller trace ids, execute retrieval, bind vector stores, call models, execute Tools, call kube-manager, or expose MCP runtime `tools/call`.

## Endpoint

```text
GET /api/agent/observability/memory-rag/trace-set-curation-contract
```

Current status:

```text
TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED
```

## What Changed

- Added `AgentMemoryRagTraceSetCurationContractResponse`.
- Added `AgentMemoryRagTraceSetCurationContractService`.
- Added the admin-only controller endpoint.
- Added direct controller tests and WebMvc security tests for anonymous, normal user, and admin access.
- Added readiness and Phase 1 roadmap endpoint-map integration.
- Fixed the Memory/RAG readiness evidence card so it no longer exceeds Java `Map.of` limits.
- Added a suite-level runtime latch view for `memory-rag-release-gate`.
- Added per-trace-set row fields for Vue:
  - `rowStatus`
  - `policyKeysPresent`
  - `missingPolicyKeys`
  - `policyMismatches`
  - `policyLatchDeclaredClosed`
  - `blockedReasons`
  - `missingEvidence`
- Changed policy evaluation from "safe default values" to explicit declaration:
  missing policy keys now become visible blockers.
- Added tests proving missing trace-set policy keys and opened suite runtime latches fail closed.

## Contract Shape

Top-level fields include:

- `suiteRuntimePolicyClosed=true`
- `allRequiredTraceSetsDefined=true`
- `allRequiredTraceSetsPolicyClosed=true`
- `reviewedTraceEvidenceCurated=false`
- `evalRuntimeAllowedNow=false`
- `retrievalRuntimeAllowedNow=false`
- `ciBlockingAllowedNow=false`
- `requiredTraceSetCount=3`
- `definedTraceSetCount=3`
- `reviewedTraceSetCount=0`

Required trace sets:

```text
memory-rag-citation-fidelity
memory-rag-privacy-tenant
memory-rag-lifecycle-policy
```

Each row is currently:

```text
rowStatus=REVIEWED_EVIDENCE_MISSING
policyLatchDeclaredClosed=true
traceIdCount=0
traceIdsVisibleInContract=false
runtimeExecutionAllowedNow=false
```

## Safety Lesson

顶级 Agent 不能依赖“默认值看起来安全”。如果 catalog key 被误删，系统必须能告诉前端和审查者：

```text
policyKeysPresent=false
missingPolicyKeys=[...]
policyLatchDeclaredClosed=false
blockedReasons=[trace-set-policy-keys-missing, trace-set-policy-latch-not-closed]
```

This is the difference between a production-looking Agent and a top-tier Agent: safety evidence must be explicit, testable, and visible.

## Latest-Technology Position

M5.71 keeps Phase 1 top-tier by adopting modern Agent engineering as evidence contracts first:

- Java/Spring remains the verified control plane.
- Spring AI Memory/RAG, VectorStore, MCP, eval, and observability stay in the adoption path after gates pass.
- MCP tools/resources/prompts are treated as governed capability surfaces, not automatic runtime authority.
- OpenTelemetry GenAI conventions remain adapter targets while `atlas.agent.*` stays the stable internal telemetry contract.
- OpenAI Agents/Responses-style tools, handoffs, guardrails, sessions, tracing, HITL, and eval loops map to kube-agent contracts and deterministic gates.
- A2A Agent Card/task/artifact provenance remains future Phase 1 handoff work after local evidence gates mature.
- GraphRAG, rerankers, and vector stores remain Phase 1 core targets after reviewed Memory/RAG trace ids and advisory gate bundles exist.

Official references checked on 2026-06-09:

- Spring Boot reference: https://docs.spring.io/spring-boot/
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/

## Verification

```powershell
mvn -q "-Dtest=AgentMemoryRagTraceSetCurationContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentPhase1ExecutionRoadmapServiceTest,ObservabilityControllerTest,AgentSecurityConfigWebMvcTest" test
```

## Safety Boundary

M5.71 adds no eval runtime execution, reviewed trace promotion, trace-set catalog mutation, candidate discovery, curation review execution, CI blocking enablement, retrieval execution, vector-store binding, embedding/reranker/LLM call, prompt mutation, memory write, audit write, Tool execution, `SafeToolExecutor` invocation, HITL invocation, kube-manager call, MCP runtime `tools/call`, external call, dependency upgrade, or NIM / HPC / Slurm / BCM Phase 2 work.
