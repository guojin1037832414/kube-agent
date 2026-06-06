# M5.21 第五批 User/RBAC 高风险 Tool 审计

> 日期：2026-06-05
> 范围：用户创建、用户删除
> 约束：只做源码对照和 mock 契约测试，不调用线上 kube-manager 用户创建/删除接口。

## 专家会诊结论

用户创建和删除属于 P0 级权限动作。成熟后端 `UserController` 暴露的真实接口是：

- `POST /api/{organizationId}/user`
- `DELETE /api/{organizationId}/user/{id}`

成熟前端 `deleteUser` 当前写成 `/api/account/{organizationId}/user/{id}`，但本地成熟后端没有找到对应 controller。基于“后端 controller 优先、缺证据不造路径”的原则，本轮用户删除继续对齐真实后端 `UserController`。

## 接口矩阵

| Tool | 成熟后端依据 | 本轮处理 | 安全策略 |
| --- | --- | --- | --- |
| `user_create` | `UserController.createUser(@RequestBody UserDetailDTO)` | `POST /api/{orgId}/user`，body 改为 DTO 白名单 | CREATE + ADMIN_ONLY + HITL |
| `user_delete` | `UserController.deleteUser(@PathVariable id)` | `DELETE /api/{orgId}/user/{id}`，schema 只暴露目标用户 `id` | DELETE + ADMIN_ONLY + HITL |

## 关键安全收口

- `user_create` 只透传 `UserDetailDTO` 业务字段，不透传 `organizationId/orgId/userId/token/sessionId/conversationId/approved`。
- 组织边界仍通过 `resolveOrganizationId()` 从可信登录上下文解析，不能由 LLM 或自然语言参数指定。
- `user_delete` 的目标 ID 必须来自 `id`；执行层仅为历史调用兼容读取 `userId`，但 Tool schema 不暴露该受保护字段。缺失时返回 `MISSING_USER_ID`，不会回退到当前登录用户上下文。
- 用户创建和删除均保持 `ADMIN_ONLY` 与 `requiresConfirmation=true`。

## 验证

- `mvn -q "-Dtest=HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest" test` 通过。
- 本轮没有访问真实 8100 用户创建/删除接口。

## 后续验收项

1. 若线上实际存在 `/api/account/{organizationId}/user/{id}`，需要在 kube-manager 源码中补齐 controller 或确认路由来源后再调整 Tool。
2. 用户创建的 `roles` 需要在前端 HITL 页面中突出展示，因为它会直接影响账号权限。
3. 后续可继续审计 `user_enable/user_disable/user_recharge` 等成熟后端已存在但 kube-agent 尚未完全接入的 RBAC 动作。
