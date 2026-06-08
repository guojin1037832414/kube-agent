# Backend Advanced Tech Stack Roadmap - 2026-06-08

## 目标口径

一期目标是顶级 kube-manager Agent Core，不是普通生产级后端，也不是只读 demo。技术选型必须同时满足：

- 当前主线可构建、可测试、可恢复；
- Agent 执行边界可审计、可追踪、可评测；
- 供应链、CI、SBOM、质量门禁进入工程默认路径；
- Java / Spring / Agent 框架升级以兼容性矩阵推进，不用不可构建的版本号伪装先进。

## 当前可落地先进线

已采用的第一批先进工程底座：

- Spring Boot 3.5.x 稳定线；
- Spring AI 1.1.7 补丁线；
- Java 17 作为当前可构建基线；
- Maven Enforcer 锁定 Java / Maven 最低版本；
- JaCoCo 生成覆盖率报告；
- CycloneDX 生成 SBOM；
- SpotBugs 进入 quality profile；
- Surefire / Failsafe 显式进入构建；
- Micrometer Tracing + OpenTelemetry OTLP 依赖进入可观测底座；
- Resilience4j 进入 HTTP 出口韧性治理底座；
- Testcontainers 进入后续真实依赖集成测试底座；
- GitHub Actions 后端质量门禁。

M5.23-1 已把可观测能力从“依赖底座”推进到“运行时内核”：

- `AgentTraceContext` 统一生成、绑定、恢复 traceId；
- traceId 进入 MDC、SafeToolExecutor、Graph state、ReAct timeline、SSE trace event、HITL resume 和 ToolCallback；
- 外部 trace 候选值经过长度/字符集/空白控制字符校验，避免日志和 MDC 注入；
- traceId 被视为控制平面字段，不作为业务 Tool 参数透传。

M5.24-1 已把 trace 内核推进到 kube-manager HTTP outlet：

- `AgentTraceContext` 可以把内部 `trc_ + 32hex` 转换为标准 W3C `traceparent`；
- `KubeManagerHttpClient` 的用户业务请求统一传播 `X-Trace-Id` 与 `traceparent`；
- GET / POST / PATCH / PUT / DELETE / `resolveOrgId` 都走同一个 header helper；
- fallback login 暂不接入业务 trace helper，避免混淆认证 bootstrap 与用户 Tool 调用；
- 源码契约禁止未来重新手写业务 `X-Token` header 而漏掉 trace。

M5.25-1 已把 trace 内核推进到 Agent 审计证据层：

- 新增 `AgentAuditEvent` / `AgentAuditOutcome` / `AgentAuditRecorder` / `AgentAuditSnapshotProvider`；
- `SafeToolExecutor` 对成功、业务失败、权限/HITL/schema 阻断、Tool 异常都记录 trace-aware audit event；
- 审计事件绑定 Tool 风险元数据、执行来源、租户/用户上下文、参数摘要和结果词表；
- 参数摘要不保存参数值，observability snapshot 只暴露脱敏诊断摘要；
- diagnostic snapshot 提供 `schemaVersion`、`generatedAt` 与 `replayCapabilities`，为前端回放、OpenTelemetry event/span 映射和后续持久化审计定义最小稳定契约；
- 当前实现仍是 in-memory diagnostic recorder，后续需要接入 durable storage、OpenTelemetry event/span 和前端回放。

M5.26-1 已把审计事件推进到遥测投影层：

- 新增 `AgentAuditTelemetryProjection` / `AgentAuditTelemetryProjector`；
- 稳定内部属性使用 `atlas.agent.*` 命名空间，作为前端回放、durable audit 和 Agent eval 的长期契约；
- OTel / GenAI 相关字段放入 `experimentalOtelAttributes`，作为可迁移兼容层；
- admin 观测快照的 recent audit summary 现在携带 telemetry projection；
- 投影层不携带 raw principal、raw reason、endpoint 字符串或参数值。

M5.27-1 已把审计遥测投影推进到 Micrometer Observation 发布层：

- 新增 `AgentAuditTelemetryPublisher`，Observation 名称为 `atlas.agent.audit`，事件名称为 `atlas.agent.audit.recorded`；
- `InMemoryAgentAuditRecorder` 在写入内存诊断快照后发布 Observation，发布失败不影响 Tool 执行结果或 audit snapshot；
- low-cardinality 标签只包含 bounded enum/boolean/名称类字段，高波动的 `auditId`、`traceId`、时间和计数进入 high-cardinality key values；
- publisher 只消费 M5.26 的脱敏投影，不导出 raw principal、conversation、reason、endpoint 或参数值；
- 当前仍是诊断/观测链路，后续高风险写路径必须增加 durable audit pre-write fail-closed gate。

M5.28-1 已把 Resilience4j 推进到 kube-manager 业务 HTTP 出口：

- 新增 `KubeManagerHttpResiliencePolicy`，让韧性语义成为显式代码边界，而不是散在方法注解上；
- GET 走 read policy：Retry + CircuitBreaker + Bulkhead；
- POST/PATCH/PUT/DELETE 走 write policy：CircuitBreaker + Bulkhead，不自动重试；
- 移除旧 `HttpRetryConfig` 和 Spring Retry 注解路径，避免写操作被统一方法注解误重试；
- 写请求重试继续 HOLD，直到 idempotency key、durable audit、HITL 和 release evidence 全部具备。

M5.29-1 已把 Spring Security 推进到 HTTP 安全入口：

- 新增 `AgentSecurityConfig`，用 `SecurityFilterChain` 承接标准 Web 安全主线；
- `AuthTokenFilter` 作为 Security filter 注册，负责把 kube-manager Bearer session 从 `UserPermissionContext` 桥接成标准 `Authentication`；
- `/api/agent/observability/**` 与非 health/info 的 `/actuator/**` 已由 Spring Security admin role 保护；
- 关闭默认 basic/form/logout，并用显式 no-op `UserDetailsService` 避免 Spring Boot 生成默认开发用户；
- 普通 Agent API 暂时保持 `permitAll`，后续按端点和方法级授权逐步迁移。

M5.29-2 已把当前用户解析推进到统一 principal 层：

- 新增 `AgentPrincipal` / `AgentPrincipalResolver`；
- resolver 优先读取 Spring Security `Authentication`，忽略 anonymous，再回落 legacy `UserPermissionContext`；
- `ObservabilityController` 已迁移到 resolver，为后续 controller guard、audit actor、method security 统一读取当前主体打底。

M5.29-3 已把统一 principal 推进到审计 actor 证据层：

- `SafeToolExecutor` 在执行入口捕获 `AgentPrincipal` 快照，再绑定 Tool 执行兼容所需的 ThreadLocal；
- `AgentAuditEventFactory` 优先使用可信 principal 的 username / organizationId，避免 caller-supplied `SafeToolExecutionRequest.userId()` 成为审计权威；
- SecurityContext 主路径和 legacy UserPermissionContext 回落路径都有契约测试，保证迁移期间不打断 SSE/Tool 兼容入口。

M5.29-4 已把前端 `X-Session-Id` 会话推进到 Spring Security，并开启首批非聊天端点授权：

- `AuthTokenFilter` 在无 Bearer header 时通过 `SessionStore` 将 `X-Session-Id` 解析为服务端 `SessionData`，再生成标准 `Authentication`；
- Bearer header 继续作为优先身份来源；即使 Bearer 未知，也不自动降级到 `X-Session-Id`，避免多身份来源的权限/审计分裂；
- `/api/agent/memory/**` 与 `/api/agent/mcp/**` 已进入 `.authenticated()`；
- `MemoryController` 使用 `AgentPrincipalResolver` 的 username 作为长期记忆 owner，不再把 raw session id 作为身份；
- chat/SSE/conversation 暂不一起锁定，后续必须先迁移它们的数据归属语义，再进入 endpoint/method authorization。

M5.29-5 已把 conversation 元数据 owner 迁移到可信 principal：

- `ConversationController` 通过 `AgentPrincipalResolver` 解析 owner，不再把 raw `X-Session-Id` 或 `anonymous` 当作用户身份；
- create/list/detail/delete/title-update 全部以 principal username 做资源归属收敛；
- `/api/agent/conversations` 与 `/api/agent/conversations/**` 已进入 `.authenticated()`；
- chat/SSE 流式运行时仍作为独立 follow-up，因为那里还涉及 token/org/trace/SSE/Graph/ReAct 上下文传播。

M5.29-6 已把 Chat/SSE 流式运行时迁移到可信 runtime identity：

- `/api/agent/chat/stream`、`/api/agent/chat/graph`、`/api/agent/hitl/**` 已进入 `.authenticated()`；
- `AtlasOrchestrator` 从 `AgentPrincipalResolver` + `SessionStore` 解析 user/token/org，从 `ConversationStore` 校验 conversation owner；
- 请求体 `userId`、raw `X-Session-Id`、未校验 `conversationId` 不再决定运行时身份；
- SSE/Graph/HITL 使用 `run-*` / `graph-*` 作为非敏感关联 ID，不复用 raw `ses_*`；
- HITL resume 增加 checkpoint owner 校验，防止跨用户恢复执行。

M5.29-7 已把剩余 Agent API 面收口到默认认证兜底：

- `AgentSecurityConfig` 已启用 `@EnableMethodSecurity`；
- 除显式 bootstrap/compatibility whitelist 外，`/api/agent/**` 默认 `.authenticated()`；
- `ObservabilityController#snapshot()` 叠加方法级 admin guard，形成 URL matcher + method security 的双层保护；
- 新增 Agent Controller 默认不会因 `.anyRequest().permitAll()` 匿名暴露。

## 最新 Agent 标准的落地顺序

以下技术代表 2026 年 Agent 工程的先进方向，但必须按可验证顺序接入：

| 标准/技术 | 一期定位 | 当前落点 | 下一步 |
|---|---|---|---|
| OpenTelemetry / GenAI semantic conventions | 统一观测模型 | 已有 Micrometer Tracing + OTLP 依赖，M5.23 建立 traceId 内核，M5.24 接入 HTTP outlet，M5.25 接入审计事件模型，M5.26 建立审计 telemetry projection，M5.27 发布审计 Observation；GenAI semconv 当前仍按实验/发展中标准对待 | 将 LLM、Tool、HTTP、HITL、audit 映射为同一 trace 下的 span/timeline，并保留属性名兼容层 |
| MCP (Model Context Protocol) | 外部 Tool / Resource / Prompt 暴露协议 | 暂不直接开放生产写工具；MCP 规范继续快速演进，最新规范要通过 manifest/schema adapter 消化 | 先做只读 Tool manifest 与 schema adapter，写工具继续 HITL/HOLD，调用层必须走 SafeToolExecutor |
| A2A (Agent2Agent) | 多 Agent 互操作协议 | 当前多专家流程仍以内部角色和 Graph 编排为主；A2A 作为 Phase 1 互操作实验轨，不替代安全执行边界 | 在执行边界、trace、audit 稳定后，评估 Agent Card / Task / streaming adapter |
| OWASP LLM / Agentic AI 安全实践 | 红队和安全门禁 | 已有 HITL、protected params、fail-closed、direct execute contract | 扩展 eval harness：prompt injection、tool misuse、excessive agency、sensitive data |
| Spring Boot 4 / Spring AI 2 | 下一代 Java Agent 栈 | 当前主线保持 Boot 3.5 + Spring AI 1.1.7 | 开兼容矩阵分支验证 Spring Framework 7、Tomcat 11、Spring AI Tool API |
| Java 21 / 25 | 运行时升级目标 | Java 17 仍是当前可构建底座 | CI matrix + 依赖兼容后再考虑主线升级 |

顶级 Agent 的“先进”不是堆满协议名，而是每个协议都能被安全边界、trace、审计、评测和恢复记忆承接。

## 官方版本依据

2026-06-09 复核官方文档后的事实基线：

- Spring Boot 官方文档当前稳定线同时包含 `4.0.6` 和 `3.5.14`；`4.0.6` 需要 Java 17+，并要求 Spring Framework 7.0.7+。
- Spring AI 官方文档当前稳定线是 `1.1.7`，`2.0.0-RC1` 仍在 Preview 区域。
- Oracle Java SE 路线图将 Java SE 17、21、25 都列为 LTS，其中 Java 25 GA 于 2025-09，Premier Support 到 2030-09。
- MCP 官方规范持续迭代，2025-11-25 规范已明确 Tool list/call、structured output、annotations 等能力；本项目仍只把它作为受控外部 Tool 发现与调用协议接入，不能绕过权限、HITL、审计和 SafeToolExecutor。
- OpenTelemetry GenAI 语义约定对 Agent/LLM/Tool 很关键，但官方状态仍是 Development，并且要求现有 instrumentation 不要默认切到最新实验约定；本项目继续先以内部字段映射和兼容层落地，避免直接把发展中属性名固化成无法迁移的数据库契约。

因此，本项目主线当前采用 `Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17` 作为可验证稳定底座；`Spring Boot 4 + Spring AI 2 + Java 21/25` 进入兼容性矩阵和试验分支。

参考：

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot Documentation Index](https://docs.spring.io/spring-boot/index.html)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)
- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification)
- [OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)

## 为什么不直接把主线改成 Java 25 / Spring Boot 4

当前开发机 JDK 是 Java 17。直接把 `java.version` 提到 21 或 25 会让本地 `mvn test` 立即失效，破坏“每轮可验证、可提交、可恢复”的工程纪律。

Spring Boot 4.0.x 官方系统要求是 Java 17+，但它会同时带来 Spring Framework 7、Tomcat 11、Servlet 6.1 等生态跃迁。更关键的是，Spring AI 当前稳定线仍是 1.1.7，面向 Spring Boot 4 的 Spring AI 2.x 仍属于预览/候选线；项目内还必须确认 Spring AI Alibaba Agent / Graph、Knife4j、ONNX Runtime、DJL tokenizer、Actuator、Tracing 和大量 Tool 契约测试的兼容性。因此 Boot 4 / Spring AI 2 属于下一阶段兼容矩阵，而不是本轮直接强切。

顶级 Agent 的工程标准不是盲目追最新主版本，而是让升级路径被测试、文档、回滚和恢复记忆保护。

## 下一阶段迁移矩阵

| 阶段 | 目标 | 验收 |
|---|---|---|
| A | Java 17 + Spring Boot 3.5.x + Spring AI 1.1.7 | 当前主线全量测试、SBOM、JaCoCo、quality profile 可运行 |
| B | Java 21 / Java 25 toolchain 验证 | CI matrix 通过，虚拟线程/HTTP 客户端压测有结论，运行时镜像和开发机都可恢复 |
| C | Spring Boot 4 / Spring Framework 7 / Spring AI 2 试验分支 | 编译、单测、Spring AI Alibaba Graph、Knife4j、Actuator、Tracing 全部兼容 |
| D | Java 25 LTS 主线候选 | 仅在依赖生态、部署镜像、IDE、CI、观测和安全扫描全部明确支持后推进 |

## 一期顶级 Agent Core 技术欠账

- 统一执行内核：ReAct、Graph、ToolCallback、legacy fallback 已全部通过 `SafeToolExecutor`；后续新增入口必须继续受契约测试约束。
- HTTP 证据链：M5.24 已完成基础 trace header 传播，M5.25 已形成 auditId/traceId 事件内核；后续要补 idempotency key、tenant evidence、baggage 与真实 OpenTelemetry client span。
- HTTP 韧性：M5.28 已把 GET 接入 Resilience4j read retry/circuit/bulkhead；写请求接入 circuit/bulkhead 但默认不自动重试，后续必须绑定 idempotency key / audit / HITL 后才能考虑受控重试。
- 连接治理：从简单 request factory 过渡到连接池或 WebClient，并暴露连接池指标。
- OpenTelemetry：M5.23/M5.24/M5.25/M5.26/M5.27 已完成 traceId 内核、HTTP 出口传播、审计事件模型、审计 telemetry projection 和审计 Observation 发布；后续要把 intent、plan、tool、HTTP、HITL、audit、final answer 映射为 span/timeline/audit 统一证据链。
- 审计持久化：M5.25 已完成内存诊断 recorder；下一步要把敏感读、高风险写、HITL 阻断、Tool 异常接入可查询、可保留、可权限控制的脱敏持久化审计存储。
- CI 门禁：SBOM、SCA、SpotBugs、覆盖率、secret scan、Agent eval 必须进入发布流程。
- 安全主干：M5.29-1 已引入 Spring Security `SecurityFilterChain` 并完成 observability/actuator 第一层保护；M5.29-2 已新增 `AgentPrincipalResolver` 统一当前主体解析；M5.29-3 已让 SafeToolExecutor 审计 actor 绑定统一 principal 快照；M5.29-4 已桥接前端 `X-Session-Id` 并把 memory/mcp 首批非聊天端点迁移到 `.authenticated()`；M5.29-5 已把 conversation 元数据 owner 迁移到 trusted principal 并保护 conversations 端点；M5.29-6 已迁移 chat/SSE/HITL runtime identity；M5.29-7 已把 `/api/agent/**` 收口为默认 authenticated 并开启方法级安全。后续重点转向更细粒度方法授权、durable audit、eval 和 replay。

## 多专家审计后的 Phase 1 技术优先级

2026-06-08 多专家审计结论：当前 Java / Spring 技术选型足够先进，短板不在“再堆新框架”，而在把已有先进底座真正接入 Agent 执行闭环。

2026-06-09 生产运维复核进一步补充：当前最需要进入主线的是 Spring Security 标准入口、durable audit、硬质量门禁、Resilience4j retry predicate、OTel span/timeline、RAG/Memory、Agent eval 和 read-only MCP schema adapter。Java 21/25、Spring Boot 4、Spring AI 2、A2A、完整 MCP broker、GraphRAG 和 virtual threads 继续走兼容矩阵，不直接压到可恢复主线。

| 优先级 | 技术任务 | 验收口径 |
|---|---|---|
| P0 | Resilience4j 真正治理 kube-manager HTTP outlet | M5.28 已完成 READ retry/circuit/bulkhead 与 WRITE no-auto-retry 第一层；下一步补 idempotency key、metrics 和高风险写 release 条件 |
| P0 | CI 从报告生成升级为硬门禁 | SpotBugs/SCA/secret scan/coverage/Agent eval 失败能阻断合并或发布 |
| P0 | `SafeToolExecutor` 唯一真实执行边界持续守护 | 新增 Graph/ReAct/ToolCallback/插件入口不能直调 `BaseTool.execute(...)` |
| P1 | Micrometer + OpenTelemetry span 化 | request、intent、plan、LLM、tool、HTTP、HITL、audit、final answer 能在同一 trace 下回放 |
| P1 | Spring Security 主线化 | M5.29-1 已完成 Bearer 身份桥接和诊断面保护；M5.29-2 已完成 principal resolver；M5.29-3 已完成审计 actor 可信快照；M5.29-4 已完成 `X-Session-Id` 会话桥接和 memory/mcp 首批 authenticated 端点；M5.29-5 已完成 conversation owner/endpoint 迁移；M5.29-6 已完成 chat/SSE/HITL runtime identity；M5.29-7 已完成 `/api/agent/**` 默认认证兜底和观测方法级 admin guard；下一步做更细粒度 method guard 与 durable audit API 授权 |
| P1 | Testcontainers 真实集成测试 | 覆盖 kube-manager HTTP contract、鉴权失败、trace header、重试/熔断边界 |
| P2 | Java 21/25 与 Spring Boot 4 / Spring AI 2 兼容矩阵 | 先在 CI matrix 或试验分支验证，不破坏当前可恢复主线 |

## 2026-06-09 最新多专家复核

Archimedes 复核后的结论与当前路线一致：一期主线不应该把“全部最先进技术”理解成一次性升级所有主版本，而是把能形成闭环的先进能力落到主线，把生态迁移和实验协议放入兼容矩阵。

主线继续推进：

- Spring Security 主线化；
- Resilience4j 真正治理 kube-manager HTTP outlet；
- Micrometer / OpenTelemetry 把 request、intent、plan、LLM、Tool、HTTP、HITL、audit、final answer 串成同一条 trace/timeline；
- durable audit 替换当前 in-memory diagnostic recorder；
- 最小 RAG：Spring AI VectorStore + kube-manager API / 运维 runbook 文档摄取 + 引用证据；
- persistent Memory：摘要记忆持久化、租户隔离、脱敏和可删除；
- MCP 先做 read-only manifest / schema adapter，未来 `tools/list` / `tools/call` 仍必须经过 `SafeToolExecutor`。

继续放入兼容矩阵：

- Java 21 / 25、Spring Boot 4、Spring Framework 7、Spring AI 2；
- OTel GenAI development 字段直接固化为数据库契约；
- 完整外部 MCP Server / broker / tool market；
- A2A / Agent Card / 跨 Agent 互操作；
- GraphRAG、知识图谱、reranker、多向量库并行；
- virtual threads / structured concurrency 压测分支；
- NIM / HPC / Slurm / BCM 专项插件继续作为二期。

下一两个里程碑建议：

- M5.28：Security + Resilience + durable audit + CI hard gate，把“可以安全执行和可追责”继续做硬。
- M5.29：RAG + persistent Memory + read-only MCP + Agent eval，把“会学习、会引用证据、会被评测”接入一期顶级 Agent Core。

学习重点：顶级 Agent 的技术先进性最终体现在“闭环能力”：能安全执行、能解释原因、能追踪证据、能评测回归、能恢复现场。框架版本只是入口，工程闭环才是主体。

## 学习重点

顶级 Agent 的技术先进性不是“用了最新版本号”这么浅。真正先进的是：

- 任何升级都可验证；
- 任何执行都可审计；
- 任何失败都可恢复；
- 任何能力都能解释它为什么安全；
- 任何新技术都服务于 Agent 的可靠性、可控性和学习价值。
