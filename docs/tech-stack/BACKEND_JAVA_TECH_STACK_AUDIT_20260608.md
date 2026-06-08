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

## P0 改进清单

1. 继续收口所有真实 Tool 执行入口到 `SafeToolExecutor`。
   - 已完成：Graph Bridge `AtlasToolCallback`、`ReActEngine`、legacy `com.atlas.tool.core.AtlasToolCallback`、`AtlasOrchestrator` fallback。
   - 当前状态：生产代码唯一永久真实 `BaseTool.execute(...)` 边界是 `SafeToolExecutor`。

2. 建立端到端 traceId。
   - M5.23-1 已完成第一层内核：`AgentTraceContext`、MDC、`SafeToolExecutionRequest/Result`、Orchestrator、`/chat/graph`、HITL resume、ReAct、Graph `tool_call/execute_node`、ToolCallback 入口和 SSE timeline metadata。
   - 下一步：把同一 traceId 传播到 kube-manager HTTP outlet、审计事件、OpenTelemetry span、前端工作台回放和 eval 报告。
   - 前端工作台必须能按 trace 回放关键证据，而不是只展示最终文本。

3. 把 Resilience4j 真正接入 kube-manager HTTP 出口。
   - READ 可配置 retry/time limiter/circuit breaker。
   - 写操作默认不自动重试，除非具备 idempotency key、HITL、审计和回滚语义。

4. 建立审计事件模型。
   - 敏感读、高风险写、HITL 阻断、Tool 异常、权限拒绝都应有脱敏审计事件。
   - 审计字段至少包含 actor、organizationId、tool、operationType、risk、traceId、decision、result 摘要。

5. 质量门禁从“生成报告”升级到“阻断发布”。
   - SpotBugs/SBOM/coverage/secret scan/Agent eval 不只产物归档，还要形成发布门槛。

## P1 改进清单

1. Spring Security 主线化。
   - 逐步把 `UserPermissionContext` ThreadLocal 兼容层迁移到 `SecurityContext` / `Authentication`。
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

## 需要避免的过度设计

- 不要把核心执行控制面拆成多个语言运行时，除非有非常明确的收益和隔离机制。
- 不要把 LLM framework 当成安全边界。
- 不要为了 Spring Boot 4 / Spring AI 2 的新版本号直接破坏当前全量测试。
- 不要在一期恢复 NIM/HPC/Slurm/BCM 专项实现；它们是二期插件，不是一期顶级 Core 的前置条件。
- 不要把审计、trace、eval 只做成日志文本；它们必须成为可查询、可回放、可阻断发布的工程对象。

## 审计判断

后端 Java 主语言不是短板，短板在于统一安全执行、trace/audit/eval、HTTP outlet 和质量门禁还没有完全主线化。M5.22 的正确优先级不是重写语言栈，而是把所有执行入口、观测链路和发布门禁收敛成一个可证明的 Agent Core。
