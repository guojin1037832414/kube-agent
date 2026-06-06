# M5.21-29 Legacy GET Tool HTTP 元数据与路径对齐审计

> 结论: 本批只补齐证据明确的历史 GET Tool HTTP/risk 元数据，并修正 2 个成熟后端路径错配；不新增写入能力，不访问真实 `8100`。

## 成熟项目证据

- 后端:
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\file\controller\DataSetController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\file\controller\FileController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\file\controller\MaterialController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\file\controller\DownloadController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\docker\controller\ImageController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\client\controller\PyTorchJobController.java`
  - `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\system\controller\InboxMessageController.java`
- 前端:
  - `F:\gitProject\vue-kube-manager\src\api\data-set.js`
  - `F:\gitProject\vue-kube-manager\src\api\file-material.js`
  - `F:\gitProject\vue-kube-manager\src\api\image.js`
  - `F:\gitProject\vue-kube-manager\src\api\pytorch-job.js`
  - `F:\gitProject\vue-kube-manager\src\api\inbox-message.js`
- mature org-scoped GET:
  - `GET /api/{organizationId}/data-set`
  - `GET /api/{organizationId}/file`
  - `GET /api/{organizationId}/material/folders`
  - `GET /api/{organizationId}/download`
  - `GET /api/{organizationId}/image`
  - `GET /api/{organizationId}/pytorch-job`
  - `GET /api/{organizationId}/inbox-message`

## 专家会诊结论

- 后端/API 专家: `file_material_list` 旧路径 `/api/{orgId}/file-material` 与成熟后端 `MaterialController` 不一致，应对齐为 `/api/{orgId}/material/folders`；`inbox_message_list` 旧路径 `/api/{orgId}/message` 与成熟后端 `InboxMessageController` 不一致，应对齐为 `/api/{orgId}/inbox-message`。
- 安全/RBAC 专家: 数据集、文件、下载任务、素材目录和站内信均可能暴露租户文件结构、下载记录或通知内容，统一标记为 `SENSITIVE_READ + requiresConfirmation=true`；镜像列表和 PyTorch 作业列表沿用当前普通资源列表语义，标记为 `READ`。
- 测试架构专家: 只使用 mock/static contract 验证 Tool 到 HTTP client 的路径、query 透传与注解元数据，不直接调用 kube-manager。

## 本批交付

- 为 7 个历史 GET Tool 补齐 `httpMethod/apiEndpoints/operationType/requiresConfirmation` 元数据:
  - `DataSetListTool`
  - `FileListTool`
  - `DownloadTaskListTool`
  - `FileMaterialListTool`
  - `InboxMessageListTool`
  - `ImageQueryTool`
  - `PytorchJobListTool`
- 修正 2 个路径错配:
  - `FileMaterialListTool`: `/api/{orgId}/file-material` -> `/api/{orgId}/material/folders`
  - `InboxMessageListTool`: `/api/{orgId}/message` -> `/api/{orgId}/inbox-message`
- 更新契约测试:
  - `M511AtlasToolHttpContractTest` 新增 legacy GET 精确白名单
  - `ListToolParameterPassThroughContractTest` 更新成熟路径期望

## HOLD 清单

- `RegistryListTool`: 当前 Tool 使用 `/api/{orgId}/registry`，成熟项目同时存在站点级 `/api/registry` 与组织级 `/api/{organizationId}/repository` 语义，暂不混入本批。
- `MigConfigListTool`: 当前 Tool 使用 `/api/{orgId}/migConfig`，成熟 evidence 指向 `GET /api/mig/{gpuId}` 且需要 `gpuId`，需单独迁移。
- `UploadStatusListTool`: 当前 Tool 使用 `/api/{orgId}/download/status`，成熟后端是 `GET /api/{organizationId}/download/status/{id}`，需改为按任务 id 查询后再纳入。
- `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: 现有 evidence 不足以确认成熟列表后端边界，继续 HOLD。
- RBAC/组织/配额审批等敏感管理面列表: 仍由 `SensitiveListToolHoldContractTest` 保护，不在本批扩大 page/limit/keyword 枚举能力。

## 验证记录

- 已通过:
  - `mvn -q "-Dtest=M511AtlasToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest" test`
  - `mvn -q "-Dtest=M511AtlasToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,SensitiveListToolHoldContractTest,M520McpManifestSafetyContractTest,ToolRegistryPermissionTest" test`
- 备注: `ToolRegistryPermissionTest` 在 test profile 中尝试下载 embedding model 超时后按预期降级，测试最终通过。
- 真实环境: 本批没有访问真实 `8100`，没有调用写入、删除、状态变更或文件内容读取接口。
