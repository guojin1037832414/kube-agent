# M5.21 第四十批 NIM 审计上下文与 readiness 计划草案审计

> 日期: 2026-06-06
> 范围: `NimCreateAuditReadinessSupport`、`NimCreateStateMachineSupport`、`NimCreateAuditReadinessSupportTest`、`NimCreateStateMachineSupportTest`
> 约束: 只构造未来 `nim_create` 可消费的审计上下文和只读 readiness 计划；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不轮询真实 NIM 服务。

## 成熟项目证据

- mature `vue-kube-manager/src/views/nim/index.vue`:
  - `createData(requestBody)` 调用 `createDeployment(requestBody)` 后，不直接展示成功，而是调用 `getDeploymentList(requestBody)`。
  - `getDeploymentList` 使用 `listDeployment({ page: 1, limit: 100, name: temp.name })` 回查创建后的 Deployment。
  - 从 `result.entranceMap['http'] ?? result.entranceMap['http1']` 派生 NIM 服务入口。
  - 展示的 `apiKey` 是占位 `Bearer {input your NGC_API_KEY here}`，不是前端生成真实 key。
  - 创建后每 5 秒调用一次 `checkServiceStatus(url.pathname)`，最多 120 次。
- mature `vue-kube-manager/src/utils/request-nim.js`:
  - readiness live 探测是 `GET apiUrl + '/v1/health/live'`。
  - 模型回读是 `GET apiUrl + '/v1/models'`。
  - 只有 Try/聊天/embedding 才会使用 API Key 进行 POST；创建后 readiness 不应走这些路径。
- mature `kube-manager`:
  - tenant 创建入口仍是 `POST /api/{organizationId}/deployment`。
  - tenant 创建后可用 `GET /api/{organizationId}/deployment` 按 name 回查列表，也有 `GET /api/{organizationId}/deployment/{name}`。

## 多专家会议

- Backend/API 专家:
  - 审计上下文需要记录 requestId、conversationId、userId、organizationId、targetTool 和未来写入 endpoint。
  - 写入 body 来源必须明确为受控状态机重建，不能复用 preview body。
- Frontend/Product 专家:
  - readiness 应保持和 mature 前端一致: deployment 回查 -> entranceMap -> `/v1/health/live` -> `/v1/models`。
  - API Key 只能展示占位说明，不能让 Agent 代用户生成或保存真实 NGC/NIM key。
- Security/RBAC 专家:
  - Tool 入参里的 token、apiKey、confirmed、safeToPost、licenseValid 等只可作为 ignored caller claims 记录，不能进入审计凭据。
  - readiness 步骤中出现 POST 必须阻断。
- Agent 架构专家:
  - 本批把 M5.21-39 的随手 Map fixture 提升为可复用的计划对象，让未来状态机接真实 provider 时有稳定输入形状。
  - 计划对象仍不是执行器，不负责发 HTTP、写日志或生成 HITL marker。
- Test 架构专家:
  - 使用纯单元测试证明 audit/readiness 可被状态机接受。
  - 使用负例证明缺少 readiness 目标或出现 POST 时状态机保持 `HELD`。
- Documentation/Learning 专家:
  - 该批是学习 Agent 安全工程的关键模式: 把“创建后检查”拆成安全计划，而不是把轮询逻辑和写入逻辑混在一起。

## 变更摘要

- 新增 `NimCreateAuditReadinessSupport`。
  - `buildAuditContext(...)` 输出:
    - `auditPrepared`
    - `auditEventType=NIM_CREATE_REQUEST`
    - `requestId/conversationId/userId/organizationId`
    - `targetTool=nim_create`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `writeBodyProvenance=SERVER_REBUILT_FROM_AUDITED_NIM_STATE`
    - `secretRedactionApplied=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `ignoredCallerClaimKeys`
    - mature evidence
  - `buildReadinessPlan(...)` 输出:
    - `readinessPollingPrepared`
    - `pollOnly=true`
    - `apiKeyPlaceholderOnly=true`
    - `apiKeyPlaceholder=Bearer {input your NGC_API_KEY here}`
    - `targets=[deployment, service, nim-health, nim-models]`
    - `steps`
    - `successSignals`
    - `forbiddenActions`
    - mature evidence
- `NimCreateStateMachineSupport` 加严:
  - audit 必须声明目标 Tool、可信 body 来源、密钥脱敏和 API Key 策略。
  - readiness 必须覆盖 `deployment/service/nim-health`。
  - readiness steps 只能是 `GET` 或 `EXTRACT_FROM_DEPLOYMENT_RESPONSE`。
  - readiness 中必须至少有 deployment GET 和 nim-health GET。
- 新增 `NimCreateAuditReadinessSupportTest`。
- 更新 `NimCreateStateMachineSupportTest` 的完整未来态 fixture。

## 安全边界

- 本批不新增真实 `nim_create` 写入能力。
- 本批不新增 readiness Tool，不调用外部 NIM 服务。
- readiness 计划是“未来轮询执行器”的输入草案，不是当前执行动作。
- 任何真实 API Key、token、password、secret 都不得进入 audit/readiness。
- 禁止 readiness 阶段 POST chat/completions、embeddings 或携带真实 Authorization header。
- `NimCreateTool` 仍为 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test`
  - `mvn -q "-Dtest=NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 真实密钥形态静态扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用 mature 源码证据和纯单元测试。

## 下一步建议

1. 设计 `NimTrustedPolicyProvider`，从可信后端上下文填充 `NimTrustedPolicySnapshot`。
2. 设计真正的审计写入服务接口，但先保持 mock/契约测试，不连接真实持久化。
3. 设计创建后 readiness 只读 Tool 或内部执行器，必须只执行本批计划允许的 GET/派生步骤。
4. 在 policy、audit、readiness 都有真实 provider 后，再考虑把 `creationGate` 演进为服务端受控的 `READY_FOR_SERVER_CONFIRMED_WRITE`。
