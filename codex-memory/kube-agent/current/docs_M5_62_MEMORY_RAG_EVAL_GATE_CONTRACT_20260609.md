# M5.62 Memory/RAG Eval Gate Contract

Date: 2026-06-09

## Summary

M5.62 adds the Memory/RAG eval gate contract that future persistent memory and retrieval must pass before any evidence can influence prompts:

```text
source evidence digest + durable lifecycle contract
        |
        | deterministic eval gate contract
        v
citation fidelity + source digest integrity + privacy leakage
        + tenant isolation + retention/staleness
        + delete/export/recovery proof + retrieval policy budget
        |
        | admin-only read model
        v
GET /api/agent/observability/memory-rag/eval-gate-contract
```

This closes the next contract gap after M5.61. The system can now describe the eval evidence required before runtime retrieval, without actually executing evals, retrieval, vector stores, LLMs, Tools, or kube-manager calls.

## Delivered

- Added `AgentMemoryRagEvalGateContractResponse`.
- Added `AgentMemoryRagEvalGateContractService`.
- Added admin-only endpoint:

```text
GET /api/agent/observability/memory-rag/eval-gate-contract
```

- Updated Memory/RAG readiness:
  - `memoryRagEvalGateContractDefined=true`.
  - `memoryRagEvalGateContractBound=false`.
  - `eval-and-observability` is now `PARTIAL`, not ready.
  - endpoint map includes `memoryRagEvalGateContract`.
- Updated top-tier readiness:
  - `memory-rag-learning` evidence includes `memoryRagEvalGateContractImplemented=true`.
  - `memoryRagEvalGateContractBound=false`.
  - recommended build order now requires eval-gate binding before retrieval runtime.
- Added service, controller, source-contract, readiness/top-tier, and MockMvc security coverage.

## Contract

The eval gate input evidence requires:

- `traceSetId`
- `evalSuiteId`
- `sourceEvidenceDigest`
- `durableLifecycleDigest`
- `retrievalPolicyDigest`
- `tenantPartitionDigest`
- `expectedCitationSeed`
- `redactionPolicyDigest`

The gate checks are:

- `citation-fidelity`
- `source-digest-integrity`
- `privacy-leakage`
- `tenant-isolation`
- `retention-staleness`
- `delete-export-recovery-proof`
- `retrieval-policy-budget`
- `unsupported-answer`
- `prompt-injection-boundary`

The failure classes are designed to fail closed:

- `MISSING_CITATION`
- `SOURCE_DIGEST_MISMATCH`
- `TENANT_PARTITION_VIOLATION`
- `RAW_SECRET_OR_PROMPT_LEAK`
- `RETENTION_OR_DELETE_PROOF_MISSING`
- `STALE_MEMORY_USED`
- `POLICY_BUDGET_BYPASS`
- `PROMPT_INJECTION_AUTHORITY_ESCALATION`

Current status remains intentionally unbound:

- `contractStatus=CONTRACT_DEFINED_NOT_BOUND`
- `evalGateContractDefined=true`
- `boundToEvalRuntime=false`
- `ciBlockingEnabled=false`
- `traceEvidenceCurated=false`
- `promptEvidenceAllowedNow=false`
- `retrievalRuntimeAllowedNow=false`

## Latest Technology Alignment

M5.62 keeps the Java mainline safe while aligning with current Agent engineering practice:

- OpenAI Agents SDK emphasizes guardrails, tracing, handoffs, sessions, and human-in-the-loop. M5.62 turns those ideas into Memory/RAG-specific gates before prompt influence: https://openai.github.io/openai-agents-python/
- MCP separates resources/tools/prompts and requires explicit security boundaries. M5.62 treats resource evidence and tool authority as separate; retrieved text cannot grant runtime Tool or MCP authority: https://modelcontextprotocol.io/specification/2025-06-18
- Spring AI VectorStore metadata can later carry eval gate digests and retrieval policy digests alongside source metadata: https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- OpenTelemetry GenAI semantic conventions are still evolving, so the project keeps stable `atlas`-owned evidence fields first and maps them later: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A artifacts can later carry gate results as cross-agent provenance, but M5.62 remains local contract-only: https://a2a-protocol.org/latest/specification/

Teaching point: a top-tier Agent does not trust retrieved memory because it "sounds right." It trusts memory only after deterministic gates prove citation fidelity, source digest integrity, privacy safety, tenant isolation, lifecycle validity, and policy-budget compliance.

## Security Boundary

M5.62 does not add:

- eval runtime execution
- CI blocking changes
- trace evidence reads
- curated trace mutation
- retrieval execution
- vector store binding
- embedding calls
- reranker calls
- LLM calls
- prompt mutation
- memory writes
- durable store calls
- document ingestion runtime
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
mvn -q "-Dtest=AgentMemoryRagEvalGateContractServiceTest,AgentMemoryRagDurableMemoryLifecycleContractServiceTest,AgentMemoryRagSourceEvidenceDigestContractServiceTest,AgentMemoryRagCitationSourceContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
