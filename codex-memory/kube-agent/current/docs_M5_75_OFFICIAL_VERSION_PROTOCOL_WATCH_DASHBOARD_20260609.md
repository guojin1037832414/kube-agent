# M5.75 Official Version / Protocol Watch Dashboard

## 背景

用户的终极目标不是普通生产级 Agent，而是顶级 Agent 学习工程。因此“引入全部最先进技术”不能理解成盲目升级依赖或打开外部运行时，而要先让最新官方来源、协议、安全指南、采纳门禁和阻断项变成可审计、可测试、可由 `vue-kube-manager` 渲染的后端契约。

M5.75 在 M5.74 官方版本/协议 Watch 之上新增 Vue-ready Dashboard：

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch/dashboard
```

## 本次交付

- 新增 `AgentOfficialVersionProtocolWatchDashboardResponse`。
- 新增 `AgentOfficialVersionProtocolWatchDashboardService`。
- 新增 Controller 方法 `officialVersionProtocolWatchDashboard()`。
- Dashboard 嵌入 M5.74 `sourceWatch`，并派生：
  - `sourceCards`
  - `technologyTrackCards`
  - `adoptionGateRows`
  - `blockedRuntimeShortcutRows`
  - `disabledRuntimeActions`
  - `renderSections`
  - `dashboardPolicy`
  - `safety`
  - `privacy`
- 同步接入：
  - `AgentOfficialVersionProtocolWatchResponse.endpointMap`
  - `AgentAdvancedTechnologyAdoptionContractResponse.endpointMap`
  - `AgentTopTierReadinessOverviewResponse`
  - `AgentPhase1ExecutionRoadmapResponse`
  - `AgentVueReadinessControlPlaneResponse`
- 更新安全契约测试和 WebMvc 安全过滤链测试。

## 最新官方来源刷新

M5.75 同时把 2026-06-02 NSA 发布的 MCP Security Cybersecurity Information 纳入官方来源 Watch：

```text
id: nsa-mcp-security-2026-06
url: https://media.defense.gov/2026/Jun/02/2003943289/-1/-1/0/CSI_MCP_SECURITY.PDF
mode: SECURITY_GATE_BASELINE
```

这意味着 MCP 最新安全实践现在是 Phase 1 官方观察源的一部分，但它只进入安全门禁和 Vue 证据展示，不打开 MCP `tools/call` 运行时。

## 当前契约状态

```text
schemaVersion=agent-official-version-protocol-watch-dashboard.v1
dashboardStatus=DASHBOARD_READY_TO_RENDER_OFFICIAL_WATCH
sourceCardCount=8
technologyTrackCardCount=8
adoptionGateCount=7
blockedRuntimeShortcutCount=6
runtimeControlAllowed=false
phase1TopTierGoalPreserved=true
phase2NimHpcSlurmBcmPaused=true
```

## 安全边界

M5.75 是 admin-only、read-only、dashboard-only、Vue-workbench-only。

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

顶级 Agent 的“先进”不是把所有新东西接进运行时，而是把新技术变成一条可控链路：

```text
官方来源
  -> 人工/Git review
  -> 兼容性矩阵
  -> 后端 typed contract
  -> Vue 只读证据面板
  -> redacted trace / eval / audit / replay 证据
  -> 单独 release-gated runtime binding
```

M5.75 的价值是让前端能直接渲染这条链路，并且明确所有“看起来很先进但现在不安全”的按钮都必须隐藏或禁用。

## 验证

已通过：

```bash
mvn -q "-Dtest=AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
