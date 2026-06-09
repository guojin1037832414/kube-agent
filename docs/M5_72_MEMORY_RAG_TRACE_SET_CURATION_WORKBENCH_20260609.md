# M5.72 Memory/RAG Trace-Set Curation Workbench

## 目标

M5.72 在 M5.71 的只读 curation contract 之上，新增一个面向 `vue-kube-manager` 的后端工作台读模型：

```text
GET /api/agent/observability/memory-rag/workbench/trace-set-curation/overview
```

这一步不是打开 eval runtime，也不是让前端执行 curation review。它的目标是把 Memory/RAG 三条 trace-set 证据线转换成 Vue 可以直接渲染的卡片、状态、禁用动作、suite latch、workflow、endpoint map、安全证明和隐私证明。

## 交付内容

- 新增 `AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse`。
- 新增 `AgentMemoryRagTraceSetCurationWorkbenchOverviewService`。
- 新增 admin-only Controller endpoint：
  `GET /api/agent/observability/memory-rag/workbench/trace-set-curation/overview`。
- 接入 `AgentVueReadinessControlPlaneResponse`，让 Vue 控制面可以发现该工作台。
- 接入 `AgentPhase1ExecutionRoadmapResponse`，让该工作台成为 Phase 1 顶级 Agent 路线图的一部分。
- 接入 `AgentMemoryRagReadinessResponse`，让 Memory/RAG readiness 页面能导航到该工作台。
- 反向接入 `AgentMemoryRagTraceSetCurationContractResponse.endpointMap`，让 contract 和 workbench 形成闭环。
- 新增服务测试、直接 Controller 测试、源码级安全契约测试、WebMvc 安全测试，并更新 readiness / roadmap / Vue 控制面测试。

## 当前工作台状态

当前 catalog 已经有三条 Memory/RAG trace-set 行，但还没有 reviewed redacted trace ids：

```text
workbenchStatus=WORKBENCH_READY_TO_RENDER_REVIEWED_EVIDENCE_GAPS
schemaVersion=agent-memory-rag-trace-set-curation-workbench-overview.v1
curationCardCount=3
blockingCardCount=3
requiredTraceSetCount=3
definedTraceSetCount=3
reviewedTraceSetCount=0
runtimeControlAllowed=false
```

三张 curation cards 分别对应：

- `memory-rag-citation-fidelity`
- `memory-rag-privacy-tenant`
- `memory-rag-lifecycle-policy`

每张卡片都暴露：

- `status=REVIEWED_EVIDENCE_MISSING`
- `severity=BLOCKING`
- `traceIdsVisibleInWorkbench=false`
- `policyLatchDeclaredClosed=true`
- `missingEvidence`
- `blockedReasons`
- `disabledRuntimeActions`
- `renderHints`
- `gitReviewRequired=true`
- `humanReviewRequired=true`

## 安全边界

M5.72 只组合以下只读模型：

- `AgentMemoryRagTraceSetCurationContractService.contract()`
- `AgentMemoryRagEvalSuiteBindingContractService.contract()`
- `AgentMemoryRagReadinessService.readiness()`

它不调用：

- `.gate()`
- `.gateBundle()`
- `.run()`
- `.curationReview()`
- candidate discovery
- promotion workflow
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
overviewOnly=true
vueWorkbenchOnly=true
traceIdsAcceptedFromCaller=false
traceIdsVisibleInWorkbench=false
candidateDiscoveryAllowedNow=false
curationReviewAllowedNow=false
traceSetGateAllowedNow=false
gateBundleButtonEnabledNow=false
catalogMutationAllowed=false
runtimeCatalogWrite=false
retrievalRuntimeAllowedNow=false
toolExecution=false
safeToolExecutorInvocation=false
mcpToolCall=false
kubeManagerCalls=false
llmUsed=false
externalCalls=false
phase2NimHpcSlurmBcmTouched=false
```

## 教学重点

顶级 Agent 的前端工作台不能只靠前端判断“这个按钮该不该出现”。后端应该发布稳定、可测试、可审计的 read model，让 Vue 只做渲染：

```text
catalog / contract evidence
        |
        v
backend-owned workbench read model
        |
        v
Vue renders cards, blockers, latches, and disabled actions
        |
        v
human Git review curates redacted trace ids
        |
        v
future advisory gate bundle
        |
        v
future separate CI/runtime promotion
```

这也是 Agent 开发从“能跑 demo”走向“可长期治理”的关键：运行时能力越强，越需要先把 evidence、policy latch、operator visibility 和 recovery memory 做稳。

## 最新技术选型定位

M5.72 继续采用 evidence-first 的“先进技术引入”策略：

- Java/Spring 继续作为 Phase 1 控制面主语言，因为它适合 typed contracts、Spring Security、deterministic tests、Actuator/Micrometer/OpenTelemetry、SBOM 和企业交付。
- Spring AI 的 RAG、VectorStore、MCP、observability 能力继续放在兼容矩阵与后续绑定阶段，不能从工作台读模型直接进入 runtime。
- MCP `2025-11-25` 规范继续作为 tools/resources/prompts 治理参考，但 M5.72 不暴露 runtime `tools/call`。
- OpenTelemetry GenAI semantic conventions 继续作为外部适配目标，内部稳定契约仍是 `atlas.agent.*` 和本项目的 read models。
- OpenAI Agents SDK 的 handoffs、guardrails、tracing、MCP、HITL/evals 思路用于设计治理闭环，但不会绕过 Java 后端的安全门禁。
- A2A Agent Card / task / artifact / versioning 思路继续用于未来多 Agent handoff/provenance，一期必须先把本地证据链做稳。

官方参考锚点：

- Spring AI Reference: https://docs.spring.io/spring-ai/reference/
- MCP specification 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/

## 验证

本切片验证命令：

```text
mvn -q "-Dtest=AgentMemoryRagTraceSetCurationWorkbenchOverviewServiceTest,AgentMemoryRagTraceSetCurationContractServiceTest,AgentMemoryRagReadinessServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

已通过。
