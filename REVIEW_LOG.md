# kube-agent REVIEW_LOG

> 本文件记录阶段性开发闭环：问题背景、解决方案、测试结果、代码 Review、风险与后续计划。

## 2026-05-26 16:40 - M5.5 Schema 扩面 HOLD 边界固化：敏感身份/RBAC 与非普通列表固定查询

### 背景
- M5.3 已给一批安全只读列表 Tool 增加 `page/limit` 或 `page/limit/keyword` 参数契约。
- 继续扫描剩余固定 `page=1, limit=100` Tool 时发现候选混杂：用户/账号/RBAC、Dashboard 固定摘要、指标摘要、详情/历史查询、全局 GPU 映射等。
- 若继续机械扩面，会把敏感读取或摘要/详情接口误扩成可翻页、可搜索、可枚举的普通列表入口。

### 专家会诊结论
1. 测试/质量门禁专家有效返回：下一批必须先补 HOLD 反向保护，形成 `spec 声明 + doExecute 透传/忽略 + prompt/HOLD` 的闭环；不能只声明 schema。
2. 安全/RBAC 与 schema 子会诊因子代理慢调用超时，未形成有效报告；本轮按保守策略处理，不将用户/RBAC/全局资源/Dashboard/详情指标类纳入普通列表扩面。
3. 现场源码扫描确认：`UserQueryTool` 已标注 `SENSITIVE_READ + requiresConfirmation=true`；`UserManagementTool/UserDetailTool` 虽尚未统一补齐生产风险注解，但同属身份管理读取域，本轮按保守策略纳入 HOLD；`HelmReleaseHistoryTool/MpiJobDetailTool` 是指定对象历史/详情语义；`NodeMetricsTool/GpuMetricsTool/GpuMapDetailTool/NodeAllocationTool` 是指标/映射/分配摘要语义。

### 变更内容
- `SensitiveListToolHoldContractTest`：新增身份/RBAC HOLD 保护：
  - `UserQueryTool` → 用户账号列表敏感读取；同时移除隐藏 `page/pageSize` 类型声明，避免入口层消费分页参数。
  - `UserManagementTool` → 用户管理列表敏感读取
  - `UserDetailTool` → 用户详情敏感读取
- 新增 `NonListFixedQueryHoldContractTest`：固化非普通列表固定查询 HOLD：
  - `NodeMetricsTool`
  - `GpuMetricsTool`
  - `GpuMapDetailTool`
  - `NodeAllocationTool`
  - `HelmReleaseHistoryTool`
  - `MpiJobDetailTool`
- 新测试同时验证：
  - 不声明 `page/limit/keyword` 标准列表三件套；
  - 不通过 `name/search/kw` 等 alias 暴露搜索入口；
  - 身份/RBAC 与非普通列表执行层均忽略调用方注入的 `page=9/limit=999/keyword/name/search/kw`，仍只向 kube-manager 下发固定 `page=1, limit=100`。

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| HOLD 定向契约 | `mvn -q -Dtest=SensitiveListToolHoldContractTest,DashboardFixedQueryHoldContractTest,NonListFixedQueryHoldContractTest test` | ✅ PASS |
| Review 后补强定向 | `mvn -q -Dtest=SensitiveListToolHoldContractTest,NonListFixedQueryHoldContractTest test` | ✅ PASS |
| Schema/Prompt/HITL 相关回归 | `mvn -q -Dtest=SensitiveListToolHoldContractTest,DashboardFixedQueryHoldContractTest,NonListFixedQueryHoldContractTest,ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,HomeInfoPublicPageLimitContractTest,ToolRegistryPromptContractTest,M4Px4ToolParameterAliasContractTest,M513HitlFailClosedContractTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | Python added-line scan | ✅ secret_suspects=0 |

### 代码 Review
#### 优点
- 本轮没有贸然扩大生产 Tool 参数面，而是先固化红线，符合“先实验再铺开”和安全优先策略。
- 新增测试覆盖执行层反向保护，避免出现“不声明 schema 但 doExecute 偷偷消费调用方分页/搜索参数”的隐患。
- 对身份/RBAC、详情/历史、指标摘要三类语义做了区分，避免把所有 `Map.of(page, limit)` 误判为普通列表。

#### 风险
- 部分指标/详情类未来可能确实需要专用 schema（如 `release/id/nodeName`），当前 HOLD 只是防误扩面，不代表永久不开放。
- `GpuMapDetailTool` 使用 `/api/gpu/all/gpu-map`，属于全局视角；后续若开放参数必须先确认后端权限与脱敏边界。

### 根因与解决方案
- 根因：历史 Tool 中大量固定分页查询的代码形态相似，但业务语义差异很大，不能通过正则/批量脚本直接套普通列表契约。
- 解决：先把敏感域与非普通列表固定查询纳入 HOLD 合同测试，再选择真正低风险的普通列表小批次扩面。

### 后续建议
1. 下一轮只从已确认普通资源列表中选择 3~6 个 Tool 做 schema 扩面，并同步 pass-through 测试。
2. 对详情类单独设计 `id/release/name` 等细粒度参数契约，不复用 `keyword`。
3. 对指标摘要类评估是否采用 page/limit-only，必须先确认是否会扩大资源拓扑枚举面。


## 2026-05-24 17:55 - M5.17 Tool HTTP/风险元数据第四批基础设施 GET/READ 扩面

### 背景
- M5.16 后 declared=43、READ=40，仍有大量历史 Tool 缺少 `httpMethod/apiEndpoints/operationType`。
- M5.13 fail-closed 已成为安全底座；UNKNOWN Tool 会被保守拦截，因此低风险只读 Tool 需要继续分批治理。
- 用户要求继续 M5，本轮按“专家会诊前置 + 先实验再铺开”选择基础设施运行态查询小批次，不直接进入 MCP/Memory/Observability。

### 专家会诊结论
1. 安全专家：本批仅纳入纯 `httpClient.get(...)`、无写入/删除/审批/下载导出、非 RBAC/LDAP/用户/组织/配额敏感管理域的 Tool；GET 不是 READ 的同义词。
2. 源码契约/测试专家：必须精确声明 endpoint，并增加 M5.17 白名单测试，尤其 dashboard 近似路径不能按 Tool 名称臆造。
3. 架构推进专家：MCP 暴露外部协议之前必须先完成 Tool 风险元数据治理，否则会把 UNKNOWN Tool 风险放大。

### 变更内容
- 第四批补充 15 个 GET/READ Tool 元数据：
  - `ClusterQueryTool` → `GET /api/{orgId}/hpc-job/cluster`
  - `NodeQueryTool` → `GET /api/{orgId}/node`
  - `NodeMetricsTool` → `GET /api/{orgId}/node`
  - `GpuQueryTool` → `GET /api/{orgId}/node/all/gpu-map`
  - `GpuMetricsTool` → `GET /api/{orgId}/node/all/gpu-map`
  - `NetworkQueryTool` → `GET /api/{orgId}/dashboard/deployment`
  - `PodQueryTool` → `GET /api/{orgId}/pod`
  - `DaemonSetQueryTool` → `GET /api/{orgId}/dashboard/deployment`
  - `DeploymentQueryTool` → `GET /api/{orgId}/deployment`
  - `ServiceQueryTool` → `GET /api/{orgId}/dashboard/resources`
  - `IngressQueryTool` → `GET /api/{orgId}/dashboard/deployment`
  - `ResourceMonitorTool` → `GET /api/{orgId}/resource`
  - `ResourcePresetListTool` → `GET /api/{orgId}/resource-preset`
  - `SlurmClusterListTool` → `GET /api/{orgId}/bcm/slurm-cluster`
  - `SlurmNodeListTool` → `GET /api/{orgId}/slurm-node`
- `M511AtlasToolHttpContractTest`：新增 `m517InfrastructureReadEndpoints_shouldMatchReviewedWhitelist`，复用 `verifyExpectedReadEndpoint(expected, milestone, violations)`，区分 M5.16/M5.17 错误码前缀。
- 新增 `docs/m5/M5.17_tool_metadata_read_expansion_notes_20260524.md` 记录会诊结论、风险边界和验收标准。

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| M511 HTTP 元数据契约 | `mvn -q -Dtest=M511AtlasToolHttpContractTest test` | ✅ PASS |
| HITL/风险定向回归 | `mvn -q -Dtest=M513HitlFailClosedContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 全量测试 | `mvn -q test` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | Python 静态扫描 | ✅ secret_suspects=0 |
| 覆盖率脚本 | Python 静态统计 | ✅ total=110, declared=58, read=55 |

### 覆盖率变化
- Tool 总数：110。
- 已声明 HTTP 元数据：58（M5.16 后为 43，本轮新增 15）。
- READ 白名单：55（M5.16 后为 40，本轮新增 15）。
- 未迁移 Tool 仍由 UNKNOWN / fail-closed 保护。

### 代码 Review
#### 优点
- 生产代码只补充注解元数据，不改变真实业务执行逻辑。
- 新增 M5.17 endpoint 精确白名单测试，避免只校验 endpoint 非空导致路径错误漏检。
- 批次选择遵循安全边界，暂缓敏感管理域与写删动作。

#### 风险
- 节点、Pod、Deployment、GPU、Slurm 等运行态信息会暴露资源拓扑，仍依赖后端 org 隔离、权限校验与审计。
- `resource_preset_list` 虽为只读，但可能涉及资源规格策略；后续若返回敏感策略需升级为敏感 READ。
- `NetworkQueryTool`、`IngressQueryTool`、`DaemonSetQueryTool` 当前使用 dashboard/deployment 近似路径，契约测试锁定的是现状，不代表后端已有专用 API。

### 根因与解决方案
- 根因：历史 Tool 没有静态 HTTP/风险元数据，MCP/外部协议化前无法形成可靠安全边界。
- 解决：继续小批次人工会诊，选择路径清晰且无副作用的基础设施 GET Tool，声明 `GET + endpoint + READ`，并用源码契约测试锁定。

### 后续建议
1. M5.18：治理敏感 GET / SENSITIVE_READ 语义，重点 RBAC、LDAP、用户、组织、配额、文件、日志等域。
2. M5.19：治理 POST/DELETE/ACTION 高风险 Tool，补 `requiresConfirmation=true` 与命令式确认规则。
3. M5.20：在 Tool 风险元数据稳定后再启动 MCP Server 第一版。

## 2026-05-24 14:20 - M5.16 Tool HTTP/风险元数据第三批 GET/READ 扩面与 endpoint 精确契约

### 背景
- M5.14/M5.15 已将低风险 GET/READ Tool 元数据覆盖推进到 declared=28、READ=25，但仍有大量历史 Tool 缺少 `httpMethod/apiEndpoints/operationType`。
- M5.13 HITL fail-closed 使 UNKNOWN Tool 默认被保守拦截；为了改善只读查询体验，需要继续按“专家会诊前置 + 先实验再铺开”扩面。
- 本轮脚本初筛发现 15 个路径明确、纯 `httpClient.get(...)`、无写调用、非 RBAC 的候选，同时排除了路径动态不清、file/storage 数据读取、RBAC/user/quota/download/upload 等敏感项。

### 专家会诊结论
1. 安全专家：15 个候选均可纳入 READ；但 `log_query`、云资源/节点/Deployment 详情、Helm 仓库列表属于“只读但可能敏感的元数据”，需在 Review 中记录依赖后端权限/脱敏/隔离。
2. 源码契约/测试专家：必须精确声明动态尾段 endpoint，尤其 `helm_release_history` 的 `/{release}/histories` 和 `mpi_job_detail` 的 `/{id}`；不要按 Tool 名称臆造 detail path。
3. 架构推进专家：M5.16 可一次推进 15 个，但必须同步增加 endpoint 精确白名单契约测试，否则 endpoint 写错会成为隐藏风险。
4. 旧 `API_ENDPOINTS_PATTERN` 对 `{orgId}` 会提前截断，这次测试先失败后修复，证明契约测试自身也需要随着元数据治理持续加固。

### 变更内容
- 第三批补充 15 个 GET/READ Tool 元数据：
  - `BareMetalAppListTool` → `GET /api/{orgId}/bare-metal-application`
  - `ComposeListTool` → `GET /api/{orgId}/compose`
  - `HelmChartInfoTool` → `GET /api/{orgId}/helm/charts/single`
  - `HelmChartSearchTool` → `GET /api/{orgId}/helm/repositories/charts`
  - `HelmReleaseHistoryTool` → `GET /api/{orgId}/helm/releases/{release}/histories`
  - `HelmReleaseListTool` → `GET /api/{orgId}/helm/releases`
  - `HelmRepoListTool` → `GET /api/{orgId}/helm/repositories`
  - `MpiJobDetailTool` → `GET /api/{orgId}/mpi-job/{id}`
  - `MpiJobListTool` → `GET /api/{orgId}/mpi-job`
  - `LogQueryTool` → `GET /api/log`
  - `CloudResourceListTool` → `GET /api/{orgId}/cloud`
  - `CurrencyQueryListTool` → `GET /api/{orgId}/currency`
  - `DeploymentDetailTool` → `GET /api/{orgId}/deployment`
  - `ImageDetailByNameTool` → `GET /api/{orgId}/image/name`
  - `NodeDetailTool` → `GET /api/{orgId}/node`
- `M511AtlasToolHttpContractTest`：
  - 新增 M5.16 endpoint 精确白名单测试，校验 15 个 Tool 的 `GET + READ + endpoint` 三元组；
  - 修复 `API_ENDPOINTS_PATTERN`，支持 endpoint 内部占位符，避免 `/api/{orgId}` 被截断为 `/api/{orgId`。

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| M511 HTTP 元数据契约 | `mvn -q -Dtest=M511AtlasToolHttpContractTest test` | ✅ PASS |
| HITL fail-closed 契约 | `mvn -q -Dtest=M513HitlFailClosedContractTest test` | ✅ PASS |
| 元数据/风险定向回归 | `mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 全量测试 | `mvn -q test` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | Python diff scan | ✅ secret_suspects=0 |
| 覆盖率脚本 | Python 静态统计 | ✅ total=110, declared=43, read=40 |

### 覆盖率变化
- Tool 总数：110。
- 已声明 HTTP 元数据：43（M5.15 后为 28，本轮新增 15）。
- READ 白名单：40（M5.15 后为 25，本轮新增 15）。
- 未迁移 Tool 仍由 UNKNOWN / fail-closed 保护。

### 代码 Review
#### 优点
- 生产代码只补充注解元数据和两处 JavaDoc endpoint 精确说明，不修改业务执行逻辑。
- 本轮同步补 endpoint 精确白名单测试，解决了旧契约“只校验 endpoint 非空，不校验是否写对”的盲区。
- 动态路径 `/{release}/histories`、`/{id}` 已被测试锁住，避免列表/详情路径混淆。
- `API_ENDPOINTS_PATTERN` 修复后支持 `{orgId}` 等占位符，契约测试更贴合项目实际写法。

#### 风险
- `LogQueryTool` 读取日志，可能暴露敏感运行数据；当前仍按只读查询处理，但依赖后端权限、脱敏和限流。
- `CloudResourceListTool`、`NodeDetailTool`、`DeploymentDetailTool` 属于资产/拓扑/运行元数据读取，必须依赖 org 隔离。
- `HelmRepoListTool` 不得返回仓库凭据；如果后端返回 secret/token，需要后续提高风险等级。
- 大量 B_REVIEW_PATH Tool 路径仍不够明确，暂不纳入 READ，需要后续逐个源码确认或 AST 提取。

### 根因与解决方案
- 根因 1：历史低风险 GET Tool 缺少风险元数据，M5.13 fail-closed 下无法进入 READ 白名单。
  - 解决：经三方专家会诊确认后，第三批选择 15 个路径明确、无写调用、非敏感管理域的 Tool 补 `GET + endpoint + READ`。
- 根因 2：旧 endpoint 正则无法处理 endpoint 字符串内部的 `{orgId}` 占位符，导致精确测试误判。
  - 解决：改为匹配 endpoint 数组内的双引号字符串，支持占位符花括号。
- 根因 3：原有契约测试不校验 endpoint 精确性，动态尾段可能写漏。
  - 解决：新增 M5.16 endpoint 白名单测试锁定 15 个已审查 endpoint。

### 后续建议
1. 对 B_REVIEW_PATH 的 31 个路径动态 Tool 做 AST/源码级路径还原，不要依赖简单正则盲猜。
2. 对 file/storage/download/upload/RBAC/user/quota 等敏感域建立“敏感 READ / ACTION / requiresConfirmation”专门批次。
3. 若继续扩大覆盖率，建议将 `M511AtlasToolHttpContractTest` 从正则扫描升级为 JavaParser/AST Analyzer。

## 2026-05-24 11:20 - M5.15 Tool HTTP/风险元数据第二批 GET/READ 扩面收尾

### 背景
- M5.14 已完成首批 10 个 GET/READ Tool 元数据扩面，但 `ToolRegistry` 中仍有大量历史 Tool 未声明 HTTP/风险元数据。
- M5.13 HITL fail-closed 已成为执行层安全边界：只有明确 `operationType=READ` 且 `requiresConfirmation=false` 的 Tool 可直接执行。
- 用户要求继续按“专家会诊前置、先实验再铺开”推进，避免把敏感 GET、下载导出、写操作或 admin-only 能力误标为免确认 READ。

### 专家会诊结论
1. 第二批只迁移低风险 GET/READ 查询 Tool；READ 是免确认白名单，不能机械等价于 HTTP GET。
2. 对 dashboard、系统信息、GPU、命名空间、节点分配、镜像仓库、模型列表、裸金属模板查询等只读能力可小批推进。
3. 全量测试中两个失败并非本轮生产 Tool 注解引入，而是 M5.13 fail-closed 后测试内存 Tool 缺少风险元数据，被守卫按 UNKNOWN 正确拦截。
4. 修复测试失败的最佳方案是补测试夹具 READ 元数据；禁止修改生产默认构造器让 metadata=null 放行，也不应把成功路径断言改成接受 blocked。

### 变更内容
- 第二批补充 13 个 GET/READ Tool 元数据：
  - `DashboardDeploymentCountTool` → `GET /api/{orgId}/dashboard/deployment/count`
  - `DashboardImageCountTool` → `GET /api/{orgId}/dashboard/image/count`
  - `DashboardEasyFlowTool` → `GET /api/{orgId}/dashboard/easy-flow`
  - `SysInfoMapTool` → `GET /api/public/sys-info/all/map`
  - `SysModelListTool` → `GET /api/model`
  - `GpuGlobalListTool` → `GET /api/gpu`
  - `GpuMapDetailTool` → `GET /api/gpu/all/gpu-map`
  - `GpuDetailListTool` → `GET /api/{orgId}/gpu-detail`
  - `NamespaceQueryTool` → `GET /api/namespace`
  - `NodeAllocationTool` → `GET /api/{orgId}/node/organization/allocation`
  - `ImageRepositoryTool` → `GET /api/{orgId}/image/repository`
  - `ModelListTool` → `GET /api/{orgId}/model`
  - `BareMetalTemplateTool` → `GET /api/bare-metal-config-template`
- `AtlasToolCallbackTest`：为测试专用 `RecordingTool` 构造安全 READ `ToolMetadata`，保留 HITL fail-closed，同时验证 alias 归一化确实进入 `BaseTool.execute`。
- `ReActEngineMultiStepE2ETest`：新增带 READ 注解的测试子类 `PodStatusRecordingTool`、`EventQueryRecordingTool`，恢复 ReAct 两轮工具 + Final Answer 成功路径。

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| 失败用例复跑 | `mvn -q -Dtest=AtlasToolCallbackTest,ReActEngineMultiStepE2ETest test` | ✅ PASS |
| HITL fail-closed 契约 | `mvn -q -Dtest=M513HitlFailClosedContractTest test` | ✅ PASS |
| 元数据/风险定向回归 | `mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 全量测试 | `mvn -q test` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 覆盖率脚本 | Python 静态统计 | ✅ total=110, declared=28, read=25 |

### 覆盖率变化
- Tool 总数：110。
- 已声明 HTTP 元数据：28（M5.14 后为 15，本轮新增 13）。
- READ 白名单：25。
- 未迁移 Tool 仍由 UNKNOWN / fail-closed 保护。

### 代码 Review
#### 优点
- 生产变更仅限 13 个 Tool 注解元数据，不改业务执行逻辑、不触发真实 kube-manager 数据面请求。
- endpoint 与源码中 `String path` / `httpClient.get(...)` 的 GET 路径一致，风险语义清晰。
- 测试修复没有绕过 HITL，而是让测试夹具显式声明 READ 元数据，安全边界保持收紧。
- 全量测试已从 2 个 fail-closed 相关失败恢复为全绿。

#### 风险
- `bare_metal_template` 隶属 deploy agent，虽实际为只读模板查询，但后续 deploy 域 Tool 需要继续逐个确认，不能整体放宽。
- 源码级元数据契约仍依赖正则扫描，随着迁移规模扩大可考虑 JavaParser/AST Analyzer。
- 剩余未迁移 Tool 数量仍较多，用户体验上可能继续出现安全拦截，需要继续分批治理。

### 根因与解决方案
- 根因 1：历史 Tool 缺少 `httpMethod/apiEndpoints/operationType`，M5.13 fail-closed 下无法进入 READ 白名单。
  - 解决：经专家会诊后选取第二批低风险 GET/READ Tool，逐个补充注解元数据。
- 根因 2：测试内存 Tool 没有生产注解或测试专用 `ToolMetadata`，在 M5.13 后被 HITL 守卫按 UNKNOWN 拦截，导致成功路径单测不再真正执行 Tool。
  - 解决：为测试夹具显式提供 READ 元数据，恢复测试目标，同时不改变生产 fail-closed。

### 后续建议
1. 继续按 10～15 个/批推进剩余低风险 READ Tool，优先普通查询和前端高频展示类。
2. 对导出/下载、审批、配额、写操作、删除操作建立单独高风险批次，默认 `requiresConfirmation=true`。
3. 若第三批后元数据数量继续扩大，评估将 `M511AtlasToolHttpContractTest` 的源码扫描升级到 AST 级别。


## 2026-05-24 00:45 - M5.14 Tool HTTP/风险元数据首批 GET/READ 扩面治理

### 背景
- M5.11 建立了 `@AtlasToolMapping` HTTP/风险元数据契约小样本，但历史 Tool 大量仍未声明 `httpMethod/apiEndpoints/operationType`。
- M5.12/M5.13 已分别完成风险透明化与 HITL fail-closed 强拦截；未迁移 Tool 会继续被保守拦截，但低风险只读 Tool 也无法精确进入 READ 白名单。
- 用户要求“先实验再铺开”，且 READ 标注等价于免 HITL 白名单，不能机械地把所有 GET 批量标 READ。

### 专家会诊结论
1. M5.14 应先加固契约测试，再做首批低风险 GET/READ 小样本迁移。
2. HTTP GET 只能作为 READ 候选信号，不能作为 READ 判定依据；READ 必须证明无副作用、无敏感泄露、权限边界清楚。
3. POST/DELETE/ACTION、下载导出、敏感 admin-only、占位 Tool 暂不纳入本批 READ 扩面。
4. 大量 UNKNOWN Tool 应继续由 M5.13 fail-closed 保护，不能为了覆盖率牺牲安全边界。

### 变更内容
- `M511AtlasToolHttpContractTest`
  - 新增 `BASE_TOOL_FILE` 与 `EXTENDS_BASE_TOOL_PATTERN`。
  - 新增 `readVisibleClientFieldNames(...)`，支持未来 Tool 继承 `BaseTool` 后仍识别基类可见 `KubeManagerHttpClient` 字段。
  - 保持源码级静态测试，不启动 Spring、不访问真实 kube-manager。
- 首批补充 10 个 GET/READ Tool 元数据：
  - `HomeModelListTool` → `/api/public/home-info/model-list`
  - `HomeIndustryListTool` → `/api/public/home-info/industry-solutions`
  - `HomeNimListTool` → `/api/public/home-info/nim`
  - `HomeIndustryClassListTool` → `/api/public/home-info/industry-classification`
  - `HomeRepositoryListTool` → `/api/public/home-info/repository`
  - `QuotaMyListTool` → `/api/{orgId}/quota/my`
  - `ResourceUsageListTool` → `/api/{orgId}/resource`
  - `NamespaceListTool` → `/api/{orgId}/namespace`
  - `TableListTool` → `/api/{orgId}/table`
  - `ClusterOverviewTool` → `/api/{orgId}/dashboard/resources`

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| M511 契约测试 | `mvn -q -Dtest=M511AtlasToolHttpContractTest test` | ✅ PASS |
| 定向回归组合 | `mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest,M513HitlFailClosedContractTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 独立 Review | delegate_task | ✅ PASS，无 blocker |

### 覆盖率变化
- Tool 总数：110。
- 已声明 HTTP 元数据：15。
- 未声明 HTTP 元数据：95。
- GET/READ 白名单：12。

### 代码 Review
#### 优点
- 坚持小样本扩面，避免一次性把 80+ GET Tool 误放入免确认白名单。
- 契约测试先行，保护已迁移 Tool 的 HTTP 方法、endpoint、operationType 一致性。
- 本阶段仅改注解元数据和测试识别逻辑，不改业务执行路径，回归风险低。

#### 风险
- `EXTENDS_BASE_TOOL_PATTERN` 是源码正则，未来复杂继承声明可能需要 AST Analyzer。
- 本阶段未迁移剩余 95 个 Tool，历史 Tool 仍需继续分批治理。
- 部分 GET Tool 可能涉及敏感数据或管理面信息，后续不能机械迁移。

### 根因与解决方案
- 根因：M5.11 初始小样本只覆盖 5 个代表 Tool，M5.13 fail-closed 后，未声明 Tool 会被保守拦截；要提升体验与准确性，必须逐步扩大可信 READ 元数据覆盖。
- 解决：先通过专家会诊确立 READ 白名单边界，再加固契约测试，最后只迁移人工确认过的低风险 GET/READ 小样本。

### 后续建议
1. 继续按 10～15 个/批推进普通 GET/READ Tool，敏感 admin-only 与下载导出类单独审查。
2. 为 POST/DELETE/ACTION Tool 建立高风险迁移批次，确保 `requiresConfirmation=true`。
3. 若迁移规模继续扩大，将源码正则分析器升级为 JavaParser/AST Analyzer。


## 2026-05-23 23:45 - M5.13 HITL fail-closed 执行层强拦截前后端同步治理

### 背景
- M5.12 已完成 Tool 风险元数据透明化，但它不是安全边界，仍可能出现高风险 Tool 仅靠 Prompt/UI 提示执行的问题。
- 既有 `hitl_confirm` 节点只是返回确认文案，占位意义大于执行层强拦截。
- 用户要求所有功能前后端同步推进，且删除/修改类操作不做真实破坏性测试，只跑通逻辑和契约。

### 专家会诊 / 独立 Review 结论
1. HITL 安全边界必须下沉到每个真实 `tool.execute(...)` 前，不能只放在 Brain 决策或前端弹窗。
2. 只能信任后端 `HITLController` 在 `confirmToken` 校验成功后注入的 `HitlConfirmation`；不信 LLM 参数、前端字段或用户自然语言“已确认”。
3. `Graph tool_call`、`ReActEngine`、`AtlasOrchestrator` legacy fallback、`ToolCallback` 都是潜在直接执行入口，必须统一接入守卫。
4. clarify 与普通新会话必须显式清空确认 marker，避免旧确认继承。
5. confirm 后必须确保恢复链路进入可读取 `hitl_confirmation` 的 `supervisorGraph tool_call`，并复用已确认的 `CALL_TOOL` 决策，避免重新决策覆盖。

### 变更内容
#### 后端 kube-agent
- 新增 `HitlConfirmation`：服务端可信人工确认 marker。
- 新增 `HitlGuard`：基于 Tool 元数据执行 fail-closed 风险判定。
- `ToolRegistry` 增加元数据解析能力。
- `AtlasGraphConfig.supervisorGraph/tool_call` 在 `tool.execute` 前校验 `hitl_confirmation + HitlGuard`。
- `ReActEngine`、`AtlasOrchestrator` legacy fallback、`graph.bridge.AtlasToolCallback`、`tool.core.AtlasToolCallback` 均接入 `HitlGuard`。
- `HITLController` 改为注入 `@Qualifier("supervisorGraph")`；confirm 成功后注入 `HitlConfirmation`；clarify 路径显式清空 marker。
- `supervisorGraph` supervisor 节点优先复用 resume 注入的 `brain_decision`，保障确认后 `CALL_TOOL` 不被覆盖。
- 普通 Graph/Supervisor 新会话显式 `hitl_confirmation=null`。
- 新增 `M513HitlFailClosedContractTest`，覆盖多入口守卫、确认 marker、clarify 清理、确认后复用决策等契约。

#### 前端 kube-agent-vue
- `useChat.ts` 增强 confirm/clarify SSE 解析。
- `ChatView.vue` 对缺 `threadId/confirmToken` 的确认流 fail-closed，不调用确认接口。
- `ChatBubble.vue` 将风险文案改为“执行前确认”，与后端强拦截语义一致。
- 新增 `scripts/m513-hitl-contract-test.cjs` 保护前端确认流契约。

### 测试结果
| 项目 | 命令/方式 | 结果 |
|------|-----------|------|
| 后端定向契约 | `mvn -q -Dtest=M513HitlFailClosedContractTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 前端契约 | `node scripts/m513-hitl-contract-test.cjs` | ✅ PASS |
| 前端构建 | `npm run build` | ✅ PASS（Element Plus 依赖 Rollup 注释 warning，不阻塞） |
| 后端空白检查 | `git diff --check` | ✅ PASS |
| 前端空白检查 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | Python added-lines scan | ✅ `secret_suspects=0` |
| 独立 Review 第 1 轮 | delegate_task | ❌ 发现多入口绕过/clarify marker 继承风险，已修复 |
| 独立 Review 第 2 轮 | delegate_task | ❌ 发现确认后恢复可能重新决策覆盖，已修复 |
| 独立 Review 第 3 轮 | delegate_task | ✅ PASS |

### 代码 Review
#### 优点
- 安全边界从 UI/Prompt 下沉到执行层，符合 fail-closed 原则。
- 多执行入口统一接入 `HitlGuard`，降低未来绕过风险。
- 服务端可信 marker 与前端 fail-closed 同步设计，前后端语义一致。
- 源码契约测试覆盖了本阶段关键架构约束，且不触碰真实删除/修改类后端数据。

#### 风险
- 当前主要是源码契约/编译构建验证，尚未补运行时 mock 集成测试。
- 两个同名 `AtlasToolCallback` 类仍增加维护认知成本，后续建议合并或重命名。
- `HitlConfirmation` 未来可加强 threadId 维度校验，进一步收紧跨 checkpoint 边界。

### 根因与解决方案
- 根因：M5.12 只做风险透明化，旧 HITL 节点没有在 `tool.execute` 前建立硬边界；同时项目存在 Graph/ReAct/legacy/ToolCallback 多条执行路径。
- 解决：新增统一 `HitlGuard` 并接入所有已知执行入口；确认 marker 只由服务端 token 校验后注入；普通/clarify 路径显式清空；confirm 恢复链路固定到 `supervisorGraph` 并复用注入决策。

### 后续建议
1. 补充运行时 mock 集成测试，模拟完整 HITL confirm 放行链路。
2. 清理两个同名 `AtlasToolCallback` 类，降低维护误改概率。
3. 将 `threadId` 纳入 `HitlConfirmation`/`HitlGuard` 校验参数，进一步收紧边界。
4. 继续分批迁移剩余 Tool 的 HTTP/风险元数据，提升 guard 判定准确度。

## 2026-05-23 21:06 - M5.12 Tool 风险元数据透明化前后端同步治理

### 背景
- M5.11 已将 `httpMethod/apiEndpoints/operationType/requiresConfirmation` 写入 `@AtlasToolMapping` 小样本，但风险元数据尚未进入 Prompt、SSE 和前端时间线。
- 用户要求后续每个功能必须前后端同步推进，因此 M5.12 同时修改 kube-agent 后端与 kube-agent-vue 前端。
- 本阶段定位为“风险透明化”，不是执行层安全边界；真实 fail-closed HITL 拦截留到 M5.13。

### 专家会诊结论
1. 风险元数据应进入 ToolRegistry/Prompt，使 LLM 能区分 READ 与 ACTION/DELETE 等高影响操作。
2. Prompt 和前端不应展示 `apiEndpoints`，避免泄露内部 kube-manager 路径或诱导模型绕过 Tool。
3. SSE 不新增顶层协议字段，复用已有 `metadata` 扩展位，降低前端兼容风险。
4. UI 必须明确“建议确认/占位能力/高风险删除”只是透明化提示，不应让用户误以为已有强制拦截。

### 变更内容
#### 后端 kube-agent
- `ToolRegistry.ToolMetadata` 增加风险字段并从 `@AtlasToolMapping` 初始化。
- `buildSystemPromptForCurrentUser()` 增加 `风险标签` 行，并加入 M5.12 非强拦截说明。
- `ReActEvent` 增加带扩展元数据的 `toolStart/toolDone` 重载。
- `ReActEngine` 在 `tool_start/tool_done` 中透传 `httpMethod/operationType/requiresConfirmation`。
- 新增/扩展测试：
  - `ToolRegistryPromptContractTest#buildSystemPrompt_shouldExposeRiskMetadataWithoutLeakingApiEndpoints`
  - `ReActEventRiskMetadataTest`

#### 前端 kube-agent-vue
- `src/types/index.ts` 新增 `ToolRiskMetadata/ToolOperationType`。
- `src/components/ChatBubble.vue` 在 ReAct 时间线展示风险 chip、建议确认标签与 DELETE/PLACEHOLDER 提示。

### 测试结果
- 后端定向测试：`mvn -q -Dtest=ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → PASS。
- 前端构建：`npm run build` → PASS（`vue-tsc && vite build`）。
- 后端 `git diff --check` → PASS。
- 前端 `git diff --check` → PASS。

### 代码 Review
#### 优点
- 前后端同步落地，符合新开发铁律。
- 复用 metadata，不破坏既有 SSE 协议。
- Prompt/前端均不展示 endpoint，降低信息泄露面。
- 测试覆盖了 Prompt 风险标签、endpoint 不泄露、SSE metadata 合并保留 `params/costMs`。

#### 风险
- M5.12 不是强安全边界，高风险 Tool 仍需 M5.13 在执行层 fail-closed。
- 当前只基于 M5.11 小样本 Tool 展示准确风险；未迁移 Tool 仍需继续治理。
- 前端 chip 只展示后端已推送 metadata 的事件；旧服务/旧事件不会补全历史风险信息。

### 后续建议
1. M5.13 实现真正 HITL 执行层强拦截：`requiresConfirmation=true` 的 Tool 必须收到有效确认 token 后才能执行。
2. 分批迁移剩余 Tool 的 HTTP/风险注解元数据。
3. 在状态大盘中增加 Tool 风险覆盖率统计：已声明/未知/高危需确认数量。


## 2026-05-20 23:10 - ReAct Tool 参数契约第一批扩展 + URL 查询参数修复

### 背景
- 已完成 `ToolParameterSpec`、`ToolInputSchemaBuilder`、schema-first `ToolParameterNormalizer`、`ReActPromptBuilder/ToolRegistry` 工具目录参数契约接入。
- 本轮按“先实验再铺开”原则，不批量修改全部 Tool，只选择第一批 3 个诊断/查询 Tool 验证契约扩展路径：
  - `log_query`
  - `deployment_detail`
  - `node_detail`

### 变更内容
1. `LogQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`podName`、`namespace`、`lines`。
   - aliases：兼容 `pod_name`、`pod`、`targetName`、`keyword`、`ns`、`tailLines` 等历史/LLM 输出字段。

2. `DeploymentDetailTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数保持为当前执行逻辑读取的 `name`，description 明确限定为 Deployment/实例名称。
   - aliases 支持 `deploymentName`、`instanceName`、`targetName` 等。
   - 修复旧代码将 `?name=...` 手拼到 path，导致 `KubeManagerHttpClient` URI 编码为 `%253F` 的问题；改为统一放入 query map。

3. `NodeDetailTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数保持为当前执行逻辑读取的 `name`，description 明确限定为 Kubernetes Node 节点名称。
   - aliases 支持 `nodeName`、`hostName`、`targetName` 等。
   - 同步修复 `?name=...` 手拼 path 的潜在 `%253F` 编码问题。

4. 测试补充
   - `ToolParameterNormalizerTest`
     - 新增第一批 3 个 Tool 的 schema-first alias 归一化测试。
   - `ToolRegistryPromptContractTest`
     - 新增 prompt contract 测试，确保工具目录展示 canonical 参数，不展开 aliases。

### 测试结果
- 目标单测：
  - 命令：`mvn -Dtest=ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest test`
  - 结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

- 编译打包：
  - 命令：`mvn -DskipTests package`
  - 结果：BUILD SUCCESS。

- 服务健康检查：
  - 启动端口：8500。
  - 命令：`curl http://localhost:8500/actuator/health`
  - 结果：`{"status":"UP"}`。

- 真实 SSE E2E：
  1. 登录账号：`zhaotiandi / ninePwd!`，返回 `organizationId=100002`。
  2. 查询：`查询部署实例 aaaa 的详情`
     - SSE 返回 `event:done`。
     - 内容返回：`未查询到部署实例 aaaa 的相关详情信息。`
     - 服务日志确认调用：`[HTTP GET] /api/100002/deployment 参数={limit=100, name=aaaa, page=1}`。
     - 已确认 `%253F` 编码问题消失。
  3. 查询：`查看 pod nginx-not-exist-schema 最近 50 行日志`
     - SSE 返回 `event:done`。
     - 服务日志确认调用：`[HTTP GET] /api/log 参数={organizationId=100002, podName=nginx-not-exist-schema, keyword=nginx-not-exist-schema, lines=50, userId=zhaotiandi}`。

### 代码 Review
#### 优点
- 保持“小样本验证”策略，未盲目批量改 109 个 Tool。
- schema canonical 字段严格贴合当前 Tool 实际读取字段，避免 Prompt 契约与执行逻辑脱节。
- description 明确限定 `name` 的资源类型，降低 LLM 在 `name` 字段上的跨资源误填概率。
- 修复了真实 E2E 才暴露的 URL 查询参数拼接问题，提高 `deployment_detail` 与 `node_detail` 后端调用可靠性。

#### 风险
- `deployment_detail` 和 `node_detail` 仍共用 canonical `name`，长期看不如 `deploymentName/nodeName` 类型安全；当前为兼容旧执行逻辑的保守选择。
- `log_query` 的后端接口仍使用 `keyword` 语义，`podName` 与日志关键字存在一定耦合；后续如后端支持明确 podName 参数，可进一步收敛。
- 本轮 E2E 走的是 `CALL_TOOL -> tool_call` 简单路径，不是完整 `DELEGATE_REACT` 多步链路；参数契约对 ReAct Prompt 的收益已由 prompt contract 单测覆盖，但仍建议后续补专门 ReAct 多步 E2E。

### 后续建议
1. 继续按批次扩展 ToolParameterSpec：优先处理 detail/query/diagnose 类 Tool。
2. 后续独立重构 detail 类 Tool 的 canonical 字段：从 `name` 演进为资源类型明确的 `deploymentName/nodeName/podName`，并同步修改执行逻辑。
3. 增加 URL 参数构造专项扫描，查找所有 `path += "?"` 写法，统一改为 query map。
4. 补充完整 ReAct 多步链路 E2E，验证工具目录参数契约是否稳定提升 LLM 工具调用准确率。

## 2026-05-20 23:45 - 文档里程碑重对齐 + URL query 拼接专项清理

### 背景
- 用户要求“更新文档，然后继续推进”。
- 文档层面：`ROADMAP.md` 仍停留在 M1.5 旧基线，`CHANGELOG.md` 未记录 M3.2 ReAct 与 M4.1 Tool Schema，`docs/会话上下文快照_20260520.md` 仍把 ReAct 核心文件列为待创建。
- 代码层面：上一轮 E2E 已发现 `path += "?name="` 可能被 URI builder 编码为 `%253F`，扫描后剩余 4 个同类风险点。

### 专家会诊结论
1. 文档必须对齐到当前真实状态：M3.2 ReAct MVP 已落地，M4.1 Tool Schema 参数契约分批铺开中。
2. `CHANGELOG.md` 应补 `[M3.2]` 与 `[M4.1]`，不能继续写 M3/M4 待启动。
3. `docs/会话上下文快照_20260520.md` 应从 `fdd8c42` 更新到 `c296a3c`，并把“新建 ReActEngine”等待办改为已完成归档。
4. URL query 构造应统一改为 `httpClient.get(path, queryMap)`，禁止手拼 `?xxx=`。

### 本轮代码变更
修复 4 个剩余 URL query 拼接点：

| 文件 | 原字段 | 修复方式 |
|------|--------|----------|
| `HelmChartSearchTool.java` | `?keyword=` | `query.put("keyword", kwParam.toString())` |
| `HelmChartInfoTool.java` | `?chart=` | `query.put("chart", chartParam.toString())` |
| `ImageDetailByNameTool.java` | `?name=` | `query.put("name", nameParam.toString())` |
| `FileSelectStorageTool.java` | `?name=` | `query.put("name", nameParam.toString())` |

所有文件保留原有 `page=1`、`limit=100` 行为，并使用 `LinkedHashMap` 保持参数构造清晰可审计。

### 本轮文档变更
- 重写 `ROADMAP.md`：对齐当前基线为“M3.2 ReAct MVP 已打通；M4.1 Tool Schema 参数契约分批铺开中”。
- 重写 `CHANGELOG.md`：新增 M3.2 ReAct 与 M4.1 Tool Schema 章节。
- 更新 `docs/会话上下文快照_20260520.md`：从旧 `fdd8c42` 快照更新到当前 `c296a3c` 之后的真实状态。
- 追加当前 `REVIEW_LOG.md` 记录。

### 静态验证
- 命令：`grep -RIn 'path += "?' src/main/java/com/atlas/tool/impl || true`
- 结果：无输出。
- 命令：`grep -RIn '\?name=|\?chart=|\?keyword=' src/main/java/com/atlas/tool/impl || true`
- 结果：无输出。

### 代码 Review
#### 优点
- 修复范围小且明确，只处理 4 个已扫描出的风险点。
- 不改变业务参数语义，不改变必填参数校验，不改变分页默认行为。
- 统一消除 URL query injection 和 `%253F` 编码风险。
- 文档同步反映真实工程进度，避免 ROADMAP/CHANGELOG/会话快照继续误导后续开发。

#### 风险
- 本轮 URL 修复仍需编译、重启和真实 SSE E2E 验证。
- Helm 相关接口可能因后端 Helm 服务未连接返回业务失败；本轮验收重点是 path/query 分离，不以 Helm 业务数据是否存在为唯一标准。
- 里程碑全景图与 `PROJECT_ATLAS_V3.md` 仍建议后续继续做深度重对齐。

### 后续建议
1. 执行目标测试、package、重启服务、4 条 E2E 查询。
2. 继续第二批 `ToolParameterSpec`：优先对本轮 4 个 URL 修复 Tool 补参数契约。
3. 后续单独更新 `docs/v3.1/项目里程碑全景图_20260519.md` 与 `PROJECT_ATLAS_V3.md`，避免总览文档漂移。

### 执行结果更新（2026-05-21 00:00）
- 目标单测：`ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest` 全部通过，`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- 打包：`mvn -DskipTests package`，BUILD SUCCESS。
- 服务重启：端口 8500，`/actuator/health={"status":"UP"}`。
- 真实 SSE E2E：4/4 返回 `event:done`。
  1. `查询名称为 test-storage 的存储详情` → 命中 `file_select_storage`，日志确认 `/api/100002/file/selectStorage 参数={page=1, limit=100, name=test-storage}`。
  2. `查询镜像 nginx:latest 的详情` → 命中 `image_detail_by_name`，日志确认 `/api/100002/image/name 参数={page=1, limit=100, name=nginx:latest}`。
  3. `查询 Helm Chart nginx 的详情` → 命中 `helm_chart_info`，日志确认 `/api/100002/helm/charts/single 参数={page=1, limit=100, chart=nginx}`。
  4. `搜索 Helm Chart 关键字 redis` → SSE `event:done`，Brain target=`helm_chart_search`；业务返回空结果属于可接受状态。

## 2026-05-21 01:30 - ToolParameterSpec 第二批：Storage/Image/Helm 查询类参数契约

### 背景
- 在完成 URL query 拼接专项清理后，继续按“先实验再铺开”推进 Tool 参数契约。
- 本批选择刚刚通过真实 SSE E2E 的 4 个 Tool，链路稳定、风险可控：
  - `file_select_storage`
  - `image_detail_by_name`
  - `helm_chart_info`
  - `helm_chart_search`

### 专家会诊结论
1. 不改执行字段名，canonical 必须贴合当前 Tool 实际读取逻辑。
2. `name` 字段高度歧义，必须通过 description 明确资源类型，不能全局猜测。
3. aliases 只用于 schema-first normalizer 兼容历史/LLM输出，不在 ReAct 工具目录展开，避免反向诱导 LLM 输出 alias。
4. Helm 语义必须区分 Chart / Release / Repository；keyword 只能表示模糊搜索词，不是精确名称。

### 变更内容
1. `FileSelectStorageTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `name`（必填）。
   - description 明确：存储卷/PVC 名称，不是文件名、目录名、镜像名或 StorageClass。
   - aliases: `storageName`, `storage_name`, `storage`, `pvc`, `pvcName`, `volumeName`, `targetName` 等。

2. `ImageDetailByNameTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `name`（可选，匹配当前执行逻辑）。
   - description 明确：容器镜像名称或镜像引用，如 `nginx:latest`、`library/nginx:1.25`。
   - aliases: `imageName`, `image_name`, `image`, `containerImage`, `imageRef`, `targetName` 等。

3. `HelmChartInfoTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `chart`（必填）。
   - description 明确：Helm Chart 名称或标识，不是 Helm Release 名称，也不是 Dashboard 图表。
   - aliases: `chartName`, `chart_name`, `helmChart`, `helm_chart`。

4. `HelmChartSearchTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `keyword`（可选）。
   - description 明确：Helm Chart 模糊搜索关键字，不是精确 Chart 名称、Release 名称或仓库名称。
   - aliases: `q`, `query`, `search`, `searchText`, `search_text`, `filter`。

5. 测试补充
   - `ToolParameterNormalizerTest`
     - 新增第二批 Storage/Image/Helm Tool schema-first alias 归一化测试。
     - 验证 `storageName -> name`、`imageName -> name`、`chartName -> chart`、`searchText -> keyword`。
     - 验证不会误归一到 `storageClass`、`podName`、`deploymentName`、`releaseName`、`name` 等错误字段。
   - `ToolRegistryPromptContractTest`
     - 新增第二批 Tool Prompt contract 测试。
     - 验证 ReAct 工具目录只展示 canonical 参数，不展开 `storage_name/image_name/chart_name/searchText` 等 alias。

### 测试结果
- 目标单测：
  - 命令：`mvn -Dtest=ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest test`
  - 结果：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 打包：
  - 命令：`mvn -DskipTests package`
  - 结果：BUILD SUCCESS。
- 服务：
  - 重启新 jar，端口 8500。
  - `/actuator/health={"status":"UP"}`。
- 真实 SSE E2E：
  1. `查询名称为 test-storage 的存储详情` → `event:done`，无 error；日志命中 `file_select_storage`，参数 `{page=1, limit=100, name=test-storage}`。
  2. `查询镜像 nginx:latest 的详情` → `event:done`，无 error；日志命中 `image_detail_by_name`，参数 `{page=1, limit=100, name=nginx:latest}`。
  3. `查询 Helm Chart nginx 的详情` → `event:done`，无 error；日志命中 `helm_chart_info`，参数 `{page=1, limit=100, chart=nginx}`。
  4. `搜索 Helm Chart 关键字 redis` → `event:done`，无 error；业务返回空结果属于可接受状态。

### 代码 Review
#### 优点
- 继续保持小批量推进，不盲目全量铺开 109 个 Tool。
- 只补参数契约，不改执行字段名，避免破坏当前稳定路径。
- 对高歧义 `name` 做了资源类型限定，降低 LLM 在 Storage/Image/Deployment/Pod 间误填概率。
- Normalizer 和 Prompt 两条线都有测试，兼顾执行兼容与 Prompt 约束。

#### 风险
- `file_select_storage`、`image_detail_by_name` 当前 canonical 仍是 `name`，长期看语义不如 `storageName/imageName` 清晰；后续如迁移需同步改执行逻辑、必填校验和测试。
- Helm Chart 搜索/详情返回空数据与后端仓库配置有关，不代表 Tool 失败；E2E 验收重点是路由、参数和 SSE 生命周期。
- 本批仍主要验证 `CALL_TOOL -> tool_call` 路径，完整 ReAct 多步链路还需专项 E2E。

### 后续建议
1. 第三批 ToolParameterSpec 继续选择已验证链路的查询/诊断 Tool，例如 `pod_status`、`deployment_query`、`event_query`。
2. 对 `name` canonical 的工具建立迁移计划，逐步转为 `imageName/storageName/deploymentName/nodeName` 等更语义化字段。
3. 增加 ReAct 多步成功路径 E2E，验证 Prompt 参数契约是否能让 LLM 在多轮 Action 中优先输出 canonical。



## 2026-05-21 20:35 - ToolParameterSpec 第三批：Pod/Deployment 查询主链路参数契约

### 背景
- 本轮继续推进 K8s 诊断/查询主链路参数契约建设，目标链路为：查 Pod → 查 Deployment/实例 → 查 Event → 汇总根因。
- 按专家会诊结论，禁止只声明 `ToolParameterSpec` 而不让 `doExecute` 使用参数，否则会形成“伪参数”并误导 ReAct/LLM。
- 源码复核确认当前真实已注册 Tool 为：
  - `pod_status` → `PodQueryTool`
  - `deployment_status` → `DeploymentQueryTool`
- `pod_query`、`deployment_query`、`event_query` 当前未定位为独立已注册 Tool，本轮不凭空新增伪 Tool。

### 变更内容
1. `PodQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`namespace`、`podName`、`username`、`status`，全部保持可选，兼容零参数查看 Pod 列表。
   - aliases 支持 `ns`、`pod_name`、`targetName`、`userName`、`phase` 等历史/LLM 输出字段。
   - `doExecute` 从固定 `page/limit` 扩展为 `LinkedHashMap` query map，按需透传 `namespace/name/username/status`。
   - 继续通过 `resolveOrganizationId(params)` 解析组织 ID，未硬编码 orgId。

2. `DeploymentQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`name`、`namespace`、`username`、`status`，全部保持可选。
   - description 明确平台术语：“实例”= Deployment，不是 Pod。
   - aliases 支持 `deploymentName`、`instanceName`、`deployName`、`ns`、`owner`、`instanceStatus` 等。
   - `doExecute` 使用 `LinkedHashMap` query map 透传筛选条件，禁止手拼 URL。

3. 测试补充
   - `ToolRegistryPromptContractTest`
     - 新增第三批 `pod_status` / `deployment_status` ReAct 工具目录参数契约测试。
     - 验证 prompt 展示 canonical 参数，不展开 alias。
   - `ToolParameterNormalizerTest`
     - 新增第三批 schema-first alias 归一化测试。
     - 验证 `pod_name/ns/userName/phase` 可归一到 Pod canonical 参数。
     - 验证 `deploymentName/ns/owner/instanceStatus` 可归一到 Deployment canonical 参数，且不会误归一为 `podName`。

### 重要修正
- 初次测试暴露 `pod_status` 的 description 中出现了 `pod_name` 字符串，导致 prompt contract 失败。
- 该问题说明 alias 即使只写在中文描述里，也会诱导 LLM 输出非 canonical 字段。
- 已修正为：alias 只保留在 `ToolParameterSpec.aliases` 元数据中，不出现在 ReAct prompt 描述文本中。

### 测试结果
- 定向测试：
  - 命令：`mvn -q -Dtest=ToolRegistryPromptContractTest,ToolParameterNormalizerTest test`
  - 结果：通过，`Failures: 0, Errors: 0`。
- 全量测试：
  - 命令：`mvn -q test`
  - 结果：BUILD SUCCESS。
- 打包验证：
  - 命令：`mvn -q -DskipTests package`
  - 结果：BUILD SUCCESS。

### 代码 Review
#### 优点
- 遵循“先实验再铺开”：先实现 `pod_status` 小样本并定向测试，再铺到真实存在的 `deployment_status`。
- Tool schema 与执行逻辑同步修改，避免“声明参数但不生效”的伪参数问题。
- URL query 使用 map 构造，避免手工拼接带来的编码和注入风险。
- orgId 继续从上下文/参数解析，未新增硬编码组织 ID。
- 明确平台术语：“实例”= Deployment，降低 Pod/Deployment 混淆风险。
- prompt contract 测试覆盖 alias 不外显，防止 ReAct 工具目录诱导 LLM 生成 alias。

#### 风险
- 后端 `/api/{orgId}/pod` 与 `/api/{orgId}/deployment` 对 `namespace/name/username/status` 的具体筛选支持度仍依赖 kube-manager 实现；本轮保证 query map 真实透传，但未新增真实后端联调 E2E。
- `deployment_status` 的 canonical `name` 仍存在资源类型歧义；当前为贴合现有执行逻辑的兼容选择，后续可在统一迁移时演进为更强类型的 `deploymentName`。
- `event_query` 当前没有已注册 Tool，诊断链路中的 Event 查询仍是缺口。

### 后续建议
1. 继续定位 kube-manager Event API 与前端事件入口，确认是否应新增 `event_query` Tool。
2. 为 Pod/Deployment 列表接口补真实 SSE E2E，验证后端筛选字段实际命中效果。
3. 后续批量扩展查询类 Tool 时，继续坚持：先确认接口支持，再 schema + `doExecute` 同步透传。
4. 可考虑抽取通用 `putIfPresent` 到 BaseTool，减少各 Tool 私有重复代码。


## 2026-05-21 21:30 - event_query 小样本落地：基于 kube-manager Pod warning 的异常事件摘要 Tool

### 背景
- 上一轮第三批 Tool 参数契约扩展后，诊断链路仍缺少 `event_query`。
- 用户明确要求：暂不在 kube-agent 直接引入 Kubernetes Java Client，优先基于 kube-manager 已有能力实现。
- 专家会诊结论：当前 kube-manager 暂无独立完整 Event API；可先基于 `GET /api/{orgId}/pod` 返回记录中的 `warning` 字段，实现 Pod Warning/异常事件摘要查询。
- 本轮遵循“不造伪参数”原则：只声明真实生效参数，不把 Kubernetes 原生 EventList 能力伪装到 Tool schema 中。

### 方案边界
- 新 Tool 名称：`event_query`。
- 能力定位：Pod Warning/异常事件摘要查询，不是完整 Kubernetes EventList。
- 后端调用：`GET /api/{orgId}/pod`。
- 后端透传参数：`namespace`、`username`、`status`。
- kube-agent 本地过滤参数：`podName`、`reason`、`keyword`。
- 明确不声明：`fieldSelector`、`labelSelector`、`since`、`type`、`involvedObjectKind` 等 kube-manager 当前不支持的 Kubernetes 原生 Event 参数。
- 返回结构：`dataKind=podWarningSummaries`、`podWarningSummaries`、`count`、`source`、`query`、`limitations`。

### 本轮代码变更
1. 新增 `src/main/java/com/atlas/tool/impl/EventQueryTool.java`
   - 注册 `@AtlasToolMapping(name = "event_query", agent = "diag", intentId = "event_query")`。
   - 通过 `resolveOrganizationId(params)` 获取 orgId，未硬编码组织 ID。
   - 使用 `LinkedHashMap` query map 构造后端查询参数，未手拼 URL query。
   - 基于 kube-manager Pod 列表 `warning/warnings/eventWarning/message` 字段生成 Warning 摘要。
   - 空 warning 不输出，避免把正常 Pod 包装成“无事件”。
   - 失败返回改为泛化提示，详细异常仅写入日志，避免直接暴露后端异常细节。

2. 更新 `src/main/resources/intents.yml`
   - 新增 `event_query` intent。
   - description 明确声明该能力“基于 kube-manager Pod 列表 warning 字段，不是完整 Kubernetes EventList”。
   - 参数列表只包含真实生效的 6 个字段。

3. 更新参数契约测试
   - `ToolParameterNormalizerTest`：覆盖 `event_query` 的 schema-first alias 归一化，并验证不产生 `fieldSelector` 伪参数。
   - `ToolRegistryPromptContractTest`：验证 ReAct prompt 暴露 canonical 参数，且不暴露不支持的 Kubernetes 原生 Event 参数。

4. 新增 `src/test/java/com/atlas/tool/impl/EventQueryToolTest.java`
   - 覆盖主流程：查询 Pod warning、透传 `namespace/status`、本地按 `reason` 过滤。
   - 覆盖参数契约：只暴露真实支持参数，不暴露 Kubernetes 原生 Event 伪参数。
   - 覆盖边界：非 List 响应返回空摘要、空白过滤参数安全处理。
   - 覆盖大小写不敏感的 `podName/keyword` 本地过滤。

### 测试结果
- 定向测试：
  - 命令：`mvn -Dtest=EventQueryToolTest,ToolParameterNormalizerTest,ToolRegistryPromptContractTest test`
  - 结果：`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 全量测试：
  - 命令：`mvn test`
  - 结果：`Tests run: 134, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

### 代码 Review
#### 优点
- 严格遵守用户约束：未引入 Kubernetes Java Client，未新增 `pom.xml` 依赖。
- Tool schema 与执行逻辑一致：声明的 6 个参数均有真实透传或本地过滤行为。
- 返回结果显式包含 `limitations`，防止 LLM/ReAct 将本工具误解为完整 EventList。
- orgId 继续从上下文/参数解析，未出现硬编码组织 ID。
- URL query 使用 map 构造，避免手拼 query 的编码风险。
- 单测覆盖正常路径、参数契约、prompt contract、空响应、大小写过滤与伪参数防护。
- 独立 Reviewer 进行 fail-closed 审查，结论为无阻塞问题；根据建议补强了边界测试和错误信息泛化。

#### 风险
- 当前能力依赖 kube-manager Pod DTO 中的 `warning` 字段，信息粒度不等同于 Kubernetes 原生 Event。
- 本轮未做真实后端 SSE E2E；仅通过单测和 Spring 全量测试验证代码路径。
- `limit=100` 是当前小样本默认值，若集群 Pod 数较大，后续需要分页聚合或按 namespace/podName 更精确查询。
- `orgId` 参与 path 构造，当前依赖 `resolveOrganizationId` 的可信输出；后续可考虑在 BaseTool 层统一增加 orgId 格式校验。

### 后续建议
1. 在 kube-manager 暴露真实 Event API 后，可新增 `kubernetes_event_query` 或升级 `event_query`，但必须同步调整 schema 与 limitations。
2. 结合 ReAct 诊断链路，把 `pod_status -> deployment_status -> event_query -> log_query` 做成可观测多步 E2E。
3. 若真实后端支持 Pod 精确 name 查询，应将 `podName` 从本地过滤升级为后端透传参数。
4. 抽取 `putIfPresent` 等 query 构造小工具到 BaseTool，减少各 Tool 重复代码。

## 2026-05-21 22:50 - event_query 接入 ReAct Pod 多步诊断提示词链路

### 背景
- 上一轮已新增 `event_query`，但它只是独立 Tool；ReAct 多步诊断 Prompt 尚未明确要求在 Pod 故障排查中使用事件摘要。
- 现有 `AtlasBrain.shouldUseReAct()` 对 `Warning`、`FailedScheduling`、`调度失败` 等事件/调度类故障词召回不足，可能导致复杂诊断没有进入 ReAct 多步链路。
- 本轮遵循“小样本先验证”和 TDD：先写 Prompt/Brain 契约测试确认缺口，再做最小实现。

### 变更内容
1. `AtlasBrain`
   - 扩展 ReAct 静态守卫关键词：`warning`、`event`、`事件`、`异常事件`、`告警`、`调度失败`、`failedscheduling`、`unschedulable`、`failedmount`、`createcontainerconfigerror`、`createcontainererror`。
   - 目标是将 Pod Warning、调度失败、挂载失败、容器创建失败等诊断类问题召回到 `DELEGATE_REACT`。

2. `ReActPromptBuilder`
   - 新增“Pod 诊断工具调用规则”。
   - 规则要求默认先查 `pod_status` 获取基础状态。
   - 对 Pending、ImagePullBackOff、ErrImagePull、ContainerCreating、CreateContainerConfigError、CreateContainerError、FailedMount、Unschedulable、FailedScheduling 等控制面/调度/镜像/创建阶段问题，优先调用 `event_query`。
   - 对 CrashLoopBackOff、RestartCount>0、Running 但 Ready=false、Terminated Error、OOMKilled 等运行时问题，要求结合 `event_query` 与 `log_query`。
   - 明确 `event_query` 只是基于 kube-manager Pod warning 字段的异常事件摘要，不是完整 Kubernetes EventList；禁止构造 `fieldSelector/labelSelector/since/type/involvedObjectKind` 等不支持参数。
   - 要求最终诊断按“现象、证据、判断、建议”组织，避免单工具绝对结论。

3. 测试补充
   - 新增 `ReActPromptBuilderPodDiagnosticContractTest`：锁定 ReAct Prompt 中必须包含 `pod_status/event_query/log_query` 证据链、事件能力边界、不支持参数禁止语义、最终诊断结构。
   - 扩展 `AtlasBrainMockTest`：覆盖 Warning 事件、FailedScheduling、调度失败等输入必须进入 ReAct 守卫。

### 测试结果
- 红灯验证：新增测试最初失败，失败点为 `AtlasBrain` 未覆盖 Warning/FailedScheduling/调度失败，`ReActPromptBuilder` 未包含 Pod 诊断工具调用规则。
- 定向测试：
  - 命令：`mvn -Dtest=ReActPromptBuilderPodDiagnosticContractTest,AtlasBrainMockTest test`
  - 结果：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 宽测试：
  - 命令：`mvn -Dtest=ReActPromptBuilderPodDiagnosticContractTest,AtlasBrainMockTest,ToolRegistryPromptContractTest,EventQueryToolTest,ReActEngineParamMergeTest,ReActEnginePolicyTest test`
  - 结果：`Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 全量测试：
  - 命令：`mvn test`
  - 结果：`Tests run: 135, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 安全扫描：新增 diff 未发现硬编码密钥、危险进程执行、eval/exec 等问题。
- 独立代码 Review：通过，无阻塞问题；建议后续补行为级 ReAct 多步测试。

### 代码 Review
#### 优点
- 变更范围非常小，只修改 Brain 召回关键词与 ReAct Prompt 策略，不改 ReActEngine 执行循环，降低回归风险。
- Prompt 明确声明 `event_query` 能力边界，避免把 Pod warning 摘要伪装成完整 Kubernetes Event API。
- TDD 顺序清晰：先红灯确认缺口，再最小实现，再定向/宽/全量测试。
- Final Answer 结构化要求有助于减少“只凭日志/只凭事件”的单证据误判。

#### 风险
- `warning/event/failed` 等关键词较宽，可能让部分简单查询进入 ReAct 链路，增加一次 LLM/工具编排成本；当前为了诊断命中率优先可以接受。
- 新增测试是 Prompt 契约测试，尚未验证真实 LLM 是否严格按 `pod_status -> event_query -> log_query` 顺序执行。
- `event_query` 仍受 kube-manager Pod warning 字段粒度限制，不等同于原生 Kubernetes EventList。

### 后续建议
1. 补充 mock LLM 或可控 ReAct loop 行为级测试，验证 Pending/FailedScheduling 优先调用 `event_query`。
2. 补真实 SSE E2E：构造/选择一个存在 warning 的 Pod，观察 ReAct 是否形成 `pod_status -> event_query -> final/log_query` 证据链。
3. 若后续发现简单事件查询链路过重，可将 Brain 关键词从宽泛词改为组合判定或交给 IntentArbiter/Embedding 做更细路由。

## 2026-05-23 12:50 - M5.8 业务 Tool 禁止 sysadmin fallback token 自动降级

### 背景
- 专家会诊后，本轮选择最高价值且最小扩散面的安全闭环：`KubeManagerHttpClient#get/post/delete` 业务请求入口。
- M5.7 已完成 `fallbackOrgId` 可信语义收口，但 HTTP 客户端仍存在一个更底层的风险：业务 Tool 在缺少用户 ThreadLocal Token 时可能通过 `resolveToken()` 透明降级为 sysadmin fallback token。
- 该行为对开发兼容友好，但对多租户/RBAC 是权限放大器：一旦 Graph/ReAct/异步链路漏传 token，业务请求可能不再以真实用户身份执行。

### 专家会诊结论
1. 安全与多租户视角：业务请求必须 fail-closed，系统级 fallback 只能作为显式白名单能力，不能出现在 Tool 默认路径。
2. 架构视角：不要大范围重构上层 Agent 编排，先在 HTTP 客户端出口做最小安全门，后续再抽象 SystemContextPolicy。
3. 测试视角：必须用 MockRestServiceServer 断言缺 Token 时不发出任何 fallback 登录/业务请求，避免只测异常文案。
4. 开源对标视角：LangChain/LangGraph、Dapr、K8s controller-runtime 的通用经验是默认用户上下文优先，特权上下文必须显式声明和可审计。

### 变更内容
1. `KubeManagerHttpClient`
   - `get/post/delete` 改为调用 `resolveUserTokenRequired(operation, path)`。
   - 新增 `resolveUserTokenRequired`：ThreadLocal 用户 Token 为空时抛出 `IllegalStateException`，拒绝 sysadmin fallback。
   - 保留 `resolveToken()`，但文档明确其只允许未来显式系统任务使用，禁止业务 Tool 默认路径调用。

2. `KubeManagerHttpClientTokenFallbackSecurityTest`
   - 新增 5 个测试覆盖 GET/POST/DELETE 缺用户 Token fail-closed。
   - 验证用户 Token 存在时 Authorization Header 使用真实用户 Token。
   - 验证系统任务 fallback 入口仍保留，避免误伤未来健康探测/后台同步场景。

3. 文档
   - `CHANGELOG.md` 新增 M5.8 条目。
   - `docs/M5_8_AUDIT_CHECKLIST_20260523.md` 新增阶段审计清单。

### 测试结果
- 定向测试：`mvn test -q -Dtest=KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 5 tests, 0 failures。
- 安全组合回归：`mvn test -q -Dtest=KubeManagerHttpClientResolveOrgIdSecurityTest,M57FallbackOrgIdSourceContractTest,BaseToolOrganizationIdGovernanceTest,KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 17 tests, 0 failures。
- 全量测试：`mvn test -q` → ✅ 182 tests, 0 failures, 0 errors, 0 skipped。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- 新增 diff 行敏感信息/危险执行扫描：✅ 未发现硬编码密钥、PAT、危险进程执行、`eval/exec` 等模式。

### 代码 Review
#### 优点
- 改动点集中在 HTTP 出口，安全收益大、扩散风险小。
- 业务请求与系统任务 Token 语义被明确拆开，降低后续误用概率。
- 单测不仅验证异常，还验证不会触发 fallback 登录请求，覆盖了真实安全意图。
- 保持详细中文注释，方便后续维护者理解为什么不能自动降级。

#### 风险
- `resolveToken()` 仍然保留 fallback 能力，后续如果新增调用方必须强制 Review 和测试。
- 本轮未全量扫描是否存在绕过 `KubeManagerHttpClient#get/post/delete` 的独立 HTTP 出口。
- 本轮没有做真实 SSE E2E；但安全边界位于客户端 Token 解析层，Mock 测试已精确覆盖。

### 后续建议
1. 下一批做“HTTP 出口契约审计”：扫描所有 `RestClient/WebClient/RestTemplate` 直接调用点，确认业务请求都经过统一安全门。
2. 引入 `SystemContextPolicy` 或源码契约测试，让系统任务 fallback 必须显式白名单化。
3. 服务重启后补一次登录 + 只读查询 SSE 冒烟，确认业务链路正确携带用户 Token。

### 持续学习总结
- 多租户 Agent 系统里，“开发兼容 fallback”很容易变成“生产权限放大器”。
- 安全治理应优先卡在最底层出口，先让默认路径安全，再逐步给系统任务开显式白名单。
- 对安全分支的测试不能只断言抛异常，还要断言危险副作用没有发生。


## 2026-05-23 16:31 - M5.9 HTTP 出口与 fallback token 源码契约治理

### 背景
- M5.8 已将 `KubeManagerHttpClient#get/post/delete` 收口为必须使用用户 ThreadLocal Token，缺失用户上下文时 fail-closed。
- 本轮继续推进时，哥哥明确要求：避免影响 kube-manager 的数据；所有删除和修改类不需要真实测试，只需要跑通逻辑。
- 因此 M5.9 选择低副作用、高收益的小样本落点：新增源码级契约测试，防止未来业务代码绕过统一 HTTP 出口或重新把 sysadmin fallback token 接回业务默认路径。

### 专家 Review 会诊结论
- 快速专家 Review 会诊结果：PASS with Notes。
- 结论：当前源码契约测试方向正确，不访问真实 kube-manager，不会影响 kube-manager 数据，可以合入。
- 专家建议补强：HTTP 出口扫描模式应覆盖 `HttpURLConnection/openConnection/HttpClient.newHttpClient` 等直接 HTTP 路径，并区分 kube-manager 出口与外部下载出口。
- 已按建议补强，并将 `ModelDownloader` 显式归类为“外部 Embedding 模型下载出口，不访问 kube-manager 数据面”。

### 变更内容
1. 新增 `M59HttpSecurityBoundaryContractTest`
   - 扫描 `src/main/java` 生产源码。
   - 白名单外禁止直接创建/注入 HTTP 客户端，避免业务 Tool 绕过 `KubeManagerHttpClient`。
   - 直接 HTTP 出口模式覆盖：`RestClient`、`RestTemplate`、`WebClient`、`HttpURLConnection`、`HttpClient`、`openConnection`、`OkHttpClient`、`Feign`、`Apache HttpClient` 等。
   - 白名单明确限定：
     - `KubeManagerHttpClient`：统一 kube-manager 数据面 HTTP 出口；
     - `AuthController`：登录代理入口；
     - `ModelDownloader`：外部 Embedding 模型下载，不访问 kube-manager 数据面。
2. 锁定 M5.8 token fallback 边界
   - `KubeManagerHttpClient#get/post/delete` 必须调用 `resolveUserTokenRequired`。
   - 业务方法不得调用允许 sysadmin fallback 的 `resolveToken()`。
   - 生产源码中 `resolveToken()` 调用点数量被锁定为仅方法声明本身。
3. 文档同步
   - `CHANGELOG.md` 新增 M5.9 记录。
   - 新增 `docs/M5_9_AUDIT_CHECKLIST_20260523.md`。

### 验证结果
- 定向逻辑验证：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest` → 通过。
- 安全组合回归：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → 通过。
- 打包：`mvn -q -DskipTests package` → BUILD SUCCESS。
- 格式检查：`git diff --check` → 通过。
- Diff 敏感信息/危险执行扫描：通过，未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。
- 数据影响：未启动服务，未调用真实 kube-manager API，未执行真实删除/修改操作。

### 代码 Review
#### 优点
- 完全符合“避免影响 kube-manager 数据”的要求，只做源码扫描和单元逻辑验证。
- 把 M5.8 的安全修复升级为 CI 可持续防回归契约，降低未来新增 Tool 绕过统一出口的风险。
- 显式区分 kube-manager 数据面出口、登录代理出口和外部模型下载出口，避免误把所有 HTTP 行为混为一类。
- 失败信息会输出具体文件与行号，便于后续新增代码时快速定位违规点。

#### 风险
- 当前是源码字符串级扫描，不是 AST/ArchUnit 级强约束；如果未来代码通过非常规封装或反射绕过，可能需要更强架构测试。
- `AuthController` 当前为文件级白名单，未来如果在该类中加入非登录代理的数据面访问，契约可能无法细分识别。
- HTTP 客户端生态较多，后续新增 Feign/Retrofit/第三方 SDK 时，需要同步扩展契约模式或白名单说明。

### 后续建议
1. 后续可引入 ArchUnit，将包依赖、类依赖、方法调用约束升级为结构化架构测试。
2. 后续所有新增外部 HTTP 出口必须明确分类：kube-manager 数据面、认证代理、外部资源下载或第三方服务，并写入契约白名单说明。
3. 继续保持“专家会诊 → 小样本 → 逻辑验证 → Review → 文档 → 双远端同步”的闭环。

## 2026-05-23 17:36 - M5.10 ArchUnit 架构级安全边界契约治理

### 背景
- M5.9 已通过源码字符串契约测试锁定 HTTP 出口与 fallback token 方法体语义。
- 继续推进时，目标是把“源码扫描”进一步升级为“架构级依赖边界测试”，但仍必须避免影响 kube-manager 数据。
- 本轮选择小样本落地 ArchUnit，只做静态字节码/依赖分析，不启动服务、不访问真实 kube-manager、不执行真实删除/修改。

### 专家会诊与开源调研结论
- Java 架构专家建议：M5.10 适合最小引入 ArchUnit；ArchUnit 负责结构级、依赖级规则，M5.9 源码契约继续负责方法体语义。
- 安全专家复核：PASS；要求 ArchUnit 测试不得使用 `@SpringBootTest`，不得注入 Bean，不得调用真实 HTTP 方法。
- 开源调研：TNG/ArchUnit 是 Java architecture test library，用 plain Java unit testing 检查架构和编码规则，适合作为 CI 中的架构边界防回归机制。

### 变更内容
1. `pom.xml`
   - 新增 test scope 依赖：`com.tngtech.archunit:archunit-junit5:1.3.0`。
   - 该依赖仅用于测试，不进入生产运行时。
2. 新增 `M510ArchitectureBoundaryTest`
   - 使用 `@AnalyzeClasses(packages = "com.atlas", importOptions = DoNotIncludeTests.class)`。
   - 不使用 `@SpringBootTest`，不启动 Spring 容器。
   - 规则一：白名单外生产代码不得直接依赖底层 HTTP 客户端。
   - 规则二：`com.atlas.tool..` 不得依赖底层 HTTP 客户端。
   - 规则三：`com.atlas.controller..` 不得直接依赖 `com.atlas.tool.impl..`。
3. 底层 HTTP 客户端覆盖范围
   - `RestClient`
   - `RestTemplate`
   - `WebClient`
   - `java.net.*`
   - `OkHttp`
   - `Feign/OpenFeign`
   - Apache HttpClient 4/5

### 验证结果
- 定向验证：`mvn test -q -Dtest=M510ArchitectureBoundaryTest` → 通过。
- 安全组合回归：`mvn test -q -Dtest=M510ArchitectureBoundaryTest,M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → 通过。
- 打包：`mvn -q -DskipTests package` → BUILD SUCCESS。
- 格式检查：`git diff --check` → 通过。
- Diff 敏感信息/危险执行扫描：通过，未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。
- 数据影响：未启动服务，未访问真实 kube-manager API，未执行真实删除/修改操作。

### 代码 Review
#### 优点
- 将 M5.9 的源码级 HTTP 出口治理升级为更稳定的 ArchUnit 架构依赖治理。
- 保持最小落地，只加三条高价值规则，避免一次性大范围重构历史代码。
- 测试不启动 Spring，不访问真实服务，完全符合“避免影响 kube-manager 数据”的要求。
- 明确保留 M5.9 源码契约，避免用 ArchUnit 强行替代其不擅长的方法体语义检查。

#### 风险
- 当前规则仍是第一批最小架构边界，尚未覆盖 service/orchestrator/react/config 等完整层级。
- `com.atlas.http..` 作为包级白名单粒度较粗，未来如该包内出现非受控 HTTP 出口，需要继续细化。
- ArchUnit 规则会增加 CI 对依赖边界的敏感度，未来新增合法例外时必须写清白名单原因。

### 后续建议
1. M5.11 可继续推进“Tool 注解 method 与实际 HTTP 调用一致性契约”，专门防止 Agent 以为是 GET 但代码实际 POST/DELETE 的语义错位。
2. 后续可逐步增加 ArchUnit layer rules：controller → orchestrator/react → tool/core → http，保护整体依赖方向。
3. 保持 M5.7-M5.10 安全治理链共同运行，形成多层防线。

## 2026-05-23 19:19 CST - M5.11 Atlas Tool HTTP 元数据契约小样本治理

### 背景
- M5.9/M5.10 已经分别完成 HTTP 出口源码契约和 ArchUnit 架构边界，但 Tool 注解层仍只有 `name/agent/description/intentId`，缺少机器可判定的 HTTP 方法、API 路径和业务风险语义。
- 静态恢复现场发现当前 Tool 系统实际使用 `@AtlasToolMapping`，而不是旧记忆中的 `@AgentTool(method=...)`；因此 M5.11 目标调整为“AtlasToolMapping 声明与真实 KubeManagerHttpClient 调用一致性”。
- 本阶段严格遵守哥哥要求：删除/修改类不做真实破坏性测试，只跑源码/单元/编译逻辑。

### 专家会诊结论
1. 架构专家：建议新增 `httpMethod/apiEndpoints/operationType`，但先小样本，不要一次性迁移 110 个 Tool。
2. 测试契约专家：建议新增独立 `M511AtlasToolHttpContractTest`，源码扫描注解和 `KubeManagerHttpClient#get/post/delete` 调用；历史 Tool 可暂时跳过，已声明 Tool 强校验。
3. 安全生产专家：指出 `POST` 不等于普通写，可能代表 delete/stop/abort/scale；风险语义必须独立建模，DELETE/ACTION/HOLD/PLACEHOLDER 必须走确认。

### 本轮代码变更
- 扩展 `AtlasToolMapping`：新增 `httpMethod`、`apiEndpoints`、`operationType`、`requiresConfirmation`，并内置 `OperationType` 枚举。
- 小样本补齐 5 个 Tool 元数据：
  - `EventQueryTool`：`GET /api/{orgId}/pod`，`READ`。
  - `StorageQueryTool`：多路径 `GET` fallback，`READ`。
  - `MpiJobSubmitTool`：`POST /api/{orgId}/mpi-job/submit`，`ACTION`，需确认。
  - `ImageDeleteTool`：`DELETE /api/{orgId}/image/{var}`，`DELETE`，需确认。
  - `DeployScaleTool`：`NONE` + `PLACEHOLDER`，标记未来 scale endpoint，需确认。
- 新增 `M511AtlasToolHttpContractTest`：只校验已声明 `httpMethod` 的 Tool，验证方法一致性、风险元数据、endpoint 元数据和占位 Tool 不得真实调用 HTTP。

### 测试结果
- 定向测试：`mvn -Dtest=M511AtlasToolHttpContractTest test` → BUILD SUCCESS，1 test passed。
- 编译打包：`mvn -DskipTests package` → BUILD SUCCESS。
- 格式检查：`git diff --check` → 通过。
- 静态 Review 扫描：当前声明 `httpMethod` 的 Tool 共 5 个：
  - `deploy_scale`：NONE / PLACEHOLDER / requiresConfirmation=true / 无真实 HTTP 调用；
  - `event_query`：GET / READ / 实际 GET；
  - `image_delete`：DELETE / DELETE / requiresConfirmation=true / 实际 DELETE；
  - `mpi_job_submit`：POST / ACTION / requiresConfirmation=true / 实际 POST；
  - `storage_status`：GET / READ / 实际 GET。

### 代码 Review
#### 优点
- 完整遵守“专家会诊 → 小样本验证 → 逻辑测试 → Review/文档”的开发铁律。
- 新注解字段均有默认值，不破坏历史 110 个 Tool 的编译与注册。
- 小样本覆盖 GET、POST、DELETE、多路径 GET fallback、NONE/PLACEHOLDER 五种关键形态，代表性足够。
- 契约测试不启动 Spring、不访问 kube-manager、不执行真实删除/修改，安全边界清晰。
- 高风险语义从 HTTP Method 中独立出来，避免 `POST delete`、`POST stop` 之类操作被误判为普通写入。

#### 风险
- 当前只是小样本，不是全量治理；历史 Tool 仍可能缺少元数据，后续需要持续铺开。
- 契约测试暂未解析 endpoint 表达式是否与源码完全一致，只校验已声明 endpoint 存在；这是 M5.11 有意保守，避免 AST 复杂度过高。
- `requiresConfirmation` 目前是元数据声明，还没有接入 ToolRegistry Prompt 或执行层强制拦截，后续需继续做 runtime fail-closed。
- `DeployScaleTool` 被标为 PLACEHOLDER 后，仍需后续专门治理“占位 Tool 不应返回已执行成功”的用户体验与安全语义。

### 后续建议
1. M5.12：将 `operationType/requiresConfirmation/httpMethod` 注入 ToolRegistry system prompt，让 LLM 看见风险标签。
2. M5.13：在 Tool 执行网关/BaseTool 层实现 DELETE/ACTION/PLACEHOLDER 的确认状态强制拦截。
3. 分批迁移剩余 Tool：先 GET 查询类，再 DELETE，再 POST/ACTION，最后处理动态路径和占位 Tool。
4. 后续升级契约测试 endpoint 解析能力：从“存在性校验”增强到“源码路径模板与注解路径集合一致”。
