# M5.21 第三十批 MIG 配置只读 Tool 审计

> 日期: 2026-06-06
> 范围: `MigConfigListTool`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不接入 MIG 新增/修改/删除。

## 成熟项目证据

- mature `kube-manager` 后端:
  - `MigConfigController` 使用 `@RequestMapping("/api/mig")`
  - `GET /api/mig/{gpuId}` 返回指定 GPU 规格的 MIG 配置清单。
  - `POST /api/mig`、`PUT /api/mig`、`DELETE /api/mig/{id}` 均带 `SYS_ADMIN_ONLY` 隔离策略，属于后续高风险管理操作。
- mature `vue-kube-manager` 前端:
  - `src/api/migConfig.js#getMigConfig(id)` 调用 `/api/mig/${id}`。
  - `src/views/gpu/index.vue` 中用户点击某一行 GPU 的 `MIG List` 后，使用该行 `id` 查询 MIG 清单。
- 旧 kube-agent:
  - `MigConfigListTool` 误用 `/api/{orgId}/migConfig`，并暴露 `page/limit/keyword`，与成熟系统不一致。

## 多专家会诊

- Backend/API 专家:
  - MIG 读取不是租户路径，也不是分页列表；真实契约是站点级知识库路径 `GET /api/mig/{gpuId}`。
  - `gpuId` 是 URL path 片段，必须强制正整数校验。
- Frontend/Product 专家:
  - 用户工作流是先查 GPU 规格，再查看某一 GPU 支持的 MIG 切分。
  - Tool 描述和 intent 示例应引导用户提供 `gpuId`，而不是说“列出所有 MIG 配置”。
- Security/RBAC 专家:
  - 只读 MIG 规格本身不改变状态，标记为 `READ`，无需 HITL。
  - 但它属于 GPU/集群管理上下文，不应匿名公开给 Agent，权限收敛为 `AUTHENTICATED`。
  - 不接入 `POST/PUT/DELETE /api/mig`，避免让 Agent 直接改变 GPU 切分知识库。
- Agent 架构专家:
  - 保留 `mig_config_list` intentId 兼容历史意图，但 schema 只暴露必填 `gpuId`。
  - 不复用标准列表三件套，避免 LLM 以为可以分页、搜索或全局枚举。
  - MCP manifest 只应导出匿名安全的 `PUBLIC + READ` Tool；`mig_config_list` 虽是 READ，但需要登录，不进入外部 MCP 安全清单。
- Test 架构专家:
  - 新增 mock HTTP 契约测试，验证路径、空 query、非法 `gpuId` 短路和 metadata。
  - 从标准列表参数契约中移除 MIG，防止旧伪列表行为回流。
- Documentation/Learning 专家:
  - 本批体现一个重要 Agent 开发原则: Tool 名字像 list 不代表后端真的是列表；以成熟前后端证据为准。

## 变更摘要

- `MigConfigListTool`
  - 路径: `/api/{orgId}/migConfig` -> `/api/mig/{gpuId}`
  - 参数: `page/limit/keyword` -> 必填 `gpuId`
  - 权限: `PUBLIC` -> `AUTHENTICATED`
  - 元数据: `GET + READ + requiresConfirmation=false`
- 新增 `MigConfigReadToolHttpContractTest`
  - 锁定成熟路径与空 query。
  - 拒绝 `../42` 等路径注入。
  - 验证只暴露 `gpuId`，不暴露标准列表参数。
- 更新 `M511AtlasToolHttpContractTest` legacy GET 白名单。
- 更新 `ListToolParameterPassThroughContractTest` 与 `ListToolParameterSpecContractTest`，移除 MIG 旧列表预期。
- 更新 `intents.yml`，让自然语言入口要求 `gpuId`。
- 加固 `McpToolManifestService`，将导出规则收紧为 `PUBLIC + READ + requiresConfirmation=false`。

## HOLD 清单

- `POST /api/mig`: 新增 MIG 配置，`SYS_ADMIN_ONLY`，暂不接入。
- `PUT /api/mig`: 修改 MIG 配置，`SYS_ADMIN_ONLY`，暂不接入。
- `DELETE /api/mig/{id}`: 删除 MIG 配置，`SYS_ADMIN_ONLY`，暂不接入。
- `RegistryListTool`: 仍需单独确认 `/api/registry` 与 `/api/{orgId}/repository` 的产品语义。
- `UploadStatusListTool`: M5.21-30 时仍需从伪列表改为按任务 `id` 查询；已在 M5.21-31 对齐为 `GET /api/{orgId}/download/status/{id}`。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: 继续等待后端边界证据。

## 验证

- 已通过:
  - `mvn -q "-Dtest=MigConfigReadToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态敏感信息扫描: 未发现真实凭证，命中项仅为文档/配置注释中的 api-key/password 说明。
  - `mvn -q test`

> 说明: 全量测试在 test profile 下尝试下载本地缺失的 embedding 模型并超时降级，这是当前测试环境的预期行为；Maven 退出码为 0。

## 外部记忆同步

- 已同步到 `H:\codex重要文件\kube-agent`:
  - `PROJECT_MISSION_AND_MEMORY.md`
  - `SESSION_PROGRESS_20260606_M521_29.md`
  - `M5_21_THIRTIETH_WAVE_MIG_CONFIG_READ_AUDIT_20260606.md`
  - `M5_21_WAVE_INDEX_20260606.md`
  - `CHANGELOG.md`

## 是否访问真实 8100

否。本批只使用源码证据、前端调用证据和 mock HTTP client 契约测试。
