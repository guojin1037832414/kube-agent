# M5.21 第四十五批 NIM readiness HTTP adapter 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateReadinessHttpAdapterSupport`、`NimCreateReadinessHttpAdapterSupportTest`、`NimCreateStateMachineSupport`
> 约束: 只设计 mock-first request spec 编译层；不注册 Tool，不发 HTTP，不访问真实 kube-manager `8100`，不轮询真实 NIM 服务，不发送、保存或展示真实 API Key。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 创建后链路是:
  - 创建成功后按名称回查 Deployment；
  - 从唯一命中的 `entranceMap.http/http1` 派生 NIM 服务入口；
  - 只读 `GET /v1/health/live` 判断 live；
  - 只读 `GET /v1/models` 回读模型名；
  - chat/completion/embedding 才会进入 POST 和真实 API Key 使用链路，readiness 不应触发。
- M5.21-40 已生成 readiness plan。
- M5.21-43 已新增只读 executor，用离线响应快照产出 READY/PENDING/BLOCKED/REJECTED/TIMEOUT。
- M5.21-44 已要求未来 `nim_create` 写入前必须看到 READY executor report。
- 本批补的是计划和执行器之间的 adapter 契约：把计划编译成可审计 request specs，但仍不执行网络。

## 多专家会诊

- 架构边界专家:
  - adapter 必须是纯数据编译器，不持有 `KubeManagerHttpClient`，也不能引入 `RestClient`、`java.net`、okhttp、feign 等底层 HTTP 能力。
  - 输出只能是 request specs，不得被当作执行报告或 release gate。
- Backend/API 专家:
  - deployment 回查只允许 `GET /api/{orgId}/deployment`，query 固定为 `organizationId/page=1/limit=100/name`。
  - NIM 侧只允许 `/v1/health/live` 和 `/v1/models`，不允许把 chat/completions 等 endpoint 混入 readiness。
- Security/RBAC 专家:
  - service URL 不信任调用方随意注入；必须拒绝 userinfo、query、fragment、路径穿越、localhost/127/8100 和非法端口。
  - plan 和 service URL 中出现真实 Bearer/API Key/secret-shaped 值时 fail-closed。
- Agent 架构专家:
  - 本批把创建后观测链路拆得更清楚:
    - plan: 目标和步骤；
    - adapter: 只读请求规格；
    - executor: 响应快照判定；
    - state machine: 写入前门禁。
  - 这种分层能防止“有计划就当已执行”“有 specs 就当 READY”的 Agent 幻觉。
- Test 架构专家:
  - 测试必须断言 `networkAccess=NOT_PERFORMED`、无 body、无 headers、无真实 Authorization、无 8100。
  - 负例要覆盖 POST、未知 GET、未准备计划、不安全 query、secret、不安全 service URL、缺 models step。
- Documentation/Learning 专家:
  - 这一批适合作为学习 Agent aftercare 设计的样板：真实世界副作用要逐层建模，先把契约写死，再考虑可替换执行层。

## 变更摘要

- 新增 `NimCreateReadinessHttpAdapterSupport`。
- `compile(...)` 输入:
  - `readinessPlan`
  - `serviceApiUrl`
  - `attempt`
- `compile(...)` 输出:
  - `readinessHttpAdapter=NIM_CREATE_READINESS_HTTP_ADAPTER`
  - `executionMode=REQUEST_SPEC_CONTRACT_ONLY`
  - `networkAccess=NOT_PERFORMED`
  - `sideEffect=NONE`
  - `readOnly=true`
  - `pollOnly=true`
  - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
  - `apiKeyHeaderPolicy=DO_NOT_SEND_REAL_API_KEY`
  - `state`
  - `adapterPrepared`
  - `requestSpecs`
  - `derivedSteps`
  - `executorHandoff`
  - `pendingBy`
  - `blockedBy`
  - `forbiddenActionsEnforced`
- request specs 只包含:
  - deployment:
    - `method=GET`
    - `clientBoundary=KUBE_MANAGER_HTTP_GATEWAY`
    - `pathTemplate=/api/{orgId}/deployment`
    - `query={page:1, limit:100, name}`
  - nim-health:
    - `method=GET`
    - `clientBoundary=NIM_SERVICE_READINESS_PROBE`
    - `apiPath={basePath}/v1/health/live`
  - nim-models:
    - `method=GET`
    - `clientBoundary=NIM_SERVICE_READINESS_PROBE`
    - `apiPath={basePath}/v1/models`
- service 不是 HTTP request spec，只是:
  - `EXTRACT_FROM_DEPLOYMENT_RESPONSE`
  - `deploymentListResponse.result[0].entranceMap.http|http1`
- 当 `serviceApiUrl` 尚未派生时:
  - `state=READY_FOR_DEPLOYMENT_POLL`
  - 只生成 deployment GET request spec
  - `pendingBy` 包含 `SERVICE_URL_NOT_DERIVED`
- 当 service URL 可安全解析时:
  - `state=READY_FOR_READ_ONLY_HTTP_GETS`
  - 生成 deployment、health、models 三个 GET specs
- 加严 `NimCreateStateMachineSupport`:
  - readiness plan 必须覆盖 `deployment/service/nim-health/nim-models`。
  - 缺少 `nim-models` target 或 step 时返回 `READINESS_PLAN_NOT_READY`。

## Fail-Closed 边界

- readiness plan 缺少 prepared/pollOnly/placeholder-only/四目标/steps -> 拒绝。
- readiness step 不是 `GET` 或 `EXTRACT_FROM_DEPLOYMENT_RESPONSE` -> 拒绝。
- readiness step target 不属于四个白名单 -> 拒绝。
- `GET {nimApiBasePath}/v1/chat/completions`、`/v1/embeddings` 或其他未审计 endpoint -> 拒绝。
- deployment query 出现额外字段、不安全 organizationId、不安全 deployment name、`../` 或路径分隔符 -> 拒绝。
- service URL:
  - 非 http/https -> 拒绝；
  - `https://key@host` userinfo -> 拒绝；
  - query/fragment -> 拒绝；
  - 非法端口或 8100 -> 拒绝；
  - localhost/127/loopback -> 拒绝；
  - `..`、`%2e`、反斜杠、控制字符 -> 拒绝。
- plan 或 service URL 中出现真实 Bearer/API Key/secret-shaped 值 -> 拒绝。
- 任一拒绝发生时 `adapterPrepared=false` 且 `requestSpecs=[]`。

## 安全边界

- 本批不新增 Tool、Controller、HTTP endpoint。
- 本批不引入 `KubeManagerHttpClient`、`RestClient`、`RestTemplate`、`WebClient`、`java.net`、okhttp、feign、Apache HTTP client。
- 本批不访问真实 `8100`。
- 本批不调用 `POST /api/{orgId}/deployment`。
- 本批不调用真实 NIM `/v1/health/live` 或 `/v1/models`。
- 本批不生成、不保存、不展示、不发送真实 NGC/NIM API Key。
- adapter 输出不是 `readinessExecutionReport`，不能让 `nim_create` 放行。
- `nim_create` 仍保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateReadinessHttpAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateReadinessExecutorSupportTest" test`
  - `mvn -q "-Dtest=NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 真实密钥形态静态扫描: 只命中测试中的假值 `Bearer real-key-material`，用于验证 fail-closed；未发现真实密钥。
  - `mvn -q test`

全量测试说明: test profile 中 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用成熟源代码证据、纯 support 契约和单元测试。

## 下一步建议

1. 设计 durable audit writer adapter 的真实持久化替换点，但仍先以 mock contract 保护。
2. 继续把 `NimTrustedPolicyProviderSupport` 接到真实 license/user/org 可信读取链路，mock-first 验证。
3. 设计受控 POST body rebuild contract，明确未来写入 body 不得直接复用 preflight preview。
4. 只有 trusted policy、HITL、durable audit receipt、READY readiness executor report、受控 body rebuild 和 release 开关全部齐备后，才讨论打开 `nim_create` 写入。
