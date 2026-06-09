# M5.61 Memory/RAG Durable Memory Lifecycle Contract

Date: 2026-06-09

## Summary

M5.61 adds the durable Memory/RAG lifecycle contract for future persistent memory storage:

```text
safe summary memory + source/citation/digest contracts
        |
        | lifecycle evidence required before persistence
        v
tenant partition + retention + delete proof + export proof
        + recovery checkpoint + source ACL + eval gate
        |
        | admin-only read model
        v
GET /api/agent/observability/memory-rag/durable-memory-lifecycle-contract
```

This slice keeps Phase 1 top-tier standards high while staying contract-only. It does not add a database table, vector index, retrieval runtime, delete job, export job, recovery job, or prompt evidence injection.

## Delivered

- Added `AgentMemoryRagDurableMemoryLifecycleContractResponse`.
- Added `AgentMemoryRagDurableMemoryLifecycleContractService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/memory-rag/durable-memory-lifecycle-contract
```

- Updated `ObservabilityController` constructor and endpoint map.
- Updated Memory/RAG readiness:
  - `durable-memory-store` is now `PARTIAL`, not ready.
  - `durableMemoryLifecycleContractDefined=true`.
  - `durableMemoryLifecycleContractBound=false`.
  - endpoint map includes `durableMemoryLifecycleContract`.
- Updated top-tier readiness:
  - `memory-rag-learning` evidence includes `durableMemoryLifecycleContractImplemented=true`.
  - `durableMemoryLifecycleContractBound=false`.
  - endpoint map includes `memoryRagDurableMemoryLifecycleContract`.
- Added service, controller, source-contract, readiness/top-tier, and MockMvc security coverage.

## Contract

The durable lifecycle contract defines these required future fields:

- `memoryRecordId`
- `tenantPartitionDigest`
- `sourceEvidenceDigest`
- `retentionPolicyId`
- `deleteProofDigest`
- `exportProofDigest`
- `recoveryCheckpointDigest`
- `evalGateDigest`

It also defines seven rule groups:

- tenant partition rules
- retention rules
- deletion proof rules
- export proof rules
- recovery rules
- eval gate rules
- blocked-until / recommended build-order rules

Current status remains intentionally unbound:

- `contractStatus=CONTRACT_DEFINED_NOT_BOUND`
- `lifecycleContractDefined=true`
- `boundToDurableStoreRuntime=false`
- `retentionEnforcedNow=false`
- `deleteEndpointImplemented=false`
- `exportEndpointImplemented=false`
- `recoveryCheckpointBound=false`
- `promptEvidenceAllowedNow=false`

## Latest Technology Alignment

The design was checked against current public technical directions on 2026-06-09:

- OpenAI Agents SDK exposes modern Agent primitives such as handoffs, guardrails, sessions, human-in-the-loop, and tracing. M5.61 mirrors that spirit by making memory lifecycle evidence explicit before runtime prompt use: https://openai.github.io/openai-agents-python/
- MCP defines resources, prompts, tools, roots, sampling, elicitation, and explicit consent/safety expectations. M5.61 keeps future MCP memory/resource evidence separate from runtime `tools/call`: https://modelcontextprotocol.io/specification/2025-06-18
- Spring AI `VectorStore` separates retrieval and mutation capabilities and supports metadata filters. M5.61 prepares lifecycle metadata before any vector-store binding: https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- OpenTelemetry GenAI semantic conventions are still marked Development, so this project keeps stable internal evidence fields now and maps to OTel later: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A artifacts represent task outputs with metadata. M5.61 prepares `exportProofDigest` / `recoveryCheckpointDigest` style artifact anchors for future cross-agent provenance: https://a2a-protocol.org/latest/specification/

Teaching point: top-tier durable memory is a lifecycle system, not just persistence. A remembered fact needs owner, tenant boundary, source custody, retention policy, delete/export proof, recovery proof, eval gate, and frontend visibility before it should ever influence a prompt.

## Security Boundary

M5.61 does not add:

- real durable memory store
- database calls
- vector store binding
- embedding calls
- reranker calls
- LLM calls
- document ingestion runtime
- retrieval execution
- prompt mutation
- memory writes
- retention purge job
- delete endpoint or delete executor
- export endpoint or export archive
- recovery checkpoint writer or rebuild job
- Tool execution
- `SafeToolExecutor` invocation
- HITL invocation
- audit writes
- durable receipt issuance
- kube-manager calls
- `RestClient`
- `WebClient`
- MCP runtime `tools/call`
- NIM / HPC / Slurm / BCM Phase 2 work

The endpoint is admin-only, read-only, contract-only, and fail-closed.

## Verification

Required verification for this slice:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=AgentMemoryRagDurableMemoryLifecycleContractServiceTest,AgentMemoryRagSourceEvidenceDigestContractServiceTest,AgentMemoryRagCitationSourceContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
