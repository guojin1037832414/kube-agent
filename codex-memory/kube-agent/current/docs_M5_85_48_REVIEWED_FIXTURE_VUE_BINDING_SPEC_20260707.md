# M5.85-48 Reviewed Fixture Vue Binding Spec

## 目标

把 reviewed fixture 的后端证据链整理成 `vue-kube-manager` 可直接消费的只读绑定规范，避免前端猜字段、猜按钮、猜质量门含义。

## 新增入口

- `GET /api/agent/observability/eval/workbench/reviewed-fixture-vue-binding-spec`
- `schemaVersion=agent-reviewed-trace-fixture-vue-binding-spec.v1`
- `bindingStatus=VUE_BINDING_SPEC_READY`

## 代码变更

- 新增 `AgentReviewedTraceFixtureVueBindingSpecService`
- 新增 `AgentReviewedTraceFixtureVueBindingSpecResponse`
- 新增 `AgentReviewedTraceFixtureVueBindingSpecServiceTest`
- 更新 `ObservabilityController`
- 更新 `AgentEvalWorkbenchCapabilitiesService`
- 更新 `AgentEvalWorkbenchOverviewResponse`
- 更新 Controller / Security / WebMvc / overview / capability 相关测试

## 前端契约

绑定规范输出：

- component specs：summary、trace set table、candidate workbench、人审包、人审 gate、readiness、disabled runtime actions、raw read model panel
- field bindings：trace set 路径、人审字段、expected digest、`runtimeFixtureCommitAllowed=false`、failedQualityGates、`runtimeCatalogWrite=false`
- workflow stages：capability discovery、candidate workbench、human review package、human review gate、manual Git fixture commit、manifest rescan、catalog patch review、release review
- state rendering rules：gate ready 只是人工 Git 信号，质量门未授予必须以 danger 展示
- disabled action bindings：fixture upload、catalog write、CI blocking、release approve、MCP tools/call、kube-manager write、HITL、LLM eval、retrieval/vector runtime 都必须缺席或禁用
- test fixtures：gate success、gate rework、无 runtime 按钮、raw JSON 只读、不泄露敏感字段

## 安全边界

- `adminOnly=true`
- `readOnly=true`
- `bindingSpecOnly=true`
- `vueWorkbenchOnly=true`
- `runtimeControlAllowed=false`
- `runtimeButtonsAllowed=false`
- `fixtureUploadAccepted=false`
- `createsFixtureFile=false`
- `catalogMutationAllowed=false`
- `runtimeCatalogWrite=false`
- `toolExecution=false`
- `mcpToolCall=false`
- `kubeManagerCalls=false`
- `llmUsed=false`
- `externalCalls=false`
- `auditWrite=false`
- `memoryWrite=false`
- `hitlInvocation=false`
- `ciBlockingEnabled=false`
- `releaseAuthority=false`

## 验证

- 聚焦/回归测试已通过：`mvn -q "-Dtest=RuleMatcherTest,ToolRegistryPermissionTest,AgentReviewedTraceFixtureVueBindingSpecServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`
- 完整测试已通过：`mvn -q test`
- 打包已通过：`mvn -q "-DskipTests" package`
- 格式检查已通过：`git diff --check`，仅有 Windows LF/CRLF 提示。
- 真实测试密码字面量扫描无源码、文档或恢复记忆命中；真实密码未落盘。

## 下一步

优先路线：在 `vue-kube-manager` 按该 binding spec 实现 reviewed fixture 只读工作台，先用 mock fixtures 验证页面和禁用按钮。

备选路线：准备首个真实 reviewed fixture，但必须先由 human review gate 输出最终 digest，再通过人工 Git review 提交真实 JSON；运行时仍不能写 fixture 或 catalog。
