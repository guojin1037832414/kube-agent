# M5.58 Memory/RAG Readiness Contract

Date: 2026-06-09

## Summary

M5.58 adds a backend-owned Memory/RAG readiness contract for the Phase 1 top-tier Agent goal:

```text
current safe summary memory
        |
        v
AgentMemoryRagReadinessService
        |
        | admin-only read model
        v
GET /api/agent/observability/memory-rag/readiness
```

This endpoint is a readiness contract, not a retrieval endpoint. It explains why the current memory layer is safe summary memory only, and what evidence must exist before durable Memory/RAG can enter the runtime prompt path.

## Delivered

- Added `AgentMemoryRagReadinessResponse`.
- Added `AgentMemoryRagReadinessService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/memory-rag/readiness
```

- Updated the top-tier readiness overview so the `memory-rag-learning` card now points to the new readiness contract.
- Added service, controller, source-contract, and MockMvc security coverage.

## Current State

- `schemaVersion=agent-memory-rag-readiness.v1`
- `readinessVerdict=MEMORY_RAG_CONTRACT_DEFINED_NOT_READY`
- `phase1TopTierGoalPreserved=true`
- `currentSafeSummaryMemoryEnabled=true`
- `durableMemoryReady=false`
- `ragReady=false`
- `citationContractReady=false`
- `evalCoverageReady=false`

The endpoint reads only bounded local facts from `ConversationSummaryMemoryStore`:

- current memory user count
- `MAX_SUMMARIES_PER_USER`
- current safe summary memory capability

## Readiness Cards

M5.58 publishes six cards:

- `safe-summary-memory` -> `READY`
- `durable-memory-store` -> `BLOCKED`
- `tenant-and-privacy-governance` -> `PARTIAL`
- `rag-retrieval-layer` -> `BLOCKED`
- `citation-and-source-contract` -> `BLOCKED`
- `eval-and-observability` -> `BLOCKED`

The top gaps are therefore:

- durable memory store with retention, delete, export, and recovery metadata
- tenant-aware persistent memory partitioning
- redacted ingestion and retrieval policy
- citation/source digest contract
- Memory/RAG eval coverage for citation fidelity, privacy leakage, tenant isolation, and stale retrieval
- Vue readiness workbench consumption

## Security Boundary

M5.58 does not add:

- real vector database binding
- runtime retrieval
- embedding model calls
- reranker calls
- LLM calls
- memory writes from the readiness endpoint
- raw conversation exposure
- raw document exposure
- raw retrieved chunk exposure
- Tool execution
- `SafeToolExecutor` invocation
- HITL invocation
- audit writes
- durable receipt issuance
- kube-manager calls
- `RestClient`
- `WebClient`
- MCP runtime `tools/call`
- NIM / HPC / Slurm / BCM Phase 2 implementation

The endpoint is admin-only, read-only, summary-only, and local-process-only.

## Recommended Build Order

The response recommends the next Memory/RAG order:

1. Define the citation/source contract.
2. Add durable memory with retention, deletion, export, and recovery metadata.
3. Bind a tenant-isolated vector store through a reviewed retrieval policy.
4. Add redacted runbook and kube-manager documentation ingestion.
5. Add Memory/RAG eval coverage for citation fidelity, privacy leakage, tenant isolation, and staleness.
6. Wire the Vue Memory/RAG readiness workbench.

## Latest Technology Import Decision

The 2026-06-09 technology line remains:

- Stable mainline: Java 17, Spring Boot 3.5.x, Spring AI 1.1.7, Spring Security, Resilience4j, Micrometer/OTel foundation, deterministic evals, MCP safe manifest/governance, and workspace-local recovery memory.
- Compatibility matrix: Spring Boot 4.x, Spring Framework 7.x, Spring AI 2.x, Java 21/25, full MCP runtime broker, OTel GenAI semantic-convention migration, A2A, GraphRAG, rerankers, multi-vector retrieval, virtual threads, and structured concurrency.

M5.58 intentionally imports the latest Memory/RAG direction as a fail-closed readiness contract first. It does not pretend that vector retrieval, Spring AI VectorStore, GraphRAG, reranking, or citation-grounded prompting are already runtime-bound.

Teaching point: a top-tier Agent does not become smarter by silently adding retrieval. It becomes trustworthy when every remembered or retrieved fact has owner, tenant, retention, redaction, source digest, citation, eval, and replay evidence before it can influence an answer.

## Verification

Required verification for this slice:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=AgentMemoryRagReadinessServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

Optional broader check:

```powershell
mvn -q "-Dtest=MemoryControllerTest,ConversationSummaryMemoryStoreTest,AgentMemoryRagReadinessServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
