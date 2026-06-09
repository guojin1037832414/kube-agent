# M5.44 Eval Workbench Overview

## 背景

M5.43 提供了 capability manifest，解决“前端如何发现后端 eval/replay/promotion 能力”的问题。

M5.44 继续向 `vue-kube-manager` 工作台推进：新增一个 overview read model，解决“前端首屏如何展示当前 eval trace-set 状态、下一步动作和 drill-down 入口”的问题。

## 已交付

- 新增 `AgentEvalWorkbenchTraceSetView`。
- 新增 `AgentEvalWorkbenchOverviewResponse`。
- 新增 `AgentEvalWorkbenchOverviewService`。
- 新增 admin-only `GET /api/agent/observability/eval/workbench/overview`。
- 扩展 `AgentEvalWorkbenchCapabilitiesService`，让 capability manifest 也暴露 `workbench-overview`。
- overview 聚合 capabilities、trace-set catalog、compact gate bundle、trace-set UI rows、next actions、endpoint templates、policy 和 privacy proof。

## 安全边界

- Endpoint 保持 observability admin-only，并有 method-level `@PreAuthorize`。
- Overview 是 read-only / overview-only，不执行 Tool，不调用 kube-manager，不使用 LLM，不发起外部调用。
- 不查询 raw audit storage，不运行 candidate discovery，不把候选自动写入 catalog。
- 不嵌入 replay timeline，不嵌入 per-trace reports；具体排障仍由 admin 显式进入 replay/eval drill-down。
- `catalogMutationAllowed=false`、`runtimeCatalogWrite=false`、`toolExecution=false`、`kubeManagerCalls=false`。
- 空 trace-set catalog 会显示 `NEEDS_REDACTED_EVIDENCE`，不会伪装成可开启 blocking CI 的 PASS。

## 学习点

顶级 Agent 的前端工作台应该分层：

1. Capability manifest：告诉前端后端支持哪些能力和 schema。
2. Overview：告诉前端当前系统状态和下一步动作。
3. Workflow artifact：承载候选发现、复核、patch proposal 等人工/Git 审查流程。
4. Drill-down payload：在管理员明确进入排障时才返回 replay timeline / eval report。

这个分层能防止 Vue 页面把导航、状态、执行和发布授权混在一起，也让后端持续拥有 release/evidence 语义的解释权。

## 验证

- `mvn -q "-Dtest=AgentEvalWorkbenchOverviewServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`
