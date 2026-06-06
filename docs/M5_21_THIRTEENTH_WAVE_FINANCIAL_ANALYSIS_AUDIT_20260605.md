# M5.21-13 成本与使用分析 Tool 审计

## 背景

为了让 AI 助手不仅能完成 kube-manager 操作，还能解释“资源用了多少、任务花了多少钱、账单金额从哪里来”，本批对照成熟 `kube-manager` 与 `vue-kube-manager` 的成本/账单页面，补齐三个只读分析入口：

- 容器使用记录：运行历史、资源使用明细、用户/容器维度分析。
- Pod 使用账单：按应用汇总扣费记录，支持成本追踪。
- 计费配置：解释资源单价和账单金额来源。

本批仍只接入 GET；不调用真实 8100；不接入充值、保存计费配置、删除计费配置等会改变线上财务数据的接口。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `PodUseController` 提供 `GET /pod-use/record` 与 `GET /pod-use/bill`，`CostConfigController` 提供 `GET /cost`，三者均由后端角色/组织逻辑收敛数据范围。 |
| vue-kube-manager 前端专家 | 成熟前端已在用户记录、成本账单、云资源计费配置页面稳定使用这些接口。 |
| Agent 安全专家 | 使用记录、账单、定价均属于敏感读；Tool 必须 `SENSITIVE_READ + requiresConfirmation=true`，且 query 只能使用 DTO 白名单字段。 |
| 数据分析专家 | 这三类数据能把 EasyFlow/Deployment/Pod 运行记录与账单金额连接起来，是后续自动成本分析、异常扣费解释和容量建议的基础。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `pod_use_record_list` | GET | `/api/{orgId}/pod-use/record` | SENSITIVE_READ | AUTHENTICATED |
| `pod_use_bill_list` | GET | `/api/{orgId}/pod-use/bill` | SENSITIVE_READ | AUTHENTICATED |
| `cost_config_list` | GET | `/api/{orgId}/cost` | SENSITIVE_READ | AUTHENTICATED |

## 安全约束

- 不接入 `PUT /api/{orgId}/user/recharge`。
- 不接入 `POST /api/{orgId}/cost` 或 `DELETE /api/{orgId}/cost/{costConfigId}`。
- `organizationId/orgId` 只来自可信 `UserPermissionContext`，不信任 Tool 参数中的伪造租户。
- query 只透传成熟 DTO 明确支持的字段：分页、时间范围、应用名、容器名、用户姓名、Pod 状态。
- 不透传 `token/userId/sessionId/conversationId/approved/deviceAmount` 等上下文、审批或写入字段。
- 页大小上限为 1000，避免一次敏感查询拉取过多数据。

## 变更清单

- 新增 `FinancialAnalysisQuerySupport`
- 新增 `PodUseRecordListTool`
- 新增 `PodUseBillListTool`
- 新增 `CostConfigListTool`
- 新增 `FinancialAnalysisToolHttpContractTest`
- 更新 `M511AtlasToolHttpContractTest` 敏感读 endpoint 白名单
- 更新 `intents.yml` 新增 3 个成本/账单分析意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`FinancialAnalysisToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、EasyFlow、权限、参数契约和 HTTP 安全边界。
