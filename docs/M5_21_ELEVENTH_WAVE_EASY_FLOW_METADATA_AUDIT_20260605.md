# M5.21-11 EasyFlow 流程/阶段元数据 Tool 审计

## 背景

第十批已经接入 EasyFlow 实例日志、日志列表、日志摘要与日志解析器。为了让 AI 助手不仅能“读日志”，还能理解日志属于哪个流程、哪个阶段、阶段运行了什么镜像/命令/资源，本批接入成熟 kube-manager 的 EasyFlow 流程与阶段只读元数据接口。

本批仍遵守线上安全约束：只接入 GET，不接入流程创建、流程更新、流程删除、阶段新增、阶段更新、阶段删除、实例推进或实例清理。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `EasyFlowController` 已提供 `GET /flow`、`GET /flow/{flowId}`、`GET /flow/{flowId}/stage`、`GET /flow/{flowId}/stage/{stageId}`，适合作为日志分析的结构化上下文。 |
| vue-kube-manager 前端专家 | 成熟前端 `src/api/easy-flow.js` 已稳定使用同一组 GET 接口，写接口则独立分布在 create/update/delete/step/process。 |
| Agent 安全专家 | 流程/阶段详情可能包含命令、镜像、环境变量等敏感元数据，但当前按登录用户组织上下文读取即可；日志正文仍保持 HITL 敏感读取。 |
| 数据分析专家 | 阶段元数据中的 `code/analyzerCode/resourceCode/image/commands` 可帮助 AI 解释日志摘要和训练失败原因。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `easy_flow_flow_list` | GET | `/api/{orgId}/easy-flow/flow` | READ | AUTHENTICATED |
| `easy_flow_flow_detail` | GET | `/api/{orgId}/easy-flow/flow/{flowId}` | READ | AUTHENTICATED |
| `easy_flow_stage_list` | GET | `/api/{orgId}/easy-flow/flow/{flowId}/stage` | READ | AUTHENTICATED |
| `easy_flow_stage_detail` | GET | `/api/{orgId}/easy-flow/flow/{flowId}/stage/{stageId}` | READ | AUTHENTICATED |

## 安全约束

- 不调用真实 8100 写接口；本批测试全部使用 `KubeManagerHttpClient` mock。
- `organizationId/orgId` 继续只来自 `UserPermissionContext`，不信任 Tool 参数中的伪造租户。
- `flowId/stageId` 必须是数字 path segment，拒绝 `../`、`/extra` 等非预期路径片段。
- `easy_flow_flow_list` 只透传 `page/limit/flowId/type/description`，不透传 token、orgId、任意用户字段。
- 未接入 `POST /flow`、`POST /flow/{flowId}`、`DELETE /flow/{flowId}`、`POST/PUT/DELETE /stage` 等写接口。

## 变更清单

- 新增 `EasyFlowFlowListTool`
- 新增 `EasyFlowFlowDetailTool`
- 新增 `EasyFlowStageListTool`
- 新增 `EasyFlowStageDetailTool`
- 新增 `EasyFlowMetadataToolHttpContractTest`
- 更新 `EasyFlowLogToolSupport`，复用 flowId/stageId 数字路径片段校验
- 更新 `M511AtlasToolHttpContractTest` 白名单
- 更新 `intents.yml` 新增 4 个意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`EasyFlowMetadataToolHttpContractTest,EasyFlowLogToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、外链、EasyFlow、权限、参数契约。
