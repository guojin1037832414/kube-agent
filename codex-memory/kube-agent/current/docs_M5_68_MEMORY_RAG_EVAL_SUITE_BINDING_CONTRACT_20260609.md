# M5.68 Memory/RAG Eval-Suite Binding Contract

M5.68 advances the fourth M5.64 roadmap step: `memory-rag-eval-suite-binding`.

It answers a practical top-tier RAG question: after defining the Memory/RAG eval gates in M5.62, do those gate checks have a reviewed deterministic eval suite and trace-set evidence path, or are they still only conceptual?

## Endpoint

```text
GET /api/agent/observability/memory-rag/eval-suite-binding-contract
```

## Delivered

- Added `AgentMemoryRagEvalSuiteBindingContractResponse`.
- Added `AgentMemoryRagEvalSuiteBindingContractService`.
- Added admin-only controller binding in `ObservabilityController`.
- Updated Memory/RAG readiness, eval workbench capabilities, Phase 1 roadmap, Vue readiness control plane, advanced technology adoption, and top-tier readiness overview.
- Added service, controller, source-contract, readiness/top-tier, roadmap, Vue, adoption, workbench, and MockMvc security tests.

## Current Contract State

```text
schemaVersion=agent-memory-rag-eval-suite-binding-contract.v1
contractStatus=CONTRACT_DEFINED_NOT_BOUND
evalSuiteBindingContractDefined=true
memoryRagEvalSuiteBound=false
memoryRagTraceSetBound=false
reviewedTraceEvidenceRequired=true
evalRuntimeExecuted=false
ciBlockingEnabled=false
retrievalRuntimeAllowedNow=false
mappedGateCheckCount=0
missingGateCheckCount=9
```

The current built-in eval suite catalog does not yet define the future Memory/RAG check codes, so every binding row stays `NEEDS_SUITE_CHECK`.

Required gate checks:

- `citation-fidelity`
- `source-digest-integrity`
- `privacy-leakage`
- `tenant-isolation`
- `retention-staleness`
- `delete-export-recovery-proof`
- `retrieval-policy-budget`
- `unsupported-answer`
- `prompt-injection-boundary`

Required future trace sets:

- `memory-rag-citation-fidelity`
- `memory-rag-privacy-tenant`
- `memory-rag-lifecycle-policy`

## Security Boundary

M5.68 is intentionally contract-only.

It does not:

- run eval suites;
- call trace-set gates;
- mutate trace-set catalogs;
- enable CI blocking;
- execute retrieval;
- bind vector stores;
- call embedding models, rerankers, LLMs, tools, MCP `tools/call`, or kube-manager;
- write memory or audit evidence;
- issue durable receipts;
- invoke HITL;
- touch NIM / HPC / Slurm / BCM Phase 2 scope.

## Learning Point

顶级 RAG 不是先接向量库再补测试。真正成熟的顺序是：

```text
source digest + lifecycle proof
        |
        v
Memory/RAG eval gate contract
        |
        v
eval-suite binding contract
        |
        v
reviewed redacted trace evidence
        |
        v
advisory gate bundle
        |
        v
separate reviewed CI/runtime binding slice
```

M5.68 教的是“评测门禁也需要可绑定的套件和 trace set”。没有这一步，后续 retrieval 即使能跑，也只是技术演示；有了这一步，未来 Spring AI VectorStore、GraphRAG、reranker、MCP resources/tools、OpenTelemetry GenAI spans、OpenAI Agents-style guardrails 和 A2A artifact provenance 才能共同落在同一条可审计证据链上。

## Latest-Technology Alignment

Checked on 2026-06-09:

- OpenAI Agents SDK / Agents docs emphasize tools, handoffs, guardrails, sessions, and tracing as first-class Agent engineering surfaces.
- Spring AI documents ChatClient advisors, chat memory, vector-store-backed RAG, and observability, which match this project's future Memory/RAG binding direction.
- MCP latest specification treats tools, resources, and prompts as protocol surfaces that need user consent and host-side governance.
- OpenTelemetry GenAI semantic conventions still mark GenAI spans as development-stage, so this project keeps stable internal `atlas.agent.*` fields and maps outward through adapters.
- A2A protocol work centers on Agent Card discovery, tasks, messages, and artifacts, which fits the future handoff/provenance lane after local evidence gates mature.

Decision: all of those advanced directions remain inside Phase 1's top-tier target, but runtime authority still enters only through backend-owned contracts, deterministic evals, reviewed evidence, Vue operator visibility, and recovery memory.

## Verification

```powershell
mvn -q "-Dtest=AgentMemoryRagEvalSuiteBindingContractServiceTest,AgentMemoryRagEvalGateContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
