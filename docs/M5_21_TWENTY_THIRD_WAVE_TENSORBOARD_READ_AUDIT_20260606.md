# M5.21-23 TensorBoard 训练监控只读 Tool 审计

> 日期: 2026-06-06
> 结论: 本批仅接入 TensorBoard list/environment/runs GET 读取能力；TensorBoard 创建、更新、删除和 trainjob TensorBoard 创建/删除继续 HOLD。

## 专家会诊结论

- mature 前端 `src/api/tensorboard.js` 明确使用 `getTensorBoard`、`getTensorBoardRuns`、`getTensorBoardEnvironment`，页面位于 `src/views/deployment/tensorboard/index.vue`。
- mature 后端 `TensorBoardController` 提供:
  - `GET /api/{organizationId}/tensorboard`
  - `GET /api/{organizationId}/tensorboard/data/environment`
  - `GET /api/{organizationId}/tensorboard/data/runs`
  - `GET /api/{organizationId}/tensorboard/trainjob-runs/{tensorBoardDeploymentId}`
- 这些接口不改变 kube-manager/Kubernetes 状态，但返回用户训练监控环境、训练任务 runs 或访问状态，可能涉及用户训练资产，因此统一标记为 `SENSITIVE_READ + requiresConfirmation=true`。
- 外部 Agent 安全实践强调最小权限、工具级审批和 read/write 分离。本批只接入 GET，不接入 create/update/delete。

参考资料:
- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Agent Framework Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 交付清单

- `TensorBoardListTool`
  - 补齐 `httpMethod=GET`
  - endpoint: `/api/{orgId}/tensorboard`
  - 风险: `SENSITIVE_READ`
  - 权限: `AUTHENTICATED`
- `TensorBoardEnvironmentTool`
  - endpoint: `/api/{orgId}/tensorboard/data/environment`
  - 固定空 query，禁止用户构造额外查询参数。
- `TensorBoardRunsTool`
  - endpoint: `/api/{orgId}/tensorboard/data/runs`
  - 固定空 query，避免误传过滤字段造成后端语义漂移。
- `TrainJobTensorBoardRunsTool`
  - endpoint: `/api/{orgId}/tensorboard/trainjob-runs/{tensorBoardDeploymentId}`
  - `tensorBoardDeploymentId` 只允许正整数，阻止路径穿透。

## HOLD 清单

- `POST /api/{organizationId}/tensorboard`
- `PUT /api/{organizationId}/tensorboard`
- `DELETE /api/{organizationId}/tensorboard`
- `POST /api/{organizationId}/tensorboard/trainjob-tensorboard`
- `DELETE /api/{organizationId}/tensorboard/trainjob-tensorboard/{tensorBoardDeploymentId}`

上述接口会创建、更新或删除训练监控资源，后续若接入，必须按高风险运行态资源变更处理，至少包含 HITL 确认、目标复述、DTO 白名单、审计日志和 mock 契约。

## 验证

- 新增 `TensorBoardReadToolHttpContractTest`，覆盖可信 org、固定路径、query 白名单、非法 path ID fail-closed 和敏感读取元数据。
- 扩展 `M511AtlasToolHttpContractTest`，将 TensorBoard 只读接口纳入 SENSITIVE_READ endpoint 精确白名单。
- 本批测试仅使用 mock HTTP client，未访问真实 `8100`。
