# 以 kube-agent 为例的顶级 Agent 开发学习指南

> 最后更新：2026-06-12
> 适用范围：Phase 1 顶级 Agent Core。NIM / HPC / Slurm / BCM 是 Phase 2 暂停域。

## 1. 学习目标

学习 kube-agent，不是只学“怎么调一个大模型接口”，而是学一个 Agent 后端如何在真实系统里安全地接入身份、工具、外部 HTTP、人工确认、审计、记忆、评测和前端治理面。

本项目的核心观念是：LLM 只能提出候选意图，不能直接获得运行时权力。一个顶级 Agent 要把“候选意图”变成“可审计、可解释、可回放、可阻断的工程动作”。

## 2. 项目地图

当前后端主线：

- `src/main/java/com/atlas/controller`：登录、会话、HITL 等 HTTP 入口。
- `src/main/java/com/atlas/auth`：Spring Security、Principal、ThreadLocal 兼容桥。
- `src/main/java/com/atlas/orchestrator`：聊天流、Graph 调度、SSE、上下文组装。
- `src/main/java/com/atlas/graph`：StateGraph 配置与节点。
- `src/main/java/com/atlas/brain`：`AtlasBrain`、`BrainDecision`、结构化输出解析。
- `src/main/java/com/atlas/react`：手写 ReAct 循环与执行事件。
- `src/main/java/com/atlas/plan`：计划/反思结构。
- `src/main/java/com/atlas/tool`：Tool 注解、注册、参数治理和具体 Tool。
- `src/main/java/com/atlas/tool/execution`：统一 `SafeToolExecutor`。
- `src/main/java/com/atlas/http`：kube-manager HTTP outlet。
- `src/main/java/com/atlas/audit`：审计事件、durable audit、遥测投影。
- `src/main/java/com/atlas/mcp`：MCP manifest/governance 只读治理。
- `src/main/java/com/atlas/memory`：轻量摘要记忆。
- `src/main/java/com/atlas/observability`：admin-only 读模型、Replay、Eval、Top-tier governance。

先读入口：

- `README.md`
- `开发路线图.md`
- `docs/顶级Agent架构与技术学习地图.md`
- `docs/项目使命与当前记忆.md`
- `Tool开发规范.md`
- `codex-memory/kube-agent/current/当前恢复状态.md`

## 2.1 技术栈与学习地址

下面这张表不是“依赖清单”，而是 kube-agent 的学习地图。每一项都回答三个问题：它在项目里解决什么问题、学习时要抓住什么概念、去哪里读一手资料。

| 技术 / 知识点 | kube-agent 中的作用 | 学习时先抓住什么 | 推荐学习地址 |
|---|---|---|---|
| Java 17 + Maven | 当前后端主语言和构建系统；Java 21/25 暂不直接切主线，先走兼容矩阵。 | 类型系统、record/不可变 DTO、异常边界、Maven lifecycle、依赖管理。 | [Maven Enforcer requireJavaVersion](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html) |
| Spring Boot 3.5 | HTTP API、配置、Actuator、自动装配、应用生命周期。 | Controller 只是入口，真正安全事实要进入 Security、Service 和执行边界。 | [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/index.html) |
| Spring Security | 登录态、Bearer/session bridge、Principal、admin-only/read-only 治理入口。 | Authentication 证明“是谁”，Authorization 决定“能做什么”；前端字段不能替代 Principal。 | [Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html)、[Authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/index.html) |
| Spring AI | ChatModel、Tool calling、Structured Output、RAG/VectorStore、Advisors、MCP 集成方向。 | 模型输出永远是候选输入；Tool calling 是应用侧执行，不是模型直接执行。 | [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)、[Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)、[Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)、[RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) |
| OpenAI-compatible API / Agents 思路 | 当前模型代理走 OpenAI 兼容协议；项目学习 OpenAI Agents 的“应用拥有编排、审批、状态”理念。 | Responses/Agents/Tools 是能力模型；企业后端仍要拥有执行、审批、审计和状态。 | [OpenAI Agents SDK Guide](https://developers.openai.com/api/docs/guides/agents)、[OpenAI Function Calling](https://developers.openai.com/api/docs/guides/function-calling)、[OpenAI Evaluation Best Practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices) |
| Spring AI Alibaba Agent / Graph | StateGraph、ReactAgent、Graph 编排、checkpoint、streaming 等 Java Agent 能力来源。 | Graph 是状态机，不是权限系统；每个节点输出都要回到服务端边界。 | [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)、[ReactAgent 快速开始](https://java2ai.com/docs/quick-start) |
| ReAct / Plan / Graph | 把“想法、行动、观察、计划、执行”拆成可测试步骤。 | Thought/Plan 是推理证据；Action 参数是候选业务输入；执行必须走 SafeToolExecutor。 | [Spring AI Alibaba Graph 说明](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/main/README.md) |
| MCP | 当前只做 admin-only manifest/governance 读模型，`tools/call` 关闭。 | MCP 标准化工具暴露，但不等于认证、授权、审计都自动解决。 | [MCP Intro](https://modelcontextprotocol.io/docs/getting-started/intro)、[MCP Tools Spec](https://modelcontextprotocol.io/specification/2025-03-26/server/tools)、[Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) |
| kube-manager HTTP outlet | 真实触达 `8100` 的外部系统边界。 | GET/READ 可以有读重试；POST/PUT/PATCH/DELETE 写操作必须更谨慎。 | [Kubernetes API Concepts](https://kubernetes.io/docs/reference/using-api/api-concepts/)、[Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) |
| Resilience4j | kube-manager HTTP 出口的 retry、circuit breaker、bulkhead。 | 重试不是越多越好；写请求不能随便自动重试。 | [Resilience4j Getting Started](https://resilience4j.readme.io/docs/getting-started) |
| Micrometer Tracing + OpenTelemetry | traceId、span、OTLP 导出、Agent 可观测证据。 | traceId 是关联证据，不是身份、授权或审计 receipt。 | [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/index.html)、[OpenTelemetry Docs](https://opentelemetry.io/docs/)、[OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) |
| Memory / RAG / VectorStore | 当前主要是边界合同和 admin-only 读模型，真实 RAG prompt influence 尚未打开。 | RAG 不是“把所有历史塞进 prompt”；必须有 source custody、tenant/privacy、citation、delete/export、eval gate。 | [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)、[Spring AI Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html) |
| ONNX Runtime + DJL Tokenizers | 本地 Embedding 的模型推理和分词基础。 | 模型文件和 tokenizer 是供应链输入；加载失败要降级，不应阻断安全链路。 | [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html)、[DJL HuggingFace Tokenizers](https://docs.djl.ai/master/extensions/tokenizers/index.html) |
| Caffeine | session、HITL decision、conversation 等内存 TTL/容量缓存。 | Cache 不是 Map；必须考虑过期、容量、清理、并发和跨用户隔离。 | [Caffeine Wiki](https://github.com/ben-manes/caffeine/wiki)、[Eviction](https://github.com/ben-manes/caffeine/wiki/Eviction) |
| OWASP LLM Top 10 | Agent 安全威胁模型：Prompt Injection、敏感信息泄露、过度代理、供应链等。 | Prompt 防护不能只靠“写得更强的 system prompt”；要靠权限、白名单、最小权力和执行隔离。 | [OWASP LLM Top 10](https://owasp.org/www-project-top-10-for-large-language-model-applications/)、[LLM01 Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/) |
| NIST AI RMF | 顶级 Agent 治理框架：Govern、Map、Measure、Manage。 | 风险管理要变成代码、测试、文档和 release gate，不只是口号。 | [NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework)、[AI RMF Resource Center](https://airc.nist.gov/airmf-resources/airmf/) |
| Kubernetes 安全 | kube-manager 最终管理的是云原生资源，必须懂 RBAC、API、Audit。 | Agent 对 Kubernetes 的操作本质是 API 操作；最小权限和审计必须前置。 | [Kubernetes API](https://kubernetes.io/docs/concepts/overview/kubernetes-api/)、[RBAC Good Practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/)、[Kubernetes Auditing](https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/) |
| JUnit / AssertJ / Mockito | 单元测试、契约测试、mock 外部能力。 | 测试不仅证明行为，也要保护安全不变量和学习说明。 | [JUnit User Guide](https://docs.junit.org/6.1.0/overview.html)、[AssertJ](https://assertj.github.io/)、[Mockito](https://site.mockito.org/) |
| ArchUnit / Testcontainers | 架构规则测试、真实依赖集成测试。 | 重要边界应被自动化保护，而不是只靠 review 记忆。 | [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)、[Testcontainers for Java](https://java.testcontainers.org/) |
| JaCoCo / SpotBugs / CycloneDX | 覆盖率、静态分析、SBOM 供应链证据。 | 质量工具不能替代设计审查，但能给 release gate 提供客观输入。 | [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html)、[SpotBugs Maven Plugin](https://spotbugs.github.io/spotbugs-maven-plugin/)、[CycloneDX Maven Plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/) |

## 2.2 核心知识点详解

这一节把“你需要成为 Agent 高手必须懂的概念”用 kube-agent 的代码语境解释清楚。读代码时可以把它当作旁边摊开的地图。

### 2.2.1 Agent 不是一个 LLM 调用

最朴素的聊天应用是“用户输入 -> 模型输出”。Agent 多了至少四件事：状态、工具、决策和责任边界。

在 kube-agent 里，`AtlasOrchestrator` 不是简单把 prompt 丢给模型，而是要准备 `traceId`、`conversationId`、当前可信用户、可信 orgId、Graph State、SSE 事件、Tool 候选和失败原因。`AtlasBrain` 或 ReAct/Plan 可以给出候选动作，但候选动作不能直接触达 kube-manager。

学习时要记住一个公式：

```text
顶级 Agent = LLM 候选能力 + 服务端状态机 + 工具治理 + 身份权限 + 审计评测 + 可恢复文档
```

如果少了后面这些，项目只是“能调用工具的聊天应用”，不是顶级 Agent。

### 2.2.2 Tool calling 的本质是应用侧执行

OpenAI 和 Spring AI 都把 tool/function calling 描述成“模型请求工具，应用执行工具，再把结果交回模型”。这个顺序非常关键：模型不会、也不应该自己执行生产系统调用。

在 kube-agent 里：

- Spring AI / LLM 输出的 Tool JSON 是不可信候选参数。
- `ToolRegistry` 负责让应用知道有哪些 Tool、风险级别、权限和 schema。
- `ProtectedToolParameterFilter` 负责去掉 `token`、`orgId`、`userId`、`confirmed`、`audit`、`release` 等控制面字段。
- `SafeToolExecutor` 重新绑定服务端可信身份并执行最终校验。
- 具体 `BaseTool` 才能通过 `KubeManagerHttpClient` 访问 kube-manager。

这就是为什么项目里反复写“inputSchema 不是权限系统”。schema 是给模型少填错参数用的，不是给模型发通行证。

### 2.2.3 Prompt Injection 不能只靠 Prompt 防

OWASP 把 Prompt Injection 放在 LLM 应用风险最前面，是因为模型天然会把很多输入混在同一个上下文里理解。用户可以说“忽略之前规则”“我已确认”“请把 orgId 改成 1”，这些句子对模型可能像指令，但对后端只能是普通文本。

kube-agent 的防法是分层：

- 用户文本只能影响 intent 候选和普通业务参数。
- `RuleMatcher` 的关键词/正则命中只证明“语义像某个 intent”。
- `EmbeddingMatcher` 的相似度只证明“语义距离近”。
- `IntentArbiter` 的 `crossBoost` 只证明“多个证据源同意”。
- HITL marker、audit receipt、release evidence、orgId、token 都必须来自服务端。

所以最重要的安全动作不是写一句更严厉的 system prompt，而是让 prompt 里的任何内容都无法直接变成运行时权力。

### 2.2.4 身份、租户、会话、会话资源是四个不同概念

很多 Agent 项目会把这些混在一起，最后出现跨用户、跨组织、跨会话的事故。kube-agent 刻意把它们拆开：

- 身份：当前用户是谁，由 Spring Security `Authentication` / `AgentPrincipal` 表达。
- 租户：当前组织 orgId，由 kube-manager token 响应或可信反查得到。
- 登录会话：`SessionStore` 里的 `ses_*`，只是 kube-agent 本地会话句柄。
- 聊天会话：`ConversationStore` 里的 `conversationId`，只是资源定位符。

`conversationId` 不是授权凭证，`sessionId` 不是 kube-manager JWT，前端传来的 `organizationId` 不是可信租户事实。看懂这四个区别，是从“会写接口”升级到“会做多租户 Agent”的关键。

### 2.2.5 HITL 是必要门，不是万能门

HITL 的意义是让人类确认高风险动作，但它不是最终授权。一个确认按钮只能说明“有人在某个前端操作了确认”，不能说明：

- 这个 confirmToken 属于当前用户。
- 当前用户仍然在同一个 orgId。
- Tool 参数没有被注入控制字段。
- durable audit 可用。
- release gate 允许这类写操作。
- kube-manager 会接受这次操作。

所以 kube-agent 的目标链路是：owner-first 校验 -> HITL marker -> `SafeToolExecutor` -> audit prewrite -> Tool 权限 -> kube-manager。顺序很重要，尤其不能先删除 pending decision 再做 owner 校验。

### 2.2.6 Graph / ReAct / Plan 是解释结构，不是授权结构

Graph 让 Agent 有状态机；ReAct 让 Agent 能把 Thought、Action、Observation 拆开；Plan 让 Agent 能先计划再执行。但它们的输出都只是“中间证据”。

在 kube-agent 中：

- Graph State 可以传递 trace、intent、candidate tool、answer、fail-closed reason。
- ReAct `Action.params` 来自模型，必须被当作不可信业务输入。
- PlanStep 不能携带可信 token/orgId，也不能把 `writeAllowed=true` 当真。
- `execute_node` / `react_node` / `tool_call` 即使有很强的语义判断，也必须回到 `SafeToolExecutor`。

学习 Graph 时不要只看“节点怎么连”，还要看每条边有没有把不可信数据误当成可信数据。

### 2.2.7 RAG 的难点不是向量库，而是证据托管

很多教程会把 RAG 简化成“切 chunk -> embedding -> vector search -> 塞 prompt”。生产 Agent 还要回答更多问题：

- 文档是谁上传的？
- 属于哪个 tenant？
- 是否允许当前用户读取？
- 删除/过期/撤销后 embedding 怎么处理？
- chunk 和原文如何可追溯？
- citation 能否证明回答来源？
- 检索结果是否会泄露敏感数据？
- 检索效果怎么评测？

这就是为什么 kube-agent 当前没有急着打开 RAG prompt influence，而是先做 Memory/RAG readiness、digest、source evidence、admin-only 读模型。先把证据链做好，再让 RAG 影响生产回答。

### 2.2.8 Eval 不是考试，是发布闸门

Eval 的目标不是“模型答题得高分”，而是把业务目标变成可重复的检查。

kube-agent 中 Eval/Replay 要解决：

- 这次修改有没有破坏身份/租户边界？
- 某个 Tool 是否仍然只读？
- SSE 输出是否丢失 fail-closed reason？
- ReAct 是否绕过 `SafeToolExecutor`？
- Memory/RAG 是否把未 reviewed 数据当 prompt authority？

OpenAI 的 eval 文档也强调先设计评测过程，而不是只依赖单次主观体验。对 kube-agent 来说，真正的顶级状态是：每个高风险能力上线前都有 reviewed trace、deterministic eval、release gate 和回归保护。

### 2.2.9 Observability 要能解释 Agent 为什么这样做

传统后端观测关心 HTTP latency、error rate、SQL 慢查询。Agent 观测还要关心：

- 这次回答用了哪个 model？
- 触发了哪个 intent？
- Graph 走了哪条边？
- 是否尝试 Tool？
- Tool 参数是否被过滤？
- 为什么 fail-closed？
- HITL 是谁确认的？
- audit receipt 是否存在？

Micrometer / OpenTelemetry 给的是 trace/metrics/logs 的通用技术底座，OTel GenAI 语义约定给的是 AI 调用、工具调用、agent span 的统一词汇。kube-agent 当前先用自己的 read model 和 trace 结构把证据讲清楚，后续再逐步对齐 GenAI semantic conventions。

### 2.2.10 MCP 是能力互操作，不是安全豁免

MCP 的价值是解决“每个模型/客户端都要为每个工具写一套集成”的问题。但 MCP server 暴露 tool 后，真正危险的问题才开始：

- 谁能发现这个 tool？
- 谁能调用这个 tool？
- tool input schema 会不会泄露内部路径？
- 外部 caller 能不能伪造 orgId/token？
- tool result 会不会泄露 raw token、raw prompt、raw audit？
- MCP runtime 是否有审计和 consent？

kube-agent 当前只开放 admin-only manifest/governance，不开放 `tools/call`，就是因为 Phase 1 要先把 Tool 治理和安全证据做稳。会接 MCP 不难，难的是安全地接。

### 2.2.11 Kubernetes API 思维

kube-manager 背后管理的是 Kubernetes/云平台资源。Agent 只要开始调用 kube-manager，就要用 Kubernetes API 思维审视行为：

- GET/list/watch 是读，但也可能是敏感读。
- create/update/patch/delete 是写，必须有幂等、审计、确认和回滚思路。
- RBAC 应遵循最小权限。
- audit log 要回答谁、何时、对什么资源做了什么。
- ServiceAccount、用户 token、orgId/namespace/project 不能混为一谈。

这也是为什么 kube-agent 的 `KubeManagerReadOnlySmokeTest` 只从 GET/READ `/api/{orgId}/node` 开始，而不是直接做创建、删除、扩缩容。

### 2.2.12 质量工具服务于 release gate

Maven Enforcer、JaCoCo、SpotBugs、CycloneDX、JUnit、ArchUnit、Testcontainers 都不是“为了显得工程化”。它们分别回答：

- 当前开发环境是否满足最低 JDK/Maven 要求？
- 哪些关键路径没有测试覆盖？
- 字节码里有没有明显 bug 或安全风险？
- 依赖供应链是否可追溯？
- 架构边界是否被绕过？
- 外部依赖能否在隔离环境复现？

顶级 Agent 的 release gate 不应该只看“能跑起来”，而要看身份、权限、Tool、安全、审计、RAG、Eval、供应链和文档证据是否同时成立。

## 2.3 推荐学习顺序

如果你想从 Agent 小白逐步变成能设计顶级 Agent 的工程师，建议按下面顺序读项目和资料：

1. 先读 Spring Boot / Spring Security，理解后端应用、认证、授权、过滤器和 Principal。
2. 读 kube-agent 的 `AuthController`、`SessionStore`、`AuthTokenFilter`、`AgentPrincipalResolver`，画出身份和 orgId 来源图。
3. 读 Spring AI Tool Calling 和 OpenAI Function Calling，再读 `ToolRegistry`、`ProtectedToolParameterFilter`、`SafeToolExecutor`。
4. 读 OWASP LLM Top 10，回头检查为什么项目反复说“LLM 输出是不可信候选”。
5. 读 `AtlasOrchestrator`、`AtlasGraphConfig`、`ReActEngine`、`PlanEngine`，理解 Graph/ReAct/Plan 只是编排证据。
6. 读 MCP 官方文档，再读 kube-agent 的 MCP manifest/governance，理解为什么一期不开 `tools/call`。
7. 读 RAG、VectorStore、Chat Memory，再读 kube-agent 的 Memory/RAG evidence read model，理解为什么证据托管先于向量检索。
8. 读 OpenTelemetry / Micrometer / Eval 文档，再读 `ObservabilityController`、Replay、Eval、Audit，理解顶级 Agent 如何被解释和回放。
9. 最后读 `docs/顶级Agent架构与技术学习地图.md` 和 `codex-memory/kube-agent/current/当前恢复状态.md`，把项目进度和长期目标合在一起看。

## 3. 一条请求的生命线

一个普通聊天请求大致经过这些阶段：

1. 前端携带 `X-Session-Id: ses_*` 或 Bearer token 调用后端。
2. `AuthTokenFilter` 把服务端保存的 session/token/orgId 桥接成 Spring Security `Authentication` 和 `UserPermissionContext` ThreadLocal。
3. `AgentPrincipalResolver` 解析当前可信主体。
4. `AtlasOrchestrator` 组装用户输入、conversation、traceId、token/orgId、Graph state。
5. `/api/agent/chat/stream` 走主 `supervisorGraph`；`/api/agent/chat/graph` 走实验 `compiledGraph` / `atlasGraph`。
6. `AtlasBrain`、Graph、ReAct 或 Plan 产出 `BrainDecision` 或候选 Tool 调用。
7. 所有真实 Tool 执行必须进入 `SafeToolExecutor`。
8. `SafeToolExecutor` 重新校验权限、租户、HITL、受保护参数和 durable audit gate。
9. `BaseTool` 通过 `KubeManagerHttpClient` 访问 kube-manager 8100。
10. 结果经 SSE、审计、Trace、Eval/Replay 读模型回到前端。

学习重点：每一步都在减少“不可信输入”对运行时权力的影响。

读代码时可以按下面顺序做一次手工 trace：

1. 从 Controller 方法看请求参数，标记哪些来自前端。
2. 到 `AuthTokenFilter` 看 token/orgId/userId 如何恢复。
3. 到 `AtlasOrchestrator` 看 traceId、conversationId、Graph state 如何创建。
4. 到 `AtlasBrain` / Graph / ReAct 看模型输出如何被解析成候选动作。
5. 到 `SafeToolExecutor` 看候选动作如何被重新校验。
6. 到 `KubeManagerHttpClient` 看真正外部 HTTP 请求的 token、orgId、method、path、query/body。
7. 回到 SSE/Observability/Audit 看用户和管理员如何理解结果。

常见误区：

- 误区一：模型判断“这是查询节点”就可以直接请求 kube-manager。正确做法：模型判断只是 intent，真实请求还要有可信 token/orgId 和 Tool 权限。
- 误区二：Graph State 里有 `orgId` 就可信。正确做法：要确认它来自 Spring Security / `UserPermissionContext`，不是来自 PlanStep 或 LLM params。
- 误区三：SSE 返回成功文本就代表 Tool 成功。正确做法：展示文本、Tool result、audit receipt、release gate 是不同证据。

## 4. 身份与租户边界

关键代码：

- `AuthController`
- `SessionStore`
- `AuthTokenFilter`
- `UserPermissionContext`
- `AgentPrincipalResolver`
- `AgentSecurityConfig`

当前规则：

- 登录请求里的 `organizationId` 只作为 kube-manager 登录参数，不是本地可信租户事实。
- 本地 `SessionStore` 里的 orgId 必须来自 kube-manager 响应或本次 token 反查。
- Bearer 认证路径也要恢复 token+orgId 原子上下文。
- `X-Session-Id` 是 `ses_*` 会话索引，不是用户身份，也不是 conversation owner。
- 前端、LLM、请求体中的 `userId` / `role` / `orgId` 都不能成为授权事实。

这轮 review 修复的典型问题：如果登录响应只有 token，而请求带 `organizationId=999999`，旧代码会把请求值当可信 orgId；现在必须用 token 反查或拒绝创建 Session。

你应该能画出两条身份恢复路线：

```text
登录路线:
LoginRequest(username/password/organizationId候选值)
  -> kube-manager login / token response
  -> trusted token + trusted orgId
  -> SessionStore(ses_*)
  -> AuthTokenFilter
  -> AgentPrincipal + UserPermissionContext

Bearer路线:
Authorization: Bearer <token>
  -> token lookup / trusted orgId recovery
  -> AgentPrincipal + UserPermissionContext
```

验收清单：

- `password` 不进入日志、响应、memory、audit raw、prompt。
- `token` 只在服务端上下文和 kube-manager HTTP 出口使用，不返回前端。
- `orgId` 缺失时不应悄悄使用默认组织。
- 异步线程执行结束后必须恢复旧 ThreadLocal。
- `conversationId` 只能定位资源，不能跨用户读取或删除。

## 5. HITL 不是一个按钮

关键代码：

- `HITLController`
- `TimedDecisionCache`
- `HitlConfirmation`
- `HitlGuard`

HITL 的正确理解：

- confirmToken 只能证明“前端拿到了凭证”，不能证明凭证属于当前用户。
- confirm 必须先校验 checkpoint 的 `user_id`、checkpoint orgId、当前 principal 和当前 principal orgId，再消费 token。
- clarify 虽然不需要 confirmToken，也必须先校验 owner，再删除 pending 决策。
- confirm 只注入服务端 `HitlConfirmation` marker；真正 Tool 执行仍要走 `SafeToolExecutor`。
- HITL 通过了，不代表 durable audit、权限、租户、release gate 也通过了。

这轮 review 修复的典型问题：旧顺序是先 `decisionCache.remove(...)` 再做 owner 校验，跨用户失败请求也可能破坏原用户的待确认状态；现在改成 owner-first，并且要求当前主体 orgId 与 checkpoint orgId 同时存在且一致。

HITL 学习时要分清三个对象：

- pending decision：服务端暂存的待确认事实，必须有 TTL 和 owner。
- confirmToken：前端提交的确认凭据，只是索引或证明材料，不是最终授权。
- `HitlConfirmation` marker：服务端注入执行链的“已确认”证据，仍要被 Tool 执行链二次校验。

设计 HITL 新能力时要问：

- 这个确认属于哪个 userId / orgId / traceId？
- confirm 是否 owner-first？
- clarify 是否也校验 owner？
- token 用完后是否单次消费？
- 超时、重复提交、跨用户提交、跨组织提交分别如何 fail-closed？

## 6. Tool 执行安全链

关键代码：

- `ToolRegistry`
- `ProtectedToolParameterFilter`
- `ToolParameterNormalizer`
- `SafeToolExecutor`
- `BaseTool`
- `KubeManagerHttpClient`

正确执行顺序：

1. 从 `ToolRegistry` 找 Tool 和风险元数据。
2. 校验当前用户是否可见/可执行该 Tool。
3. 过滤 `token`、`orgId`、`userId`、`confirmed`、`audit`、`release` 等受保护字段。
4. 用服务端可信 token/orgId 重新绑定 ThreadLocal。
5. 高风险 Tool 先过 HITL。
6. 高风险写操作再过 durable audit prewrite gate。
7. 执行 `BaseTool.execute(...)`。
8. 记录审计、恢复 ThreadLocal。

学习重点：Tool 的输入 Map 不是安全上下文，只是候选业务参数。子类需要租户时调用 `resolveOrganizationId(params)`，不能从 `params.organizationId` 取值，更不能给默认租户。

Tool 参数可以分三类：

| 参数类型 | 示例 | 可信来源 | 处理方式 |
|---|---|---|---|
| 普通业务参数 | `namespace`、`name`、`page`、`keyword` | 前端/LLM/Plan 可提供候选值 | 校验、归一化、编码、限幅 |
| 服务端控制参数 | `token`、`orgId`、`userId`、`traceId` | Spring Security / UserPermissionContext / 服务端生成 | 调用方传入的一律过滤 |
| 权力证明参数 | `confirmed`、`hitlToken`、`auditReceipt`、`releaseDecision`、`writeAllowed` | HITL / audit / release gate 服务端证据 | 不接受外部伪造 |

读一个新 Tool 时，按这个顺序审查：

1. Tool 是否有明确 `operationType`、risk level、permission metadata？
2. READ Tool 是否真的只构造 GET query/path？
3. path segment 是否编码和拒绝注入？
4. body 是否白名单构造？
5. 是否调用 `resolveOrganizationId` 而不是读 `params.organizationId`？
6. 是否避免把 raw token、raw response、raw prompt 返回前端或日志？

## 7. 写操作为什么默认难

顶级 Agent 的写操作不是“LLM 说要创建，所以调用 POST”。它至少需要：

- 显式 `operationType` 风险元数据。
- 当前用户真实 token。
- 可信 orgId。
- 权限校验。
- HITL 确认。
- durable audit 可用。
- 执行前 durable prewrite receipt。
- 幂等键。
- 写后 readback。
- release gate。
- 失败补偿/回滚说明。
- reviewed trace/eval 证据。

当前默认策略：高风险写在 durable audit 不 ready 时 fail-closed。已有历史写 Tool 代码可以存在，但不能被文档或前端当成一期已开放 runtime authority。

一个写 Tool 从 HOLD 到可开放，至少要补齐这些证据：

| 证据 | 为什么需要 |
|---|---|
| Tool 风险元数据 | 让 Registry、MCP manifest、前端、审计都知道它是写操作。 |
| HITL 文案和参数摘要 | 让用户确认“将对哪个资源做什么”。 |
| durable audit prewrite | 执行前先留下不可抵赖的意图记录。 |
| 幂等键 | 避免网络重试或用户重复提交造成重复创建/删除。 |
| 写后 readback | 证明外部系统状态与预期一致。 |
| rollback / compensation 说明 | 写失败或半成功时能解释如何处理。 |
| reviewed trace + eval | 用真实或仿真轨迹证明边界不会被绕过。 |
| frontend read model | 前端只展示后端拥有的状态，不自己制造“可执行”。 |

## 8. MCP 当前只是治理面

关键代码：

- `McpManifestController`
- `McpToolManifestService`
- `McpGovernanceOverviewService`

当前 MCP 事实：

- `/api/agent/mcp/**` 是 admin-only。
- Manifest / governance 是只读读模型。
- 不开放 MCP runtime server。
- 不开放 `tools/call`。
- 不接受外部 caller 提供 Tool 参数。
- 不导出写 Tool、敏感 Tool、未知风险 Tool、需要 HITL 的 Tool。
- NIM / HPC / Slurm / BCM 二期域 Tool 不进入一期 MCP manifest。

学习重点：MCP 是能力协议，不是权限模型。接 MCP 越容易，越要先把 governance、consent、audit、tool safety 做清楚。

未来如果要打开 MCP runtime，学习和设计时至少要补：

- `tools/list` 与 `tools/call` 分离，list 可见不等于 call 可执行。
- 每次 call 都要映射到 `SafeToolExecutor`，不能引入第二条裸执行通道。
- 外部 MCP caller 的身份、租户、权限和审计要有明确来源。
- Tool result 要 redacted-only，不能泄露内部 endpoint、token、raw audit、raw prompt。
- 写 Tool 默认不导出；确需导出时要有 HITL、audit、release 和 operator consent。

## 9. Memory/RAG 的当前边界

关键代码：

- `MemoryController`
- `ConversationSummaryMemoryStore`
- `AgentMemoryRag*` observability services

当前 Memory 有两层：

- 用户级轻量摘要接口：保存 caller-submitted bounded summary，做基础正则脱敏和截断。
- admin-only Memory/RAG 读模型：描述未来 source custody、citation、digest、lifecycle、eval gate、trace curation 的证据合同。

不能误解的地方：

- 当前摘要不是服务端验证过的事实。
- 当前摘要不是可信 RAG 引用源。
- 当前摘要不能直接自动注入 prompt 成为权威。
- 正则脱敏不是完整 DLP。

未来要打开 RAG prompt influence，必须先有 source custody、tenant/privacy、delete/export、reviewed traces、eval gate、operator visibility。

RAG 上线前的最小设计题：

1. 文档来源：谁上传、何时上传、属于哪个 org/user/project？
2. 切分策略：chunk 大小、重叠、结构化元数据、原文定位如何保存？
3. 向量索引：embedding 模型版本、重建策略、删除策略如何管理？
4. 检索权限：当前 Principal 能否读取每个 chunk？
5. 引用证据：回答中的 citation 能否回到 source/chunk/hash？
6. 提示注入：文档里的恶意指令如何被隔离成“内容”而不是“系统指令”？
7. 评测：召回率、幻觉率、泄露率、延迟和成本如何度量？

## 10. Eval / Replay / Observability

关键代码：

- `ObservabilityController`
- `AgentReplayTimelineService`
- `AgentEval*` services
- `AgentAudit*`

当前能力：

- Observability 和 Top-tier 读模型基本是 admin-only。
- Replay / Eval 有确定性、脱敏、admin-only 读模型和部分 suite run/gate 入口。
- Eval 的目的不是给运行时“自动授权”，而是为 release gate、回归测试和学习复盘提供证据。

仍关闭的能力：

- CI blocking promotion。
- LLM-as-judge runtime eval。
- Memory/RAG retrieval eval runtime。
- 未 reviewed trace 的 release authority。

学习重点：Eval 不是装饰品。没有 reviewed trace 和可解释 gate 的 Agent，很难称为顶级。

一个好的 Eval 用例应该包含：

- 输入：用户文本、身份上下文、orgId、conversationId、必要 fixture。
- 预期路由：应该命中哪个 intent，哪些 intent 不应命中。
- 预期 Tool：是否允许调用 Tool，Tool 名称、method、path/query/body 的边界。
- 预期安全结果：是否 fail-closed，错误码是什么，用户可见文案是什么。
- 预期证据：trace、audit、HITL、release、memory/RAG citation 是否存在或必须不存在。

Replay 的价值是把一次 Agent 行为拆成时间线。你可以从 replay 里学习：

- 哪一步最早知道用户身份？
- 哪一步产生 intent？
- 哪一步过滤了控制字段？
- 哪一步决定不执行？
- 哪一步把结果投影给前端？

## 11. Graph / ReAct / Plan 怎么学

建议阅读顺序：

1. `AtlasOrchestrator`：看 HTTP/SSE 入口如何准备 state。
2. `AtlasGraphConfig`：看 StateGraph 节点、条件边、checkpoint 策略。
3. `AtlasBrain`：看自然语言如何变成结构化 `BrainDecision`。
4. `BrainDecision` / `ExecutionContext`：记住它们是候选决策，不是授权事实。
5. `ReActEngine`：看多步 reasoning/action/observation 如何保持执行边界。
6. `PlanEngine`：看计划/反思如何与 Tool 执行分离。
7. `SafeToolExecutor`：确认所有路径最终回到统一执行边界。

下一批中文注释应重点覆盖这里，因为这里是从“会写 Controller”升级到“会设计 Agent 编排”的关键。

Graph/ReAct/Plan 的学习口诀：

```text
Graph 管流程，ReAct 管循环，Plan 管计划。
它们都不管授权；授权和执行必须回到 SafeToolExecutor。
```

做代码 review 时重点查：

- Graph State 是否混入 LLM 参数中的控制字段。
- ReAct `Action.params` 是否经过 `ProtectedToolParameterFilter`。
- Plan 是否只能计划，不能伪造 `approved/writeAllowed/releaseDecision`。
- delegate 子图是否恢复 ThreadLocal，避免身份泄漏。
- SSE 是否展示 fail-closed reason，而不是空白或 `{}`。

## 12. 前端与后端的分工

后端 owned：

- 身份事实。
- Tool 元数据。
- MCP/export policy。
- release / audit / eval / memory readiness。
- 只读治理模型。
- 是否允许执行。

前端 owned：

- 渲染。
- 筛选、搜索、折叠、详情。
- 本地草稿和当前会话 UI 状态。

前端不能 owned：

- 权限。
- release decision。
- HITL marker。
- durable audit receipt。
- kube-manager writeAllowed。
- MCP runtime enablement。

这也是为什么当前 Vue 工作台应优先读后端 GET read models，而不是自己造治理逻辑。

前端设计时可以展示：

- 当前用户可见 Tool 目录。
- Tool 风险等级和只读/写入状态。
- HITL 待确认摘要。
- MCP manifest/governance 只读状态。
- Eval、Replay、Audit、Memory/RAG readiness。

前端不能做：

- 根据按钮状态决定后端是否有权限。
- 自己拼接 kube-manager 写请求。
- 把用户确认直接变成 `confirmed=true` 传给 Tool。
- 把本地缓存的 orgId/token/userId 当作服务端事实。
- 把 “Agent 回答成功” 展示成 “生产操作已审计并发布”。

## 13. 一个切片的标准闭环

以这轮 review 为例，一个合格切片应包含：

1. 读代码与测试，确认真实行为。
2. 找文档与代码不一致处。
3. 修最危险的代码边界。
4. 补源码契约或 focused tests。
5. 修文档和学习说明。
6. 更新 `codex-memory/kube-agent/current`。
7. 跑 focused tests、全量 test、validate、diff check。
8. commit + push。

这样做的意义：项目进度、学习记忆和工程质量不会因为会话重启或上下文丢失而断裂。

切片大小建议：

- 能在 1-3 小时内读完、改完、测完、提交。
- 只改一个边界或一个学习主题。
- 优先给已有行为补解释和契约，不为了注释大规模重写历史代码。
- 每次都留下 `codex-memory` 恢复记录，说明“下一步从哪里继续”。

## 14. 练习题

1. 找一个 READ Tool，解释它从哪里拿 token/orgId，为什么不能信 `params.organizationId`。
2. 跟踪一次 HITL confirm，从 SSE `hitl_request` 到 `SafeToolExecutor`，列出每个 fail-closed 点。
3. 比较 `/api/agent/chat/stream` 和 `/api/agent/chat/graph` 的 state 初始化差异。
4. 选一个 Memory/RAG read model，说明它目前为什么还不能打开 retrieval runtime。
5. 给一个未来写 Tool 设计 release gate checklist，至少包含 durable audit、idempotency、readback、HITL、rollback。
6. 读 `RuleMatcherTest` 和 `IntentArbiterTest`，解释关键词命中、embedding 相似度和 crossBoost 为什么都不是授权。
7. 读 `TokenPropagatingTaskDecoratorTest`，说明异步线程为什么必须恢复旧 ThreadLocal。
8. 设计一个 MCP runtime `tools/call` 开启前的架构测试，确保它只能委托 `SafeToolExecutor`。
9. 设计一个 RAG source custody DTO，写清 sourceId、tenant、chunkHash、citation、delete/export 状态。
10. 选一个失败用例，写出用户可见错误、管理员可见审计、开发者可见 trace 三种不同视角。

## 14.1 分阶段学习任务

| 阶段 | 目标 | 读什么 | 产出 |
|---|---|---|---|
| 入门 | 知道 Agent 后端不是单次模型调用。 | 本指南 1-3 节、README、路线图。 | 画出“一条请求的生命线”。 |
| 身份安全 | 能解释 token/orgId/session/conversation 的区别。 | auth、store、config 相关代码和测试。 | 画身份来源图，列 fail-closed 点。 |
| Tool 安全 | 能审查一个 Tool 是否安全。 | ToolRegistry、SafeToolExecutor、BaseTool、query helper。 | 写一个 READ Tool 审查表。 |
| 编排 | 能解释 Graph/ReAct/Plan 怎么产生候选动作。 | AtlasOrchestrator、AtlasGraphConfig、ReActEngine、PlanEngine。 | 画 Graph state 与 Tool 执行边界图。 |
| 治理 | 能解释 MCP、HITL、Audit、Eval、Replay 的关系。 | mcp、hitl、audit、observability。 | 写一个写 Tool 开放前 release gate。 |
| 进阶 | 能设计 RAG/Multi-Agent 的安全上线方案。 | memory/rag read models、多专家审查读模型、官方资料。 | 写一份 RAG 或 MCP runtime 设计 ADR。 |

## 14.2 判断自己是否真的学会

如果你能回答下面这些问题，说明你已经从“会调模型”进入“会设计 Agent 后端”的阶段：

- 为什么 Tool input schema 不等于权限系统？
- 为什么 `conversationId` 不是授权？
- 为什么 HITL confirmToken 需要 owner-first 校验？
- 为什么写请求不能随便 retry？
- 为什么 RAG prompt influence 要晚于 source custody？
- 为什么 MCP manifest 可以开放但 `tools/call` 要关闭？
- 为什么 Eval 结果不能直接授予 release authority？
- 为什么 traceId 不能当身份？
- 为什么前端不能拥有 `writeAllowed`？
- 为什么 Phase 2 暂停 NIM/HPC/Slurm/BCM 不等于 Phase 1 降级？

## 15. 当前最重要的安全不变量

- 当前代码事实高于文档，文档冲突时先读代码和测试。
- LLM/Plan/ReAct/前端输出都是候选输入，不是授权事实。
- token/orgId/userId/HITL/audit/release 只能来自服务端可信上下文。
- HITL 确认不是写操作充分条件。
- 高风险写默认 fail-closed，durable audit 缺失不执行。
- MCP governance admin-only，runtime tools/call 关闭。
- Memory summary 不是可信 RAG authority。
- Phase 2 NIM/HPC/Slurm/BCM 暂停，不降低 Phase 1 顶级 Agent Core 标准。

## 16. 技术专题精读索引

这一节更像课程讲义：每个专题都用 kube-agent 的真实代码说明“这门技术到底解决什么问题、该怎么学、学完以后在本项目里能看懂什么”。如果前面的章节是地图，这里就是逐个地点的导览牌。

### 16.1 Java / Maven / Spring 控制平面

kube-agent 选择 Java / Spring 作为主语言，不是因为它最时髦，而是因为 Agent 后端最终是一个控制平面：它要长期维护身份、权限、审计、配置、HTTP 出口、测试契约和运维可观测。Java 的类型系统、record、泛型、异常模型和成熟生态，很适合把“LLM 的不确定输出”包进确定的服务端边界。

在本项目里要重点看：

- `record` / DTO：用于表达只读返回值、请求契约和 evidence artifact。学习点是不可变数据结构如何减少运行时篡改面。
- Maven lifecycle：`test`、`validate`、dependency management 和插件校验构成最基础的工程闸门。
- Spring Boot auto-configuration：让 Controller、Service、Configuration、Actuator、配置属性按约定装配，但不能因此把安全事实藏进隐式魔法里。
- Spring Security filter chain：请求从 HTTP 进入业务代码前，必须先恢复可信 Principal 和权限上下文。
- ThreadLocal 兼容桥：历史 Tool / kube-manager outlet 需要 token/orgId，`AuthTokenFilter` 和 `TokenPropagatingTaskDecorator` 负责桥接与清理。

kube-agent 的学习口令：

```text
Java/Spring 负责确定性边界，LLM 负责候选语义。
类型、测试、配置和安全链路越清楚，Agent 的可控性越高。
```

推荐资料：

- [Java 17 Records](https://docs.oracle.com/en/java/javase/17/language/records.html)
- [Maven Build Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/index.html)
- [Spring Security Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security Authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/index.html)

### 16.2 LLM 接入、Tool Calling 与结构化输出

LLM 接入不是“发一段 prompt，拿一段文本”。生产 Agent 至少要处理模型选择、上下文裁剪、结构化输出、工具调用、失败降级、超时、重试、成本和安全审查。kube-agent 当前把模型能力放在 `AtlasBrain`、Spring AI 适配、ReAct / Plan 编排中，但真正执行永远回到服务端。

关键概念：

| 概念 | 正确理解 | kube-agent 中的边界 |
|---|---|---|
| ChatModel | 与模型服务交互的抽象。 | 只产生文本或结构化候选，不直接访问 kube-manager。 |
| Structured Output | 让模型输出可解析 JSON / DTO。 | 解析成功不等于授权成功，仍需服务端校验。 |
| Tool Calling | 模型请求应用执行某个工具。 | Tool JSON 是候选参数，必须进入 `SafeToolExecutor`。 |
| Run Context | 应用运行时可见、模型不可见的可信上下文。 | token/orgId/userId 应留在服务端上下文，不塞进 prompt。 |
| Prompt | 影响模型行为的文本。 | Prompt 不能承载权限、租户、HITL、audit 或 release 决策。 |

读源码时可以按这条线走：

```text
用户文本
  -> AtlasOrchestrator 组装上下文
  -> AtlasBrain / Spring AI 产出 BrainDecision
  -> Graph / ReAct / Plan 产生候选 Tool
  -> ToolParameterNormalizer / ProtectedToolParameterFilter
  -> SafeToolExecutor
```

推荐资料：

- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [OpenAI Function Calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Agents SDK - Agent definitions](https://developers.openai.com/api/docs/guides/agents/define-agents)
- [OpenAI Agents SDK - Running agents](https://developers.openai.com/api/docs/guides/agents/running-agents)

### 16.3 Graph / ReAct / Plan / 多 Agent 编排

Agent 编排要解决的是“复杂任务如何拆步、路由、观察和恢复”。kube-agent 里同时保留了 Graph、ReAct、Plan 和多专家审查读模型，是为了让你学习不同编排范式的边界。

| 编排方式 | 解决什么 | 容易误解什么 | kube-agent 的约束 |
|---|---|---|---|
| Graph | 用状态机表达节点、边和条件跳转。 | 以为 Graph State 里的字段天然可信。 | State 只能传递证据，不能凭空制造 token/orgId/writeAllowed。 |
| ReAct | 把 Thought / Action / Observation 拆开。 | 以为 Action 就是可以执行的命令。 | Action.params 仍是模型候选输入。 |
| Plan | 先规划步骤，再尝试执行或解释。 | 以为 PlanStep 可以携带授权。 | Plan 只能建议，不授予 HITL/audit/release。 |
| Agents as Tools | 主 Agent 调用专家 Agent 辅助。 | 以为专家返回就等于生产事实。 | 专家输出仍是证据或建议。 |
| Handoff / A2A | 一个 Agent 把任务交给另一个 Agent。 | 以为交接就是权限转移。 | 当前只做 provenance / compatibility / review 证据，不开放 runtime handoff。 |

学习时要特别观察失败路径。顶级 Agent 的难点不在“成功时能跑通”，而在“缺 token、缺 orgId、Tool 不可见、HITL 过期、audit 不 ready、kube-manager 不通时，是否能解释并安全停止”。

推荐资料：

- [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)
- [OpenAI Agents SDK - Orchestration and handoffs](https://developers.openai.com/api/docs/guides/agents/orchestration)
- [OpenAI Agents SDK - Results and state](https://developers.openai.com/api/docs/guides/agents/results)
- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)

### 16.4 Tool 治理与 kube-manager HTTP 出口

Tool 是 Agent 从“会说”走向“会做”的桥。桥越强，越要有栏杆。kube-agent 的 Tool 治理不是一个类，而是一条链：

```text
ToolRegistry
  -> permission / risk metadata
  -> ToolParameterNormalizer
  -> ProtectedToolParameterFilter
  -> SafeToolExecutionRequest
  -> SafeToolExecutor
  -> BaseTool
  -> KubeManagerHttpClient
```

需要掌握的知识点：

- Tool 元数据：名称、描述、参数、权限、风险等级、operationType、是否需要 HITL。
- 参数归一化：把别名转成 canonical 字段，减少模型填错字段。
- 受保护参数过滤：`token`、`orgId`、`userId`、`traceId`、`confirmed`、`auditReceipt`、`releaseDecision` 等绝不能由前端或模型提供。
- URL 构造：GET query/path 要用结构化构造，避免手拼导致二次编码或注入。
- kube-manager token：只在服务端上下文和 HTTP 出口使用，不进入模型上下文。
- 重试策略：读请求可以有限重试；写请求需要幂等键、审计和 readback，不能无脑 retry。

推荐资料：

- [Kubernetes API Concepts](https://kubernetes.io/docs/reference/using-api/api-concepts/)
- [Kubernetes RBAC Good Practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/)
- [Kubernetes Auditing](https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/)
- [Resilience4j Getting Started](https://resilience4j.readme.io/docs/getting-started)

### 16.5 HITL、Guardrails、Audit 与 Release Gate

HITL、Guardrails、Audit、Release Gate 常被混在一起，但它们承担的是不同职责：

| 能力 | 解决的问题 | 不能替代什么 |
|---|---|---|
| Guardrails | 自动检查输入、输出或 Tool 行为。 | 不能替代身份权限和审计。 |
| HITL | 高风险动作前暂停，等待人类确认。 | 不能替代 owner 校验、audit 和 release gate。 |
| Audit | 记录谁在什么时候尝试或完成了什么。 | 不能自动证明动作是安全的。 |
| Release Gate | 判断某类能力是否允许上线或执行。 | 不能被单次模型输出或前端按钮绕过。 |

kube-agent 当前的高风险写目标链路应当是：

```text
trusted principal + trusted orgId
  -> Tool risk metadata
  -> HITL owner-first confirmation
  -> durable audit prewrite
  -> idempotency key
  -> SafeToolExecutor
  -> kube-manager write
  -> readback / compensation
  -> replay + eval evidence
```

学习时要记住：确认按钮是用户体验，不是安全系统本身。真正的安全系统是“确认背后的服务端证据链”。

推荐资料：

- [OpenAI Agents SDK - Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)
- [NIST AI Risk Management Framework](https://www.nist.gov/itl/ai-risk-management-framework)
- [OWASP LLM Top 10](https://owasp.org/www-project-top-10-for-large-language-model-applications/)

### 16.6 Memory / RAG / VectorStore / GraphRAG

RAG 表面上是检索增强生成，工程上是“证据如何进入模型上下文”。kube-agent 当前先做 Memory/RAG readiness、source evidence、digest、lifecycle、eval gate 和 trace curation，不急着打开 retrieval prompt influence，是因为生产 RAG 的核心风险不是向量相似度，而是证据托管。

必须理解的 RAG 组件：

| 组件 | 作用 | kube-agent 上线前要问的问题 |
|---|---|---|
| Source | 原始文档或事实来源。 | 谁上传、属于哪个 tenant、是否可删除和导出？ |
| Chunk | 可检索片段。 | 如何保留原文位置、hash、权限和过期状态？ |
| Embedding | 把文本变成向量。 | 模型版本、tokenizer、重建策略和失败降级是什么？ |
| VectorStore | 存储和检索向量。 | 是否按租户隔离，删除是否能影响索引？ |
| Reranker | 对候选片段二次排序。 | 是否会引入新模型风险和额外延迟？ |
| Citation | 回答中的引用证据。 | 用户能否回到 source/chunk/hash 验证？ |
| Eval Gate | 检查 RAG 是否可靠。 | 召回率、幻觉率、泄露率和越权率如何度量？ |

GraphRAG、reranker、hybrid search、agentic retrieval 都可以成为顶级 Agent 的进阶能力，但必须晚于 source custody、privacy/tenant、lifecycle、eval 和 operator visibility。

推荐资料：

- [Spring AI Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Spring AI Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html)
- [DJL HuggingFace Tokenizers](https://docs.djl.ai/master/extensions/tokenizers/index.html)

### 16.7 Eval / Replay / Observability

顶级 Agent 不能只靠“我试了一下好像可以”。它要能回答：

- 当时用户是谁？
- 模型看到了什么？
- 命中了哪个 intent？
- 计划了什么？
- 调用了哪个 Tool？
- 参数过滤发生了吗？
- 为什么执行或为什么不执行？
- 用户看到什么？
- 管理员能审计什么？
- 下次代码修改会不会破坏这个行为？

kube-agent 用 Replay 把行为拆成时间线，用 Eval 把期望行为变成可重复检查，用 Observability 读模型把证据投影给管理员和前端工作台。

Eval 可以分三层学：

| 层次 | 目标 | 示例 |
|---|---|---|
| Deterministic contract eval | 不依赖 LLM，检查代码契约。 | Tool 参数过滤、MCP manifest 不导出写 Tool。 |
| Reviewed trace eval | 用人工审阅过的脱敏轨迹回归。 | 某次 HITL fail-closed 路径必须保持。 |
| LLM-as-judge eval | 用模型辅助判断质量。 | 回答是否完整、引用是否充分。当前仍需单独 gate。 |

Observability 要避免两个极端：一个极端是只记录普通日志，无法解释 Agent 决策；另一个极端是把 raw prompt、token、敏感响应全写进日志。正确方向是 redacted、typed、trace-linked、role-aware。

推荐资料：

- [OpenAI Agents SDK - Integrations and observability](https://developers.openai.com/api/docs/guides/agents/integrations-observability)
- [OpenAI Agents SDK - Tracing](https://openai.github.io/openai-agents-python/tracing/)
- [OpenAI Evals Guide](https://developers.openai.com/api/docs/guides/evals)
- [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/index.html)
- [OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)

### 16.8 MCP：Resources、Prompts、Tools 的区别

MCP 的学习重点不是“怎么把工具列出来”，而是“列出来以后谁能看、谁能调用、调用结果如何治理”。MCP 至少有三类概念：

| MCP 能力 | 含义 | 安全重点 |
|---|---|---|
| Resources | 给模型或客户端读取的上下文数据。 | 读权限、租户隔离、敏感字段脱敏。 |
| Prompts | 可复用提示词或工作流模板。 | 模板版本、变量来源、prompt injection 边界。 |
| Tools | 可被调用的函数或外部动作。 | caller 身份、参数过滤、审计、HITL、执行边界。 |

kube-agent 当前只把 MCP 当 admin-only 只读治理面：可以展示 Tool manifest 和 governance overview，但不开放 runtime server，不开放 `tools/call`。这不是保守，是正确顺序。因为 `tools/list` 和 `tools/call` 必须分离，可见不等于可执行。

推荐资料：

- [MCP Introduction](https://modelcontextprotocol.io/docs/getting-started/intro)
- [MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18)
- [MCP Tools](https://modelcontextprotocol.io/specification/2025-06-18/server/tools)
- [Spring AI MCP Overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)

### 16.9 Agent 安全：从 Prompt Injection 到供应链

Agent 安全不能只看 Prompt Injection。一个顶级 Agent 至少要覆盖：

- Prompt Injection：用户或文档试图覆盖系统规则。
- Tool Abuse：模型或用户诱导调用高风险工具。
- Excessive Agency：Agent 被授予超过任务需要的权力。
- Sensitive Information Disclosure：token、prompt、audit、租户数据泄露。
- Insecure Output Handling：把模型输出直接拼 SQL、URL、命令或 YAML。
- Supply Chain：模型文件、依赖、MCP server、插件、容器镜像不可信。
- Multi-tenant Isolation：跨 org/user/conversation 数据污染。
- Auditability：出事后无法解释谁做了什么。

kube-agent 的对应防线：

| 风险 | 防线 |
|---|---|
| Prompt 注入控制字段 | `ProtectedToolParameterFilter` + 服务端上下文重绑定。 |
| 跨租户 | trusted orgId、`UserPermissionContext`、owner-first 校验。 |
| Tool 越权 | `ToolRegistry` 元数据 + `SafeToolExecutor`。 |
| 高风险写 | HITL + durable audit + release gate。 |
| 证据污染 | redacted replay、reviewed trace、catalog patch review。 |
| 供应链 | Maven dependency gate、SBOM、模型文件降级策略。 |

推荐资料：

- [OWASP LLM Top 10](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [NIST AI RMF Resource Center](https://airc.nist.gov/airmf-resources/airmf/)
- [CycloneDX Maven Plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [SpotBugs Maven Plugin](https://spotbugs.github.io/spotbugs-maven-plugin/)

### 16.10 测试、契约与质量门

在普通项目里，测试证明“功能没坏”。在 Agent 项目里，测试还要证明“模型、前端、Graph、ReAct、Plan、MCP、RAG 都没有绕过安全边界”。所以 kube-agent 的测试大量采用 contract test 和 source marker test。

测试类型可以这样理解：

| 测试类型 | 学什么 | kube-agent 例子 |
|---|---|---|
| Unit test | 一个类的确定行为。 | 参数归一化、缓存 TTL、DTO 投影。 |
| Contract test | 跨类边界的安全约定。 | Tool schema 不是权限、MCP 不导出写 Tool。 |
| Source marker test | 注释和安全说明不能丢。 | 中文教学注释 contract。 |
| Integration test | 多个组件协作。 | ReAct 多步、Graph 节点、HTTP outlet。 |
| Opt-in smoke | 真实外部系统验证。 | kube-manager `8100` READ smoke。 |
| Architecture test | 防止绕过边界。 | 未来可用 ArchUnit 锁定 MCP call 只能进 SafeToolExecutor。 |

好的 Agent 测试不只是断言“返回成功”，还要断言“没有调用不该调用的东西”。例如：

- 没有创建 HITL marker。
- 没有写 audit/memory。
- 没有调用 kube-manager。
- 没有打开 MCP `tools/call`。
- 没有把 `traceId` 当身份。
- 没有把 eval result 当 release authority。

推荐资料：

- [JUnit User Guide](https://docs.junit.org/current/user-guide/)
- [AssertJ](https://assertj.github.io/doc/)
- [Mockito](https://site.mockito.org/)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Testcontainers for Java](https://java.testcontainers.org/)
- [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html)

## 17. 从小白到高手的项目化学习路线

下面这条路线不是只读资料，而是把阅读、编码、测试、审计和文档串起来。每完成一段，都应该能回到 kube-agent 写出一个小切片。

| 阶段 | 学习目标 | 项目实践 | 完成标准 |
|---|---|---|---|
| 第 1 阶段：后端基本功 | 会读 Spring Controller / Service / Config / Test。 | 追踪一次 `/api/agent/chat/stream` 请求。 | 画出 Controller 到 Orchestrator 的调用图。 |
| 第 2 阶段：身份与租户 | 分清 token、session、Principal、orgId、conversationId。 | 阅读 auth/store 代码和测试。 | 能解释为什么前端 orgId 不可信。 |
| 第 3 阶段：Tool 安全 | 理解 Tool calling 和 SafeToolExecutor。 | 审查一个 READ Tool 的参数、权限、HTTP path。 | 写出 Tool 安全审查清单。 |
| 第 4 阶段：编排 | 理解 Graph / ReAct / Plan 的差异。 | 跟踪一次 ReAct 或 Graph fail-closed。 | 能指出候选动作在哪里被拦住。 |
| 第 5 阶段：HITL 与审计 | 理解人工确认、审计、release gate 的关系。 | 设计一个写 Tool 上线前证据包。 | 能说清 HITL 为什么不是充分条件。 |
| 第 6 阶段：RAG 与 Memory | 理解 source custody、citation、eval。 | 为一个未来知识库写 DTO 和 gate checklist。 | 能解释为什么当前不打开 retrieval runtime。 |
| 第 7 阶段：Eval / Observability | 理解 replay、trace、deterministic eval。 | 给一个历史 bug 写 reviewed trace eval 思路。 | 能把一次 Agent 行为拆成时间线。 |
| 第 8 阶段：多 Agent 与协议 | 理解 MCP、A2A、agents-as-tools、handoff。 | 设计只读 MCP runtime 前置架构测试。 | 能证明可见、可调用、可执行是三件事。 |
| 第 9 阶段：工程治理 | 掌握文档、恢复记忆、提交、发布闸门。 | 完成一个小切片并更新 codex-memory。 | 能让新会话完整恢复上下文。 |

学习时建议每次只选一个问题，不要同时学所有东西。例如这一周只学“Tool 参数为什么不可信”，就围绕 `ProtectedToolParameterFilter`、`SafeToolExecutor`、`BaseToolOrganizationIdGovernanceTest` 和相关文档做一轮闭环。

## 18. 官方资料导航清单

下面按主题整理一份长期可用的资料清单。优先读官方文档，博客和视频可以作为辅助，但不要把它们当成项目安全决策来源。

| 主题 | 优先资料 |
|---|---|
| Java / Maven | [Java 17 Records](https://docs.oracle.com/en/java/javase/17/language/records.html)、[Maven Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)、[Maven Enforcer](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html) |
| Spring Boot / Security | [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/index.html)、[Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html)、[Authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/index.html) |
| Spring AI | [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)、[Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)、[RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)、[MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) |
| OpenAI Agent 思路 | [Agents SDK Guide](https://developers.openai.com/api/docs/guides/agents)、[Agent definitions](https://developers.openai.com/api/docs/guides/agents/define-agents)、[Orchestration](https://developers.openai.com/api/docs/guides/agents/orchestration)、[Guardrails](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)、[Tracing](https://openai.github.io/openai-agents-python/tracing/) |
| MCP | [MCP Intro](https://modelcontextprotocol.io/docs/getting-started/intro)、[MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18)、[MCP Tools](https://modelcontextprotocol.io/specification/2025-06-18/server/tools) |
| Kubernetes / kube-manager 背景 | [Kubernetes API](https://kubernetes.io/docs/concepts/overview/kubernetes-api/)、[API Concepts](https://kubernetes.io/docs/reference/using-api/api-concepts/)、[RBAC Good Practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/)、[Auditing](https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/) |
| 可观测 | [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/index.html)、[OpenTelemetry Docs](https://opentelemetry.io/docs/)、[OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) |
| RAG / Embedding | [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)、[Spring AI Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)、[ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html)、[DJL Tokenizers](https://docs.djl.ai/master/extensions/tokenizers/index.html) |
| 安全治理 | [OWASP LLM Top 10](https://owasp.org/www-project-top-10-for-large-language-model-applications/)、[NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework)、[NIST AI RMF Resource Center](https://airc.nist.gov/airmf-resources/airmf/) |
| 测试与质量 | [JUnit](https://docs.junit.org/current/user-guide/)、[AssertJ](https://assertj.github.io/doc/)、[Mockito](https://site.mockito.org/)、[ArchUnit](https://www.archunit.org/userguide/html/000_Index.html)、[Testcontainers](https://java.testcontainers.org/)、[JaCoCo](https://www.eclemma.org/jacoco/trunk/doc/maven.html)、[CycloneDX](https://cyclonedx.github.io/cyclonedx-maven-plugin/) |

最后提醒一句：资料越新，越不能直接变成主干依赖或运行时开关。kube-agent 的路线是先把新技术变成“可解释的合同、测试、文档、读模型和恢复记忆”，再用 reviewed trace、eval gate 和 release evidence 决定是否进入运行时。
