# M5.43 Eval Workbench Capabilities

## 背景

M5.42 已经提供了 promotion workflow，但未来 `vue-kube-manager` eval workbench 仍需要一个稳定入口来发现后端有哪些 replay/eval/promotion 能力、每个能力对应的 schema、推荐 UI 流程和安全边界。

M5.43 新增的是 capability manifest，不是执行入口。它让前端可以用一个 admin-only GET 请求拿到可渲染的工作台能力图。

## 已交付

- 新增 `AgentEvalWorkbenchCapability`。
- 新增 `AgentEvalWorkbenchCapabilitiesResponse`。
- 新增 `AgentEvalWorkbenchCapabilitiesService`。
- 新增 admin-only `GET /api/agent/observability/eval/workbench/capabilities`。
- Manifest 覆盖 trace-set catalog、candidate discovery、curation review、catalog patch proposal、promotion workflow、gate bundle、replay timeline、eval report。
- Manifest 给出推荐流程：catalog -> promotion workflow -> patch proposal -> gate bundle -> replay timeline -> eval report。

## 安全边界

- Endpoint 是 observability admin-only，并保留 method-level `@PreAuthorize`。
- Capability manifest 是 metadata-only。
- 不查询审计存储，不运行 eval，不执行 Tool，不调用 kube-manager，不发起外部网络，不使用 LLM。
- 不包含 raw principal、organization、conversation、kube-manager endpoint、reason text、parameter values。
- 所有 capability 都声明 `runtimeCatalogWrite=false`、`toolExecution=false`、`kubeManagerCalls=false`。

## 学习点

顶级 Agent 的前端工作台不应该硬编码一堆后端路径和隐含流程。后端应该提供自描述 capability manifest，前端按 manifest 渲染导航、按钮、状态和 drill-down。这样当后端安全边界、schemaVersion、推荐流程变化时，工作台可以跟随后端契约演进。

## 验证

- `mvn -q "-Dtest=AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalTraceSetPromotionWorkflowServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`
