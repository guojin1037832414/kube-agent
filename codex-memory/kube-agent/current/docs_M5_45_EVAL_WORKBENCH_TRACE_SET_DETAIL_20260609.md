# M5.45 Eval Workbench Trace-Set Detail

## 背景

M5.44 提供了 eval workbench overview，让 `vue-kube-manager` 可以渲染 trace-set 列表、状态和下一步动作。

M5.45 补上列表点击后的详情读模型：前端不应该自己拼接 catalog、gate、workflow path 和安全策略，而应该消费后端已经建模好的 trace-set detail contract。

## 已交付

- 新增 `AgentEvalWorkbenchTraceSetDetailResponse`。
- 新增 `AgentEvalWorkbenchTraceSetDetailService`。
- 新增 admin-only `GET /api/agent/observability/eval/workbench/trace-sets/{traceSetId}`。
- 扩展 capability manifest，新增 `workbench-trace-set-detail`。
- detail response 包含：
  - trace-set UI row；
  - curated trace anchors；
  - evidence requirements；
  - compact gate artifact；
  - promotion checklist；
  - next actions；
  - endpoint templates；
  - detail policy；
  - privacy proof。

## 安全边界

- Endpoint 保持 observability admin-only，并有 method-level `@PreAuthorize`。
- Detail 是 read-only / detail-only，不执行 Tool，不调用 kube-manager，不使用 LLM，不发起外部调用。
- 不运行 candidate discovery，不查询 raw audit storage，不把候选自动写入 catalog。
- 不嵌入 replay timeline，不嵌入 per-trace reports；具体排障仍由 admin 显式进入 replay/eval drill-down。
- `candidateDiscoveryExecuted=false`、`catalogMutationAllowed=false`、`runtimeCatalogWrite=false`、`toolExecution=false`、`kubeManagerCalls=false`。

## 学习点

顶级 Agent 工作台应该有清晰的信息层级：

1. Overview：告诉管理员哪些 trace set 需要关注。
2. Detail：解释某个 trace set 的目标、证据要求、当前 gate 状态和安全下一步。
3. Workflow：把候选发现、复核、patch proposal 组合成审查 artifact。
4. Drill-down：只在明确排障时返回 replay timeline / eval report。

这种分层让前端变得更简单，也让后端继续拥有证据语义、发布权限和安全边界的解释权。

## 验证

- `mvn -q "-Dtest=AgentEvalWorkbenchTraceSetDetailServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`
