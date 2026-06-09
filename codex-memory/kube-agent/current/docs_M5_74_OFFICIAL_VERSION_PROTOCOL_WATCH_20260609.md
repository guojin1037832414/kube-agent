# M5.74 Official Version / Protocol Watch

## 目标

M5.74 把“引入全部最先进技术”落成一个后端可查询、可测试、可教学的官方版本/协议 Watch：

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch
```

这个接口回答一个很关键的问题：一期顶级 Agent 该如何跟踪 Spring AI、OpenAI Responses/Agents、MCP、A2A、OpenTelemetry GenAI、OWASP LLM Top 10、GraphRAG / reranker / vector store 等先进方向，而不把主线变成盲目升级、不可恢复、不可审计的实验场。

## 交付内容

- 新增 `AgentOfficialVersionProtocolWatchResponse`。
- 新增 `AgentOfficialVersionProtocolWatchService`。
- 新增 admin-only Controller endpoint：
  `GET /api/agent/observability/top-tier/official-version-protocol-watch`。
- 集成到：
  - `AgentAdvancedTechnologyAdoptionContractResponse`
  - `AgentTopTierReadinessOverviewResponse`
  - `AgentPhase1ExecutionRoadmapResponse`
  - `AgentVueReadinessControlPlaneResponse`
- 新增 `AgentOfficialVersionProtocolWatchServiceTest`。
- 更新 Controller、source-security、advanced-tech、top-tier readiness、Phase 1 roadmap、Vue readiness 测试。

## 当前契约状态

```text
schemaVersion=agent-official-version-protocol-watch.v1
watchStatus=OFFICIAL_WATCH_DEFINED_NOT_RUNTIME_BOUND
sourceReviewDate=2026-06-09
officialSourcesOnly=true
officialSourceCount=7
technologyTrackCount=8
phase1TopTierGoalPreserved=true
javaSpringControlPlanePreserved=true
phase2NimHpcSlurmBcmPaused=true
runtimeUpgradePerformed=false
dependencyUpgradePerformed=false
externalCallsPerformed=false
```

## 官方来源

M5.74 明确记录 7 条官方来源，并要求后续更新必须经过 Git review：

- Spring AI Reference: https://docs.spring.io/spring-ai/reference/
- OpenAI Responses API migration guide: https://platform.openai.com/docs/guides/migrate-to-responses
- OpenAI Agents SDK guide: https://platform.openai.com/docs/guides/agents-sdk/
- Model Context Protocol 2025-11-25 specification: https://modelcontextprotocol.io/specification/2025-11-25
- A2A latest specification: https://a2a-protocol.org/latest/specification/
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OWASP Top 10 for LLM Applications: https://genai.owasp.org/llm-top-10/

注意：这些链接是构建时/开发时审阅证据，不是请求时抓取对象。接口不会联网，不会把网络状态变成运行时依赖。

## 技术轨道

M5.74 把先进技术分成 8 条轨道：

| Track | 一期采纳决策 | 说明 |
| --- | --- | --- |
| `java-spring-governed-control-plane` | `KEEP_MAINLINE` | Java/Spring 继续作为身份、RBAC、审计、eval、release gate、恢复记忆的治理控制面。 |
| `spring-ai-memory-rag-mcp` | `EVIDENCE_FIRST` | Spring AI 的 Memory/RAG/VectorStore/MCP/advisor/model 能力先进入证据契约。 |
| `openai-responses-agents-interop` | `CONTRACT_MATRIX` | Responses/Agents 的 tools、handoffs、guardrails、sessions、tracing、evals 映射到本地契约。 |
| `mcp-runtime-call-plane` | `MANIFEST_FIRST` | MCP 先做 manifest/governance，`tools/call` 以后必须经过 SafeToolExecutor、HITL、audit、eval、release gate。 |
| `a2a-handoff-provenance` | `PROVENANCE_BEFORE_HANDOFF` | A2A 先作为 Agent Card/task/message/artifact provenance，不开放跨 Agent 运行时授权。 |
| `otel-genai-observability-adapter` | `EXPERIMENTAL_ADAPTER_ONLY` | OTel GenAI 仍通过 adapter 对齐，稳定内部字段 `atlas.agent.*` 继续作为主 schema。 |
| `owasp-llm-risk-controls` | `SECURITY_GATE_BASELINE` | OWASP LLM 风险进入 prompt injection、敏感数据、供应链、Tool misuse、overreliance 等 release gate。 |
| `advanced-rag-graphrag-rerankers-vector-stores` | `RUNTIME_BLOCKED_UNTIL_EVIDENCE` | GraphRAG、reranker、vector store 仍是一期开目标，但必须等 Memory/RAG 证据门禁成熟后才影响 prompt。 |

## 为什么这也是“引入最新技术”

顶级 Agent 的先进性不是“今天把依赖全升到最新”。真正先进的是每项技术进入系统前都回答：

- 官方来源是谁？
- 当前稳定度如何？
- 一期采用它解决什么工程问题？
- 它是否会扩大 Tool / MCP / A2A / retrieval / kube-manager 权限？
- 它需要哪些 trace、audit、replay、eval、Vue 可视化、Git review、恢复记忆证据？
- 如果以后升级失败，如何回滚和恢复？

M5.74 因此把“最新技术”变成可审计对象：

```text
official source
        |
        v
version/protocol watch
        |
        v
compatibility matrix
        |
        v
backend-owned contract
        |
        v
Vue read-only visibility
        |
        v
reviewed trace/eval/release evidence
        |
        v
separate runtime binding slice
```

## 安全边界

M5.74 是 read-only watch，不是 runtime enablement：

```text
adminOnly=true
readOnly=true
watchOnly=true
officialSourcesResolvedAtBuildReviewTime=true
runtimeMutationAllowed=false
runtimeUpgradePerformed=false
dependencyUpgradePerformed=false
toolExecution=false
safeToolExecutorInvocation=false
hitlInvocation=false
kubeManagerCalls=false
mcpToolsCall=false
a2aRuntimeHandoff=false
llmUsed=false
externalCalls=false
auditWrite=false
durableReceiptIssued=false
memoryWrite=false
retrievalExecuted=false
nimHpcSlurmBcmTouched=false
```

它没有：

- Spring Boot / Spring AI / Java 主版本升级；
- runtime MCP `tools/call`；
- A2A runtime handoff；
- VectorStore / embedding / reranker / GraphRAG 调用；
- OpenAI / LLM 调用；
- kube-manager / 8100 调用；
- audit / memory / catalog 写入；
- CI blocking 开关；
- NIM / HPC / Slurm / BCM Phase 2 重新打开。

## 教学重点

你在学习 Agent 开发时，要把“追最新”拆成两层：

1. 信息层：知道官方协议、SDK、框架、语义约定、风险模型现在是什么。
2. 工程层：把它们变成契约、门禁、测试、可观测、前端只读工作台和恢复记忆。

很多 Agent 项目只停在第一层，所以 demo 很快，但生产风险很高。这个项目要走到第二层：让每个先进技术都有进入系统的证据链。

## 验证

本切片先通过：

```text
mvn -q "-Dtest=AgentOfficialVersionProtocolWatchServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest" test
```

最终提交前还会执行更宽的 `mvn -q "-DskipTests" validate` 和相关回归测试。

## 下一步

- Vue 增加 official version/protocol watch dashboard，只渲染来源、轨道、门禁、阻断项，不渲染 runtime enable 按钮。
- 继续收集 reviewed redacted eval / Memory/RAG trace evidence。
- 在真实证据存在后，再推进 advisory gate bundle、CI blocking、retrieval runtime、MCP runtime、A2A handoff 的独立审查切片。
- NIM / HPC / Slurm / BCM 继续保持 Phase 2 暂停。
