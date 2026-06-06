# M5.21-10 EasyFlow 日志分析 Tool 审计

## 背景

本批目标是把 kube-manager 已成熟的 EasyFlow 实例日志与日志摘要能力接入 kube-agent，让 AI 助手可以围绕训练、测试、推理流程做只读诊断和数据分析。

用户明确要求测试 API 时谨慎，更新和删除操作不能影响线上系统。因此本批只接入 GET 读取接口，不接入 `step/process/clean/delete` 等会改变实例状态的接口。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `EasyFlowController` 已提供实例列表、实例详情、日志、日志列表、日志摘要、解析器列表等 GET 接口，适合作为 AI 诊断证据源。 |
| vue-kube-manager 前端专家 | 成熟前端 `src/api/easy-flow.js` 使用相同路径：`/instance`、`/instance/{id}`、`/log/{stage}`、`/log/{stage}/list`、`/log/{stage}/abstract`、`/analyzer`。 |
| Agent 安全专家 | 日志可能包含训练参数、文件路径、错误堆栈和业务数据，必须按 `SENSITIVE_READ + requiresConfirmation=true` 处理。 |
| 数据分析专家 | 日志摘要接口可把原始日志转换为结构化指标，是后续“训练进度/失败根因/指标变化”分析链路的关键输入。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `easy_flow_instance_list` | GET | `/api/{orgId}/easy-flow/instance` | READ | AUTHENTICATED |
| `easy_flow_instance_detail` | GET | `/api/{orgId}/easy-flow/instance/{instanceId}` | READ | AUTHENTICATED |
| `easy_flow_analyzer_list` | GET | `/api/{orgId}/easy-flow/analyzer` | READ | AUTHENTICATED |
| `easy_flow_instance_log` | GET | `/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}` | SENSITIVE_READ | AUTHENTICATED + HITL |
| `easy_flow_instance_log_list` | GET | `/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/list` | SENSITIVE_READ | AUTHENTICATED + HITL |
| `easy_flow_instance_log_abstract` | GET | `/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/abstract` | SENSITIVE_READ | AUTHENTICATED + HITL |

## 安全约束

- 不调用真实 8100 写接口；本批测试全部使用 `KubeManagerHttpClient` mock。
- `organizationId/orgId` 继续由 `UserPermissionContext` 决定，忽略 Tool 参数中的伪造租户字段。
- 日志 query 只透传 `limitBytes/sinceSeconds/tailLines/timestamps` 白名单字段，不透传任意用户参数。
- 日志摘要只透传 `analyzerCode`，解析器编码不确定时先调用 `easy_flow_analyzer_list`。
- 未接入 `PUT /instance/{id}/step`、`PUT /instance/{id}`、`DELETE /instance/{id}`，避免 Agent 误推进或清理线上流程。

## 变更清单

- 新增 `EasyFlowInstanceListTool`
- 新增 `EasyFlowInstanceDetailTool`
- 新增 `EasyFlowAnalyzerListTool`
- 新增 `EasyFlowInstanceLogTool`
- 新增 `EasyFlowInstanceLogListTool`
- 新增 `EasyFlowInstanceLogAbstractTool`
- 新增 `EasyFlowLogToolHttpContractTest`
- 更新 `M511AtlasToolHttpContractTest` 白名单
- 更新 `intents.yml` 新增 6 个意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`EasyFlowLogToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 回归测试：覆盖 ReAct/HITL/SafeToolExecutor/高风险 mutation/列表参数/权限注册等既有关键契约。
