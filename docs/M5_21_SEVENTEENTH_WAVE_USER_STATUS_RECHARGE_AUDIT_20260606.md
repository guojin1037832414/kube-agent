# M5.21-17 User/RBAC 状态与充值高风险 Tool 审计

## 背景

本批继续推进“通过 AI 助手覆盖 kube-manager 操作”的目标，补齐成熟后端 `UserController`
中已存在但 kube-agent 尚未接入的用户启用、禁用、充值能力。

用户状态和账户余额会直接影响登录、资源使用和财务记录，因此本批不做静默自动执行，全部按
高风险变更处理。

## 专家会诊结论

- 后端专家：成熟接口为 `PUT /api/{organizationId}/user/enable/{id}`、
  `PUT /api/{organizationId}/user/disable/{id}`、`PUT /api/{organizationId}/user/recharge`。
- 前端专家：成熟前端已有充值入口，充值 DTO 仅包含 `userId/amount/remark`，其中 `amount`
  单位为分。
- 安全专家：参考 OpenAI Agents SDK、Microsoft Agent Framework 和 OWASP Agentic AI 对
  HITL 的建议，账号状态与资金余额变更必须要求人工审批，审批内容要展示精确 Tool、目标 ID
  和 payload，不能让模型自由描述后绕过确认。
- 测试专家：本批只做 mock 契约测试，不访问真实 8100 写接口，避免影响线上数据。

## 本批交付

- 新增 `UserEnableTool`：启用目标用户账号。
- 新增 `UserDisableTool`：禁用目标用户账号。
- 新增 `UserRechargeTool`：为目标用户充值。
- 新增 `UserRiskMutationSupport`：统一目标用户 ID、充值金额和充值 body 白名单校验。
- 扩展 `HighRiskMutationToolHttpContractTest`：锁定 PUT path、充值 body 白名单、非法参数
  fail-closed。
- 扩展 `M511AtlasToolHttpContractTest`：把 3 个新增 Tool 纳入高风险 endpoint 白名单。
- 扩展 `intents.yml`：新增 `user_enable`、`user_disable`、`user_recharge` 意图。

## 安全边界

- 3 个 Tool 均声明为 `ACTION`，并设置 `requiresConfirmation=true`。
- 3 个 Tool 均为 `ADMIN_ONLY`。
- `{orgId}` 只来自可信 `UserPermissionContext`，忽略伪造的 `organizationId/orgId`。
- 启用/禁用只接受显式目标用户 ID，不使用当前登录上下文中的 `userId`。
- 充值 body 只透传 `userId/amount/remark`，不透传 `balance/approved/token/sessionId` 等字段。
- 金额必须是正整数，单位为分；非法金额在 HTTP 调用前失败。

## 未接入范围

- 未接入用户更新 `PUT /api/{orgId}/user`，该接口字段较宽，需后续单独审计 DTO 白名单。
- 未接入角色创建、角色分配、权限菜单初始化等更高权限 RBAC 操作。
- 未调用真实 8100 写接口。
