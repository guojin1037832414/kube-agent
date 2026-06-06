# M5.21 第三十三批 镜像注册处敏感只读 Tool 审计

> 日期: 2026-06-06
> 范围: `RegistryListTool`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不接入 registry 新增/更新/删除。

## 成熟项目证据

- mature `kube-manager` 后端:
  - `RegistrySiteController` 使用 `@RequestMapping("/api/registry")`。
  - `GET /api/registry` 查询镜像注册处列表，query 参数是 `keyWord`。
  - `POST /api/registry`、`PUT /api/registry`、`DELETE /api/registry/{id}` 均带 `SYS_ADMIN_ONLY` 隔离策略。
  - `RegistrySiteDTO` 返回 `id/displayName/url/username`，不返回 password，但仍包含注册处地址和用户名。
  - `RepositoryController` 使用 `@RequestMapping("/api/{organizationId}/repository")`，是组织内产品/应用镜像目录，不是 registry 配置。
- mature `vue-kube-manager` 前端:
  - `src/api/registry.js#listRegistry(query)` 调用 `/api/registry`。
  - `src/views/image/registry/index.vue` 使用 `listRegistry(this.listQuery)` 管理镜像注册处配置。
  - `src/api/repository.js#listRepository(query)` 调用 `/api/{organizationId}/repository`，用于 NV AIE / NGC / NIM 等产品镜像目录。
  - `src/api/image.js#listRepository(query)` 调用 `/api/{organizationId}/image/repository`，已有 `image_repository` Tool 覆盖组织镜像仓库列表。
- 旧 kube-agent:
  - `RegistryListTool` 误用 `/api/{orgId}/registry`，并暴露 `page/limit/keyword` 标准列表契约。

## 多专家会诊

- Backend/API 专家:
  - `registry` 是站点级镜像注册处认证配置，不带组织路径。
  - 成熟 GET 只接收 `keyWord`，不支持 `page/limit`。
- Frontend/Product 专家:
  - “镜像注册处配置”和“组织应用仓库目录”是两个页面/接口语义，Agent 不能用一个 Tool 混查。
  - 历史 `registry_list` 可以保留，但文案应改成“镜像注册处列表/配置”。
- Security/RBAC 专家:
  - 返回值含 registry URL 与 username，属于站点级敏感配置读取，按 `SENSITIVE_READ + requiresConfirmation=true`。
  - 增删改是 `SYS_ADMIN_ONLY`，本批不接入。
- Agent 架构专家:
  - 从标准分页列表契约中移除 `registry_list`，避免 LLM 传 `page/limit` 形成伪参数。
  - `keyword` 作为 alias 兼容旧自然语言，但最终透传成熟后端字段 `keyWord`。
- Test 架构专家:
  - 新增 mock HTTP 契约测试，验证站点路径、`keyWord` 透传、alias 兼容、无分页参数和风险元数据。
  - 更新 M5.21 legacy GET 白名单，将 registry 纳入敏感只读受审计端点。
- Documentation/Learning 专家:
  - 本批体现 Agent 开发原则: 名字相近的 API 不能靠字符串猜，要以成熟控制器、前端页面和 DTO 返回字段确认业务边界。

## 变更摘要

- `RegistryListTool`
  - 路径: `/api/{orgId}/registry` -> `/api/registry`
  - 参数: `page/limit/keyword` -> 可选 `keyWord`
  - 元数据: `SENSITIVE_READ + requiresConfirmation=true`
  - 语义: 查询镜像注册处配置列表，不查询组织产品 repository 目录。
- 新增 `RegistrySiteToolHttpContractTest`
  - 锁定成熟路径 `/api/registry`。
  - 验证 `keyWord` 透传与 `keyword` alias 兼容。
  - 验证不暴露 `page/limit`。
  - 验证敏感读取和人工确认元数据。
- 更新 `M511AtlasToolHttpContractTest` legacy GET 白名单。
- 更新 `ListToolParameterPassThroughContractTest` 与 `ListToolParameterSpecContractTest`，移除 registry 旧分页列表预期。
- 更新 `intents.yml`，让自然语言入口表达“镜像注册处”。

## HOLD 清单

- `POST /api/registry`: 新增镜像注册处，`SYS_ADMIN_ONLY`，暂不接入。
- `PUT /api/registry`: 更新镜像注册处，`SYS_ADMIN_ONLY`，暂不接入。
- `DELETE /api/registry/{id}`: 删除镜像注册处，`SYS_ADMIN_ONLY`，暂不接入。
- `GET /api/registry/repo-tag`: 查询注册处镜像列表，可能触发外部 registry 访问，需单独评估。
- `GET /api/{orgId}/repository`: 组织内产品/应用镜像目录，语义不同，后续单独审计。
- `GET /api/{orgId}/repository/category`、`/tags`、`/nim/tags`: 目录分类和 tag 查询，后续单独审计。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: 继续等待后端边界证据。

## 验证

- 已通过:
  - `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态密钥扫描：未发现真实凭据；命中项仅为文档/配置注释中解释 api-key、password 等安全词汇。
  - `mvn -q test`

> 说明：全量测试中测试 profile 会尝试拉取 embedding model，网络超时后按预期降级，测试进程最终返回成功。

## 外部记忆同步

- 已按用户要求同步恢复文档到 `H:\codex重要文件\kube-agent`。
- 后续恢复本批时，先阅读本文件、`docs/PROJECT_MISSION_AND_MEMORY.md`、`docs/SESSION_PROGRESS_20260606_M521_29.md` 与 `docs/M5_21_WAVE_INDEX_20260606.md`。

## 是否访问真实 8100

否。本批只使用源码证据、前端调用证据和 mock HTTP client 契约测试。
