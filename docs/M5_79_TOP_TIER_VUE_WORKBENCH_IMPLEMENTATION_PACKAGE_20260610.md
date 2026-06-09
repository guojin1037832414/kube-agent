# M5.79 Top-Tier Vue Workbench Implementation Package

> 日期：2026-06-10  
> 范围：Phase 1 顶级 Agent 最新技术治理工作台  
> 状态：后端只读实现包已定义，前端尚未在 `vue-kube-manager` 仓库落地

## 1. 这次交付解决什么问题

M5.76 已经给出 Official Version / Protocol Watch 的 Vue binding spec。  
M5.78 已经给出 Advanced Technology Compatibility Matrix 的 Vue binding spec。

但前端真正实现时还会遇到一个跨页问题：

- 两个页面应该放在哪些路由？
- 两页共用哪些只读组件？
- API client 应该调用哪几个后端端点？
- fixture 如何验证“没有运行时按钮”？
- disabled actions 如何跨页保持一致？
- 官方来源、矩阵、禁用动作、验收场景之间的关系由谁负责？

M5.79 新增一个后端权威的实现包：

```text
GET /api/agent/observability/top-tier/vue-workbench-implementation-package
```

它不是前端代码，也不是运行时控制 API。它是给 `vue-kube-manager` 的只读实现合同：前端可以照着它做路由、API client、组件、fixture 和验收测试，但不能凭页面行为打开任何升级、MCP、A2A、RAG、CI、kube-manager write 或 Phase 2 域插件能力。

## 2. 响应模型

核心响应类：

```text
AgentTopTierVueWorkbenchImplementationPackageResponse
AgentTopTierVueWorkbenchImplementationPackageService
```

关键状态：

- `schemaVersion=agent-top-tier-vue-workbench-implementation-package.v1`
- `packageStatus=IMPLEMENTATION_PACKAGE_READY`
- `sourceBindingSpecsEmbedded=true`
- `runtimeControlAllowed=false`
- `routeSpecCount=2`
- `apiClientBindingCount=4`
- `pageAssemblyCount=2`
- `sharedComponentCount=7`
- `acceptanceFixtureCount=6`

M5.79 嵌入两个来源规格：

- `officialWatchBindingSpec`
- `compatibilityMatrixBindingSpec`

这样 Vue 不需要重新发明治理逻辑，只需要渲染后端已经定义好的页面结构与状态规则。

## 3. 页面和路由

M5.79 定义两个前端页面路由：

```text
/agent/top-tier/official-version-protocol-watch
/agent/top-tier/advanced-technology-compatibility-matrix
```

对应两个来源 binding spec：

```text
/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec
/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec
```

学习点：前端路由本身也是 Agent 治理的一部分。一个顶级 Agent 不能只在后端安全，前端也必须明确哪些页面只是观察、哪些页面可以执行、哪些页面永远不应该出现按钮。

## 4. API Client 绑定

M5.79 定义 4 个只读 API client：

- `fetchOfficialWatchDashboard`
- `fetchOfficialWatchBindingSpec`
- `fetchCompatibilityMatrix`
- `fetchCompatibilityMatrixBindingSpec`

这些 client 全部满足：

- `method=GET`
- `requiresAdminSession=true`
- `mockedFixtureAllowed=true`
- `runtimeBackendCallAllowed=false`
- `kubeManager8100Required=false`

这意味着未来 Vue 单元测试可以完全使用 mocked HTTP fixture，不需要启动真实 kube-manager，也不需要触发任何 Agent runtime。

## 5. 共享组件契约

M5.79 给出 7 个共享只读组件契约：

- `StatusBadge`
- `MetricNumber`
- `EvidenceTagList`
- `ReadonlyTable`
- `DisabledActionList`
- `ExternalOfficialLink`
- `ReadonlyJsonPanel`

所有共享组件都必须：

- `readOnly=true`
- `runtimeControlAllowed=false`
- `inlineEditAllowed=false`

学习点：前端组件不是单纯的视觉复用。对于 Agent 系统，组件也是安全语义的载体。例如 `DisabledActionList` 不只是灰色列表，而是“这里绝不能有 click handler”的安全合同。

## 6. 验收 Fixture

M5.79 定义 6 个前端验收 fixture：

- `official-watch-page-renders-with-mocked-binding-spec`
- `compatibility-matrix-page-renders-with-mocked-binding-spec`
- `cross-page-navigation-keeps-read-only-state`
- `runtime-buttons-absent-in-both-pages`
- `admin-auth-required-for-all-api-calls`
- `source-json-drilldown-redacted`

这些 fixture 的共同约束：

- 只使用 mocked HTTP。
- 不要求真实 runtime backend call。
- 不要求 kube-manager `8100`。
- 不允许 runtime control。

## 7. 安全边界

M5.79 是：

- admin-only
- read-only
- implementation-package-only
- Vue-workbench-only
- external-call-free at request time

M5.79 不做这些事：

- 不修改 `pom.xml`
- 不升级 Java / Spring Boot / Spring AI
- 不运行 LLM
- 不执行 Tool
- 不调用 `SafeToolExecutor`
- 不调用 HITL
- 不调用 kube-manager 或 `8100`
- 不暴露 MCP `tools/call`
- 不运行 A2A handoff
- 不执行 retrieval / vector store / reranker / GraphRAG
- 不写 memory / audit / durable receipt
- 不修改 eval catalog
- 不启用 CI blocking
- 不触碰 NIM / HPC / Slurm / BCM 二期范围

## 8. 多专家审查记录

本切片继续采用多专家参与。

Newton（前端契约 / 产品体验）建议：M5.76/M5.78 已经把两个单页规格做完整，但还缺跨页 workbench 契约。Vue 最容易卡在路由、Tab、跨页 drilldown、统一禁用动作和整页 fixture。M5.79 采纳这条建议，交付 `vue-workbench-implementation-package`。

Faraday（后端架构）建议：下一步可做 `advanced-technology-compatibility-matrix/evidence-readiness`，把 matrix lane 与 reviewed trace / eval / Memory-RAG evidence gaps 做成只读证据就绪层。这个建议没有并入 M5.79，因为 M5.79 更聚焦 Vue 落地包；Faraday 建议更适合作为 M5.80。

## 9. 下一步

推荐下一切片：

```text
M5.80 Advanced Technology Compatibility Matrix Evidence Readiness
```

它应当把 M5.77/M5.78/M5.79 与 reviewed redacted traces、Memory/RAG review manifest、eval gate evidence 对齐，继续保持只读、无运行时、无外部调用。

真正的 `vue-kube-manager` 仓库实现顺序：

1. 新建顶级 Agent workbench 导航。
2. 添加 4 个只读 API client。
3. 实现 7 个共享只读组件。
4. 实现 Official Watch 页面。
5. 实现 Compatibility Matrix 页面。
6. 添加 mocked fixture tests。
7. 验证所有 runtime control buttons 均不存在。
