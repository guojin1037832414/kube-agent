# M5.21 第三十二批 下载任务进度敏感只读 Tool 审计

> 日期: 2026-06-06
> 范围: `DownloadTaskProgressTool`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不接入下载任务开始/暂停/恢复/删除。

## 成熟项目证据

- mature `kube-manager` 后端:
  - `DownloadController` 使用 `@RequestMapping("/api/{organizationId}/download")`。
  - `GET /api/{organizationId}/download/progress/{id}` 调用 `downloadService.getProgress(id)`，返回指定下载任务的实时进度。
  - `GET /api/{organizationId}/download/status/{id}` 已由 M5.21-31 对齐为状态读取。
  - `POST /start`、`POST /pause/{id}`、`POST /resume/{id}`、`DELETE /{id}` 均改变任务或文件生命周期，继续 HOLD。
- mature `vue-kube-manager` 前端:
  - `src/api/upload.js#getUploadFileProgress(id)` 调用 `/api/{organizationId}/download/progress/${id}`。
  - `src/api/upload.js#getUploadFileStatus(id)` 调用 `/api/{organizationId}/download/status/${id}`。
- 旧 kube-agent:
  - 已有 `DownloadTaskListTool` 查询任务列表。
  - 已有 `UploadStatusListTool` 查询指定任务状态。
  - 缺少单独的任务进度 Tool，导致 Agent 不能按成熟前端工作流读取 progress、downloaded、totalSize 等进度字段。

## 多专家会诊

- Backend/API 专家:
  - `progress/{id}` 是独立 GET 接口，不应混进 status Tool。
  - `id` 是 URL path 片段，必须复用正整数校验。
- Frontend/Product 专家:
  - UI 将状态、进度、列表作为不同 API，Agent 也应支持相同工作流: 先列表找任务，再查状态或进度。
  - 用户自然语言常说“上传进度/下载进度”，但成熟 API 领域名是 download task，Tool 文案需兼容两类说法。
- Security/RBAC 专家:
  - 进度响应可能包含任务状态、已下载大小、总大小等文件任务上下文，按 `SENSITIVE_READ + requiresConfirmation=true` 处理。
  - 本批不接入暂停、恢复、删除，避免 Agent 直接改变下载任务。
- Agent 架构专家:
  - 新增 `download_task_progress`，避免继续扩大历史 `upload_status_list` 的语义。
  - 抽出 `DownloadTaskQuerySupport`，让状态和进度 Tool 共用同一套 ID schema 与路径注入防护。
- Test 架构专家:
  - 新增 mock HTTP 契约测试，验证成熟路径、空 query、非法 `id` 短路、schema 和风险元数据。
  - 更新 M5.21 legacy GET 白名单，将 progress 纳入受审计的敏感只读接口。
- Documentation/Learning 专家:
  - 本批展示一个 Agent 工具设计原则: 当成熟系统有清晰的 API 分工时，Agent Tool 也应保持分工清晰，避免一个 Tool 承担过多语义。

## 变更摘要

- 新增 `DownloadTaskProgressTool`
  - 路径: `GET /api/{orgId}/download/progress/{id}`
  - 参数: 必填正整数 `id`
  - 元数据: `SENSITIVE_READ + requiresConfirmation=true`
- 新增 `DownloadTaskQuerySupport`
  - 统一下载任务 ID schema。
  - 统一 `id` path 片段正整数校验。
  - `UploadStatusListTool` 改为复用该 helper。
- 新增 `DownloadTaskProgressToolHttpContractTest`
  - 锁定成熟路径与空 query。
  - 拒绝 `42/extra` 等路径注入。
  - 验证只暴露 `id`，不暴露标准列表参数。
  - 验证敏感读取和人工确认元数据。
- 更新 `M511AtlasToolHttpContractTest` legacy GET 白名单。
- 更新 `intents.yml`，新增 `download_task_progress` 自然语言入口。

## HOLD 清单

- `POST /api/{orgId}/download/start`: 开始下载任务，改变后端状态，暂不接入。
- `POST /api/{orgId}/download/pause/{id}`: 暂停下载任务，改变后端状态，暂不接入。
- `POST /api/{orgId}/download/resume/{id}`: 恢复下载任务，改变后端状态，暂不接入。
- `DELETE /api/{orgId}/download/{id}`: 删除任务且可删除文件，暂不接入。
- `RegistryListTool`: M5.21-32 时仍需单独确认 `/api/registry` 与 `/api/{orgId}/repository` 的产品语义；已在 M5.21-33 对齐为站点级 `GET /api/registry`。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: 继续等待后端边界证据。

## 验证

- 已通过:
  - `mvn -q "-Dtest=DownloadTaskProgressToolHttpContractTest,DownloadTaskStatusToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态敏感信息扫描: 未发现真实凭证，命中项仅为文档/配置注释中的 api-key/password 说明。
  - `mvn -q test`

> 说明: 全量测试在 test profile 下尝试下载本地缺失的 embedding 模型并超时降级，这是当前测试环境的预期行为；Maven 退出码为 0。

## 外部记忆同步

- 待提交前同步到 `H:\codex重要文件\kube-agent`:
  - `PROJECT_MISSION_AND_MEMORY.md`
  - `SESSION_PROGRESS_20260606_M521_29.md`
  - `M5_21_THIRTY_SECOND_WAVE_DOWNLOAD_PROGRESS_READ_AUDIT_20260606.md`
  - `M5_21_WAVE_INDEX_20260606.md`
  - `CHANGELOG.md`

## 是否访问真实 8100

否。本批只使用源码证据、前端调用证据和 mock HTTP client 契约测试。
