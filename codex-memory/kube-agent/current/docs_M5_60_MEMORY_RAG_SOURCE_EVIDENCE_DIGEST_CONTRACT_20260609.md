# M5.60 Memory/RAG Source Evidence Digest Contract

Date: 2026-06-09

## Summary

M5.60 adds the deterministic source evidence digest contract for future Memory/RAG retrieval:

```text
redacted source evidence
        |
        | pure Java SHA-256 derivation
        v
MemoryRagSourceEvidenceDigestDeriver
        |
        | admin-only read model
        v
GET /api/agent/observability/memory-rag/source-evidence-digest-contract
```

This is the next chain-of-custody layer after M5.59. It defines how future source, chunk, and evidence digests are derived before any retrieved material can influence a prompt.

## Delivered

- Added `MemoryRagSourceEvidenceInput`.
- Added `MemoryRagSourceEvidenceDigestResult`.
- Added `MemoryRagSourceEvidenceDigestDeriver`.
- Added `AgentMemoryRagSourceEvidenceDigestContractResponse`.
- Added `AgentMemoryRagSourceEvidenceDigestContractService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/memory-rag/source-evidence-digest-contract
```

- Updated Memory/RAG readiness to expose `sourceEvidenceDigestContractDefined=true` and `sourceEvidenceDigestContractBound=false`.
- Updated the citation/source contract to require server-derived source evidence digests.
- Updated the top-tier readiness endpoint map so operators can navigate to the new contract.
- Added pure-Java digest tests, service tests, controller tests, source-contract tests, and MockMvc security coverage.

## Contract

The source evidence input accepts only stable ids, bounded enums, and SHA-256 digests:

- `sourceId`
- `sourceType`
- `sourceVersion`
- `sourceUriDigest`
- `tenantScopeDigest`
- `sourceAclDigest`
- `redactionStatus`
- `redactionPolicyDigest`
- `retentionPolicy`
- `sourceContentDigest`
- `sourceMetadataDigest`
- `chunkContentDigest`
- `retrievalPolicyDigest`

The deriver produces:

- `sourceDigest`
- `chunkDigest`
- `evidenceDigest`
- `citationSeed`
- `digestSource=server-derived-sha256-redacted-source-evidence.v1`

The result keeps `rawSourceAccepted=false`, `promptEvidenceAllowedNow=false`, `boundToIngestionRuntime=false`, and `reusableAcrossTenantScope=false`.

## Latest Technology Alignment

M5.60 keeps the Java mainline buildable while aligning the contract with current Agent engineering directions:

- Spring AI `VectorStore` metadata can later store `sourceDigest`, `chunkDigest`, tenant scope, and redaction metadata.
- MCP `resources` and future `tools/call` evidence can map to the same source digest boundary before runtime use.
- A2A task artifacts can carry `evidenceDigest` / `citationSeed` as cross-agent provenance anchors.
- OpenTelemetry GenAI retrieval spans can map internal digest fields to stable observability attributes.
- OpenAI-style Agent guardrails and handoffs can treat source evidence digests as the proof that retrieved context is allowed to enter a prompt.

Teaching point: top-tier RAG is not a vector database feature. It is a custody protocol. Retrieval quality matters, but every fact also needs identity, tenant scope, redaction proof, digest, citation, freshness, eval coverage, and operator visibility.

## Security Boundary

M5.60 does not add:

- real ingestion
- real retrieval
- vector store binding
- embedding model calls
- reranker calls
- LLM calls
- prompt mutation
- memory writes
- document ingestion runtime
- raw source exposure
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

## Verification

Required verification for this slice:

```powershell
mvn -q "-DskipTests" validate
git diff --check
mvn -q "-Dtest=MemoryRagSourceEvidenceDigestDeriverTest,AgentMemoryRagSourceEvidenceDigestContractServiceTest,AgentMemoryRagCitationSourceContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
