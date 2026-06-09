# M5.70 Memory/RAG Trace-Set Catalog Entries

## Goal

M5.70 turns the M5.69 Memory/RAG suite catalog from "suite check codes exist" into "trace-set evidence lanes exist, but reviewed evidence is not curated yet."

This is still a catalog and contract slice. It does not open Memory/RAG eval runtime, retrieval runtime, vector stores, LLM calls, CI blocking, or Phase 2 specialist domains.

## What Changed

- Added three trace-set catalog entries to `observability/eval-trace-sets.json`:
  - `memory-rag-citation-fidelity`
  - `memory-rag-privacy-tenant`
  - `memory-rag-lifecycle-policy`
- Bound all three trace sets to `suiteId=memory-rag-release-gate`.
- Kept `traceIds=[]` so no synthetic or unreviewed evidence can pass as release evidence.
- Added Memory/RAG-specific curation policy:
  - `requiresReviewedSourceEvidenceDigest=true`
  - `requiresReviewedMemoryLifecycleEvidence=true`
  - `catalogOnlyUntilReviewed=true`
  - `suiteRuntimeExecutionAllowed=false`
  - `runtimeRetrievalAllowed=false`
  - `ciBlockingAllowed=false`
- Added guarantees that the catalog rows contain no raw document, prompt, retrieved chunk, principal, organization, conversation, endpoint, reason, or parameter values.
- Added guarantees that they do not execute retrieval, vector stores, embedding models, rerankers, LLMs, Tools, kube-manager calls, memory writes, or audit writes.
- Added fail-closed trace-set gate behavior for disabled suites:
  - `gateVerdict=SUITE_RUNTIME_DISABLED`
  - `pass=false`
  - `emptyInput=true`
  - `suiteGate=null`
  - `runtimeExecutionAllowed=false`
  - `retrievalRuntimeAllowed=false`
  - `ciBlockingEnabled=false`
- Added an independent trace-set policy latch:
  - if a future suite runtime is enabled while the Memory/RAG trace set still has `suiteRuntimeExecutionAllowed=false`, the trace-set gate returns `TRACE_SET_RUNTIME_DISABLED`
  - if `catalogOnlyUntilReviewed=true` and `traceIds=[]`, the trace-set gate also remains closed
  - the disabled artifact still has `suiteGate=null`, no retrieval, no vector store, no embeddings, no reranker, no memory write, and no audit write
- Updated Memory/RAG binding contract state to:
  - `contractStatus=TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED`
  - `availableTraceSetCount=7`
  - `memoryRagTraceSetBound=false`
- Updated the binding contract to derive `catalogOnlyUntilReviewed`, `suiteRuntimeExecutionAllowed`, `runtimeRetrievalAllowed`, and `ciBlockingAllowed` from actual trace-set catalog policy instead of relying only on hard-coded ideal values.

## Important Meaning

`memoryRagTraceSetBound=false` now means reviewed redacted trace ids are missing. It no longer means the trace-set rows are missing from the catalog.

The current sequence is:

```text
suite check codes defined
        |
        v
trace-set catalog rows defined
        |
        v
reviewed redacted trace ids curated
        |
        v
advisory Memory/RAG gate bundle
        |
        v
separate CI and retrieval runtime promotion
```

## Learning Model

Top-tier RAG needs three evidence lanes before retrieval can affect prompts:

- Citation fidelity: answer text must be tied to source/chunk/evidence digests.
- Privacy and tenant isolation: raw prompts, source bodies, tenant secrets, and cross-tenant retrieval must stay blocked.
- Lifecycle policy: stale memory, retention, delete/export/recovery proof, and retrieval budgets must be testable.

M5.70 teaches that these lanes should be represented as durable catalog entries before runtime code is added. This makes later Spring AI VectorStore, GraphRAG, reranker, MCP resource, OpenTelemetry GenAI, OpenAI Agents/Evals, or A2A provenance work attach to explicit evidence instead of implicit prompt rules.

The Curie review addendum tightened the design: a suite-level runtime switch is not enough. Memory/RAG trace sets also need their own catalog policy latch, so future suite promotion cannot accidentally make an unreviewed RAG evidence lane runnable.

## Verification

```powershell
mvn -q "-Dtest=AgentEvalTraceSetCatalogServiceTest,AgentEvalTraceSetGateBundleArtifactTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchGateBundleSummaryServiceTest,AgentEvalWorkbenchTraceSetDetailServiceTest,AgentEvalWorkbenchPromotionWorkflowServiceTest,AgentMemoryRagEvalSuiteBindingContractServiceTest,AgentReviewedEvalTraceEvidenceServiceTest,AgentReleaseBlockingEvalGateContractServiceTest,AgentMemoryRagReadinessServiceTest,ObservabilityControllerTest" test
```

## Safety Boundary

M5.70 adds no eval runtime execution, reviewed trace evidence promotion, trace-set runtime mutation, CI blocking enablement, retrieval execution, vector-store binding, embedding/reranker/LLM calls, prompt mutation, memory writes, audit writes, Tool execution, `SafeToolExecutor` invocation, HITL invocation, kube-manager calls, MCP runtime `tools/call`, external calls, dependency upgrades, or NIM / HPC / Slurm / BCM Phase 2 work.
