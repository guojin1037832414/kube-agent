# M5.81 Backend Technology Modernization Decision

## 目标

M5.81 新增一个后端技术现代化决策端点，用来回答一个很关键的问题：

> 一期目标要成为顶级 Agent，并且要引入全部最先进技术；那 Java/Spring 后端主语言和主控平面还是否合适？哪些最新技术现在可以进入主线，哪些必须先进兼容矩阵和证据门禁？

新增接口：

```text
GET /api/agent/observability/top-tier/backend-technology-modernization-decision
```

本轮结论是：Java/Spring 仍然是一期顶级 Agent Core 的首选后端控制平面。原因不是“保守”，而是它最适合承载身份、RBAC、Tool 权限、HITL、审计、回放、评测、发布门禁、恢复记忆和面向 `vue-kube-manager` 的稳定读模型。最新技术继续全部纳入一期视野，但通过官方源复核、兼容矩阵、证据就绪、评测、release gate 和 Git 审查进入，不直接变成运行时按钮或依赖升级。

## 交付内容

- 新增 `AgentBackendTechnologyModernizationDecisionResponse`。
- 新增 `AgentBackendTechnologyModernizationDecisionService`。
- 新增 admin-only Controller 方法 `backendTechnologyModernizationDecision()`。
- 新增 `AgentBackendTechnologyModernizationDecisionServiceTest`。
- 将该端点加入 official watch、official watch dashboard、official watch Vue binding spec、advanced technology adoption、compatibility matrix、compatibility matrix Vue binding spec、evidence readiness、top-tier readiness overview、Phase 1 roadmap、Vue readiness control plane、top-tier Vue workbench implementation package。
- 将 Vue readiness dashboard count 从 `16` 扩展到 `17`。
- 将 Vue workbench implementation package 扩展为四页：
  - official version/protocol watch
  - advanced technology compatibility matrix
  - advanced technology evidence readiness
  - backend technology modernization decision
- 官方源复核日期刷新为 `2026-06-10`。
- Spring AI 2 preview 候选线从 `2.0.0-RC1` 更新为 `2.0.0-RC2`，仍保持 compatibility lane，不进入主线依赖。

## 当前响应状态

```text
schemaVersion=agent-backend-technology-modernization-decision.v1
decisionStatus=JAVA_SPRING_MAINLINE_ADVANCED_COMPATIBILITY_LANES_BLOCKED_BY_EVIDENCE
officialSourceCount=8
mainlineDecisionCount=8
compatibilityLaneCount=10
blockedCompatibilityLaneCount=10
modernizationGateCount=8
blockedShortcutCount=9
learningStepCount=8
javaBackendStillPreferred=true
javaSpringControlPlanePreserved=true
phase2NimHpcSlurmBcmPaused=true
compatibilityBranchAllowed=true
mainlineRuntimeUpgradeAllowedNow=false
dependencyUpgradeAllowedNow=false
runtimeControlAllowed=false
ciBlockingAllowedNow=false
```

这表示：项目仍在引入最新技术，但当前 reviewed trace、Memory/RAG fixtures、runtime release-gate、compatibility branch 证据还不足，因此不能直接升级 Java/Spring/Spring AI 主线，也不能开启 MCP tools/call、A2A runtime handoff、retrieval runtime、CI blocking 或 kube-manager 写能力。

## 技术选型结论

### 主线保持

- `Java 17`：继续作为当前可恢复、可构建、可验证的主线基线。
- `Spring Boot 3.5.x`：继续承载 Spring Security、Web MVC、Actuator、Micrometer、配置与测试主线。
- `Spring AI 1.1.x`：继续作为已验证的模型访问、ToolCallback、Memory/RAG/MCP 接入方向的稳定层。
- `SafeToolExecutor + HITL + durable audit + eval + release gate`：继续作为真正的 Agent 权限边界。
- `vue-kube-manager`：先消费后端只读治理读模型，再实现任何运行时控制面。

### 进入兼容矩阵

- Java 21 / Java 25。
- Spring Boot 4 / Spring Framework 7。
- Spring AI 2.0.0-RC2。
- OpenAI Responses / Agents SDK 风格的 tracing、handoffs、guardrails、evals。
- MCP 2025-11-25 runtime `tools/call`。
- A2A multi-Agent provenance。
- OpenTelemetry GenAI semantic conventions。
- GraphRAG、reranker、vector store。
- kube-manager state-changing writes。
- SBOM、dependency audit、CI blocking。

## 教学重点

### 1. 顶级 Agent 的先进性来自证据链

正确路线：

```text
official source -> compatibility matrix -> evidence readiness -> reviewed tests -> release gate -> runtime binding
```

错误路线：

```text
latest label -> pom upgrade -> runtime button
```

M5.81 的价值在于把“我们要全部最先进技术”变成一个工程决策模型：每个先进技术都被看见、被跟踪、被解释，但不会绕过安全、审计、评测和恢复能力。

### 2. Java/Spring 不是过时选择，而是控制平面选择

Agent 的核心难点不是写一个 prompt，而是让模型、工具、权限、租户、审计、HITL、评测、回放、发布门禁、前端工作台和恢复记忆组成一个可运营系统。Java/Spring 在这里的价值是 typed control plane，而不是追求语言潮流。

### 3. Preview 技术要被学习，但不能伪装成生产主线

Spring AI 2.0.0-RC2、Boot 4、JDK 25、MCP 最新规范、A2A、OTel GenAI、GraphRAG 等都很重要，也必须进入学习和设计视野。但在本项目里，它们需要先通过 compatibility branch、focused tests、security/privacy regression、Vue read-only evidence、recovery memory 和 Git-reviewed release decision。

### 4. 多 Agent / Agent 群也要先解决 provenance

A2A、handoff、外部 Agent runtime 可以增强系统，但不能把本地执行权交出去。正确设计是先传递 Agent Card、task、artifact digest、trace/audit/provenance，再由本地 Java/Spring 控制面决定是否执行工具、是否需要 HITL、是否进入 release gate。

## 安全边界

M5.81 是：

- admin-only
- read-only
- decision-only
- source read-model composition only
- no runtime control
- no dependency upgrade
- no compatibility branch creation
- no CI blocking

M5.81 只组合：

- `officialVersionProtocolWatchService.watch()`
- `evidenceReadinessService.readiness()`

M5.81 不做：

- 不修改 `pom.xml`
- 不升级 Java / Spring Boot / Spring AI
- 不创建兼容分支
- 不调用 kube-manager 或 `8100`
- 不运行 LLM
- 不执行 Tool
- 不调用 `SafeToolExecutor`
- 不调用 HITL
- 不运行 eval suite / trace-set gate / curation review / candidate discovery
- 不写 audit / memory / durable receipt
- 不执行 retrieval / vector store / embedding / reranker / GraphRAG
- 不开启 MCP runtime `tools/call`
- 不运行 A2A runtime handoff
- 不触碰 NIM / HPC / Slurm / BCM 二期范围

## 多专家审计

- Confucius / security-architecture review：未发现 P0/P1/P2 阻塞问题；确认新端点 admin-only/read-only，service 只组合 official watch + evidence readiness，没有误触 LLM、Tool、SafeToolExecutor、HITL、kube-manager/8100、MCP tools/call、A2A runtime、retrieval/vector/reranker/GraphRAG、audit/memory write、dependency upgrade 或 Phase 2 范围。
- Erdos / docs-recovery review：确认 M5.81 需要同步 `CHANGELOG.md`、本教学文档、长期架构学习文档、项目记忆、tech stack roadmap、workspace-local `codex-memory` 镜像和 SHA 恢复清单；确认继续使用 `F:\gitProject\kube-agent\codex-memory\kube-agent\current`，不再默认写入 H 盘。

## 验证命令

本切片提交前需要验证：

```powershell
mvn -q "-DskipTests" validate
mvn -q "-Dtest=AgentBackendTechnologyModernizationDecisionServiceTest,AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessServiceTest,AgentAdvancedTechnologyCompatibilityMatrixServiceTest,AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecServiceTest,AgentTopTierVueWorkbenchImplementationPackageServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
git diff --check
```

## 下一步

- 让 `vue-kube-manager` 消费四页 workbench package，渲染 official watch、compatibility matrix、evidence readiness、backend modernization decision。
- 补真实 reviewed redacted eval traces。
- 补 Memory/RAG 三类 reviewed fixtures。
- 为 Java 21/25、Spring Boot 4、Spring AI 2.0.0-RC2 建立独立 compatibility branch 验证路径。
- MCP/A2A/RAG 原型继续排在 SafeToolExecutor、release gate、reviewed evidence、Vue 可见性和 recovery memory 之后。
- NIM / HPC / Slurm / BCM 继续作为二期暂停。
