# M5.21 第三十四批 产品/应用镜像目录敏感只读 Tool 审计

> 日期: 2026-06-06
> 范围: `RepositoryCatalogListTool`、`RepositoryCatalogCategoryListTool`、`RepositoryCatalogTagListTool`、`RepositoryCatalogNimTagListTool`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不接入镜像拉取、重试、删除或部署创建。

## 成熟项目证据

- mature `kube-manager` 后端:
  - `RepositoryController` 使用 `@RequestMapping("/api/{organizationId}/repository")`。
  - `GET /api/{organizationId}/repository` 接收 `RepositoryParamDTO`，字段包括 `page/limit/displayName/status/industryCategory/aieSupported/aieEssential/isOneClickDeploy`。
  - `GET /api/{organizationId}/repository/category` 查询产品/应用镜像目录分类。
  - `GET /api/{organizationId}/repository/tags` 通过 query `repository` 查询普通 tag 状态。
  - `GET /api/{organizationId}/repository/nim/tags` 通过 query `repository` 查询 NIM tag 状态；后端注释说明 NIM tag 语义无法和普通 tag 共用。
  - `RepositoryServiceImpl#listRepository` 会结合 Repository、Image、RepositoryCategory 与 tag 状态，返回 NGC/NV AIE/NIM 等产品目录上下文。
- mature `vue-kube-manager` 前端:
  - `src/api/repository.js` 定义 `listRepository`、`listRepositoryCategory`、`listRepositoryTags`、`listNimRepositoryTags`，分别对齐上述四个 GET。
  - `views/ngc/index.vue` 使用 `displayName/page/limit/industryCategory` 查询 NGC 目录，并用 `listRepositoryTags` 读取 tag 状态。
  - `views/nvaie/image/index.vue` 使用 `aieSupported=true` 查询 NV AIE 目录。
  - `views/nim/index.vue` 使用 `isOneClickDeploy=1` 查询 NIM 目录，并用 `listNimRepositoryTags({ repository: row.resourceId })` 读取 NIM tag。
- 旧/现有 kube-agent:
  - M5.21-33 已把 `RegistryListTool` 对齐为站点级 `GET /api/registry`。
  - `ImageRepositoryTool` 已覆盖 `GET /api/{orgId}/image/repository`，语义是普通组织镜像仓库列表。
  - 产品/应用镜像目录此前没有独立 Tool，容易被误混到 registry 或 image repository。

## 多专家会议

- Backend/API 专家:
  - `/api/{orgId}/repository` 是组织内产品/应用镜像目录，不是站点 registry 配置。
  - `/tags` 和 `/nim/tags` 必须要求明确 `repository`，不能做无条件枚举。
- Frontend/Product 专家:
  - NGC、NV AIE、NIM 页面都复用目录列表，但筛选字段不同；NIM 的 `isOneClickDeploy` 是后续部署编排前置条件。
  - 用户自然语言里的“NIM 镜像版本”“NGC tag”“产品镜像目录”应进入本批 Tool，而不是 `registry_list`。
- Security/RBAC 专家:
  - 目录会暴露组织内可用产品、模型、镜像状态与一键部署能力，按 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`。
  - 本批不开放镜像拉取、重试、删除、部署创建；这些都会改变运行环境或镜像状态，继续 HOLD。
- Agent 架构专家:
  - 采用“一成熟 API 一个 Tool”，让 schema、权限、HITL 与测试边界清楚。
  - 新增 `RepositoryCatalogQuerySupport`，集中说明 registry、image repository、repository catalog 的边界，并统一参数校验。
- Test 架构专家:
  - 新增 mock HTTP 契约测试，锁定可信 orgId、成熟路径、query 透传、非法 repository fail-closed、敏感读取元数据。
  - 更新 `M511AtlasToolHttpContractTest` 白名单，确保 endpoint 元数据持续受静态契约约束。
- Documentation/Learning 专家:
  - 本批是学习 Agent Tool 设计中“相似名词必须拆业务边界”的典型案例：registry site、image repository、repository catalog 三者不能只靠英文相似度合并。

## 变更摘要

- 新增 `RepositoryCatalogQuerySupport`。
  - 列表 query 支持 `page/limit/displayName/status/industryCategory/aieSupported/aieEssential/isOneClickDeploy`。
  - tag query 只支持必填 `repository`，拒绝包含 query、脚本或控制字符的输入。
- 新增 `RepositoryCatalogListTool`。
  - API: `GET /api/{orgId}/repository`。
  - 用于查询组织内 NGC/NV AIE/NIM 等产品/应用镜像目录。
- 新增 `RepositoryCatalogCategoryListTool`。
  - API: `GET /api/{orgId}/repository/category`。
- 新增 `RepositoryCatalogTagListTool`。
  - API: `GET /api/{orgId}/repository/tags?repository=...`。
- 新增 `RepositoryCatalogNimTagListTool`。
  - API: `GET /api/{orgId}/repository/nim/tags?repository=...`。
- 新增 `RepositoryCatalogToolHttpContractTest`，并更新 `M511AtlasToolHttpContractTest`。
- 更新 `intents.yml`，新增四个 repository catalog 自然语言入口。

## HOLD 清单

- 镜像拉取、重试、删除、推送、构建、加载等会改变镜像状态的能力继续 HOLD。
- NIM 一键部署创建仍由 `NimCreateTool` 占位保护；后续需要先完成 repository/tag/template/resource preset/deploy_create_instance 编排审计。
- `GET /api/registry/repo-tag` 仍 HOLD，因为它可能触发站点级 registry 外部枚举，语义不同于本批本地目录 tag 状态。
- 目录分类的增删改若后续存在接口，必须按配置写入能力单独审计。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool` 继续等待后端边界证据。

## 验证

- 已通过:
  - `mvn -q "-Dtest=RepositoryCatalogToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态密钥扫描：未发现真实凭据；命中项为 0。
  - `mvn -q test`

> 说明：全量测试中测试 profile 会尝试拉取 embedding model，网络超时后按预期降级，测试进程最终返回成功。

## 外部记忆同步

- 已按用户要求同步恢复文档到 `H:\codex重要文件\kube-agent`。
- 后续恢复本批时，先阅读本文件、`docs/PROJECT_MISSION_AND_MEMORY.md`、`docs/SESSION_PROGRESS_20260606_M521_29.md` 与 `docs/M5_21_WAVE_INDEX_20260606.md`。

## 是否访问真实 8100

否。本批只使用 mature 源码证据、前端调用证据和 mock HTTP client 契约测试。
