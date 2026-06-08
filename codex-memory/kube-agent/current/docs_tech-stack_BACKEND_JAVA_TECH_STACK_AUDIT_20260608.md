# 后端 Java 技术栈先进性审计 - 2026-06-08

## 结论

当前后端继续以 Java / Spring 为主语言是合理且先进的选择。对 `kube-agent` 这种需要长期维护、强安全边界、审计、可观测、稳定 HTTP 集成、丰富测试和团队可学习性的 Agent Core 来说，Java 生态的工程确定性明显高于为了“看起来更 Agent”而把核心控制面改成脚本式运行时。

但“Java 是合理的”不等于“当前没有升级空间”。一期顶级 Agent 的正确路线是：主线保持 `Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17` 的可验证稳定底座，同时建立 Java 21/25、Spring Boot 4、Spring AI 2 的兼容矩阵和试验分支。这样既能拥抱最新技术，又不会牺牲每轮测试、提交、推送和恢复能力。

## 官方版本事实

2026-06-08 核对官方文档后的事实：

- Spring Boot 文档当前显示 `4.0.6`，并列出 `3.5.14` 文档线；Spring Boot 4.0.6 要求 Java 17+、Spring Framework 7.0.7+，并支持到 Java 26。
- Spring AI 官方文档当前稳定线包含 `1.1.7`，同时 `2.0.0-RC1` / `2.0.0-M7` 仍属于候选或里程碑线。
- Oracle Java SE Roadmap 将 Java SE 25 标为 LTS，GA 时间为 2025-09，Premier Support 到 2030-09。

参考：

- Spring Boot System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot Documentation Index: https://docs.spring.io/spring-boot/index.html
- Spring AI Reference: https://docs.spring.io/spring-ai/reference/search.html
- Spring AI 1.0.8 / 1.1.7 / 2.0.0-M7 release note: https://spring.io/blog/2026/05/23/spring-ai-1-0-8-1-1-7-2-0-0-M7-available-now/
- Oracle Java SE Support Roadmap: https://www.oracle.com/java/technologies/java-se-support-roadmap.html

## 为什么 Java 仍是一期主线

### 1. Agent Core 本质上是控制面系统

顶级 Agent 不只是 prompt + tool call。它需要：

- 身份、租户、RBAC、HITL、审计和 fail-closed；
- Tool 元数据、参数 schema、风险分类和执行边界；
- SSE、HTTP 出口、重试、熔断、限流、traceId 和 metrics；
- 大量契约测试、防绕过测试和供应链门禁；
- 与 `kube-manager` 成熟 API 的长期稳定对齐。

这些都是 Java / Spring Boot 长期擅长的企业级控制面能力。

### 2. Spring AI 适合作为 Agent 接入层，但不能代替安全内核

Spring AI 的 Tool Calling、ChatClient、模型抽象非常适合接入大模型和函数调用。但项目的安全内核必须由自己的 `SafeToolExecutor`、`HitlGuard`、`ToolRegistry` 和审计链路承载，不能把“模型选了某个 Tool”当成执行授权。

学习重点：框架负责连接模型，执行边界负责证明为什么可以执行。

### 3. Java 17 当前是可恢复底座，Java 21/25 是升级目标

当前开发机和主线验证基于 Java 17。直接把 Maven `java.version` 改到 21/25 会让本地验证失效，违背“每完成一部分都可测试、可提交、可恢复”的项目纪律。

正确做法是先做 CI/toolchain matrix：

- Java 17：当前主线；
- Java 21：虚拟线程、HTTP 客户端和依赖兼容验证；
- Java 25：LTS 候选验证；
- Java 26：只作为 Boot 4 支持范围内的前瞻验证，不作为一期主线。

## 2026-06-09 复核结论

M5.28-1 完成后，后端主语言审计结论进一步明确：`Java + Spring` 不是当前项目的短板，反而是一期顶级 Agent Core 最合适的控制面主线。真正需要追赶“顶级 Agent”标准的是安全身份、持久审计、硬质量门禁、评测闭环、RAG/Memory 和跨协议互操作的工程化程度。

当前已经属于先进主线的部分：

- `Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17` 作为可构建、可测试、可恢复的稳定底座；
- `SafeToolExecutor` 作为唯一真实 Tool 执行边界；
- `Resilience4j 2.3.0` 已接入 kube-manager 业务 HTTP 出口，读请求 retry/circuit/bulkhead，写请求 no-auto-retry；
- `Micrometer Observation + OpenTelemetry OTLP` 已进入审计 telemetry 链路；
- `CycloneDX SBOM`、`JaCoCo`、`SpotBugs`、`ArchUnit`、`Testcontainers` 已进入工程底座；
- Spring AI / Spring AI Alibaba 被用于模型、ToolCallback 和 Graph/Agent 编排接入，但不承担最终安全授权。
- M5.29-1 已接入 Spring Security `SecurityFilterChain`，把 kube-manager Bearer session 桥接为标准 `Authentication`，并保护 observability/actuator 诊断面。
- M5.29-2 已新增 `AgentPrincipalResolver`，让 controller / audit / method security 后续可以统一读取当前主体，而不是各自读 `SecurityContext` 或 ThreadLocal。
- M5.29-3 已把 `AgentPrincipalResolver` 接入 `SafeToolExecutor` 审计链路，审计 actor 优先来自服务端可信 principal 快照，而不是 caller-supplied request 字段。
- M5.29-4 已把前端 `X-Session-Id` 通过 `SessionStore` 桥接到 Spring Security，并将 `/api/agent/memory/**`、`/api/agent/mcp/**` 作为首批非聊天端点迁移到 `.authenticated()`。
- M5.29-5 已把 `ConversationController` 会话元数据 owner 迁移到 `AgentPrincipalResolver`，并将 `/api/agent/conversations`、`/api/agent/conversations/**` 迁移到 `.authenticated()`。
- M5.29-6 已把 Chat/SSE/Graph/HITL 运行时身份迁移到 `AgentPrincipalResolver + SessionStore + ConversationStore` 的可信快照，并将 `/api/agent/chat/stream`、`/api/agent/chat/graph`、`/api/agent/hitl/**` 迁移到 `.authenticated()`。

仍需要升级的部分：

- Spring Security 主线仍在迁移：M5.29-1 已完成 Bearer 身份桥接和诊断面保护，M5.29-2 已完成统一 principal resolver，M5.29-3 已完成审计 actor 可信快照，M5.29-4 已完成 `X-Session-Id` 会话桥接和 memory/mcp 首批端点授权，M5.29-5 已完成 conversation owner/endpoint 迁移，M5.29-6 已完成 Chat/SSE/Graph/HITL runtime identity 迁移；后续需要把剩余 `/api/agent/**` 从兼容放行迁移到显式 endpoint/method authorization，并做 controller guard 去重；
- 审计仍是 `InMemoryAgentAuditRecorder` 诊断实现，`durableRetention=false`，不能替代可查询、可保留、可权限控制的持久审计；
- SpotBugs / SBOM / coverage / secret scan / Agent eval 还没有全部变成失败即阻断的硬门禁；
- Resilience4j read retry 还应继续细分异常和状态码：网络异常、超时、429、502、503、504 可考虑重试，400、401、403、404 不应重试；
- OpenTelemetry 还需要把 request、intent、plan、LLM、Tool、HTTP、HITL、audit、final answer 映射为同一 trace 下的 span/timeline；
- RAG / persistent Memory / Agent eval / read-only MCP schema adapter 还需要进入一期主线，A2A 和完整 MCP broker 进入兼容矩阵。

## P0 改进清单

1. 继续收口所有真实 Tool 执行入口到 `SafeToolExecutor`。
   - 已完成：Graph Bridge `AtlasToolCallback`、`ReActEngine`、legacy `com.atlas.tool.core.AtlasToolCallback`、`AtlasOrchestrator` fallback。
   - 当前状态：生产代码唯一永久真实 `BaseTool.execute(...)` 边界是 `SafeToolExecutor`。

2. 建立端到端 traceId。
   - M5.23-1 已完成第一层内核：`AgentTraceContext`、MDC、`SafeToolExecutionRequest/Result`、Orchestrator、`/chat/graph`、HITL resume、ReAct、Graph `tool_call/execute_node`、ToolCallback 入口和 SSE timeline metadata。
   - 下一步：把同一 traceId 传播到 kube-manager HTTP outlet、审计事件、OpenTelemetry span、前端工作台回放和 eval 报告。
   - 前端工作台必须能按 trace 回放关键证据，而不是只展示最终文本。

3. 继续深化 kube-manager HTTP 出口韧性治理。
   - 已完成：M5.28-1 将 GET 接入 Resilience4j read retry/circuit/bulkhead，并将 POST/PATCH/PUT/DELETE 接入 write circuit/bulkhead 且不自动重试。
   - 下一步：补充 retry predicate / status predicate，确保 400/401/403/404 等确定性失败不会被重试，429/502/503/504 和网络抖动才进入受控读重试。

4. 建立审计事件模型。
   - 敏感读、高风险写、HITL 阻断、Tool 异常、权限拒绝都应有脱敏审计事件。
   - 审计字段至少包含 actor、organizationId、tool、operationType、risk、traceId、decision、result 摘要。

5. 质量门禁从“生成报告”升级到“阻断发布”。
   - SpotBugs/SBOM/coverage/secret scan/Agent eval 不只产物归档，还要形成发布门槛。
   - 当前 `spotbugs-maven-plugin` 仍配置 `failOnError=false`，这适合早期收敛报告，但不适合作为最终发布门禁。

6. 标准安全入口主线化。
   - 已完成第一层：M5.29-1 引入 Spring Security `SecurityFilterChain`，保护 `/api/agent/observability/**` 和 `/actuator/**` 管理面。
   - 已完成身份消费层：M5.29-2 引入 `AgentPrincipalResolver`，M5.29-3 将审计 actor 绑定到可信 principal 快照。
   - 已完成会话兼容层：M5.29-4 将前端 `X-Session-Id` 反查为服务端 `SessionData` 后桥接到 `Authentication`，并保护 memory/mcp 首批非聊天端点。
   - 已完成 conversation 元数据层：M5.29-5 将 raw session-id owner 迁移到可信 principal，并保护 conversations 端点。
   - 已完成 Chat/SSE 运行时层：M5.29-6 将 streaming / graph / ReAct / HITL runtime identity 收口到 trusted principal + server-side session + owner-checked conversation。
   - 下一步：把剩余 `/api/agent/**` 从兼容放行迁移到显式 endpoint/method authorization。
   - `AuthTokenFilter` 可以保留为兼容桥，但生产鉴权、角色和端点策略应由 Spring Security 承担。

7. 持久审计主线化。
   - `InMemoryAgentAuditRecorder` 继续作为诊断快照；高风险写、敏感读、HITL 阻断、权限拒绝和 Tool 异常必须进入 append-only durable audit。
   - 持久审计需要 admin-only 查询、脱敏、保留策略、traceId/auditId 索引和高风险写 pre-write fail-closed gate。

## P1 改进清单

1. Spring Security 身份事实迁移。
   - M5.29-1 已把缓存 Bearer session 桥接到 `SecurityContext` / `Authentication`。
   - M5.29-2 / M5.29-3 已把 controller guard 与 audit actor 的读取入口逐步迁入 `AgentPrincipalResolver`。
   - M5.29-4 已把 `X-Session-Id` 会话桥接到 `SecurityContext`，并让长期记忆只按 trusted principal 分桶。
   - M5.29-5 已让 conversation 元数据只按 trusted principal 分桶。
   - ThreadLocal 可保留为 legacy bridge，但权限事实不能长期散落。

2. OpenTelemetry / audit 主线化。
   - 把 `traceId` 映射为 OTel trace/span 结构，逐步补充 GenAI/Tool/HTTP/HITL span 属性。
   - 审计事件至少绑定 actor、organizationId、conversationId、traceId、tool、operationType、risk、decision、result 摘要。

3. Java 21/25 兼容矩阵。
   - CI 增加 Java 21/25 job。
   - 验证 ONNX Runtime、DJL tokenizer、Spring AI Alibaba Graph、Knife4j、Testcontainers、OTel、Resilience4j。

4. Spring Boot 4 / Spring AI 2 试验分支。
   - 验证 Spring Framework 7、Servlet 6.1、Tomcat 11、Spring AI 2 Tool API 对现有代码的影响。
   - 通过矩阵后再讨论主线迁移。

5. HTTP 客户端工程化。
   - 从散落的简单 HTTP 调用收敛到统一 outlet。
   - 配置连接池、超时、重试、熔断、指标、trace propagation、错误分类。

6. Agent eval 套件。
   - 覆盖意图识别、Tool 选择、参数抽取、多步 ReAct、中文口语、模糊资源名、安全红队和 must-block。

7. RAG 和 persistent Memory。
   - 使用 Spring AI VectorStore 或兼容抽象，先摄取 kube-manager API、vue-kube-manager 工作流和运维 runbook。
   - Memory 必须带租户隔离、脱敏、可删除和引用证据，不做“无限记忆”的黑盒堆积。

8. read-only MCP schema adapter。
   - 先暴露只读 manifest/schema，写工具继续 HITL/HOLD。
   - 未来 `tools/list` / `tools/call` 也必须经过 `SafeToolExecutor`、trace、audit 和权限边界。

## P2 改进清单

1. GraalVM Native Image 评估。
   - 只作为启动速度/部署形态探索，不作为一期必要条件。

2. LangGraph4j / Spring AI 2 Graph 兼容探索。
   - 仅在能替代现有 graph/reasoning 复杂度时引入。
   - 不能为了“框架更酷”牺牲当前安全边界。

3. 向量检索工程升级。
   - 当前本地 embedding 可继续服务意图 shortlist。
   - 后续可接 pgvector / Milvus / Elasticsearch vector，但一期要先把 Tool evidence、eval 和安全闭环做好。

4. eBPF / OpenTelemetry Collector / Tempo / Loki / Prometheus 全链路栈。
   - 适合部署阶段，不应阻塞当前 Agent Core 的直接执行边界收口。

5. A2A / Agent Card / 完整外部 MCP broker。
   - 这些属于顶级 Agent 互操作方向，但必须在本项目已有执行边界、trace、audit、eval 稳定后接入。
   - 互操作协议不能绕过本地权限、HITL、审计和租户边界。

## 需要避免的过度设计

- 不要把核心执行控制面拆成多个语言运行时，除非有非常明确的收益和隔离机制。
- 不要把 LLM framework 当成安全边界。
- 不要为了 Spring Boot 4 / Spring AI 2 的新版本号直接破坏当前全量测试。
- 不要在一期恢复 NIM/HPC/Slurm/BCM 专项实现；它们是二期插件，不是一期顶级 Core 的前置条件。
- 不要把审计、trace、eval 只做成日志文本；它们必须成为可查询、可回放、可阻断发布的工程对象。

## 审计判断

后端 Java 主语言不是短板，短板在于标准安全入口、持久审计、trace/audit/eval、HTTP outlet 细粒度治理、RAG/Memory 和质量硬门禁还没有完全主线化。下一阶段不应重写语言栈，而应把这些能力收敛成一个可证明、可回放、可评测、可恢复的 Agent Core。

学习重点：顶级 Agent 的“先进”不是把所有最新主版本一次性塞进主线，而是让最新能力被安全边界、审计证据、测试门禁和恢复记忆托住。Java/Spring 负责稳定控制面，兼容矩阵负责拥抱未来。
