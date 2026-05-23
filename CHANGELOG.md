# Atlas Kube-Agent 变更日志

> 按 Milestone 分组，记录所有已实现功能和重大变更。
> 格式：基于 [Keep a Changelog](https://keepachangelog.com/)。
> 里程碑前缀规范：`feat(Mx):`、`fix(Mx):`、`docs(Mx):`。

---

## [M5.12] — Tool 风险元数据透明化前后端同步治理

**周期**: 2026-05-23
**交付**: 将 M5.11 建立的 Tool HTTP/风险注解元数据接入 ToolRegistry Prompt 与 ReAct SSE metadata，并同步在 kube-agent-vue 前端 ReAct 时间线展示风险标签、确认提示和占位能力提醒。M5.12 明确只做风险透明化，不冒充执行层安全边界；真正 fail-closed HITL 强拦截留到 M5.13。

### Added

- 后端 `ToolRegistry.ToolMetadata` 新增并保留：
  - `httpMethod`：Tool 声明的 HTTP 方法；
  - `apiEndpoints`：内部 API 路径模板，仅保存在元数据中，不写入 Prompt/前端展示；
  - `operationType`：READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER 风险语义；
  - `requiresConfirmation`：是否建议人工确认。
- `ToolRegistry.buildSystemPromptForCurrentUser()` 新增紧凑风险标签：
  - 例：`operationType=ACTION, httpMethod=POST, requiresConfirmation=true`；
  - 明确提示 M5.12 风险标签只是辅助判断，不是执行层强拦截。
- `ReActEvent` 新增带 `extraMetadata` 的 `toolStart/toolDone` 重载；旧方法保持兼容。
- `ReActEngine` 在工具事件中透传非敏感风险摘要：`httpMethod/operationType/requiresConfirmation`。
- 前端 `kube-agent-vue`：
  - 新增 `ToolRiskMetadata/ToolOperationType` 类型；
  - `ChatBubble.vue` ReAct 时间线展示 `READ/GET`、`ACTION/POST`、`DELETE/DELETE` 等风险 chip；
  - 展示“建议确认”标签；
  - 对 `DELETE` 与 `PLACEHOLDER` 显示明确风险/占位提示。

### Verified

- 专家会诊：✅ 已收敛为“风险透明化优先，不泄露 endpoint，不把提示冒充安全边界”。
- 后端定向测试：`mvn -q -Dtest=ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 前端类型检查与构建：`npm run build`（内部执行 `vue-tsc && vite build`）→ ✅ 通过；仅出现 Element Plus 依赖 Rollup 注释警告。
- 后端 `git diff --check`：✅ 通过。
- 前端 `git diff --check`：✅ 通过。
- 本阶段未启动服务、未调用真实 kube-manager、未执行真实删除/修改请求。

### Risk / Deferred

- M5.12 不提供 fail-closed 执行强拦截；`requiresConfirmation=true` 在本阶段只作为透明化提示和后续 HITL 输入。
- 历史未迁移 Tool 仍可能显示 `operationType=UNKNOWN` 或未声明 HTTP；后续需继续按批次铺开 M5.11 元数据。
- 前端展示依赖后端 SSE metadata，旧服务未重启或旧事件不会显示风险 chip。
- M5.13 建议接入执行层 Human-in-the-loop 强确认，禁止高危 Tool 仅靠 Prompt/UI 提示执行。

---

## [M5.11] — Atlas Tool HTTP 元数据契约小样本治理

**周期**: 2026-05-23
**交付**: 建立 Tool 注解元数据与真实 kube-manager HTTP 调用的一致性契约小样本；扩展 `@AtlasToolMapping` 承载 `httpMethod/apiEndpoints/operationType/requiresConfirmation`，并用源码级静态测试保护已迁移 Tool，避免 LLM/ToolRegistry/安全策略误把写删操作当只读操作。

### Added

- `AtlasToolMapping` 新增兼容字段：
  - `httpMethod`：声明真实 kube-manager HTTP 方法，默认空字符串兼容历史 Tool；
  - `apiEndpoints`：声明一个或多个 API 路径模板，支持多路径 fallback；
  - `operationType`：声明 READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER 等业务风险语义；
  - `requiresConfirmation`：声明高风险 Tool 是否需要 Human-in-the-loop 确认。
- 小样本迁移 5 类代表性 Tool：
  - `event_query`：GET + READ；
  - `storage_status`：多路径 GET fallback + READ；
  - `mpi_job_submit`：POST + ACTION + requiresConfirmation；
  - `image_delete`：DELETE + DELETE + requiresConfirmation；
  - `deploy_scale`：NONE + PLACEHOLDER + requiresConfirmation。
- 新增 `M511AtlasToolHttpContractTest`：
  - 只校验已声明 `httpMethod` 的 Tool，避免一次性引爆 110 个历史 Tool；
  - 静态扫描 `KubeManagerHttpClient` 字段变量名与 `get/post/delete/put/patch` 调用；
  - 校验声明方法与真实调用一致；
  - 校验写删/占位风险语义不能伪装成 READ；
  - 校验 DELETE/ACTION/PLACEHOLDER 必须 `requiresConfirmation=true`；
  - 校验非 NONE Tool 必须声明 `apiEndpoints`。

### Verified

- 专家会诊：✅ 架构专家、测试契约专家、安全生产就绪专家均建议建立 Tool HTTP 元数据契约；结论为先小样本验证，再分批铺开。
- 定向契约测试：`mvn -Dtest=M511AtlasToolHttpContractTest test` → ✅ Tests run: 1, Failures: 0, Errors: 0。
- 编译打包：`mvn -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- 静态 Review 扫描：✅ 当前仅 5 个 Tool 声明 `httpMethod`，方法与真实 `KubeManagerHttpClient` 调用一致；本阶段未调用真实 kube-manager，未执行真实删除/修改请求。

### Risk / Deferred

- M5.11 仍是小样本强契约，历史 105 个左右 Tool 仍保持 `operationType=UNKNOWN`/未声明 HTTP 元数据，后续需按 GET → DELETE → POST/ACTION → 特殊 Tool 分批铺开。
- `DeployScaleTool` 当前被明确标记为 `PLACEHOLDER`，后续真实接入 PATCH/scale 前必须解除占位并补充真实 HTTP 契约与执行层 HITL 拦截。
- 本阶段只把元数据写入注解并用源码契约保护，尚未把风险元数据注入 ToolRegistry Prompt 或执行层强制拦截；后续 M5.12/M5.13 可继续推进 prompt 暴露与 runtime fail-closed。

---

## [M5.10] — ArchUnit 架构级安全边界契约治理

**周期**: 2026-05-23
**交付**: 引入 ArchUnit 作为架构级静态契约测试，补强 M5.9 源码字符串契约；将 HTTP 出口治理从“源码扫描”扩展到“包/类依赖边界”层面，继续保证不访问、不修改 kube-manager 真实数据。

### Added

- 新增 test scope 依赖：`com.tngtech.archunit:archunit-junit5:1.3.0`。
- 新增 `M510ArchitectureBoundaryTest`，包含 3 条架构规则：
  - 白名单外生产代码不得直接依赖底层 HTTP 客户端；
  - `com.atlas.tool..` 不得依赖底层 HTTP 客户端，只能通过受控网关访问 kube-manager；
  - `com.atlas.controller..` 不得直接依赖 `com.atlas.tool.impl..`，避免绕过 Orchestrator/ReAct/ToolRegistry 编排链路。
- 底层 HTTP 客户端覆盖范围包括：`RestClient`、`RestTemplate`、`WebClient`、`java.net.*`、`OkHttp`、`Feign/OpenFeign`、Apache HttpClient 4/5。

### Verified

- 专家会诊：✅ Java 架构专家建议小步引入 ArchUnit；安全专家复核 PASS，明确 ArchUnit 只做静态分析，不启动 Spring、不访问 kube-manager。
- 开源调研：TNG/ArchUnit 是用于 Java architecture rules 的开源架构测试库，官方定位为 plain Java unit testing 下检查架构/编码规则。
- 定向验证：`mvn test -q -Dtest=M510ArchitectureBoundaryTest` → ✅ 通过。
- 安全组合回归：`mvn test -q -Dtest=M510ArchitectureBoundaryTest,M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → ✅ 通过。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- M5.10 不替代 M5.9：ArchUnit 负责结构/依赖边界，M5.9 继续负责 `resolveToken()` 与 `resolveUserTokenRequired()` 的方法体语义。
- 当前先落最小三条规则，避免一次性引入过重分层约束造成历史代码大规模返工；后续可继续扩展到 service/orchestrator/react/config 等层级边界。
- 本阶段仍严格零数据影响：未启动服务、未调用真实 kube-manager API、未执行真实删除/修改操作。

---

## [M5.9] — HTTP 出口与 fallback token 源码契约治理

**周期**: 2026-05-23
**交付**: 新增源码级安全契约测试，锁定 kube-manager 统一 HTTP 出口与 M5.8 token fallback 边界；避免未来业务 Tool 绕过 `KubeManagerHttpClient` 或重新把 sysadmin fallback token 接回业务默认路径。

### Added

- 新增 `M59HttpSecurityBoundaryContractTest`，覆盖 3 类源码契约：
  - 白名单外生产代码不得直接创建/注入 HTTP 客户端访问 kube-manager 数据面；
  - `KubeManagerHttpClient#get/post/delete` 必须使用 `resolveUserTokenRequired`；
  - `resolveToken()` 只能作为系统任务 fallback 能力保留，不得被业务路径调用。
- HTTP 出口白名单显式区分：
  - `KubeManagerHttpClient`：统一 kube-manager 数据面 HTTP 出口；
  - `AuthController`：登录代理入口；
  - `ModelDownloader`：外部 Embedding 模型下载，不访问 kube-manager 数据面。

### Verified

- 快速专家 Review 会诊：✅ PASS with Notes；结论为当前源码契约测试安全、无 kube-manager 数据影响，建议扩展 HTTP 出口模式后合入。
- 按专家建议补强 HTTP 出口扫描模式：覆盖 `RestClient/RestTemplate/WebClient/HttpURLConnection/HttpClient/openConnection/OkHttp/Feign/Apache HttpClient` 等常见绕过路径。
- 定向逻辑验证：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest` → ✅ 通过。
- 安全组合回归：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → ✅ 通过。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- 本阶段严格遵守“避免影响 kube-manager 数据”：未启动服务、未调用真实 kube-manager API、未执行真实删除/修改操作，只做源码契约与单元逻辑验证。
- 当前契约为源码字符串级扫描，不是 AST/ArchUnit 级强约束；后续如引入 ArchUnit，可进一步把“包级依赖约束”和“方法调用约束”升级为结构化架构测试。
- `AuthController` 目前按文件级白名单放行直接 HTTP；后续若该类新增非登录代理的数据面调用，应拆分更细粒度白名单或迁移到统一 client。

---

## [M5.8] — 业务 Tool 禁止 sysadmin fallback token 自动降级

**周期**: 2026-05-23
**交付**: 将 `KubeManagerHttpClient#get/post/delete` 业务请求入口收口为“必须使用用户 ThreadLocal Token”；缺少可信用户上下文时 fail-closed，避免 Agent Tool 在无用户会话时透明降级为 sysadmin token 代跑。

### Added

- 新增 `KubeManagerHttpClientTokenFallbackSecurityTest`，覆盖 5 个安全边界：
  - GET 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - POST 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - DELETE 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - GET 存在用户 Token 时只使用用户 Token，不触发 fallback；
  - 保留系统任务 Token 解析入口 `resolveToken()` 的 fallback 能力，作为未来显式系统任务白名单能力。

### Changed

- `KubeManagerHttpClient#get/post/delete`：从 `resolveToken()` 切换为 `resolveUserTokenRequired(operation, path)`。
- 新增 `resolveUserTokenRequired` 私有方法：业务请求缺用户 Token 时抛出 `IllegalStateException`，并输出安全拒绝日志。
- 保留 `resolveToken()`，但文档明确限制为未来显式系统任务入口，禁止业务 Tool 默认路径调用。

### Verified

- 定向测试：`mvn test -q -Dtest=KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 5 tests, 0 failures。
- M5.7/M5.8 安全组合回归：`mvn test -q -Dtest=KubeManagerHttpClientResolveOrgIdSecurityTest,M57FallbackOrgIdSourceContractTest,BaseToolOrganizationIdGovernanceTest,KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 17 tests, 0 failures。
- 全量测试：`mvn test -q` → ✅ 182 tests, 0 failures, 0 errors, 0 skipped。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- 本轮先做 HTTP 客户端业务入口 fail-closed，不扩大到所有上层 Tool/HITL/Graph 结构；后续可继续审计是否存在绕过 `get/post/delete` 的独立 HTTP 出口。
- `resolveToken()` 仍保留 fallback 语义，必须仅用于显式 SYSTEM_CONTEXT_ALLOWED 系统任务；未来若新增调用方，需要白名单、审计日志和测试保护。
- 当前验证以单元/回归测试为主，未重启本地服务做真实 SSE；原因是本次安全边界位于 HTTP 客户端 token 解析层，MockRestServiceServer 已精确断言请求不会发出或只携带用户 Token。

---

## [M5.7] — fallbackOrgId 可信语义彻底收口与登录 fail-safe 治理

**周期**: 2026-05-22
**交付**: 将 `fallbackOrgId` 从可信租户上下文中彻底移除：删除 getter/config 字段语义，`resolveOrgId` 改为强类型 fail-closed，登录反查失败不创建 session，并用源码扫描契约测试防止默认组织语义回流。

### Added

- 新增 `OrgIdResolutionException`，用 `USERNAME_EMPTY`、`TOKEN_UNAVAILABLE`、`USER_NOT_FOUND`、`INVALID_RESOLVED_ORG_ID` 等 Reason 锁定 orgId 解析失败原因。
- 新增 `M57FallbackOrgIdSourceContractTest`，扫描生产源码，禁止 `fallbackOrgId/getFallbackOrgId/atlas.backend.fallback-org-id/默认租户` 等可信上下文语义回流。
- 新增 `KubeManagerHttpClientResolveOrgIdSecurityTest`，覆盖空用户名、缺 token、sysadmin token 前置校验、用户未找到、非法 orgId 立即 fail-safe、可信 orgId 正向返回等 7 个边界。
- 新增 `AuthControllerLoginFailSafeTest`，验证登录成功但无法解析可信 orgId 时不创建 session。
- 新增治理方案文档：`docs/M5_7_FALLBACK_ORG_ID_GOVERNANCE_PROPOSAL_20260522.md`。

### Changed

- `KubeManagerHttpClient#resolveOrgId(username, authToken)`：
  - 必须要求 username 与本次登录 token 非空；
  - 不再使用 sysadmin/fallback token 代查普通用户租户；
  - 不再返回或缓存默认组织；
  - 用户命中但 orgId 为空、`null` 或 `1` 时立即抛异常，不继续扫桶洗白；
  - 移除 username-only orgId cache，避免跨 session / 跨租户复用旧组织上下文。
- `AuthController#login`：登录响应缺可信 orgId 时，用本次登录 token 反查；反查失败返回 502 并拒绝创建 session。
- `AtlasOrchestrator`、`AsyncContextHolder`、`AtlasGraphConfig` 清理 fallbackOrgId/fallback 文案残留，统一表达“缺可信 orgId 则 fail-safe”。
- `CHANGELOG` 中 M5.6 deferred 项已由本阶段关闭。

### Verified

- M5.7 定向测试：`mvn -Dtest=M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` → ✅ 9 tests, 0 failures。
- M5.6/M5.7 组合回归：`mvn -Dtest=TokenPropagatingTaskDecoratorTest,AsyncContextHolderTest,AtlasOrchestratorOrgIdGuardTest,M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` → ✅ 21 tests, 0 failures。
- 全量测试：`mvn test` → ✅ 177 tests, 0 failures, BUILD SUCCESS。
- 打包：`mvn -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息扫描：✅ `SECRET_SCAN_FINDINGS 0`。
- 独立 Review 第一轮：❌ 发现 username-only orgId cache 与 sysadmin token 前置校验问题。
- 独立 Review 第二轮：✅ PASS，第一轮 blocker 全部关闭。

### Closed

- 关闭 M5.6 deferred：`KubeManagerHttpClient#getFallbackOrgId()` 与 `atlas.backend.fallback-org-id` 可信配置语义已清理。

---

## [M5.6] — 异步上下文传播与 fallbackOrgId 可信语义治理

**周期**: 2026-05-22
**交付**: 将异步执行、旧 `/chat/graph`、Graph delegate、HITL resume 的安全上下文统一升级为 `token + orgId` 原子传播；移除执行链路中 `fallbackOrgId` 作为可信租户来源的兜底语义，缺 orgId 时 fail-safe。

### Added

- 新增 `AsyncContextHolder` token+orgId Runnable/Supplier/Callable/supplyAsync 重载。
- 新增 `DelegatingExecutorTest`，覆盖代理 Executor 的 token+orgId 传播与恢复。
- 新增 `TokenPropagatingTaskDecoratorTest`，覆盖 Spring TaskDecorator 捕获提交时安全上下文。
- 扩展 `AsyncContextHolderTest`，覆盖 orgId 传播、恢复和空上下文隔离。

### Changed

- `AsyncContextHolder` 统一使用“保存旧值 → 绑定快照 → finally 恢复旧值”。
- `DelegatingExecutor` 新增 token+orgId 构造并委托 `AsyncContextHolder`。
- `AtlasAsyncConfig.TokenPropagatingTaskDecorator` 同时捕获并传播 token 与 orgId。
- `AtlasOrchestrator` 旧 `/chat/graph` 入口和异步 graphTask 使用 token+orgId 包装；传统 Tool fallback 缺 orgId 时安全拒绝。
- `AtlasGraphConfig#tool_call` 缺 orgId 时 fail-safe，不再调用 `getFallbackOrgId()`；`delegate` 只信 `state.orgId` 或 ThreadLocal，不再信孤立 `organizationId` fallback。
- `HITLController` confirm/clarify resume 从 checkpoint 恢复 token+orgId，并使用 `AsyncContextHolder.wrap` 执行。

### Verified

- M5.6 定向回归：17 tests, 0 failures, BUILD SUCCESS。
- 全量测试：168 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- Diff 敏感信息扫描：`NO_NEW_SENSITIVE_IN_DIFF`。
- 独立 Review 两轮：第一轮 CONCERN 已修复，第二轮 PASS。

### Deferred

- `KubeManagerHttpClient#getFallbackOrgId()` getter 与配置注释已在 M5.7 关闭。

---

## [M5.5] — orgId 来源治理与跨租户参数污染防护

**周期**: 2026-05-22
**交付**: 将 orgScoped Tool 的组织来源从不可信 params 收口到可信 ThreadLocal/session 上下文；治理 ReAct、Graph tool_call、Graph delegate 三条参数合并链路，防止 LLM Action 或 BrainDecision parameters 覆盖 `organizationId/orgId`。

### Added

- 新增 `BaseToolOrganizationIdGovernanceTest`，锁定 `BaseTool#resolveOrganizationId` 不再接受 params 中的 `organizationId/orgId` 作为 path 权威来源。
- 新增 `OrganizationIdGovernanceRepresentativeToolTest`，覆盖 Dashboard、Deployment、Storage 写操作以及 `GpuQueryTool`、`ClusterOverviewTool`、`ImageQueryTool` 三个 legacy Tool 的跨租户注入防护。
- 新增 M5.5 orgId 来源治理审计种子文档：`docs/M5_5_ORG_ID_SOURCE_AUDIT_SEED_20260522.md`。

### Changed

- `BaseTool#resolveOrganizationId(params)` 改为只信任 `UserPermissionContext.CURRENT_ORG_ID`。
- `ReActEngine#mergeInitialAndActionParams` 过滤受保护上下文字段，LLM Action 不得覆盖或新增 `token/organizationId/orgId/conversationId/userId`。
- `AtlasGraphConfig#tool_call` 对 Brain/LLM parameters 过滤受保护字段，系统上下文字段最后写入，并绑定/清理 token 与 orgId ThreadLocal。
- `AtlasGraphConfig#delegate` 增加 orgId state strategy、子图输入透传、ThreadLocal 绑定和 finally 清理。
- `GpuQueryTool`、`ClusterOverviewTool`、`ImageQueryTool` 统一改为 `resolveOrganizationId(params)`。
- M4/M5 既有契约测试更新为使用 ThreadLocal 可信 orgId，不再通过 params 模拟租户上下文。

### Verified

- M5.5 定向测试：13 tests, 0 failures, BUILD SUCCESS。
- M5 参数治理回归：28 tests, 0 failures, BUILD SUCCESS。
- 全量测试：161 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- Diff 敏感信息扫描：`NO_NEW_SENSITIVE_IN_DIFF`。
- 独立 Review 两轮：第一轮 CONCERN 已修复，第二轮 PASS，可提交。

### Deferred

- `fallbackOrgId` 的可信语义、`AtlasAsyncConfig` TaskDecorator orgId 传播、旧 `/chat/graph` 入口上下文治理进入后续 M5.6 专项。

---

## [M5.3] — GLOBAL/PUBLIC/NO_ORG 首页公共接口 page/limit-only 契约

**周期**: 2026-05-22
**交付**: 按后端/API、安全/RBAC、测试架构三路专家会诊结论，将 5 个 `/api/public/home-info/*` 首页公共展示 Tool 纳入受限 `page/limit-only` 参数契约；`keyword/name/search/kw` 不暴露也不透传；`limit` 最大值锁定为 100。`GpuGlobalListTool` 与 `SysModelListTool` 继续 full HOLD。

### Added

- 新增 `BaseTool#pageLimitOnlyParameterSpecs()`：只声明 `page`、`limit`，不包含 `keyword` 或搜索别名。
- 新增 `BaseTool#buildPageLimitOnlyQuery(params, maxLimit)`：只构建 `page/limit` query，忽略 `keyword/name/search/kw/orgId/organizationId` 等旁路参数，并对 `limit` 执行上限校验。
- 新增 `HomeInfoPublicPageLimitContractTest`，覆盖 5 个首页公共 Tool 的参数契约、真实透传、旁路参数不透传、`limit > 100` 拒绝、非法分页拒绝。
- `SensitiveListToolHoldContractTest` 新增 M5.3 覆盖：`GpuGlobalListTool` 与 `SysModelListTool` 不暴露 `page/limit/keyword`。

### Changed

- `HomeIndustryClassListTool`、`HomeIndustryListTool`、`HomeModelListTool`、`HomeNimListTool`、`HomeRepositoryListTool` 从固定 `page=1&limit=100` 改为受限 `page/limit-only` 查询。
- 上述 5 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 `VALUE_OUT_OF_RANGE`、`TYPE_MISMATCH` 等结构化错误码。

### Deferred

- `GpuGlobalListTool`（`/api/gpu`）与 `SysModelListTool`（`/api/model`）属于全局/跨组织资源入口，继续 HOLD，不开放 `page/limit/keyword`。
- 本阶段不修改 `PUBLIC` 权限注解；GLOBAL/PUBLIC 权限收敛后续单独专项处理。

### Verified

- TDD 红灯：新增测试首次运行 4 failures，准确暴露 home-info 无参数契约、未透传 page/limit、未限制 limit 上限。
- 定向绿灯：`/usr/share/maven/bin/mvn -Dtest=HomeInfoPublicPageLimitContractTest,SensitiveListToolHoldContractTest test` → 7 tests, 0 failures, BUILD SUCCESS。
- 邻近回归：`/usr/share/maven/bin/mvn -Dtest=HomeInfoPublicPageLimitContractTest,SensitiveListToolHoldContractTest,ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 12 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 149 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 本次 diff 敏感信息扫描：`DIFF_SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断项。

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
