# M5.78 Advanced Technology Compatibility Matrix Vue Binding Spec

## 背景

M5.77 已经把“引入全部最先进技术”拆成后端权威兼容矩阵。M5.78 的目标是再往前推进一步：让 `vue-kube-manager` 可以直接按后端发布的绑定规格实现页面，而不是在前端重新发明治理逻辑。

```text
M5.77 compatibility matrix
  -> M5.78 Vue binding spec
  -> vue-kube-manager read-only workbench
  -> future compatibility branches and release-gated runtime slices
```

## 本次交付

新增只读接口：

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec
```

新增后端契约：

- `AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse`
- `AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService`
- Controller 方法 `advancedTechnologyCompatibilityMatrixVueBindingSpec()`

该接口只组合 M5.77 兼容矩阵：

```text
AgentAdvancedTechnologyCompatibilityMatrixService.matrix()
        |
        v
AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService.spec()
```

M5.78 不改前端仓库、不升级依赖、不修改 `pom.xml`、不调用 LLM、不执行 Tool、不访问 kube-manager、不打开 MCP/A2A/RAG/CI 运行时权限。

## 当前契约状态

```text
schemaVersion=agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1
bindingStatus=VUE_BINDING_SPEC_READY
componentSpecCount=8
fieldBindingCount=14
tableColumnGroupCount=5
disabledActionBindingCount=7
fixtureCount=5
runtimeControlAllowed=false
sourceMatrixEmbedded=true
```

## Component Specs

M5.78 定义 8 个前端组件规格：

- `AdvancedTechnologyMatrixSummaryStrip`
- `SourceBaselineTable`
- `CandidateUpgradeLaneMatrix`
- `MigrationGateChecklist`
- `BlockedUpgradeShortcutTable`
- `CompatibilityTestLaneBoard`
- `MatrixImplementationChecklistPanel`
- `CompatibilityMatrixSourceJsonPanel`

每个组件都保持：

```text
readOnly=true
runtimeControlAllowed=false
inlineEditAllowed=false
```

## Field Bindings

M5.78 定义 14 条字段绑定，覆盖：

- `matrixStatus`
- `sourceBaselineCount`
- `matrixItemCount`
- `migrationGateCount`
- `sourceBaselines[].officialUrl`
- `matrixItems[].readiness`
- `matrixItems[].requiredEvidence`
- `matrixItems[].mainlineAllowedNow`
- `blockedUpgradeShortcuts[].allowed`
- `testLanes[].status`
- `safety.runtimeControlAllowed`

学习要点：字段绑定不是普通 DTO 展示，它是前后端治理边界。前端按照绑定渲染，就不会把 `allowed=false` 误做成可点击按钮。

## Table Column Groups

M5.78 定义 5 组表格列：

- `sourceBaselines`
- `matrixItems`
- `migrationGates`
- `blockedUpgradeShortcuts`
- `testLanes`

其中 `matrixItems` 必须清晰展示：

```text
currentBaseline
candidateTarget
readiness
requiredEvidence
adoptionRule
mainlineAllowedNow=false
runtimeControlAllowed=false
```

## Disabled Action Bindings

M5.78 把 M5.77 的 7 个 blocked shortcuts 转成前端禁用动作：

- `upgrade-pom-from-readiness-page`
- `treat-rc-preview-as-mainline`
- `trust-mcp-tool-annotations`
- `delegate-authority-to-external-agent`
- `enable-retrieval-before-reviewed-traces`
- `use-otel-experimental-fields-as-contract`
- `enable-ci-blocking-with-empty-fixtures`

每个动作都保持：

```text
renderAs=disabled-row
buttonVisible=false
clickHandlerAllowed=false
requiresSeparateReviewedSlice=true
blocksTopTierClaim=true
```

## Test Fixtures

M5.78 定义 5 个前端 fixture：

- `happy-path-matrix`
- `major-upgrade-lanes-visible`
- `runtime-buttons-absent`
- `blocked-shortcuts-visible`
- `source-watch-drilldown`

这些 fixture 均要求：

```text
requiresMockedHttp=true
requiresRuntimeBackendCalls=false
requiresKubeManager8100=false
```

这意味着未来前端测试可以使用 mocked HTTP 渲染完整页面，而不需要真实调用 kube-manager `8100`，也不能偷偷调用运行时后端动作。

## 安全边界

M5.78 是 admin-only、read-only、binding-spec-only、Vue-workbench-only。

它不会：

- 改 `pom.xml`；
- 升级依赖；
- 切换 Java / Spring / Spring AI 主线；
- 调用 LLM；
- 执行 Tool；
- 调用 `SafeToolExecutor`；
- 触发 HITL；
- 调用 kube-manager 或 `8100`；
- 暴露 MCP runtime `tools/call`；
- 执行 A2A runtime handoff；
- 执行 retrieval / vector search / reranker / GraphRAG；
- 写 audit / memory；
- 签发 durable receipt；
- 修改 eval catalog；
- 启用 CI blocking；
- 触碰 NIM / HPC / Slurm / BCM 二期范围。

## 学习要点

顶级 Agent 的前端不是简单页面，而是 operator workbench。它必须同时满足：

- 展示最新技术路线；
- 展示为什么不能直接启用；
- 展示哪些证据门禁还没满足；
- 让用户学习 Agent 技术治理；
- 不给用户错误的运行时按钮。

M5.78 的模式可以总结为：

```text
backend-owned read model
  -> backend-owned Vue binding spec
  -> mocked frontend fixtures
  -> no runtime controls until release gate
```

这是生产可用 Agent 和教学型 Agent 都需要的前端治理形态。

## 验证

已通过：

```bash
mvn -q "-Dtest=AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecServiceTest,AgentAdvancedTechnologyCompatibilityMatrixServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
