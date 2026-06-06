# M5.21-28 文件/存储准备上下文敏感只读 Tool 审计

> 结论: 本批仅接入当前组织文件挂载路径、PVC 挂载选项、存储选项、已申请存储详情和训练存储上下文 GET 读取能力；文件内容读取/下载、上传、编辑、复制移动、压缩解压、目录创建、存储申请/扩容/删除继续 HOLD。

## 成熟项目证据

- 后端: `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\file\controller\FileController.java`
- 前端:
  - `F:\gitProject\vue-kube-manager\src\api\file.js`
  - `F:\gitProject\vue-kube-manager\src\api\data-set.js`
- mature org-scoped GET:
  - `GET /api/{organizationId}/file/volume-path`
  - `GET /api/{organizationId}/file/volume-path/user`
  - `GET /api/{organizationId}/file/volume-path/user-extra`
  - `GET /api/{organizationId}/file/claimed-volume-option`
  - `GET /api/{organizationId}/file/selectStorage?name=...`
  - `GET /api/{organizationId}/file/train-storage`
  - `GET /api/{organizationId}/file/storage/option`
- 同域高风险或敏感内容接口:
  - `GET /api/{organizationId}/file/download`
  - `POST /api/{organizationId}/file/preview`
  - `POST /api/{organizationId}/file/edit`
  - `POST /api/{organizationId}/file/upload`
  - `POST /api/{organizationId}/file/refer`
  - `POST /api/{organizationId}/file/directory`
  - `POST /api/{organizationId}/file/batch-copy-move`
  - `DELETE /api/{organizationId}/file/batch-delete`
  - `POST /api/{organizationId}/file/storage`
  - `POST /api/{organizationId}/file/updateStorage`
  - `DELETE /api/{organizationId}/file/deleteStorage`

## 专家会诊结论

- 后端/API 专家: 这些 GET 接口属于部署、训练、课程环境和存储申请前的准备上下文，能帮助 Agent 正确选择 volume、path、PVC 和 storage option。
- 安全/RBAC 专家: 文件路径、用户挂载、PVC 和存储开关会暴露组织文件系统结构与资源策略，统一按 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true` 处理。
- 测试架构专家: 无参接口固定空 query；`selectStorage` 只允许 `name`；不透传 `organizationId/orgId/path/namespace/userId/scope/keyword` 等可能扩大枚举面或改变权限边界的字段；全部使用 mock HTTP client 验证。
- 外部参考:
  - OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
  - Microsoft Agent Framework Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
  - OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 本批交付

- `FileVolumePathTool` 从 M5.3 的 page/limit-only 普通只读迁移为敏感只读
- `FileUserVolumePathTool`
- `FileUserExtraVolumePathTool`
- `FileClaimedVolumeOptionListTool`
- `FileStorageOptionTool` 从 M5.3 的 page/limit-only 普通只读迁移为敏感只读
- `FileSelectStorageTool` 从普通只读迁移为敏感只读，并收敛为仅透传 `name`
- `FileTrainStorageTool`
- `FileStorageQuerySupport`
- `file_user_volume_path`、`file_user_extra_volume_path`、`file_claimed_volume_option_list`、`file_train_storage` 意图；既有 `file_volume_path`、`file_storage_option`、`file_select_storage` 复用原入口
- `FileStorageReadToolHttpContractTest`
- `M511AtlasToolHttpContractTest` 新增 file/storage `SENSITIVE_READ` endpoint 精确白名单
- `HomeInfoPublicPageLimitContractTest` 移除 `file_volume_path` 与 `file_storage_option` 的普通只读 page/limit 预期，避免旧契约继续把文件/存储路径当作公开低敏入口

## HOLD 清单

- 文件列表扩大枚举、文件内容预览、下载和公开下载
- 上传、URL 引用上传、新建文件、新建目录、编辑文件
- 复制、移动、重命名、压缩、解压、删除和批量删除
- 存储申请、扩容、删除、删除用户存储申请
- 数据集目录写操作和文件管理类操作
- 任何站点级、跨组织、用户指定 namespace/path 的文件或存储查询

## 验证计划

- 定向测试: `mvn -q "-Dtest=FileStorageReadToolHttpContractTest,BcmAllocationReadToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest" test`
- 关键回归: 覆盖 ReAct、HITL fail-closed、安全执行、M5.21 HTTP 契约与 ToolRegistry。
- 静态检查: `git diff --check`；扫描新增 Tool 不包含 `post/put/patch/delete`。
- 真实环境: 本批不访问真实 `8100`，不调用任何写入、删除或文件内容读取接口。
