# M5.21 第三十五批 NIM 部署只读预检 Tool 审计

> 日期: 2026-06-06
> 范围: `NimDeploymentPreflightTool`、`NimDeploymentPreflightSupport`
> 约束: 只做源码对齐与 mock 契约测试，不访问真实 kube-manager `8100`，不创建 Deployment，不轮询 NIM 服务，不读取或展示真实 API Key。

## 成熟项目证据

- mature `vue-kube-manager` 前端 `src/views/nim/index.vue`:
  - NIM 页面使用 `listRepository({ displayName, page, limit, industryCategory, isOneClickDeploy: 1 })` 查询一键部署目录。
  - 点击创建前会拒绝系统组织或 `SYS_ADMIN` 用户，并检查 NVAIE license 是否有效。
  - 创建流程先调用 `listNimRepositoryTags({ repository: row.resourceId })`，无 tag 时停止。
  - 前端使用首个 tag 组装 `temp.image = repository + ':' + tag`。
  - 再调用 `listTemplate({ image: temp.image, templateType: 'NIM' })`，无模板时停止。
  - 最后才执行 `mergeTemplate(...)`、`formatApplication(...)` 和 `createDeployment(requestBody)`。
- mature `vue-kube-manager` 前端 `src/utils/request-formatter.js`:
  - `formatApplication` 会将 CPU 核转换为毫核、内存 GB 转为 MiB。
  - GPU 使用 `gpuSpec` 从 `gpuMap` 解析 `gpuModel/migConfig`，并处理整卡、MIG 与显存限制。
  - 默认启用 `enableSecondNetwork`，并处理带宽和自动扩缩容配置。
- mature `kube-manager` 后端:
  - `RepositoryController` 提供 `GET /api/{organizationId}/repository` 和 `GET /api/{organizationId}/repository/nim/tags`。
  - `TemplateController` 提供 `GET /api/{organizationId}/template`。
  - `TemplateParamDTO` 支持 `image` 与 `templateType`，其中 `templateType=NIM` 是 NIM 模板筛选关键字段。
  - `DeploymentController` 的创建接口仍是 `POST /api/{organizationId}/deployment`，但本批不调用。
- 现有 kube-agent:
  - M5.21-34 已新增 `RepositoryCatalogNimTagListTool`，为 NIM tag 查询提供单 API 只读入口。
  - `DeployCreateTool` 已包含 deployment 创建、单位换算与 GPU map 解析逻辑，但 NIM 一键部署还缺少完整模板合并与 HITL 创建编排。
  - `NimCreateTool` 仍为 fail-closed placeholder，避免错误调用历史 `/api/{orgId}/pod`。

## 多专家会议

- Backend/API 专家:
  - NIM 一键部署不是一个独立后端 create API，而是 repository、tag、template、deployment create 的组合链路。
  - 当前阶段可以安全读取前三段 GET，但不能伪造完整 DeploymentDTO。
- Frontend/Product 专家:
  - 用户说“部署 NIM”时，Agent 应先帮用户确认可部署模型、可用 tag 和模板资源，而不是直接创建。
  - 预检结果应保留候选目录、候选 tag、候选模板和默认选择，方便下一轮 HITL 展示。
- Security/RBAC 专家:
  - 目录、tag 和模板都可能暴露组织内可用镜像、模型、资源规格和商业能力，按 `SENSITIVE_READ + requiresConfirmation=true`。
  - 本批不绕过前端里的系统组织限制、license 检查和管理员确认；这些在正式创建前必须重新设计到后端执行链。
- Agent 架构专家:
  - 新增 `nim_deployment_preflight`，避免把只读规划塞进 `nim_create`，保持 Tool 名称与副作用一致。
  - 多 GET 只读编排允许作为 Agent planning primitive，但需要在 HTTP 契约测试里声明全部 endpoint。
- Test 架构专家:
  - 使用 mock `KubeManagerHttpClient` 锁定三段 GET 顺序、query、可信 orgId、非法 repository 短路、无模板 fail-closed。
  - 扩展 `M511AtlasToolHttpContractTest`，让 endpoint 精确白名单支持一个 Tool 声明多个成熟 GET endpoint。
- Documentation/Learning 专家:
  - 这是学习 Agent 开发里“把高风险动作拆成可解释前置预检”的典型模式：先形成可审计计划，再进入人工确认和写操作。

## 变更摘要

- 新增 `NimDeploymentPreflightSupport`。
  - 暴露参数: `repository/displayName/industryCategory/tag/serviceName`。
  - 固定目录 query: `page=1&limit=20&isOneClickDeploy=true`，可选 `displayName/industryCategory`。
  - 校验 `repository` 只能来自成熟后端返回的安全目录标识。
  - 选择 tag 后组装 `image = repository + ':' + tag`，再用 `image + templateType=NIM` 查询模板。
  - 预检返回 `sideEffect=NONE`、`preflightOnly=true`、候选目录、候选 tag、候选模板、选中 image 和下一步人工确认事项。
- 新增 `NimDeploymentPreflightTool`。
  - API:
    - `GET /api/{orgId}/repository`
    - `GET /api/{orgId}/repository/nim/tags`
    - `GET /api/{orgId}/template`
  - 风险元数据: `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`。
  - 不调用 `POST /api/{orgId}/deployment`。
- 新增 `NimDeploymentPreflightToolHttpContractTest`。
- 更新 `M511AtlasToolHttpContractTest`、`intents.yml`、`CHANGELOG.md` 与 M5.21 波次索引。
- 调整 `intents.yml` 中 `nim_create` 描述，明确当前仍为安全占位，不直接执行创建。

## HOLD 清单

- `nim_create` 继续 HOLD:
  - 未完成 NVAIE license 后端化校验。
  - 未完成系统组织 / SYS_ADMIN 禁用策略在 Agent 执行链中的可信实现。
  - 未完整复刻并测试 mature 前端 `mergeTemplate` 与 `formatApplication` 的 NIM 专属 DTO 合并。
  - 未设计 NIM 创建 HITL 卡片，不能让 LLM 自行决定服务名、GPU、模板、网络和费用影响。
  - 未接入创建后的 deployment/service 状态轮询和 NIM API readiness 探测。
- 本批不展示或生成真实 API Key。前端当前展示的是用户需自行填入的 NGC API Key 占位提示，Agent 不应伪造或保存密钥。
- 镜像拉取、重试、删除、推送、构建、加载继续 HOLD。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 静态密钥扫描: 0 matches
  - `mvn -q test`

> 说明：全量测试中 test profile 尝试下载 embedding model，网络超时后按预期降级，测试进程最终返回成功。

## 是否访问真实 8100

否。本批只使用 mature 源码证据、前端调用证据和 mock HTTP client 契约测试。

## 下一步建议

1. 为 NIM 创建设计显式 Plan -> Preflight -> HITL -> DeployCreate 的多步骤编排，不让 `nim_deployment_preflight` 承担写操作。
2. 将前端 `mergeTemplate` 的字段转换和 `formatApplication` 的 GPU/网络/自动扩缩容语义整理成后端可测试的 NIM DTO 合并器。
3. 在正式打开 `nim_create` 前，补 license、系统组织限制、费用/配额提示、审计日志和创建后状态轮询测试。
