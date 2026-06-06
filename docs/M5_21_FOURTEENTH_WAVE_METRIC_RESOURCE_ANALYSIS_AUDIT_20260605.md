# M5.21-14 资源余量与公开监控指标 Tool 审计

## 背景

前几批已经补齐 EasyFlow、资源预设、账单与成本分析证据链。为了让 AI 助手在执行部署、诊断和容量建议前能够判断“当前集群是否有足够资源、整体 GPU/CPU/存储/Pod 状态如何”，本批接入成熟 kube-manager 的节点剩余资源和公开瞬时监控指标。

本批只接入 GET，不调用真实 8100；测试全部使用 mock。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `NodeController#getOrgRemaining` 提供组织级剩余可分配资源，带 `@Isolation`；`MetricPublicController` 提供 GPU、CPU、存储、Pod 的公开瞬时指标。 |
| vue-kube-manager 前端专家 | 成熟前端在 Dashboard、Operation Board 与节点视图中稳定使用节点列表、Grafana 和资源概览数据；AI 侧需要结构化指标补足可解释性。 |
| Agent 安全专家 | 节点剩余资源必须使用可信组织上下文；公开指标不接受任意 query，避免被误用成 Prometheus 自由查询入口。 |
| 数据分析专家 | 剩余资源 + GPU/CPU/存储/Pod 指标可支撑部署前容量判断、异常状态概览和资源趋势问答。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `node_remaining_resource` | GET | `/api/{orgId}/node/remaining` | READ | AUTHENTICATED |
| `metric_gpu_server_instant` | GET | `/api/public/metric/prometheus/instant/server/gpu` | READ | PUBLIC |
| `metric_cpu_server_instant` | GET | `/api/public/metric/prometheus/instant/server/cpu` | READ | PUBLIC |
| `metric_storage_server_instant` | GET | `/api/public/metric/prometheus/instant/server/storage` | READ | PUBLIC |
| `metric_pod_instant` | GET | `/api/public/metric/prometheus/instant/pod` | READ | PUBLIC |

## 安全约束

- 不接入节点创建、更新、删除、标签分配、批量同步等写接口。
- 不接入 Prometheus 自由 query；只调用成熟后端封装好的固定指标接口。
- `node_remaining_resource` 的 `organizationId/orgId` 只来自可信 `UserPermissionContext`。
- 公开指标 Tool 不透传调用方传入的 `metricType/query/page/limit` 等参数。
- 暂不接入环境、能耗、功率等外部设备指标，避免在没有业务验收场景前扩面过散。

## 变更清单

- 新增 `NodeRemainingResourceTool`
- 新增 `MetricGpuServerInstantTool`
- 新增 `MetricCpuServerInstantTool`
- 新增 `MetricStorageServerInstantTool`
- 新增 `MetricPodInstantTool`
- 新增 `MetricAndResourceAnalysisToolHttpContractTest`
- 更新 `M511AtlasToolHttpContractTest` READ endpoint 白名单
- 更新 `intents.yml` 新增 5 个资源/监控分析意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`MetricAndResourceAnalysisToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、EasyFlow、财务分析、权限、参数契约和 HTTP 安全边界。
