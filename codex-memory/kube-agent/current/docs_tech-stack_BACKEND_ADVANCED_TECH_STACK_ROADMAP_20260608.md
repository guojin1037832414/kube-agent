# Backend Advanced Tech Stack Roadmap - 2026-06-08

## 目标口径

一期目标是顶级 kube-manager Agent Core，不是普通生产级后端，也不是只读 demo。技术选型必须同时满足：

- 当前主线可构建、可测试、可恢复；
- Agent 执行边界可审计、可追踪、可评测；
- 供应链、CI、SBOM、质量门禁进入工程默认路径；
- Java / Spring / Agent 框架升级以兼容性矩阵推进，不用不可构建的版本号伪装先进。

## 当前可落地先进线

本轮采用的第一批先进工程底座：

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

## 官方版本依据

2026-06-08 核对官方文档后的事实基线：

- Spring Boot 官方文档当前稳定线同时包含 `4.0.6` 和 `3.5.14`；`4.0.6` 需要 Java 17+，并要求 Spring Framework 7.0.7+。
- Spring AI 官方文档当前稳定线是 `1.1.7`，`2.0.0-RC1` 仍在 Preview 区域。
- Oracle Java SE 路线图将 Java SE 17、21、25 都列为 LTS，其中 Java 25 GA 于 2025-09，Premier Support 到 2030-09。

因此，本项目主线当前采用 `Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17` 作为可验证稳定底座；`Spring Boot 4 + Spring AI 2 + Java 21/25` 进入兼容性矩阵和试验分支。

参考：

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot Documentation Index](https://docs.spring.io/spring-boot/index.html)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)
- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)

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

- 统一执行内核：ReAct、Graph、ToolCallback、legacy fallback 必须全部通过 `SafeToolExecutor`。
- HTTP 韧性：读请求可重试；写请求必须绑定 idempotency key / audit / HITL 后才能重试，默认不自动重试。
- 连接治理：从简单 request factory 过渡到连接池或 WebClient，并暴露连接池指标。
- OpenTelemetry：每次请求贯穿 intent、plan、tool、HTTP、HITL、audit、final answer 的 traceId。
- 审计持久化：敏感读、高风险写、HITL 阻断、Tool 异常都要有脱敏审计事件。
- CI 门禁：SBOM、SCA、SpotBugs、覆盖率、secret scan、Agent eval 必须进入发布流程。
- 安全主干：逐步引入 Spring Security `SecurityFilterChain`，把身份事实从 ThreadLocal 兼容层迁移到标准 `Authentication`。

## 学习重点

顶级 Agent 的技术先进性不是“用了最新版本号”这么浅。真正先进的是：

- 任何升级都可验证；
- 任何执行都可审计；
- 任何失败都可恢复；
- 任何能力都能解释它为什么安全；
- 任何新技术都服务于 Agent 的可靠性、可控性和学习价值。
