# M5.21 第三批 Image/Storage 高风险 Tool 审计

> 日期：2026-06-05
> 范围：镜像删除、镜像拉取、存储申请、存储删除。
> 约束：只做源码对照和 mock 契约测试，不调用线上 kube-manager 写接口。

## 专家会诊结论

本轮继续沿用 OpenAI Agents SDK Human-in-the-loop 与 LangGraph review tool calls 的实践：高风险工具执行前必须让人看到真实工具名、参数与后端动作。对 kube-manager 的线上资源操作，还需要把成熟前端和后端 controller 作为唯一接口依据，不能让 agent 自造路径或把上下文字段塞进业务 DTO。

## 接口矩阵

| Tool | 成熟项目依据 | 本批处理 | 安全策略 |
| --- | --- | --- | --- |
| `image_delete` | `vue-kube-manager/src/api/image.js` 与 `ImageController.removeImage` | `DELETE /api/{orgId}/image/{imageId}?entirely={entirely}`，默认 `entirely=false` | DELETE + ADMIN_ONLY + HITL |
| `image_pull` | `vue-kube-manager/src/views/image/image/index.vue` 与 `ImageController.pullImage` | body 改为 Image 实体风格：`repoTag/clientRepoTag/registryAuthId/scope/description` | ACTION + AUTHENTICATED + HITL |
| `storage_create` | `FileController.applyStorage` 与 `StorageApplyDTO` | body 改为 `areaCode/scope/type/size/displayName/description/message`，支持 `location -> areaCode` | CREATE + AUTHENTICATED + HITL |
| `storage_delete` | `vue-kube-manager/src/api/file.js` 与 `FileController.deleteStorage` | 改为 `DELETE /api/{orgId}/file/deleteStorage?name={name}` | DELETE + ADMIN_ONLY + HITL |

## 关键安全收口

- 镜像删除的 `entirely` 默认为 `false`。只有用户明确选择并通过 HITL 审批时，才会同时删除仓库中的真实镜像。
- 存储删除不再在缺少 `name` 时回退到 `userId`，避免把服务端用户上下文误当业务目标。
- `user/org/pub` 是系统内置存储名，`storage_delete` 会直接拒绝删除。
- 存储申请不再透传 `organizationId/orgId/userId/token/sessionId/conversationId/approved` 等上下文字段或审批字段，避免越权和伪造审批。

## 验证

- `HighRiskMutationToolHttpContractTest` 新增 Image/Storage mock 契约，锁定 HTTP 方法、路径、query/body。
- `M511AtlasToolHttpContractTest` 更新 Image/Storage 高风险 endpoint 白名单。
- 本轮不访问真实 8100 写接口。

## 后续验收项

1. 继续审 `experiment_*`：当前 `stop/delete/start` 路径需要和成熟 `experiment.js`、后端实验 controller 精确对齐。
2. 单独审 `user_create/user_delete`：成熟前端存在 `/api/{organizationId}/user` 与历史 `/api/account/...` 差异，需要确认线上实际路由和权限策略后再改。
3. 对 Image/Storage 的 HITL 前端展示补充更清晰的高风险字段说明，尤其是 `entirely=true` 与存储删除影响范围。
