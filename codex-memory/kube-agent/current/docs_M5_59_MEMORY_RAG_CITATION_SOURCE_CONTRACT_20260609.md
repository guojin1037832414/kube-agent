# M5.59 Memory/RAG Citation Source Contract

Date: 2026-06-09

## Summary

M5.59 adds the first Memory/RAG citation and source contract for future retrieval evidence:

```text
future retrieved source evidence
        |
        v
AgentMemoryRagCitationSourceContractService
        |
        | admin-only read model
        v
GET /api/agent/observability/memory-rag/citation-source-contract
```

This endpoint is a contract, not a retriever. It defines what source and citation evidence must exist before any future RAG result can enter a runtime prompt.

## Delivered

- Added `AgentMemoryRagCitationSourceContractResponse`.
- Added `AgentMemoryRagCitationSourceContractService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/memory-rag/citation-source-contract
```

- Updated M5.58 readiness to expose `citationSourceContractDefined=true` and a `citationSourceContract` endpoint link.
- Added service, controller, source-contract, and MockMvc security coverage.

## Current State

- `schemaVersion=agent-memory-rag-citation-source-contract.v1`
- `contractStatus=CONTRACT_DEFINED_NOT_BOUND`
- `contractDefined=true`
- `boundToRetrievalRuntime=false`
- `citationRequired=true`
- `uncitedAnswerAllowed=false`
- `rawDocumentExposureAllowed=false`
- `promptEvidenceAllowedNow=false`

## Contract Fields

Required future source evidence:

- `sourceId`
- `sourceType`
- `sourceDigest`
- `tenantScope`
- `redactionStatus`
- `retentionPolicy`

Required future citation evidence:

- `citationId`
- `sourceDigest`
- `chunkDigest`
- `retrievalReason`
- `freshness`

Required future prompt evidence rules:

- redacted evidence only
- citations required for RAG-influenced answers
- tenant scope match required
- prompt evidence budget required
- eval gate required

## Security Boundary

M5.59 does not add:

- real retrieval
- vector store binding
- embedding model calls
- reranker calls
- LLM calls
- prompt mutation
- memory writes
- document ingestion
- raw document exposure
- raw prompt exposure
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
- NIM / HPC / Slurm / BCM Phase 2 work

The endpoint is admin-only, read-only, contract-only, and fail-closed.

## Recommended Build Order

The response recommends:

1. Implement source evidence DTO and digest derivation.
2. Bind durable memory with retention/delete/export metadata.
3. Add redacted kube-manager documentation and runbook ingestion.
4. Bind tenant-aware retrieval policy and prompt evidence budget.
5. Add citation fidelity, privacy, tenant isolation, and staleness evals.
6. Wire a Vue citation/source workbench.

Teaching point: top-tier RAG is not "retrieve chunks and paste them into a prompt." It is a chain of custody. Every source must be scoped, redacted, digested, retained, cited, evaluated, and visible to operators before it can affect an answer.

## Verification

Required verification for this slice:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=AgentMemoryRagCitationSourceContractServiceTest,AgentMemoryRagReadinessServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
