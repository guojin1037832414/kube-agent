# M5.21-20 模板详情只读 Tool 审计

> 日期: 2026-06-06
> 范围: mature kube-manager `TemplateController` 与 `TrainJobTemplateController`
> 结论: 本批补齐应用模板详情和训练 Job 模板详情，模板 create/update/delete 继续 HOLD。

## 专家会诊结论

- mature 后端在两个模板控制器中均提供 `GET list`、`GET detail`、`POST create`、`PUT update`、`DELETE delete`。
- kube-agent 已具备 `template_list` 与 `job_template_list`，但缺少按 ID 获取详情，导致 AI 助手在解释部署模板、训练任务模板默认配置时证据链不完整。
- 外部 Agent 安全实践强调最小权限、read/write 分离、对敏感或有副作用的 function tool 使用审批。详情接口是只读 GET，可接入；模板写操作会改变线上可复用配置，应作为高风险变更独立设计。

参考资料:

- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 本批交付

- 新增 `TemplateDetailTool`:
  - `GET /api/{orgId}/template/{templateId}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `READ`
- 新增 `JobTemplateDetailTool`:
  - `GET /api/{orgId}/train-job-template/{templateId}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `READ`
- 新增 `TemplateQuerySupport`:
  - 统一模板 ID 正整数校验。
  - 拒绝路径穿透和非数字 ID。
- 补全 `TemplateListTool` 与 `JobTemplateListTool` 的 HTTP 元数据，纳入静态契约治理。
- 新增 `TemplateDetailToolHttpContractTest`，用 mock 锁定可信 org、固定路径、非法 ID fail-closed 和 READ 元数据。

## 安全边界

- 没有调用真实 8100。
- 没有创建、更新或删除模板。
- `{orgId}` 只来自可信 `UserPermissionContext`，不读取 LLM 传入的 `organizationId/orgId`。
- `{templateId}` 只允许正整数。

## HOLD 清单

- `POST /api/{orgId}/template`
- `PUT /api/{orgId}/template`
- `DELETE /api/{orgId}/template/{templateId}`
- `POST /api/{orgId}/train-job-template`
- `PUT /api/{orgId}/train-job-template`
- `DELETE /api/{orgId}/train-job-template/{templateId}`

后续若接入，应按高风险配置变更 Tool 处理，至少包含 `requiresConfirmation=true`、DTO 白名单、目标 ID/名称复述确认和审计日志。
