# M5.21-15 行业应用分析 Tool 审计

## 背景

前几批已经补齐 EasyFlow、资源预设、成本账单、资源余量和公开监控指标。为了让 AI 助手进一步具备“理解 kube-manager 业务应用资产、部署前分析模板能力、解释实例运行与接口访问”的能力，本批接入成熟 `IndustryAppController` 中的只读接口。

本批只接入 GET，不调用真实 8100；测试全部使用 mock。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `IndustryAppController` 已提供模板列表/详情/API 文档、实例列表/API 历史、模板资源预设和高级参数等成熟 GET 能力，是行业应用分析的权威入口。 |
| vue-kube-manager 前端专家 | 成熟前端把行业应用作为 Deployment 类型之一，模板资源、镜像、端口、参数会影响创建体验；AI 需要先读懂模板和实例，再建议部署。 |
| Agent 安全专家 | 继续遵循最小权限：只接 GET；所有 `{organizationId}` 来自可信 `UserPermissionContext`；列表 query 只能使用 DTO 白名单字段；路径 ID 必须正整数。 |
| 数据分析专家 | 模板详情 + 资源预设 + 高级参数 + API 文档 + API 历史，可支撑部署前可行性分析、运行状态解释和接口访问异常定位。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `industry_app_template_list` | GET | `/api/{orgId}/industry-app/template` | READ | AUTHENTICATED |
| `industry_app_template_detail` | GET | `/api/{orgId}/industry-app/template/{appId}` | READ | AUTHENTICATED |
| `industry_app_template_api_doc` | GET | `/api/{orgId}/industry-app/template/{appId}/api-doc` | READ | AUTHENTICATED |
| `industry_app_instance_list` | GET | `/api/{orgId}/industry-app/instance` | READ | AUTHENTICATED |
| `industry_app_instance_api_history` | GET | `/api/{orgId}/industry-app/instance/{instanceId}/api-history` | READ | AUTHENTICATED |
| `industry_app_resource_preset_list` | GET | `/api/{orgId}/industry-app/template/{appId}/resource-preset` | READ | AUTHENTICATED |
| `industry_app_param_list` | GET | `/api/{orgId}/industry-app/template/{appId}/app-param` | READ | AUTHENTICATED |

## 安全约束

- 不接入行业应用模板创建、更新、删除。
- 不接入行业应用实例部署、更新、删除。
- 不接入资源预设和高级参数的保存、更新、删除。
- `organizationId/orgId` 只来自可信 `UserPermissionContext`，不信任 Tool 参数中的伪造租户。
- 列表 query 仅透传成熟 DTO 字段：模板列表支持 `page/limit/category/keyword/tags/includeDetail`；实例列表支持 `page/limit/name/status/mineOnly/includeDetail`；API 历史支持 `page/limit/httpMethod/url/sinceSeconds`。
- `appId/instanceId` 仅允许正整数，避免路径注入。
- `limit` 最大 1000，`page` 最大 10000，`sinceSeconds` 最大 86400。

## 变更清单

- 新增 `IndustryAppQuerySupport`
- 新增 `IndustryAppTemplateListTool`
- 新增 `IndustryAppTemplateDetailTool`
- 新增 `IndustryAppTemplateApiDocTool`
- 新增 `IndustryAppInstanceListTool`
- 新增 `IndustryAppInstanceApiHistoryTool`
- 新增 `IndustryAppResourcePresetListTool`
- 新增 `IndustryAppParamListTool`
- 新增 `IndustryAppAnalysisToolHttpContractTest`
- 更新 `M511AtlasToolHttpContractTest` READ endpoint 白名单
- 更新 `intents.yml` 新增 7 个行业应用分析意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`IndustryAppAnalysisToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、EasyFlow、财务分析、资源监控、权限、参数契约和 HTTP 安全边界。
