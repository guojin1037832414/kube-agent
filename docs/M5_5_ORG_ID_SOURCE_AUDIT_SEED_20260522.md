# M5.5 orgId 来源治理审计种子（2026-05-22）
## 1. 背景
- M5.4 测试中发现：当 Tool 入参携带 `organizationId=100002` 时，Dashboard path 会变为 `/api/100002/...`。
- 当前 `BaseTool#resolveOrganizationId` 优先级为 `params.organizationId` → `params.orgId` → `UserPermissionContext.CURRENT_ORG_ID` → fail。
- ReActEngine 当前参数合并规则为 initialParams 先放、actionParams 后放，因此 LLM Action 可覆盖会话级 `organizationId`。
- 本阶段目标不是继续分页参数铺开，而是治理“租户路径来源必须来自可信会话上下文”的安全边界。

## 2. 当前事实快照
- 扫描目录：`src/main/java/com/atlas/tool/impl`
- 使用 `resolveOrganizationId(params)` 的 Tool 数量：**92**
- 权限分布：`{'PUBLIC': 73, 'AUTHENTICATED': 11, 'ADMIN_ONLY': 8}`
- 读写分布：`{'READ': 72, 'WRITE': 20}`
- Query 构造分布：`{'standard-list': 32, 'fixed-query': 26, 'custom': 34}`

## 3. 风险判断
| 风险点 | 当前行为 | 风险 | M5.5 候选治理 |
|---|---|---|---|
| Tool 执行层信任 params.orgId | `resolveOrganizationId` 优先读取 params | LLM/用户可改变 `{orgId}` path | 改为 ThreadLocal/session 优先，params 只作为无会话测试/兼容 fallback 或直接禁用 |
| ReAct 合并层允许 Action 覆盖 organizationId | `merged.putAll(actionParams)` | LLM action 可覆盖会话上下文 | 保护 reserved keys：token/orgId/organizationId/conversationId 不被 action 覆盖 |
| 既有测试大量直接传 organizationId | 单元测试无 ThreadLocal | 修复后可能大面积红灯 | 先建立小样本测试工具/上下文 helper，再分阶段迁移 |
| 写操作也依赖 resolveOrganizationId | POST/PUT/DELETE path 同样来自 orgId | 跨租户写风险高于查询 | 优先覆盖写操作样本和通用基类契约 |

## 4. 代表性调用面（前 80 个）
| Tool | Permission | Agent | Op | Query模式 | Path线索 |
|---|---:|---:|---:|---:|---|
| `BareMetalAppListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/{orgId}/bare-metal-application".replace("{orgId}", orgId);` |
| `CloudResourceListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/{orgId}/cloud".replace("{orgId}", orgId);` |
| `ClusterQueryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/hpc-job/cluster";` |
| `ComposeDeployCreateTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/compose";` |
| `ComposeListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/{orgId}/compose".replace("{orgId}", orgId);` |
| `CoursewareListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/courseware/list";` |
| `CurrencyQueryListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/{orgId}/currency".replace("{orgId}", orgId);` |
| `DaemonSetQueryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/dashboard/deployment";` |
| `DashboardDeploymentCountTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/{orgId}/dashboard/deployment/count".replace("{orgId}", orgId);` |
| `DashboardEasyFlowTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/{orgId}/dashboard/easy-flow".replace("{orgId}", orgId);` |
| `DashboardImageCountTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/{orgId}/dashboard/image/count".replace("{orgId}", orgId);` |
| `DataSetListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/data-set";` |
| `DeployCreateTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/deployment";` |
| `DeployDeleteTool` | `ADMIN_ONLY` | `deploy` | `WRITE` | `custom` | `` |
| `DeployRestartTool` | `ADMIN_ONLY` | `deploy` | `WRITE` | `custom` | `` |
| `DeploymentDetailTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/{orgId}/deployment".replace("{orgId}", orgId);` |
| `DeploymentQueryTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/" + orgId + "/deployment";` |
| `DevOpsQueryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/dashboard/deployment";` |
| `DiagnosePodTool` | `PUBLIC` | `diag` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/pod";` |
| `DistributedCreateTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `` |
| `DownloadTaskListTool` | `PUBLIC` | `storage` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/download";` |
| `EventQueryTool` | `PUBLIC` | `diag` | `READ` | `custom` | `String path = "/api/" + orgId + "/pod";` |
| `ExperimentInstanceDeleteTool` | `ADMIN_ONLY` | `deploy` | `WRITE` | `custom` | `` |
| `ExperimentInstanceListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/experiment/instance";` |
| `ExperimentInstanceStopTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `` |
| `ExperimentStartTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/experiment/instance/start";` |
| `ExperimentTemplateListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/experiment/template";` |
| `ExternalLinkListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/external-link";` |
| `FileListTool` | `PUBLIC` | `storage` | `READ` | `standard-list` | `String path = "/api/{orgId}/file".replace("{orgId}", orgId);` |
| `FileMaterialListTool` | `PUBLIC` | `storage` | `READ` | `standard-list` | `String path = "/api/{orgId}/file-material".replace("{orgId}", orgId);` |
| `FileSelectStorageTool` | `PUBLIC` | `storage` | `READ` | `custom` | `String path = "/api/{orgId}/file/selectStorage".replace("{orgId}", orgId);` |
| `FileStorageOptionTool` | `PUBLIC` | `storage` | `READ` | `fixed-query` | `String path = "/api/{orgId}/file/storage/option".replace("{orgId}", orgId);` |
| `FileVolumePathTool` | `PUBLIC` | `storage` | `READ` | `fixed-query` | `String path = "/api/{orgId}/file/volume-path".replace("{orgId}", orgId);` |
| `GpuDetailListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/{orgId}/gpu-detail".replace("{orgId}", orgId);` |
| `GpuMetricsTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/node/all/gpu-map";` |
| `HelmChartInfoTool` | `PUBLIC` | `deploy` | `READ` | `custom` | `String path = "/api/{orgId}/helm/charts/single".replace("{orgId}", orgId);` |
| `HelmChartSearchTool` | `PUBLIC` | `deploy` | `READ` | `custom` | `String path = "/api/{orgId}/helm/repositories/charts".replace("{orgId}", orgId);` |
| `HelmReleaseDeleteTool` | `ADMIN_ONLY` | `deploy` | `WRITE` | `custom` | `` |
| `HelmReleaseHistoryTool` | `PUBLIC` | `deploy` | `READ` | `fixed-query` | `String path = "/api/{orgId}/helm/releases".replace("{orgId}", orgId);` |
| `HelmReleaseListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/{orgId}/helm/releases".replace("{orgId}", orgId);` |
| `HelmRepoAddTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/helm/repo";` |
| `HelmRepoListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/{orgId}/helm/repositories".replace("{orgId}", orgId);` |
| `ImageDeleteTool` | `ADMIN_ONLY` | `deploy` | `WRITE` | `custom` | `` |
| `ImageDetailByNameTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/{orgId}/image/name".replace("{orgId}", orgId);` |
| `ImagePullTool` | `PUBLIC` | `deploy` | `WRITE` | `custom` | `String path = "/api/{orgId}/image/pull".replace("{orgId}", orgId);` |
| `ImageRepositoryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/{orgId}/image/repository".replace("{orgId}", orgId);` |
| `InboxMessageListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/message";` |
| `IngressQueryTool` | `PUBLIC` | `network` | `READ` | `custom` | `String path = "/api/" + orgId + "/dashboard/deployment";` |
| `JobTemplateListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/train-job-template";` |
| `LdapConfigListTool` | `PUBLIC` | `rbac` | `READ` | `fixed-query` | `String path = "/api/{orgId}/ldap".replace("{orgId}", orgId);` |
| `MigConfigListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/migConfig";` |
| `ModelListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/{orgId}/model".replace("{orgId}", orgId);` |
| `MpiJobAbortTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `` |
| `MpiJobDetailTool` | `PUBLIC` | `deploy` | `READ` | `fixed-query` | `String path = "/api/{orgId}/mpi-job".replace("{orgId}", orgId);` |
| `MpiJobListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/{orgId}/mpi-job".replace("{orgId}", orgId);` |
| `MpiJobSubmitTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/mpi-job/submit";` |
| `NamespaceListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/namespace";` |
| `NetworkQueryTool` | `PUBLIC` | `network` | `READ` | `custom` | `String path = "/api/" + orgId + "/dashboard/deployment";` |
| `NimCreateTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `` |
| `NodeAllocationTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/{orgId}/node/organization/allocation".replace("{orgId}", orgId);` |
| `NodeDetailTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/{orgId}/node".replace("{orgId}", orgId);` |
| `NodeMetricsTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/node";` |
| `NodeQueryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/node";` |
| `OrderListTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/lease/order";` |
| `PermissionMenuListTool` | `PUBLIC` | `rbac` | `READ` | `fixed-query` | `String path = "/api/{orgId}/permission/menu".replace("{orgId}", orgId);` |
| `PodQueryTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/" + orgId + "/pod";` |
| `PytorchJobListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/pytorch-job";` |
| `PytorchJobSubmitTool` | `AUTHENTICATED` | `deploy` | `WRITE` | `custom` | `String path = "/api/" + orgId + "/pytorch-job/submit";` |
| `QuotaMyListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/quota/my";` |
| `QuotaReceiveListTool` | `PUBLIC` | `rbac` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/quota/receive";` |
| `RegistryListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/registry";` |
| `ResourceMonitorTool` | `PUBLIC` | `query` | `READ` | `custom` | `String path = "/api/" + orgId + "/resource";` |
| `ResourcePresetListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/resource-preset";` |
| `ResourceUsageListTool` | `PUBLIC` | `query` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/resource";` |
| `RoleAssignableListTool` | `PUBLIC` | `rbac` | `READ` | `fixed-query` | `String path = "/api/{orgId}/role/assignable".replace("{orgId}", orgId);` |
| `RoleEditableListTool` | `PUBLIC` | `rbac` | `READ` | `fixed-query` | `String path = "/api/{orgId}/role/editable".replace("{orgId}", orgId);` |
| `RoleQueryTool` | `PUBLIC` | `rbac` | `READ` | `custom` | `String path = "/api/" + orgId + "/role";` |
| `ServiceQueryTool` | `PUBLIC` | `query` | `READ` | `fixed-query` | `String path = "/api/" + orgId + "/dashboard/resources";` |
| `SlurmClusterListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/bcm/slurm-cluster";` |
| `SlurmNodeListTool` | `PUBLIC` | `deploy` | `READ` | `standard-list` | `String path = "/api/" + orgId + "/slurm-node";` |

> 仅展示前 80 个，完整数量 92。后续治理优先基类/合并层，避免逐 Tool 手改。

## 5. M5.5 初步假设（待专家会诊确认）
1. 安全上，`organizationId/orgId` 属于**保留上下文字段**，不应由 LLM Action 或用户 Tool 参数覆盖。
2. Tool path 的 orgId 应优先来自 `UserPermissionContext.CURRENT_ORG_ID` / 已登录 SessionData，而不是 params。
3. 为避免一次性破坏 152 个测试基线，先做小样本：选择一个 READ 列表 Tool + 一个 WRITE Tool + ReAct 参数合并测试。
4. 如专家认为需要兼容无会话单测，可在测试中显式 bind ThreadLocal，而不是继续把 `organizationId` 当业务参数。

## 6. M5.5 落地结论

- `BaseTool#resolveOrganizationId(params)` 已收口为 ThreadLocal/session 权威来源，不再信任 params 中的 `organizationId/orgId`。
- ReAct 参数合并层、Graph `tool_call` 层、Graph `delegate` 子图层均已加入受保护上下文字段治理。
- 第一次独立 Review 发现的 `GpuQueryTool`、`ClusterOverviewTool`、`ImageQueryTool` 绕过 BaseTool 漏口已修复并测试化。
- 验证结果：M5 回归 28 tests 通过，全量 Maven 161 tests 通过，二次独立 Review PASS。

## 7. 后续专项候选

- M5.6 建议专门审计 `fallbackOrgId` 是否可继续作为系统可信上下文。
- M5.6 建议统一治理 `AtlasAsyncConfig`、`AsyncContextHolder`、旧 `/chat/graph` 入口的 orgId 异步传播语义。

