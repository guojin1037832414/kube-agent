# M5.21 第三十六批 NIM 模板合并预览审计

> 日期: 2026-06-06
> 范围: `NimTemplateMergeSupport`、`NimDeploymentPreflightSupport`、`NimTemplateMergeSupportTest`、`NimDeploymentPreflightToolHttpContractTest`
> 约束: 只生成 `safeToPost=false` 的 DeploymentDTO 草案预览；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - NIM 一键部署流程: repository 目录 -> NIM tag -> `templateType=NIM` 模板 -> `mergeTemplate` -> `formatApplication` -> `createDeployment`。
  - `mergeTemplate` 会把模板里的 `cpuLimits/memLimits/gpuPercentLimits/gpuMemLimits` 从后端单位转回前端表单单位。
  - `gpuSpec = gpuModel + '#' + migConfig`；无 MIG 时使用 `gpuModel`。
  - 前端只保留 `displayName`，再 `Object.assign(temp, template)`。
- mature `vue-kube-manager/src/utils/request-formatter.js`:
  - CPU 核 -> 毫核，内存 GB -> MiB。
  - `gpuPercentLimits > 1` 时向下取整，再乘 100。
  - 整卡或多卡时 `gpuMemLimits=0`；半卡时显存 GB -> MiB。
  - `gpuSpec` 需要从 `gpuMap[gpuSpec]` 解析 `gpuModel/migConfig`。
  - 自动伸缩关闭时 `autoScaleConfig=null`；CPU-only 时 GPU 利用率目标置 0。
  - 默认 `enableSecondNetwork=true`。
- mature `kube-manager` 后端:
  - `DeploymentController` 创建接口是 `POST /api/{organizationId}/deployment`。
  - Controller 会强制 `namespace=ns{organizationId}`。
  - `DeploymentServiceImpl#createDeployment` 会禁止系统组织创建、校验资源、插入 DB 后生成最终 K8s name。
  - 因此 Agent 侧预览必须把用户可理解的 `displayName`、镜像、模板和资源作为 HITL 核心，而不能把草案当最终资源事实。

## 多专家会议

- Frontend/Product 专家:
  - 可以先迁移 `mergeTemplate/formatApplication` 的确定性换算，用于解释“如果继续创建，大概会提交什么”。
  - 前端只保护 `displayName`，但 Agent 不能复制模板覆盖服务名/镜像的潜在风险。
- Backend/API 专家:
  - 后端最终 K8s name 由服务层生成，Agent 预览里的 `name` 只能作为用户期望/展示语义，不代表最终资源名。
  - POST 创建仍必须走成熟 `DeploymentController`，并保留 service 层资源校验。
- Security/RBAC 专家:
  - `safeToPost=false` 必须测试化，防止后续把 preview 直接透传给 `deploy_create_instance` 或 `nim_create`。
  - 缺少 GPU map、缺少 displayName、缺少 license/SYS_ADMIN 策略时，body 不能宣称完整。
  - 公开 Tool 入参中的 `gpuMap` 不可信，不能让 LLM/用户手写 GPU map 伪造解析成功。
- Agent 架构专家:
  - 将 DTO merge 做成纯函数 support，可复用、可审计、可单测，也适合未来 Plan -> Preflight -> HITL -> Write 的编排。
  - 不新增任何 HTTP GET，避免趁 NIM 预检偷偷扩大到全局 GPU map 读取；未来只有受控编排可显式传入已审计 GPU map。
- Test 架构专家:
  - 单测覆盖模板覆盖字段、GPU map 缺失、GPU map 解析、CPU-only 自动伸缩、displayName 未确认。
  - HTTP 预检测试断言 `deploymentBodyPreview.safeToPost=false` 且仍只调用三段 GET。
- Documentation/Learning 专家:
  - 这是 Agent 工程里的重要模式：把高风险写操作拆成“只读证据 + 离线草案 + 明确 HOLD”，让学习者看到从 UI 迁移到 Agent 的安全降级方式。

## 变更摘要

- 新增 `NimTemplateMergeSupport`。
  - 生成 `deploymentBodyPreview`，包含:
    - `safeToPost=false`
    - `previewOnly=true`
    - `bodyComplete`
    - `bodyDraft`
    - `uiMergedDraft`
    - `gpuResolution`
    - `protectedFields`
    - `requiredBeforeCreate`
  - 对齐 mature 前端的单位换算:
    - CPU 核/毫核双向转换
    - 内存 GB/MiB 双向转换
    - GPU 百分比与显存转换
    - 带宽 `20 -> 20M`
    - 自动伸缩和二层网络默认值
  - Agent 安全偏离:
    - 同时保护 `name/displayName/image`。
    - 未确认 `displayName` 时 `bodyComplete=false`。
    - 缺少 GPU map 时保留 `gpuSpec`，但不写入 `gpuModel/migConfig`，并标记 `PENDING_GPU_MAP`。
    - 三参数公开预览重载忽略调用方提供的 `params.gpuMap`，防止伪造 GPU 解析。
- `NimDeploymentPreflightSupport` 在只读预检计划里新增 `deploymentBodyPreview`。
- 新增 `NimTemplateMergeSupportTest`。
- 扩展 `NimDeploymentPreflightToolHttpContractTest`，锁定预检结果中的 preview 字段。

## HOLD 清单

- `nim_create` 继续 HOLD，不接入 POST。
- NVAIE license 后端可信校验未完成。
- 系统组织 / SYS_ADMIN 禁止策略尚未在 Agent 执行链中完整实现。
- NIM HITL 确认卡片、审计日志、费用/配额确认、网络暴露确认未完成。
- 创建后 Deployment/Service/NIM readiness 轮询未完成。
- API Key 不由 Agent 生成、保存或展示。
- 本批不新增全局 `/api/gpu/all/gpu-map` 自动读取；GPU map 只能由未来已审计编排显式提供。
- 公开 `nim_deployment_preflight` 不消费用户/LLM 传入的 `gpuMap`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`

## 是否访问真实 8100

否。本批只使用 mature 源码证据、专家审计结果和 mock/纯函数测试。

## 下一步建议

1. 设计 NIM 创建 HITL 卡片 schema，明确服务展示名、image、templateId、GPU、网络、费用/配额和 HOLD 原因。
2. 设计 license / SYS_ADMIN / system org 的后端可信校验路径。
3. 在不打开 `nim_create` 的前提下，补一个 NIM create plan contract，证明 Plan -> Preflight -> HITL -> Write 的状态机不会跳步。
