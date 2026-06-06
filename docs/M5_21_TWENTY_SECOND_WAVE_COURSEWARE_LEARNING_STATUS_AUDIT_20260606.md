# M5.21-22 课程学习状态 Tool 审计

> 日期: 2026-06-06
> 范围: mature kube-manager `LearnDeploymentController#getLearningStatus`
> 结论: 本批仅接入课程学习状态 GET；课程实例创建、暂停、恢复、重置、删除和批量重置继续 HOLD。

## 专家会诊结论

- mature 前端 `course-preview` 实际使用 `statusDeployment(coursewareId)` 查询课程学习状态。
- mature 后端该接口为 `GET /api/{organizationId}/learn/deployment/status/{coursewareId}`，可帮助 AI 助手分析课程环境是否就绪、运行中或异常。
- 学习状态可能包含个人课程实例/环境状态，因此 Agent 侧按敏感读取处理，要求确认后执行。
- 外部 Agent 安全实践强调最小权限、read/write 分离、敏感工具审批和参数校验。本批只接入 GET，不接入任何有副作用的课程环境动作。

参考资料:

- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 本批交付

- 新增 `CoursewareLearningStatusTool`:
  - `GET /api/{orgId}/learn/deployment/status/{coursewareId}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `SENSITIVE_READ`
  - 需要确认: `true`
- 复用 `CoursewareQuerySupport`:
  - `coursewareId` 只允许正整数。
  - 拒绝路径穿透和非数字 ID。
- 新增 `CoursewareLearningStatusToolHttpContractTest`:
  - 验证可信 orgId。
  - 验证固定 GET 路径。
  - 验证非法 ID fail-closed。
  - 验证敏感读取元数据。

## 安全边界

- 没有调用真实 8100。
- 没有创建、删除、暂停、恢复、重置或批量重置课程实例。
- `{orgId}` 只来自可信 `UserPermissionContext`。
- `{coursewareId}` 只允许正整数。

## HOLD 清单

- `POST /api/{orgId}/learn/deployment/{coursewareId}`
- `DELETE /api/{orgId}/learn/deployment/delete/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/pause/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/restore/{coursewareId}/{snapshotId}`
- `POST /api/{orgId}/learn/deployment/reset/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/batch-reset/{coursewareId}`

后续若接入，应按高风险课程环境动作 Tool 处理，至少包含 `requiresConfirmation=true`、目标课件复述确认、快照 ID 强校验和审计日志。
