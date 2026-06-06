# M5.21-12 Dashboard 与资源预设分析 Tool 审计

## 背景

第十批和第十一批已经补齐 EasyFlow 实例、日志、流程和阶段元数据。为了让 AI 助手能够进一步做工作流规模分析、阶段资源配置解释和运行失败归因，本批对照成熟 `kube-manager` 与 `vue-kube-manager` 的看板和资源预设接口，补齐两个只读分析入口：

- Dashboard EasyFlow 统计：用于回答“流程数量/工作流规模/近期看板概览”等问题。
- 资源预设详情：用于解释 EasyFlow 阶段里的 `resourceCode` 或资源预设 ID 对应的 CPU/GPU/显存/存储配置。

本批仍遵守线上安全约束：只接入 GET；测试只使用 mock；不调用真实 8100 的创建、更新、删除、推进、清理接口。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `DashboardController` 提供 `GET /dashboard/easy-flow/count`；`ResourcePresetController` 提供带 `@Isolation` 的 `GET /resource-preset/{resourcePresetId}`，可作为只读分析证据源。 |
| vue-kube-manager 前端专家 | 成熟前端 `src/api/dashboard.js` 已稳定使用 EasyFlow count；流程配置页面依赖资源预设列表/详情语义来呈现阶段资源配置。 |
| Agent 安全专家 | Dashboard count 保持固定查询，不暴露 page/limit/keyword；资源预设详情必须校验正整数 ID，组织 ID 只能来自可信登录上下文。 |
| 数据分析专家 | 统计 + 资源详情可以把“流程数量、阶段配置、资源规格、日志摘要”串成分析链路，是后续自动诊断和容量建议的基础。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `dashboard_easy_flow_count` | GET | `/api/{orgId}/dashboard/easy-flow/count` | READ | PUBLIC |
| `resource_preset_detail` | GET | `/api/{orgId}/resource-preset/{resourcePresetId}` | READ | AUTHENTICATED |

## 安全约束

- 不接入 `POST/PUT/DELETE /api/{orgId}/resource-preset`。
- 不接入 EasyFlow 推进、清理、删除等会改变线上状态的接口。
- `dashboard_easy_flow_count` 继续沿用 Dashboard 固定查询：`page=1&limit=100`，忽略调用方传入的分页、搜索和伪造组织字段。
- `resource_preset_detail` 只允许 `resourcePresetId` 为正整数，拒绝 `../42`、`42/extra`、空字符串等路径注入。
- `organizationId/orgId` 继续只来自 `UserPermissionContext`，不信任 Tool 参数中的伪造租户。

## 变更清单

- 新增 `DashboardEasyFlowCountTool`
- 新增 `ResourcePresetDetailTool`
- 新增 `DashboardEasyFlowCountToolHttpContractTest`
- 新增 `ResourcePresetDetailToolHttpContractTest`
- 更新 `DashboardFixedQueryHoldContractTest`，把 EasyFlow count 纳入 Dashboard 固定查询 HOLD。
- 更新 `M511AtlasToolHttpContractTest`，加入本批 READ endpoint 精确白名单。
- 更新 `intents.yml`，新增 2 个只读分析意图。
- 更新 `CHANGELOG.md`。

## 验证计划

- 定向测试：`DashboardEasyFlowCountToolHttpContractTest,ResourcePresetDetailToolHttpContractTest,DashboardFixedQueryHoldContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、EasyFlow、权限、参数契约和 HTTP 安全边界。
