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

## 最新 Agent 标准的落地顺序

以下技术代表 2026 年 Agent 工程的先进方向，但必须按可验证顺序接入：

| 标准/技术 | 一期定位 | 当前落点 | 下一步 |
|---|---|---|---|
| OpenTelemetry / GenAI semantic conventions | 统一观测模型 | 已有 Micrometer Tracing + OTLP 依赖，M5.23 建立 traceId 内核，M5.24 接入 HTTP outlet，M5.25 接入审计事件模型，M5.26 建立审计 telemetry projection；GenAI semconv 当前仍按实验/发展中标准对待 | 将 LLM、Tool、HTTP、HITL、audit 映射为 Span 与属性，并保留属性名兼容层 |
| MCP (Model Context Protocol) | 外部 Tool / Resource / Prompt 暴露协议 | 暂不直接开放生产写工具；MCP 规范继续快速演进，最新规范要通过 manifest/schema adapter 消化 | 先做只读 Tool manifest 与 schema adapter，写工具继续 HITL/HOLD，调用层必须走 SafeToolExecutor |
| A2A (Agent2Agent) | 多 Agent 互操作协议 | 当前多专家流程仍以内部角色和 Graph 编排为主；A2A 作为 Phase 1 互操作实验轨，不替代安全执行边界 | 在执行边界、trace、audit 稳定后，评估 Agent Card / Task / streaming adapter |
| OWASP LLM / Agentic AI 安全实践 | 红队和安全门禁 | 已有 HITL、protected params、fail-closed、direct execute contract | 扩展 eval harness：prompt injection、tool misuse、excessive agency、sensitive data |
| Spring Boot 4 / Spring AI 2 | 下一代 Java Agent 栈 | 当前主线保持 Boot 3.5 + Spring AI 1.1.7 | 开兼容矩阵分支验证 Spring Framework 7、Tomcat 11、Spring AI Tool API |
| Java 21 / 25 | 运行时升级目标 | Java 17 仍是当前可构建底座 | CI matrix + 依赖兼容后再考虑主线升级 |

顶级 Agent 的“先进”不是堆满协议名，而是每个协议都能被安全边界、trace、审计、评测和恢复记忆承接。

## 官方版本依据

2026-06-08 核对官方文档后的事实基线：

- Spring Boot 官方文档当前稳定线同时包含 `4.0.6` 和 `3.5.14`；`4.0.6` 需要 Java 17+，并要求 Spring Framework 7.0.7+。
- Spring AI 官方文档当前稳定线是 `1.1.7`，`2.0.0-RC1` 仍在 Preview 区域。
- Oracle Java SE 路线图将 Java SE 17、21、25 都列为 LTS，其中 Java 25 GA 于 2025-09，Premier Support 到 2030-09。
- MCP 官方规范持续迭代，2025-11-25 规范已明确 Tool list/call、structured output、annotations 等能力；本项目仍只把它作为受控外部 Tool 发现与调用协议接入，不能绕过权限、HITL、审计和 SafeToolExecutor。
- OpenTelemetry GenAI 语义约定对 Agent/LLM/Tool 很关键，但仍要按可变标准处理，先以内部字段映射和兼容层落地，避免直接把预览属性名固化成无法迁移的数据库契约。

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
- HTTP 韧性：读请求可重试；写请求必须绑定 idempotency key / audit / HITL 后才能重试，默认不自动重试。
- 连接治理：从简单 request factory 过渡到连接池或 WebClient，并暴露连接池指标。
- OpenTelemetry：M5.23/M5.24/M5.25/M5.26 已完成 traceId 内核、HTTP 出口传播、审计事件模型和审计 telemetry projection；后续要把 intent、plan、tool、HTTP、HITL、audit、final answer 映射为 span/timeline/audit 统一证据链。
- 审计持久化：M5.25 已完成内存诊断 recorder；下一步要把敏感读、高风险写、HITL 阻断、Tool 异常接入可查询、可保留、可权限控制的脱敏持久化审计存储。
- CI 门禁：SBOM、SCA、SpotBugs、覆盖率、secret scan、Agent eval 必须进入发布流程。
- 安全主干：逐步引入 Spring Security `SecurityFilterChain`，把身份事实从 ThreadLocal 兼容层迁移到标准 `Authentication`。

## 多专家审计后的 Phase 1 技术优先级

2026-06-08 多专家审计结论：当前 Java / Spring 技术选型足够先进，短板不在“再堆新框架”，而在把已有先进底座真正接入 Agent 执行闭环。

| 优先级 | 技术任务 | 验收口径 |
|---|---|---|
| P0 | Resilience4j 真正治理 kube-manager HTTP outlet | READ 可重试/熔断/限并发；WRITE 默认不自动重试，除非具备 HITL + audit + idempotency key |
| P0 | CI 从报告生成升级为硬门禁 | SpotBugs/SCA/secret scan/coverage/Agent eval 失败能阻断合并或发布 |
| P0 | `SafeToolExecutor` 唯一真实执行边界持续守护 | 新增 Graph/ReAct/ToolCallback/插件入口不能直调 `BaseTool.execute(...)` |
| P1 | Micrometer + OpenTelemetry span 化 | request、intent、plan、LLM、tool、HTTP、HITL、audit、final answer 能在同一 trace 下回放 |
| P1 | Spring Security 主线化 | 可信身份从 ThreadLocal 兼容层迁移到标准 `Authentication/SecurityContext` |
| P1 | Testcontainers 真实集成测试 | 覆盖 kube-manager HTTP contract、鉴权失败、trace header、重试/熔断边界 |
| P2 | Java 21/25 与 Spring Boot 4 / Spring AI 2 兼容矩阵 | 先在 CI matrix 或试验分支验证，不破坏当前可恢复主线 |

学习重点：顶级 Agent 的技术先进性最终体现在“闭环能力”：能安全执行、能解释原因、能追踪证据、能评测回归、能恢复现场。框架版本只是入口，工程闭环才是主体。

## 学习重点

顶级 Agent 的技术先进性不是“用了最新版本号”这么浅。真正先进的是：

- 任何升级都可验证；
- 任何执行都可审计；
- 任何失败都可恢复；
- 任何能力都能解释它为什么安全；
- 任何新技术都服务于 Agent 的可靠性、可控性和学习价值。
