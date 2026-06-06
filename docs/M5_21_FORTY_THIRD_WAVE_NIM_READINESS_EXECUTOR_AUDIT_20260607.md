# M5.21 第四十三批 NIM readiness 只读执行器契约审计

> 日期: 2026-06-07
> 范围: `NimCreateReadinessExecutorSupport`、`NimCreateReadinessExecutorSupportTest`
> 约束: 只实现离线/纯函数执行器契约；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不轮询真实 NIM 服务，不发送/保存/展示真实 API Key。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - `createData(requestBody)` 在 `createDeployment(requestBody)` 成功后调用 `getDeploymentList(requestBody)`。
  - `getDeploymentList` 使用 `page=1, limit=100, name=temp.name` 回查 Deployment。
  - 当唯一命中时，从 `result.entranceMap['http'] ?? result.entranceMap['http1']` 派生 NIM 服务入口。
  - 创建后轮询每 5 秒一次，最多 120 次。
  - 展示的 `apiKey` 始终是占位 `Bearer {input your NGC_API_KEY here}`。
  - health live 后才调用 `getModelName(url)`，模型读取失败时返回 `fetch failed`。
- mature `vue-kube-manager/src/utils/request-nim.js`:
  - readiness health 是 `GET apiUrl + '/v1/health/live'`。
  - live 成功信号包括 `message === 'Service is live.'`、`live=true` 或 `status === 'live'`。
  - 模型读取是 `GET apiUrl + '/v1/models'`，模型名来自 `data[0].id` 或 `available_models[0]`。
  - chat/completion/embedding 才会 POST 并使用 API Key；创建后 readiness 不应触发这些路径。
- mature `kube-agent` 前序契约:
  - M5.21-40 已生成 readiness plan。
  - M5.21-42 已将 audit receipt 与 audit context 分离。
  - 本批只补“执行器判定层”，仍不开放真实 `nim_create`。

## 多专家会议

- Backend/API 专家:
  - 执行器输入必须是 readiness plan 和离线响应快照，不能自己发 HTTP。
  - Deployment 回查必须唯一命中，多个命中不能安全派生服务入口。
- Frontend/Product 专家:
  - success path 应精确对齐 mature 前端: deployment -> entranceMap -> health -> models。
  - `models` 读取失败在前端表现为 `fetch failed`，不应覆盖已经 live 的服务可用性。
- Security/RBAC 专家:
  - `Authorization`、`token`、`apiKey`、`secret`、`password` 或真实 Bearer 值出现在 plan/response 中必须 fail-closed。
  - readiness 执行器只允许 `GET` 和 `EXTRACT_FROM_DEPLOYMENT_RESPONSE`，拒绝 POST chat/embedding。
- Agent 架构专家:
  - 这是计划和真实 Tool 之间的安全中间层：先把判定规则固化为纯契约，再考虑未来接 HTTP client。
  - 输出 `pendingBy`、`blockedBy`、`nextPoll`，便于 ReAct/Plan-Execute Agent 解释等待、阻断和下一步。
- Test 架构专家:
  - 用离线 fixture 覆盖成功、等待、阻断、超时、密钥泄漏、POST 步骤等边界。
  - 不引入 mock HTTP client，避免误以为本批已执行真实 readiness。
- Documentation/Learning 专家:
  - 本批展示一个重要 Agent 工程模式: 把“外部状态轮询”拆成计划、只读执行器、未来真实 adapter 三层。

## 变更摘要

- 新增 `NimCreateReadinessExecutorSupport`。
  - 输入 `ReadinessExecutionInput`:
    - `readinessPlan`
    - `deploymentListResponse`
    - `healthResponse`
    - `modelsResponse`
    - `attempt`
  - 输出 readiness report:
    - `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`
    - `executionMode=OFFLINE_CONTRACT_EVALUATION`
    - `sideEffect=NONE`
    - `readOnly=true`
    - `pollOnly=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `apiKeyPlaceholderOnly=true`
    - `deployment/service/health/models`
    - `pendingBy`
    - `blockedBy`
    - `nextPoll`
    - `forbiddenActionsEnforced`
- 核心判定:
  - readiness plan 必须 prepared、pollOnly、覆盖 `deployment/service/nim-health/nim-models`。
  - steps 只能是 `GET` 或 `EXTRACT_FROM_DEPLOYMENT_RESPONSE`，并必须包含 deployment/service/health/models 四段。
  - Deployment 回查 0 个 -> `PENDING`，准备下一轮轮询。
  - Deployment 回查多个 -> `BLOCKED`，返回 `DEPLOYMENT_MATCH_AMBIGUOUS`。
  - service URL 只从 `entranceMap.http/http1` 读取，且必须是 http/https URL。
  - health live 信号对齐 mature 前端三种形态。
  - models 从 `data[0].id` 或 `available_models[0]` 读取；失败返回 `fetch failed`，不阻断 live 后的 `READY`。
  - 达到 `maxAttempts` 后 health 仍未 live -> `TIMEOUT`。
- 架构边界修正:
  - 初版实现曾使用 `java.net.URI` 做 URL 解析，全量 `M510ArchitectureBoundaryTest` 正确拦截了 Tool 包对 `java.net..` 的依赖。
  - 已改为受限正则字符串解析，只做离线 `http/https` 形状校验和 path 归一化，不引入网络包，也不触碰真实连接能力。

## 安全边界

- 本批不新增 Tool、不新增 Controller、不访问真实 `8100`。
- 本批不调用 `POST /api/{orgId}/deployment`。
- 本批不调用 NIM `/v1/health/live` 或 `/v1/models`，只消费离线响应快照。
- readiness 阶段禁止:
  - `POST /v1/chat/completions`
  - `POST /v1/embeddings`
  - 携带真实 `Authorization` header
  - 保存或展示真实 NGC/NIM API Key
- `nim_create` 仍保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest" test`
  - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test`
  - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 真实密钥形态静态扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用 mature 源码证据和纯单元测试。

## 下一步建议

1. 扩展状态机，让未来 `nim_create` 的 release 前置条件也能消费 readiness executor report，而不是只消费 readiness plan。
2. 设计真实 readiness HTTP adapter，但先保持 mock-first，不直接访问生产 NIM 服务。
3. 继续设计 durable audit writer adapter，确认真实审计表/日志后再替换 M5.21-42 的 mock receipt。
4. 在 trusted policy、durable audit、HITL、readiness executor report 都齐备后，再讨论受控打开 `nim_create` 写入开关。
