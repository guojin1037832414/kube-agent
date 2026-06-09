# M5.76 Official Version / Protocol Watch Vue Binding Spec

## 背景

M5.75 已经把最新 Agent 技术和协议的官方来源 Watch 转成 Vue-ready Dashboard。M5.76 继续向前推进一层：后端不只给 `vue-kube-manager` 数据，还给出可实现、可测试、可审计的前端绑定规格。

这符合用户最新修订的终极目标：一期项目必须成为顶级 Agent Core；NIM / HPC / Slurm / BCM 推迟到二期，但一期的架构、治理、安全、评测、可观测性、文档和学习价值不能降低。

## 本次交付

新增只读接口：

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec
```

新增后端契约：

- `AgentOfficialVersionProtocolWatchVueBindingSpecResponse`
- `AgentOfficialVersionProtocolWatchVueBindingSpecService`
- Controller 方法 `officialVersionProtocolWatchVueBindingSpec()`

该接口只组合 M5.75 Dashboard：

```text
AgentOfficialVersionProtocolWatchService.watch()
        |
        v
AgentOfficialVersionProtocolWatchDashboardService.dashboard()
        |
        v
AgentOfficialVersionProtocolWatchVueBindingSpecService.spec()
```

M5.76 不访问真实 `vue-kube-manager` 仓库，不调用浏览器，不调用 kube-manager，不执行 Tool，也不打开任何运行时按钮。它的价值是让前端开发时有后端权威规格，而不是在 Vue 里猜治理逻辑。

## 当前契约状态

```text
schemaVersion=agent-official-version-protocol-watch-vue-binding-spec.v1
bindingStatus=VUE_BINDING_SPEC_READY
componentSpecCount=7
fieldBindingCount=12
tableColumnGroupCount=4
disabledActionBindingCount=6
fixtureCount=4
runtimeControlAllowed=false
```

## Vue 组件规格

M5.76 发布 7 个建议组件：

- `OfficialWatchSummaryStrip`
- `OfficialSourceCardGrid`
- `TechnologyTrackMatrix`
- `AdoptionGateTable`
- `BlockedShortcutTable`
- `DisabledRuntimeActionList`
- `OfficialWatchSourceJsonPanel`

这些组件全部是 read-only、non-editable、no-runtime-control。它们只负责把后端治理证据渲染清楚。

## 字段绑定

M5.76 发布 12 个关键字段绑定，包括：

- `dashboardStatus`
- `frontendTarget`
- `sourceCardCount`
- `technologyTrackCardCount`
- `sourceCards[].title`
- `sourceCards[].officialUrl`
- `technologyTrackCards[].status`
- `technologyTrackCards[].disabledRuntimeActions`
- `adoptionGateRows[].summary`
- `blockedRuntimeShortcutRows[].blocksTopTierClaim`
- `dashboardPolicy.runtimeControlAllowed`
- `safety.mcpToolsCall`

学习要点：前端绑定的字段不是随意命名的 UI 字段，而是治理状态、证据来源、门禁状态和禁用动作的投影。顶级 Agent 的前端必须尊重这些字段。

## 表格列组

M5.76 发布 4 个表格列组：

- `sourceCards`
- `technologyTrackCards`
- `adoptionGateRows`
- `blockedRuntimeShortcutRows`

渲染要求：

- `officialUrl` 只能作为外部导航链接，不能变成拉取、安装或升级动作。
- `disabledRuntimeActions` 只能渲染为禁用行，不能渲染成按钮。
- `runtimeControlAllowed=false` 必须在 UI 上可见。
- `allowed=false` 的 shortcut 行不能暴露快捷操作。

## 禁用动作绑定

M5.76 继续明确 6 个禁止按钮：

- `upgrade-dependencies-from-dashboard`
- `enable-mcp-tools-call`
- `enable-a2a-runtime-handoff`
- `enable-retrieval-runtime`
- `enable-ci-blocking`
- `reopen-phase2-domain-plugins`

每个禁用动作都必须满足：

```text
renderAs=disabled-row
buttonVisible=false
clickHandlerAllowed=false
requiresSeparateReviewedSlice=true
```

## 测试 Fixture

M5.76 给 Vue 后续实现准备 4 个 fixture 场景：

- `happy-path-dashboard`
- `mcp-security-source-visible`
- `runtime-buttons-absent`
- `source-watch-drilldown`

其中 `mcp-security-source-visible` 要求前端能展示 `nsa-mcp-security-2026-06`，但只能作为已审阅安全来源显示，不允许打开 MCP runtime `tools/call`。

## 最新技术采用规则

2026-06-09 重新复核官方来源后，M5.76 继续沿用 evidence-first 规则：

- Spring AI 当前官方稳定线仍包含 `1.1.7`，并展示 Chat Memory、Tool Calling、MCP、RAG、Model Evaluation、Vector Databases、Observability 等能力。
- Spring Boot 官方文档当前展示 `4.0.6`，但本项目主线仍以可构建、可测试、可恢复为优先；Boot 4 应走 compatibility matrix。
- OpenAI 官方文档强调 Responses API、Agents SDK、Guardrails、Orchestration、Integrations and observability、Evaluate agent workflows、ChatKit、Tools、MCP and Connectors、Retrieval、Safety 等路线。
- MCP latest 当前仍落到 `2025-11-25` 规范，必须继续遵守 consent、privacy、tool safety 等原则。
- A2A latest 规范覆盖 Agent Card、task、streaming、push notification、security、protocol binding 和 interoperability。
- OTel GenAI、OWASP LLM Top 10、GraphRAG、reranker、vector store 等仍是一期高级能力目标，但必须先进入契约、评测、可观测和 Vue 证据面板。

结论：M5.76 没有少引入先进技术，而是把先进技术进入系统的入口继续前移到了绑定规范和测试证据层。这样后续真正开启 runtime 时不会靠热情硬冲，而是靠契约和证据推进。

## 安全边界

M5.76 是 admin-only、read-only、binding-spec-only、Vue-workbench-only。

它不会：

- 联网抓取文档；
- 升级 Java / Spring / Spring AI / OpenAI 依赖；
- 调用 LLM；
- 执行 Tool；
- 调用 `SafeToolExecutor`；
- 触发 HITL；
- 调用 kube-manager 或 `8100`；
- 暴露 MCP runtime `tools/call`；
- 执行 A2A handoff；
- 执行 retrieval / vector search / reranker / GraphRAG；
- 写 audit / memory；
- 签发 durable receipt；
- 修改 eval catalog；
- 启用 CI blocking；
- 重开 NIM / HPC / Slurm / BCM 二期范围。

## 学习要点

顶级 Agent 的 UI 不是把所有先进能力都做成按钮。更好的方式是：

```text
官方来源和安全指南
  -> 后端 Watch
  -> 后端 Dashboard
  -> 后端 Vue Binding Spec
  -> Vue 只读证据面板
  -> reviewed trace / eval / audit / replay
  -> separate release-gated runtime binding
```

这条链路可以训练一个 Agent 开发者从“看到新技术就接入”进化到“先定义权威来源、契约、门禁、证据、UI 反馈、测试和回滚”。

## 验证

已通过：

```bash
mvn -q "-Dtest=AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

