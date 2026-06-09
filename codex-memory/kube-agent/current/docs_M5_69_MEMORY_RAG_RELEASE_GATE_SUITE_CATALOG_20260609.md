# M5.69 Memory/RAG Release-Gate Suite Catalog

## Goal

M5.69 turns the M5.68 Memory/RAG eval-suite binding contract from "missing suite check codes" into "suite check codes defined, trace sets not curated."

The implementation adds a deterministic built-in eval suite:

```text
memory-rag-release-gate
```

This keeps Phase 1 moving toward a top-tier Agent Core while still blocking retrieval runtime, CI blocking, vector stores, LLM calls, and Phase 2 specialist domains.

## What Changed

- `AgentEvalSuiteCatalogService` now publishes five built-in suites.
- The new `memory-rag-release-gate` suite has default minimum score `95`.
- The suite keeps `failOnWarnings=true`.
- The suite is catalog-only in M5.69:
  - `catalogOnly=true`
  - `runtimeExecutionAllowed=false`
  - `requiresReviewedTraceSetsBeforeRun=true`
  - `ciBlockingAllowed=false`
  - `retrievalRuntimeAllowed=false`
- The suite defines all nine Memory/RAG release checks:
  - `MEMORY_RAG_CITATION_FIDELITY`
  - `MEMORY_RAG_SOURCE_DIGEST_INTEGRITY`
  - `MEMORY_RAG_PRIVACY_LEAKAGE`
  - `MEMORY_RAG_TENANT_ISOLATION`
  - `MEMORY_RAG_RETENTION_STALENESS`
  - `MEMORY_RAG_DELETE_EXPORT_RECOVERY_PROOF`
  - `MEMORY_RAG_RETRIEVAL_POLICY_BUDGET`
  - `MEMORY_RAG_UNSUPPORTED_ANSWER`
  - `MEMORY_RAG_PROMPT_INJECTION_BOUNDARY`
- `AgentMemoryRagEvalSuiteBindingContractResponse` now reports:
  - `contractStatus=SUITE_CHECKS_DEFINED_TRACE_SETS_NOT_CURATED`
  - `memoryRagEvalSuiteBound=true`
  - `mappedGateCheckCount=9`
  - `missingGateCheckCount=0`
  - `availableSuiteCount=5`
- `AgentMemoryRagReadinessResponse` now reports:
  - `memoryRagEvalSuiteExists=true`
  - `memoryRagEvalSuiteId=memory-rag-release-gate`
  - `memoryRagEvalSuiteCheckCodeCount=9`
- Existing named suite runtime endpoints reject `memory-rag-release-gate` with fail-closed conflict semantics until a later reviewed slice opens advisory Memory/RAG eval execution.

## Important Meaning

`memoryRagEvalSuiteBound=true` is intentionally narrow. It means all required Memory/RAG gate checks have matching deterministic suite check codes in the catalog.

It does not mean:

- reviewed trace evidence exists
- trace-set catalog entries are curated
- eval runtime has executed
- the existing named suite `/run` or `/gate` endpoints may run this suite
- CI blocking is enabled
- retrieval can influence prompts
- vector stores, embedding models, rerankers, or LLMs are bound
- kube-manager calls are made
- NIM / HPC / Slurm / BCM Phase 2 work is reopened

## Learning Model

Top-tier Memory/RAG should move through separate gates:

```text
suite catalog defined
        |
        v
trace evidence curated
        |
        v
deterministic eval/gate bundle executed
        |
        v
CI promotion reviewed
        |
        v
retrieval runtime promoted
```

M5.69 completes the first gate for Memory/RAG. The next gate is reviewed trace-set evidence.

## Advanced Technology Posture

The project should continue introducing the newest Agent technology through evidence-first contracts:

- OpenAI Agents / Responses patterns become tool, handoff, guardrail, tracing, and eval contracts.
- Spring AI Memory/RAG/MCP/eval/observability remains a Phase 1 target through compatibility and test gates.
- MCP tools/resources/prompts remain governed protocol surfaces, not direct runtime authority.
- OpenTelemetry GenAI concepts map to stable internal `atlas.agent.*` telemetry before external semantic adapters become mandatory.
- A2A handoff/provenance remains a compatibility-matrix target until local identity, tenant, audit, eval, and Vue governance mature.
- GraphRAG, rerankers, vector stores, Java 21/25/26, Spring Boot 4, and Spring AI 2 remain compatibility-matrix work until evidence supports promotion.

## Next Development Order

1. Add Git-reviewed trace-set catalog entries for `memory-rag-citation-fidelity`, `memory-rag-privacy-tenant`, and `memory-rag-lifecycle-policy`.
2. Curate reviewed redacted trace ids for those trace sets.
3. Add advisory Memory/RAG gate-bundle and Vue workbench visibility.
4. Promote CI blocking only in a separate reviewed slice.
5. Bind durable memory and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory all pass.

## Verification

```powershell
mvn -q "-Dtest=AgentEvalSuiteCatalogServiceTest,AgentMemoryRagEvalSuiteBindingContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchGateBundleSummaryServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## Safety Boundary

M5.69 adds no eval runtime execution, trace-set mutation, CI blocking enablement, retrieval execution, vector-store binding, embedding/reranker/LLM calls, prompt mutation, memory writes, audit writes, Tool execution, `SafeToolExecutor` invocation, HITL invocation, kube-manager calls, MCP runtime `tools/call`, external calls, dependency upgrades, or NIM / HPC / Slurm / BCM Phase 2 work.
