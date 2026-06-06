# M5.21-21 课件只读 Tool 审计

> 日期: 2026-06-06
> 范围: mature kube-manager `CoursewareController`
> 结论: 本批补齐课件详情和课件关联班级查询；课件保存、上传、删除、分发和课程环境操作继续 HOLD。

## 专家会诊结论

- mature 后端和 mature 前端均证明课件模块已有稳定接口：列表、详情、班级列表、保存、上传、删除、分发以及课程环境创建/暂停/恢复/重置等。
- kube-agent 已有 `courseware_list`，但缺少课件详情，导致 AI 助手无法解释课件内容和教学资源配置。
- `grade/{coursewareId}` 返回教学班级/组织关系，按敏感读取处理，避免普通对话中无确认暴露教学管理数据。
- 外部 Agent 安全实践强调最小权限、read/write 分离、敏感或有副作用的 function tool 使用人工审批；本批仅接入 GET，写操作和课程环境动作全部暂缓。

参考资料:

- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 本批交付

- 新增 `CoursewareDetailTool`:
  - `GET /api/{orgId}/courseware/info/{coursewareId}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `READ`
- 新增 `CoursewareGradeListTool`:
  - `GET /api/{orgId}/courseware/grade/{coursewareId}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `SENSITIVE_READ`
  - 需要确认: `true`
- 新增 `CoursewareQuerySupport`:
  - 统一 `coursewareId` 正整数校验。
  - 拒绝路径穿透和非数字 ID。
- 补全 `CoursewareListTool` 的 HTTP 元数据，纳入静态契约治理。
- 新增 `CoursewareReadToolHttpContractTest`，用 mock 锁定可信 org、固定路径、非法 ID fail-closed 和风险元数据。

## 安全边界

- 没有调用真实 8100。
- 没有保存、上传、删除、分发课件。
- 没有创建、暂停、恢复、重置或删除课程 Deployment。
- `{orgId}` 只来自可信 `UserPermissionContext`。
- `{coursewareId}` 只允许正整数。

## HOLD 清单

- `POST /api/{orgId}/courseware/update`
- `POST /api/{orgId}/courseware/save`
- `DELETE /api/{orgId}/courseware/delete/{coursewareId}`
- `POST /api/{orgId}/courseware/divide`
- `POST /api/{orgId}/learn/deployment/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/pause/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/restore/{coursewareId}/{snapshotId}`
- `POST /api/{orgId}/learn/deployment/reset/{coursewareId}`
- `POST /api/{orgId}/learn/deployment/batch-reset/{coursewareId}`
- `DELETE /api/{orgId}/learn/deployment/delete/{coursewareId}`

后续若接入，应按高风险教学资源/课程环境变更 Tool 处理，至少包含 `requiresConfirmation=true`、DTO 白名单、目标课件复述确认和审计日志。
