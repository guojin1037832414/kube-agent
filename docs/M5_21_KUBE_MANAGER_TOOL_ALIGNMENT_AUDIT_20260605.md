# M5.21 kube-manager 工具接口对齐审计

> 日期：2026-06-05
> 目标：让 kube-agent 的高风险写操作 Tool 与成熟 kube-manager/vue-kube-manager 接口保持一致，并继续执行 fail-closed、HITL、真实用户 Token 的安全边界。

## 一、专家会诊结论

本轮参考优秀 Agent 工程实践后形成三条落地原则：

1. 高风险工具必须先审批、后执行，并保留可审计的工具元数据。
2. Agent 不允许把占位能力返回成“成功”，否则会误导用户和审计链路。
3. kube-agent 作为 kube-manager 的 AI 代理，参数单位和接口路径必须以成熟前后端为准，不能凭 LLM 语义臆造。

## 二、本轮接口对齐矩阵

| Tool | 成熟项目接口依据 | 本轮处理 | 风险策略 |
| --- | --- | --- | --- |
| `deploy_create_instance` | `vue-kube-manager/src/utils/request-formatter.js` 的 `formatApplication` | CPU 核转毫核、内存 GB 转 MiB、补 `cpuRequests/memRequests`、带宽字段、二层网络默认值；GPU 创建必须明确 `gpuModel` | CREATE + HITL |
| `deploy_delete` | `DELETE /api/{organizationId}/deployment?name=xxx` | 从错误的 POST 动态路径改为 DELETE query 参数 | DELETE + ADMIN_ONLY + HITL |
| `deploy_scale` | `PATCH /api/{organizationId}/deployment/scale` | 从占位成功改为真实 PATCH，body 使用 `name + replicas` | ACTION + HITL |
| `mpi_job_submit` | `POST /api/{organizationId}/mpi-job/submit/{mpiJobId}` | 从 body `{id}` 改为路径变量 | ACTION + HITL |
| 会话详情/删除/改名 | 会话 ID 不应作为权限凭证 | 新增 `userId + conversationId` 收敛查询/修改/删除 | 防横向访问 |

## 三、GPU 参数决策

成熟前端 GPU 选择链路来自 `vue-kube-manager/src/mixins/gpu-detail.js`：

- `getNodeGpuMap()` -> `GET /api/{organizationId}/node/all/gpu-map`：组织可选择、可分配的 GPU 配置。
- `getGpuMap()` -> `GET /api/gpu/all/gpu-map`：全局 GPU/MIG 规格知识库。
- `formatApplication(temp, gpuMap)` 会把前端选择的 `gpuSpec` 翻译成 `gpuModel/migConfig` 后再提交 Deployment。

kube-agent 当前采用保守策略：

1. `deploy_create_instance` 不凭自然语言猜测 MIG 配置。
2. 用户请求 GPU 实例时，优先提供 `gpuSpec`，其值必须来自 `gpu_query` 返回的组织级 GPU map key。
3. `deploy_create_instance` 会读取 `/api/{organizationId}/node/all/gpu-map`，把 `gpuSpec` 解析为 `gpuModel/migConfig` 后再提交。
4. 如果只提供 `gpuModel`，且组织 GPU map 中存在多个 MIG/整卡候选，则 fail-closed，要求用户选择明确 `gpuSpec`。
5. `gpuPercentLimits > 0` 且缺少 `gpuSpec/gpuModel` 时 fail-closed。

## 四、ReAct 编排补强

专家会诊参考了 [OpenAI Agents SDK Human-in-the-loop](https://openai.github.io/openai-agents-python/human_in_the_loop/) 的 approval/interruption 流程，以及 [LangGraph review tool calls](https://langchain-ai.github.io/langgraph/how-tos/human_in_the_loop/review-tool-calls/) / [interrupt](https://docs.langchain.com/oss/python/langgraph/human-in-the-loop) 文档。共同结论是：敏感工具调用应在执行前暂停，暴露工具名与参数，并允许审批、修改、拒绝或补充输入。结合 kube-agent 的线上 kube-manager 风险，本轮进一步把“缺参澄清”前移到 ReAct Prompt：

1. 用户要创建 GPU 实例但没有明确 `gpuSpec` 时，ReAct 必须先调用 `gpu_query` 查询组织级 GPU map。
2. `gpu_query` 返回后，优先把返回 map 的 key 传给 `deploy_create_instance.gpuSpec`。
3. 若同一 GPU 型号有多个 MIG/整卡候选，ReAct 必须用 Final Answer 请用户选择明确 `gpuSpec`，不能直接创建。
4. `deploy_create_instance` 仍保持 CREATE 风险属性，进入执行层前继续受 HITL fail-closed 守卫约束。

## 五、参数契约补强

本轮把高风险 Tool 的结构化参数契约也纳入审计范围：

- `deploy_delete.name`：必须是精确 Deployment/实例名称，不能用组织、用户或模糊关键词替代。
- `deploy_scale.name + targetReplicas`：目标副本数必须是非负整数，审批展示时能直接看到扩缩容目标。
- `mpi_job_submit.id`：必须是明确 MPI Job ID，来源应是用户明确指定或任务列表/详情查询结果。

这些契约会进入 ToolRegistry 的可见工具目录，约束 ReAct `Action.params` 使用 canonical 参数名，也能让后续 HITL 审批界面更清楚地展示“将要执行什么”。

## 六、结构化澄清下沉

为了避免只依赖 Prompt 约束，本轮把 Tool 结构化失败信号继续下沉到统一安全执行边界：

- `AtlasToolResult.fail(message, errorCode, suggestions)` 返回的 `errorCode/suggestions` 会被 `SafeToolExecutor` 保留到 `SafeToolExecutionResult`。
- 当 Tool 已执行但业务结果为失败，且携带 `errorCode` 或 `suggestions` 时，`SafeToolExecutionResult.requiresClarification=true`。
- `toGraphUpdates()` 会同步写出 `tool_error_code/tool_suggestions/requires_clarification`，后续 SSE/前端可以据此渲染澄清任务。
- Tool 业务失败摘要不再使用成功前缀，避免把 `MISSING_GPU_SPEC`、`AMBIGUOUS_GPU_SPEC` 之类结果误展示为成功。
- `AtlasOrchestrator` 会在 `/chat/graph`、supervisorGraph `tool_call` 和 `execute_node` 流式输出中检查 `requires_clarification`，并转发为前端已有的 `clarify` SSE 事件。
- SSE JSON 序列化补齐 Iterable/List 支持，`suggestions/requiredContext` 不再退化成字符串，前端可以直接按数组渲染可选项。
- supervisorGraph resume 只在“服务端确认 marker + `CALL_TOOL` 决策”同时存在时复用注入决策；clarify resume 会重新进入 AtlasBrain，让用户补充内容触发新决策。
- `HITLController` 的 confirm/clarify resume 流也转发 Tool 结构化补参，避免用户补充后仍缺参数时失去下一轮 `clarify`。

这让 GPU 创建缺参从“模型提示词建议先问”推进到“统一执行层能识别这是补参问题，并通过 SSE 交给前端渲染”，为后续 Plan/execute_node 自动生成结构化澄清任务打底。

## 七、开发约束

- 禁止对线上 kube-manager 执行真实更新/删除测试，除非用户明确给出可回滚的测试资源和审批。
- 后续新增 Tool 时，必须同步维护 `@AtlasToolMapping` 的 `httpMethod/apiEndpoints/operationType/requiresConfirmation`。
- 涉及 kube-manager 表单默认值时，优先对照成熟前端 formatter，而不是只看后端 DTO。
- 代码中保留中文注释解释单位转换、安全边界、HITL 原因，方便后续开发者接手。
- `token/orgId/organizationId/userId/conversationId/sessionId` 只能作为服务端可信上下文，不能被写入业务 DTO body。
- Tool 入口必须能处理不可变参数 Map；类型转换只能作用在本地副本上，不能依赖调用方传入可变容器。

## 八、后续验收项

1. 把 `gpu_query -> deploy_create_instance` 编排继续下沉到 Plan/执行状态机，使缺少 GPU 信息时不仅执行层能标记澄清，还能自动生成用户可选择的 `gpuSpec` 候选。
2. 继续清理剩余 Tool 的 `operationType` 与 `getParameterSpecs()` 缺口。
3. 评估 `kube-agent-vue` 真实源码位置；当前 `F:\gitProject\kube-agent-vue` 只有 README，无法完成前端 HITL/SSE review。
