# M5.21 第四十七批 NIM 受控 POST request spec 适配器审计

> 日期: 2026-06-07 05:10 Asia/Shanghai
> 范围: `NimCreateWriteRequestSpecAdapterSupport`、`NimCreateStateMachineSupport`、NIM create 写链路相关测试
> 约束: 只新增 mock-first/纯函数请求规格契约；不注册 Tool，不新增 Controller，不持有 HTTP client，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 一键部署最终会落到 kube-manager Deployment 创建入口。
- M5.21-46 已经把未来写入 body 从 preview 升级为 durable audit receipt 绑定的受控重建报告。
- 但 body 报告仍不是 HTTP 执行规格；如果未来执行层直接消费 body，仍可能绕过 path、header、auth boundary、idempotency、digest 复核等关键约束。
- 因此本批在 body rebuilder 和未来真实 HTTP writer 之间新增一层 fail-closed POST request spec adapter：它只生成可审计请求规格，不执行网络写入。

## 多专家会诊

- Backend/API 专家:
  - request spec 必须固定为 `POST /api/{orgId}/deployment`，path template 和 resolved path 都要可审计。
  - body 必须来自受控 rebuilder 的值拷贝，不能从调用方或 preview 引用透传。
- Security/RBAC 专家:
  - spec 不允许调用方 header、Authorization、真实 NGC/NIM API Key 或 token 进入。
  - spec 必须绑定 durable audit receipt、audit context 和 body digest，不能只相信调用方声明。
- Agent 架构专家:
  - `writeRequestSpecReport` 是 future write executor 前的独立关卡，不是 release credential。
  - request spec adapter 仍是 contract-only，不得依赖 `KubeManagerHttpClient`、`RestClient`、`java.net` 或任何真实 HTTP 客户端。
- Test 架构专家:
  - 状态机绿色路径必须同时携带 `writeBodyRebuildReport` 和 `writeRequestSpecReport`。
  - 伪造 request body、伪造 digest、receipt 不匹配或 secret 泄漏都要 fail-closed。
- Documentation/Learning 专家:
  - 本批教学重点是“把执行前最后一米也合同化”：生产级 Agent 不应从合规 body 直接跳到 HTTP client。

## 变更摘要

- 新增 `NimCreateWriteRequestSpecAdapterSupport`。
  - `compile(...)` 消费:
    - `creationGate`
    - `auditContext`
    - `auditReceipt`
    - `writeBodyRebuildReport`
  - 输出:
    - `writeRequestSpecAdapter=NIM_CREATE_WRITE_REQUEST_SPEC_ADAPTER`
    - `executionMode=POST_REQUEST_SPEC_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeRequestPrepared`
    - `httpMethod=POST`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `pathTemplate=/api/{orgId}/deployment`
    - `clientBoundary=KUBE_MANAGER_HTTP_GATEWAY`
    - `callerHeadersAllowed=false`
    - `authorizationHeaderFromCallerAllowed=false`
    - `realApiKeyAllowed=false`
    - `bodySource=CONTROLLED_REBUILDER_BODY_COPY`
    - `bodyCopiedByValue=true`
    - `bodyMutationAllowed=false`
    - `bodyDigest`
    - `requestSpec`
    - `requestSpecDigest`
    - `blockedBy`
- `requestSpec` 固定表达未来执行形态:
  - `method=POST`
  - `endpoint=/api/{orgId}/deployment`
  - `resolvedPath=/api/{trustedOrgId}/deployment`
  - `queryAllowed=false`
  - `bodyAllowed=true`
  - `bodyRequired=true`
  - `kubeManagerAuthBoundary=KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY`
  - `idempotencyKeyRequiredBeforeExecution=true`
  - `executionAdapterRequired=FUTURE_DURABLE_WRITE_EXECUTOR`
  - `sideEffect=NONE`
  - `futureSideEffectIfExecuted=POST /api/{orgId}/deployment`
- `NimCreateStateMachineSupport` 加强:
  - `ReadinessRequest` 新增 `writeRequestSpecReport`。
  - 输出新增 `writeRequestSpecRequired=true`。
  - 缺少报告返回 `WRITE_REQUEST_SPEC_REPORT_NOT_READY`。
  - 合约不合法、body/digest/receipt/audit identity 不匹配返回 `WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID`。
  - 报告中出现 forbidden secret 返回 `WRITE_REQUEST_SPEC_REPORT_CONTAINS_FORBIDDEN_SECRET`。
  - 状态机会复算 `requestSpecDigest`，并确认 request body 与 rebuilder body 完全一致。
- 新增 `NimCreateWriteRequestSpecAdapterSupportTest`。
- 更新 `NimCreateStateMachineSupportTest`、`NimCreateWriteBodyRebuilderSupportTest`、`NimCreateAuditReadinessSupportTest` 的未来绿色 fixture。

## 安全边界

- 本批没有新增任何真实 HTTP 调用、Controller、Tool 注册或后台执行器。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- request spec 报告不是 release credential，不能替代 trusted policy、HITL、durable audit receipt、READY readiness executor 或 release switch。
- caller header、Authorization、token、password、secret、真实 NGC/NIM API Key 均禁止进入 request spec。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest" test`
  - `mvn -q "-Dtest=NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`

## 是否访问真实 8100

否。本批只运行纯单元测试、mock-first 契约测试和静态架构/HTTP contract 测试。

## 下一步建议

1. 继续保持 `nim_create` HOLD，直到真实 durable audit writer、真实 trusted policy reader、真实 write executor 和 release switch 都完成审计。
2. 下一批可以设计 future durable write executor 的离线契约，先要求 idempotency key、write audit handoff 和 post-write readiness handoff，但仍不执行真实 POST。
3. 或者回到真实后端落点识别，查清 durable audit log 表/服务后再替换 mock receipt。
