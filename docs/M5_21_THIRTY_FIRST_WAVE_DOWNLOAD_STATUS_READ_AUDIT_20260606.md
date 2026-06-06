# M5.21 第三十一批 下载任务状态只读 Tool 审计

> 日期: 2026-06-06
> 范围: `UploadStatusListTool`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不接入下载任务开始/暂停/恢复/删除。

## 成熟项目证据

- mature `kube-manager` 后端:
  - `DownloadController` 使用 `@RequestMapping("/api/{organizationId}/download")`。
  - `GET /api/{organizationId}/download` 查询下载任务列表。
  - `GET /api/{organizationId}/download/status/{id}` 查询指定任务状态。
  - `GET /api/{organizationId}/download/progress/{id}` 查询指定任务进度。
  - `POST /start`、`POST /pause/{id}`、`POST /resume/{id}`、`DELETE /{id}` 均改变任务状态或文件生命周期，暂不接入。
- mature `vue-kube-manager` 前端:
  - `src/api/upload.js#listUploadFile(params)` 调用 `/api/{organizationId}/download`。
  - `src/api/upload.js#getUploadFileStatus(id)` 调用 `/api/{organizationId}/download/status/${id}`。
  - `src/api/upload.js#getUploadFileProgress(id)` 调用 `/api/{organizationId}/download/progress/${id}`。
- 旧 kube-agent:
  - `DownloadTaskListTool` 已覆盖真实下载任务列表 `/api/{orgId}/download`。
  - `UploadStatusListTool` 误用不存在的 `/api/{orgId}/download/status` 分页列表，并暴露 `page/limit/keyword`。

## 多专家会诊

- Backend/API 专家:
  - 状态读取必须按任务 ID 定位；不存在全局 `download/status` 分页列表。
  - `id` 是 URL path 片段，必须强制正整数校验，避免 LLM 输出路径注入字符串。
- Frontend/Product 专家:
  - 用户工作流应是先用 `download_task_list` 找到任务，再查看单个任务状态。
  - 历史 `upload_status_list` 名称可以保留兼容，但描述和示例必须指向“任务 id 状态”。
- Security/RBAC 专家:
  - 任务状态可能包含文件路径、任务归属、大小和状态元数据，按 `SENSITIVE_READ + requiresConfirmation=true` 处理。
  - 本批只读不改变任务，但不接入开始、暂停、恢复、删除等状态变更能力。
- Agent 架构专家:
  - 保留 `upload_status_list` intentId，降低路由迁移风险。
  - Tool schema 只暴露必填 `id`，不再复用标准列表三件套。
  - `download_task_list` 负责列表检索，`upload_status_list` 负责单任务状态，这是更适合 ReAct 多步推理的 Tool 分工。
- Test 架构专家:
  - 新增 mock HTTP 契约测试，验证成熟路径、空 query、非法 `id` 短路、schema 和风险元数据。
  - 从标准列表参数契约中移除 `UploadStatusListTool`，防止旧伪列表行为回流。
- Documentation/Learning 专家:
  - 本批体现 Agent Tool 设计原则: 不要被前端文件名或历史 Tool 名误导，要以 controller 方法和 UI 调用链确认真实业务语义。

## 变更摘要

- `UploadStatusListTool`
  - 路径: `/api/{orgId}/download/status` -> `/api/{orgId}/download/status/{id}`
  - 参数: `page/limit/keyword` -> 必填 `id`
  - 元数据: `GET + SENSITIVE_READ + requiresConfirmation=true`
  - 返回语义: 查询指定下载任务状态，而不是查询上传状态列表。
- 新增 `DownloadTaskStatusToolHttpContractTest`
  - 锁定成熟路径与空 query。
  - 拒绝 `../42` 等路径注入。
  - 验证只暴露 `id`，不暴露标准列表参数。
  - 验证敏感读取和人工确认元数据。
- 更新 `M511AtlasToolHttpContractTest` legacy GET 白名单。
- 更新 `ListToolParameterPassThroughContractTest` 与 `ListToolParameterSpecContractTest`，移除 `UploadStatusListTool` 旧列表预期。
- 更新 `intents.yml`，让自然语言入口要求任务 `id`。

## HOLD 清单

- `GET /api/{orgId}/download/progress/{id}`: M5.21-31 时仍需单独接入；已在 M5.21-32 新增 `download_task_progress`。
- `POST /api/{orgId}/download/start`: 开始下载任务，改变后端状态，暂不接入。
- `POST /api/{orgId}/download/pause/{id}`: 暂停下载任务，改变后端状态，暂不接入。
- `POST /api/{orgId}/download/resume/{id}`: 恢复下载任务，改变后端状态，暂不接入。
- `DELETE /api/{orgId}/download/{id}`: 删除任务且可删除文件，暂不接入。
- `RegistryListTool`: M5.21-31 时仍需单独确认 `/api/registry` 与 `/api/{orgId}/repository` 的产品语义；已在 M5.21-33 对齐为站点级 `GET /api/registry`。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: 继续等待后端边界证据。

## 验证

- 已通过:
  - `mvn -q "-Dtest=DownloadTaskStatusToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态敏感信息扫描: 未发现真实凭证，命中项仅为文档/配置注释中的 api-key/password 说明。
  - `mvn -q test`

> 说明: 全量测试在 test profile 下尝试下载本地缺失的 embedding 模型并超时降级，这是当前测试环境的预期行为；Maven 退出码为 0。

## 外部记忆同步

- 待提交前同步到 `H:\codex重要文件\kube-agent`:
  - `PROJECT_MISSION_AND_MEMORY.md`
  - `SESSION_PROGRESS_20260606_M521_29.md`
  - `M5_21_THIRTY_FIRST_WAVE_DOWNLOAD_STATUS_READ_AUDIT_20260606.md`
  - `M5_21_WAVE_INDEX_20260606.md`
  - `CHANGELOG.md`

## 是否访问真实 8100

否。本批只使用源码证据、前端调用证据和 mock HTTP client 契约测试。
