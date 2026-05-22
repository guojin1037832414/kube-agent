# Atlas Kube-Agent 变更日志

> 按 Milestone 分组，记录所有已实现功能和重大变更。
> 格式：基于 [Keep a Changelog](https://keepachangelog.com/)。
> 里程碑前缀规范：`feat(Mx):`、`fix(Mx):`、`docs(Mx):`。

---

## [M5.2] — RBAC 管理面列表参数 HOLD 保护

**周期**: 2026-05-22
**交付**: 按后端/API、安全/RBAC、测试架构三路专家会诊共识，不开放 RBAC 管理面列表的 `page/limit/keyword`，而是将 HOLD 决策测试化，防止后续批量脚本误把身份源、组织、权限菜单、注册审核、角色边界列表接入普通列表三件套。

### Changed

- `SensitiveListToolHoldContractTest` 从 M5.1 的“仅禁止 keyword”升级为禁止敏感列表暴露 `page`、`limit`、`keyword` 三类标准列表查询参数。
- `OrderListTool`、`QuotaReceiveListTool` 的 HOLD 语义同步升级：在权限/审计专项完成前，不暴露翻页、批量枚举和搜索枚举能力。

### Added

- 新增 M5.2 RBAC 管理面 HOLD 覆盖：`LdapConfigListTool`、`OrganizationListTool`、`PermissionMenuListTool`、`RegisterAuditListTool`、`RoleAssignableListTool`、`RoleEditableListTool`。

### Deferred

- 本阶段不修改生产代码，不给上述 6 个 RBAC Tool 接入 `listQueryParameterSpecs()` / `buildListQuery(params)`。
- 当前 `PUBLIC` 权限注解被专家会诊确认为安全债务，但不与参数 HOLD 混改；后续单独进入 RBAC 权限收敛专项。

### Verified

- TDD 红灯：临时突变 `LdapConfigListTool` 暴露标准列表参数后，`SensitiveListToolHoldContractTest` 准确失败并拦截 `page` 暴露。
- 最小绿灯：撤销突变后，`/usr/share/maven/bin/mvn -Dtest=SensitiveListToolHoldContractTest test` → 2 tests, 0 failures, BUILD SUCCESS。
- 邻近回归：`/usr/share/maven/bin/mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,SensitiveListToolHoldContractTest test` → 7 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 144 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M5.1] — 账务域低风险货币列表参数契约与敏感列表 HOLD 保护

**周期**: 2026-05-22
**交付**: 进入 M5 敏感域专项后，按专家会诊结论仅将低风险账务元数据 `CurrencyQueryListTool` 纳入标准 `page/limit/keyword` 参数契约，同时为订单与审批列表建立 HOLD 防误开放测试。

### Added

- 新增 `SensitiveListToolHoldContractTest`，锁定 `OrderListTool` 与 `QuotaReceiveListTool` 在权限、字段脱敏与审计专项完成前不得暴露 `keyword` 搜索能力。

### Changed

- `CurrencyQueryListTool` 新增标准 `page/limit/keyword` 参数契约。
- `CurrencyQueryListTool` 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传调用方分页与关键词。
- `CurrencyQueryListTool` 显式 rethrow `AtlasToolValidationException`，保留结构化参数错误码与 suggestions。

### Deferred

- `OrderListTool` 继续 HOLD：订单/租赁账务敏感列表，接入前需确认租户隔离、可见范围、字段脱敏、keyword 搜索字段与审计策略。
- `QuotaReceiveListTool` 继续 HOLD：配额审批/RBAC 语义敏感，接入前需确认审批人可见范围、权限策略与审计记录。

### Verified

- TDD 红灯：新增契约测试后，`currency_query_list` 因未声明 `page`、仍固定分页、非法分页未短路而失败，符合预期。
- 定向测试：`/usr/share/maven/bin/mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,SensitiveListToolHoldContractTest test` → 6 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 143 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.8] — 账务配额候选安全分层与标准列表 Tool 小批铺开

**周期**: 2026-05-22
**交付**: 在 ACCOUNT/RBAC/GLOBAL 剩余固定分页 Tool 复扫后，按专家会诊结论仅将 2 个低风险组织内列表 Tool 纳入标准 `page/limit/keyword` 契约。

### Changed

- `ResourceUsageListTool`、`QuotaMyListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 2 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 2 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Deferred

- 暂缓 `QuotaReceiveListTool`：审批/待办/RBAC 语义，需先确认后端权限过滤与审计边界。
- 暂缓 `OrderListTool`：订单/账务敏感列表，需先确认租户隔离、可见范围与 keyword 字段语义。
- 暂缓 `CurrencyQueryListTool`、RBAC 管理类、GLOBAL/PUBLIC/NO_ORG、Dashboard/count 与特殊字段类 Tool，后续按专项治理。

### Verified

- 红灯验证：新增契约测试后，`resource_usage_list` 因未声明 `page`、执行层仍固定分页而失败，符合 TDD 预期。
- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.7] — 标准列表 Tool 参数契约第五批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 2 个 Slurm/上传状态类列表 Tool。

### Changed

- `SlurmClusterListTool`、`UploadStatusListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 2 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 2 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.6] — 标准列表 Tool 参数契约第四批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 7 个课件/消息/GPU/命名空间/数据表/Slurm 类列表 Tool。

### Changed

- `CoursewareListTool`、`DownloadTaskListTool`、`InboxMessageListTool`、`MigConfigListTool`、`NamespaceListTool`、`TableListTool`、`SlurmNodeListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 7 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 7 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：通过，无阻断问题。

---

## [M4.5] — 标准列表 Tool 参数契约第三批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 8 个 deploy/实验/Helm/外链类列表 Tool。

### Changed

- `BareMetalAppListTool`、`CloudResourceListTool`、`ComposeListTool`、`ExperimentInstanceListTool`、`ExperimentTemplateListTool`、`ExternalLinkListTool`、`HelmRepoListTool`、`HelmReleaseListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 8 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 8 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：通过，无阻断问题。

---


## [M4.4] — 高频列表 Tool 参数契约第二批铺开

**周期**: 2026-05-22
**交付**: 将 M4.3 的 schema-first 列表参数真实透传模式，从首批 4 个 Tool 扩展到 8 个高频资产/模板类列表 Tool。

### Added

- `BaseTool#listQueryParameterSpecs(String)`：统一生成 page / limit / keyword 参数契约，避免各列表 Tool 复制粘贴后 alias 或描述漂移。
- `ListToolParameterSpecContractTest`、`ListToolParameterPassThroughContractTest` 扩展到 12 个列表 Tool，覆盖第二批 P0 高频列表。

### Changed

- `DataSetListTool`、`ModelListTool`、`FileListTool`、`RegistryListTool`、`TensorBoardListTool`、`JobTemplateListTool`、`TemplateListTool`、`ResourcePresetListTool` 新增标准列表参数契约。
- 上述 8 个 Tool 的执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户传入的 `page/limit/keyword`。
- 上述 8 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool.wrapCall 的 `errorCode/suggestions` 结构化错误语义。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。

---

## [M4.3] — 列表 Tool 参数真实透传

**周期**: 2026-05-22
**交付**: M4.2 首批列表 Tool 的 `page/limit/keyword` 从 schema 声明推进到执行层真实消费。

### Added

- `BaseTool#buildListQuery()`：统一构建列表接口 query map，集中处理分页默认值、keyword 空白过滤和严格正整数校验，避免小数 Number 被截断。
- `ListToolParameterPassThroughContractTest`：锁定 `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 的参数透传、非法分页、结构化错误返回契约。

### Changed

- 4 个列表 Tool 对 AtlasToolValidationException 显式 rethrow，保留 BaseTool.wrapCall 的 errorCode/suggestions 语义。
- `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 从固定 `page=1&limit=100` 改为消费用户传入的 `page/limit/keyword`。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,KubeManagerHttpClientUrlContractTest test` → 6 tests, 0 failures。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。

---

## [M4.2] — ReAct 多步成功回归与 URL Query 契约

**周期**: 2026-05-22
**交付**: ReAct 多步成功路径 E2E、KubeManager GET query 构造契约测试、小批列表 Tool 参数契约扩展。

### Added

- `ReActEngineMultiStepE2ETest`：覆盖 `pod_status -> event_query -> Final Answer` 多步成功链路。
- `KubeManagerHttpClientUrlContractTest`：锁定 GET path/query 不混淆、不二次编码。
- `ListToolParameterSpecContractTest`：锁定列表 Tool 的 `page/limit/keyword` 参数契约。
- `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 新增分页/关键词参数契约。

### Fixed

- `KubeManagerHttpClient#get()` 改用 `RestClient.uri(builder -> ...)` 构造 path/query，避免 query 被编码进 path 或发生二次编码。

### Verified

- 目标组合测试：`mvn -Dtest=ReActEngineMultiStepE2ETest,KubeManagerHttpClientUrlContractTest,ListToolParameterSpecContractTest test` → 3 tests, 0 failures。
- 全量测试：`mvn test` → 138 tests, 0 failures, BUILD SUCCESS。

---

## [M4.1] — Tool Schema 与参数契约

**周期**: 2026-05-20
**交付**: ReAct/LLM 工具调用参数契约化，schema-first 参数归一化，首批 Tool 参数规格扩展。

### Added

- `ToolParameterSpec`：为每个 Tool 提供 canonical 参数、类型、必填、description、aliases。
- `ToolInputSchemaBuilder`：从参数契约生成 JSON Schema。
- `BaseTool#getParameterSpecs()`：Tool 自描述参数契约入口。
- `ToolParameterNormalizer` schema-first 模式：优先使用 Tool 自身 spec 做 alias 归一化。
- `ToolRegistry.findByName()` 与 `buildSystemPromptForCurrentUser()`：为 ReAct Prompt 提供轻量工具目录。
- `ReActPromptBuilder` 增强：明确要求 LLM 使用 canonical 参数名调用工具。
- 首批 Tool 参数契约：
  - `diagnose_pod`
  - `log_query`
  - `deployment_detail`
  - `node_detail`

### Changed

- `AtlasToolCallback#getToolDefinition()` 使用精确 inputSchema。
- `AtlasToolCallback#call()` 执行前统一经过参数归一化。
- ReAct Prompt 工具目录只展示 canonical 参数，不展开 aliases，避免 Prompt 膨胀。
- `deployment_detail` / `node_detail` 的 URL 查询参数从手拼 `?name=` 改为 query map。
- 后续专项开始清理剩余 `path += "?"` query 拼接点。

### Fixed

- 修复 `deployment_detail` 手拼 query 被 URI builder 编码为 `%253F` 导致 `400 BAD_REQUEST` 的问题。
- 同步修复 `node_detail` 同类潜在问题。
- 首批扩展后验证 `log_query` 的 `podName/lines` 参数链路正确。

### Verified

- 单测：`ToolParameterNormalizerTest`、`ToolRegistryPromptContractTest`、`ToolInputSchemaBuilderTest`、`AtlasToolCallbackTest`。
- 目标测试结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- 打包：`mvn -DskipTests package` BUILD SUCCESS。
- 真实 SSE E2E：
  - `查询部署实例 aaaa 的详情` → `event:done`，日志确认 `/api/100002/deployment 参数={limit=100, name=aaaa, page=1}`。
  - `查看 pod nginx-not-exist-schema 最近 50 行日志` → `event:done`，日志确认 `podName=nginx-not-exist-schema, lines=50`。

---

## [M3.2] — ReAct 多步诊断引擎 MVP

**周期**: 2026-05-20
**交付**: 手写 ReAct MVP、Graph/Orchestrator 接入、SSE 事件化、真实 E2E 稳定性修复。

### Added

- 手写 `ReActEngine` 主循环。
- `ReActMemory`：Thought/Action/Observation 管理。
- `ReActPromptBuilder`：动态构造 ReAct System Prompt。
- `ReActResult`：统一 ReAct 结果封装。
- `ReActEvent`、`ReActEventSink`、`ReActEventSinkRegistry`：ReAct 生命周期事件输出。
- `BrainDecision.ActionType.DELEGATE_REACT`：AtlasBrain 可将诊断类任务委派给 ReAct。
- StateGraph `react_node`：Graph 内部可执行 ReAct 节点。
- SSE 事件化：thinking、tool_start、tool_done、observation、content、error、done。
- `/react`、`/deep` 强制 ReAct 前缀。
- K8s 故障关键词强制 ReAct 路由。

### Changed

- ReAct 初始参数透传 `userId/token/organizationId/conversationId`。
- Graph State 不再存放运行期对象，改用 registry/sessionId 间接发布事件，避免 checkpoint 序列化污染。
- 高危操作优先进入 HITL，避免被 ReAct 抢占。
- 目标资源不存在时提前收敛，减少无效多轮推理。

### Fixed

- 修复 ReAct SSE E2E 路由不稳定。
- 修复 SSE data 多行 JSON 解析错误。
- 修复 StateGraph checkpoint 序列化 Lambda / 运行期对象异常。
- 修复 ReAct 目标资源不存在时无效多轮循环。
- 修复重复 content 输出问题。

### Verified

- ReAct 相关单测与 Graph 接入测试已多轮通过。
- 真实 SSE E2E 已验证 `/react 诊断 ... CrashLoopBackOff` 等路径。
- 服务健康检查 `/actuator/health` 返回 `UP`。

---

## [M2] — 查询全覆盖与质量加固

**周期**: 2026-05-14 ~ 2026-05-20
**交付**: 前端 9 大模块 109 个 Tool 覆盖，orgId/token 链路修复，查询类 E2E 基础能力。

### Added

- 109 个 `@AtlasToolMapping` Tool，覆盖前端 9 大模块主要按钮/API。
- `BaseTool#resolveOrganizationId()` 统一 orgId 解析。
- `extractData()` 与统一 ToolResult 返回。
- 默认值注册机制，对齐部分前端创建表单默认值。

### Fixed

- 修复登录 API 返回纯 JWT String 时无法从响应体解析 orgId 的问题。
- 修复异步线程中 token/orgId 丢失问题。
- 修复多处硬编码 orgId 导致跨租户数据错乱的风险。

---

## [M1] — 智能引擎与意图全链路

**周期**: 2026-05-14 ~ 2026-05-18
**交付**: L1-L4 意图路由 + AtlasBrain 决策 + StateGraph 编排 + HITL SSE 后端基础。

### Added

- L1 规则意图路由 — 关键词精确匹配，零 token 快速短路。
- L2 Embedding 语义预筛 — all-MiniLM-L6-v2 ONNX Runtime 本地部署。
- L3 LLM 意图分类 — ChatClient 结构化输出分类，失败降级。
- L4 fallback — LLM 不可用时使用规则/模糊兜底。
- AtlasBrain 认知决策中枢。
- StateGraph 编排引擎。
- HITL SSE 流式确认后端基础。
- TimedDecisionCache。
- ThreadLocal Token 透传。

### Security

- `@ToolPermission` + `@Isolation(SYS_ADMIN_ONLY)` + 权限过滤基础。
- HITL 命令式确认基础机制。

---

## [M0] — Atlas v2.x 基线（归档）

**周期**: 2026-05-12 ~ 2026-05-14
**交付**: 23 个 DomainPlugin + SSE 流式 + ChatMemory 持久化 + 权限网关。

### Added

- AtlasOrchestrator v2 编排器。
- DomainRouter 领域路由。
- PermissionGateway 权限网关。
- MessageWindowChatMemory。
- 环境配置分离。
- SSE 流式基础。
- MCP Server 初版探索。

---

## 后续计划

- `[M4.1 持续]` ToolParameterSpec 分批覆盖 detail/query/diagnose 类 Tool。
- `[M4.2]` Tool Prompt 长度预算与按 agent/意图裁剪。
- `[M4.3]` ReAct 多步成功路径 E2E 与真实存在资源诊断验证。
- `[M5]` Memory / MCP / Observability / Guardrails。

---

## 参考

- 完整路线图见 `ROADMAP.md`。
- 架构审计报告见 `ARCHITECTURE_AUDIT_20260518.md`。
- 文档治理方案见 `DOCUMENTATION_GOVERNANCE_REPORT.md`。
- 会话连续性快照见 `docs/会话上下文快照_20260520.md`。
