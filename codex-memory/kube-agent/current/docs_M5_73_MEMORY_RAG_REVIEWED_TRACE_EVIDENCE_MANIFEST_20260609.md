# M5.73 Memory/RAG Reviewed Trace-Evidence Manifest

## 目标

M5.73 在 M5.72 workbench overview 之后，新增一个面向 `vue-kube-manager` 和人工 Git review 的 reviewed trace-evidence manifest：

```text
GET /api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest
```

这一步不是补 fake trace，也不是启动 eval runtime。它的目标是把未来可以进入 `src/main/resources/observability/eval-trace-sets.json` 的 Memory/RAG redacted trace fixtures 所需条件全部显式化。

## 交付内容

- 新增 `AgentMemoryRagReviewedTraceEvidenceManifestResponse`。
- 新增 `AgentMemoryRagReviewedTraceEvidenceManifestService`。
- 新增 admin-only Controller endpoint：
  `GET /api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest`。
- 接入 `AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.endpointMap`。
- 接入 `AgentMemoryRagTraceSetCurationContractResponse.endpointMap`。
- 接入 `AgentMemoryRagReadinessResponse`。
- 接入 `AgentPhase1ExecutionRoadmapResponse`。
- 接入 `AgentVueReadinessControlPlaneResponse`。
- 接入 `AgentAdvancedTechnologyAdoptionContractResponse.endpointMap`。
- 新增服务测试、直接 Controller 测试、源码级安全契约测试、WebMvc 安全测试，并更新 readiness / roadmap / Vue / workbench / advanced tech tests。

## 当前状态

当前 catalog 已经有三条 Memory/RAG trace-set 行，但还没有 reviewed redacted trace ids：

```text
schemaVersion=agent-memory-rag-reviewed-trace-evidence-manifest.v1
manifestStatus=WAITING_FOR_REVIEWED_REDACTED_TRACE_FIXTURES
requiredTraceSetCount=3
reviewedTraceSetCount=0
reviewedTraceAnchorCount=0
authoritativeFixtureCount=0
promotionReadyTraceSetCount=0
runtimeControlAllowed=false
```

三条 required trace sets：

- `memory-rag-citation-fidelity`
- `memory-rag-privacy-tenant`
- `memory-rag-lifecycle-policy`

每条 row 都显式声明：

- `catalogPatchTarget=src/main/resources/observability/eval-trace-sets.json`
- `traceIdsVisibleInManifest=false`
- `authoritativeFixturePresent=false`
- `safeToPromoteNow=false`
- `safeToRunEvalNow=false`
- `safeToEnableRetrievalNow=false`
- `safeToEnableCiBlockingNow=false`
- `catalogMutationAllowed=false`
- `runtimeCatalogWrite=false`
- `humanGitReviewRequired=true`

## 证据准入字段

Manifest 给未来 reviewed trace fixtures 定义了准入 schema：

```text
traceId
traceSetId
sourceDigest
chunkDigest
tenantPartitionDigest
retentionPolicyId
reviewNote
```

注意：这些字段不是通过本 endpoint 提交的。M5.73 是只读 manifest，因此：

```text
acceptedByThisEndpoint=false
rawValueAllowed=false
traceIdsAcceptedFromCaller=false
```

### Citation fidelity

`memory-rag-citation-fidelity` 需要：

```text
sourceDigest
chunkDigest
evidenceDigest
citationSeed
retentionPolicyId
retrievalPolicyId
```

### Privacy and tenant isolation

`memory-rag-privacy-tenant` 需要：

```text
tenantPartitionDigest
sourceAclDigest
redactionProofDigest
negativeRetrievalProofDigest
```

### Lifecycle policy

`memory-rag-lifecycle-policy` 需要：

```text
retentionPolicyId
deleteProofDigest
exportProofDigest
recoveryCheckpointDigest
evalGateDigest
```

## 安全边界

M5.73 只组合以下只读契约：

- `AgentMemoryRagTraceSetCurationContractService.contract()`
- `AgentMemoryRagSourceEvidenceDigestContractService.contract()`
- `AgentMemoryRagDurableMemoryLifecycleContractService.contract()`
- `AgentMemoryRagEvalGateContractService.contract()`
- `AgentMemoryRagEvalSuiteBindingContractService.contract()`
- `AgentMemoryRagReadinessService.readiness()`

它不调用：

- `.gate()`
- `.gateBundle()`
- `.run()`
- `.curationReview()`
- candidate discovery
- raw audit query
- replay execution
- `KubeManagerHttpClient`
- `RestClient`
- `WebClient`
- `SafeToolExecutor`
- MCP runtime `tools/call`
- VectorStore
- embedding model
- reranker
- ChatClient / LLM
- memory append/recent
- audit write
- catalog write
- CI blocking switch

关键安全字段：

```text
adminOnly=true
readOnly=true
manifestOnly=true
vueWorkbenchOnly=true
traceIdsAcceptedFromCaller=false
traceIdsVisibleInManifest=false
candidateDiscoveryInvoked=false
curationReviewInvoked=false
traceSetGateInvoked=false
evalRuntimeExecuted=false
catalogMutationAllowed=false
runtimeCatalogWrite=false
retrievalExecuted=false
vectorStoreCalls=false
embeddingModelCalls=false
rerankerCalls=false
llmUsed=false
toolExecution=false
safeToolExecutorInvocation=false
mcpToolCall=false
kubeManagerCalls=false
externalCalls=false
ciBlockingChanged=false
phase2NimHpcSlurmBcmTouched=false
```

## 最新技术映射

M5.73 继续贯彻 evidence-first adoption。也就是说，“引入最先进技术”不是马上打开 runtime，而是先把最新技术映射到可测试、可审计、可教学的后端契约：

- Spring AI Memory/RAG/VectorStore: 先映射到 source digest、lifecycle、tenant/privacy、eval gate。
- OpenAI Agents tracing/guardrails/evals: 先映射到 reviewed trace anchors、guardrail evidence、fail-closed policy。
- MCP tools/resources/prompts: 先映射到 protocol governance，不开放 runtime `tools/call`。
- OpenTelemetry GenAI: 先映射到稳定内部字段和 adapter，不暴露 raw prompt / raw document / raw tool params。
- A2A Agent Card/task/artifact provenance: 先作为 future handoff evidence，不做跨 Agent runtime authority。
- OWASP LLM risks: prompt injection、sensitive disclosure、excessive agency 等风险先进入 deterministic gates。

官方参考：

- Spring AI Reference: https://docs.spring.io/spring-ai/reference/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- MCP specification 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- OWASP Top 10 for LLM Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/

## 教学重点

顶级 Agent 的 Memory/RAG 不是“把向量库接上”。真正的顺序应该是：

```text
source / lifecycle / tenant contracts
        |
        v
trace-set catalog lanes
        |
        v
workbench overview
        |
        v
reviewed trace-evidence manifest
        |
        v
human Git reviewed redacted trace IDs
        |
        v
advisory gate bundle
        |
        v
separate CI blocking review
        |
        v
durable memory and retrieval runtime
```

这就是从 Agent 小白走向 Agent 大师必须掌握的能力：不要只会调用模型，而要会设计证据链、准入门禁、可观测性、测试、前端治理面、恢复记忆和安全边界。

## 验证

本切片验证命令：

```text
mvn -q "-Dtest=AgentMemoryRagReviewedTraceEvidenceManifestServiceTest,AgentMemoryRagTraceSetCurationWorkbenchOverviewServiceTest,AgentMemoryRagTraceSetCurationContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

已通过。

## 下一步

- 获取或整理权威 reviewed redacted Memory/RAG trace fixtures。
- 通过 human/Git review 将真实 trace anchors 放入 catalog。
- 只在 reviewed fixtures 存在后，生成 advisory Memory/RAG gate bundle。
- CI blocking、retrieval runtime、durable memory runtime、MCP `tools/call`、kube-manager write authority、NIM/HPC/Slurm/BCM Phase 2 继续关闭。
