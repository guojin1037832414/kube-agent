# M5.21 第四十四批 NIM readiness 执行报告门禁审计

> 日期: 2026-06-07
> 范围: `NimCreateStateMachineSupport`、`NimCreateStateMachineSupportTest`、`NimCreateAuditReadinessSupportTest`、`NimCreateAuditWriterSupportTest`
> 约束: 只加严未来 `nim_create` 状态机放行条件；不访问真实 kube-manager `8100`，不调用 `POST /api/{orgId}/deployment`，不轮询真实 NIM 服务。

## 成熟项目证据

- M5.21-40 已把 mature 前端创建后流程建模为 readiness plan:
  - Deployment 名称回查；
  - `entranceMap.http/http1` 派生服务入口；
  - `GET /v1/health/live`；
  - `GET /v1/models`；
  - 不生成、不保存、不展示真实 API Key。
- M5.21-43 已新增 `NimCreateReadinessExecutorSupport`:
  - 只消费 readiness plan 和离线响应快照；
  - 输出 `READY/PENDING/BLOCKED/REJECTED/TIMEOUT`；
  - 强制 `sideEffect=NONE`、`readOnly=true`、`pollOnly=true`；
  - 对 POST readiness、真实 Authorization/API Key、ambiguous deployment、invalid service URL fail-closed。
- 本批把状态机从“只要有 readiness plan”升级为“必须有受控 readiness executor READY report”，避免未来开发者跳过 aftercare 判定层直接打开写入。

## 多专家会议

- Backend/API 专家:
  - 真正创建前不仅要知道怎么轮询，还要确认轮询执行契约已经能给出可审计的 READY 报告。
  - PENDING/TIMEOUT/BLOCKED 的 readiness 结果不能成为写入放行凭据。
- Frontend/Product 专家:
  - mature 前端在 create 后仍等待 health live；状态机也应把 service aftercare 链路视为必备闭环。
  - 模型读取失败可非致命，但 deployment/service/health 必须 ready。
- Security/RBAC 专家:
  - Tool 入参伪造的 `readinessReady/readinessState/readinessExecutor` 必须进入 ignored caller claims。
  - readiness report 中出现真实 `Authorization`、Bearer token、API Key、secret 时必须阻断。
- Agent 架构专家:
  - 状态机现在明确消费三个层级:
    - `readinessPlan`: 计划；
    - `readinessExecutionReport`: 只读执行结果；
    - 未来 HTTP adapter: 真实执行来源。
  - 这使 Agent 的 Plan/Execute/Observe 更清晰，减少“有计划就当已执行”的错觉。
- Test 架构专家:
  - 保留旧构造器让缺少 report 的场景继续表现为 HOLD。
  - 未来绿灯 fixture 必须显式传入 READY report。
  - 增加 PENDING 和 secret/blocking report 负例。
- Documentation/Learning 专家:
  - 本批是学习 Agent 状态机设计的关键点: 计划对象不能替代观测结果；执行报告也必须可验证来源、无副作用、无敏感凭据。

## 变更摘要

- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `readinessExecutionReport`。
- 状态机输出新增 `readinessExecutionRequired=true`。
- 新增 `validateReadinessExecutionReport(...)`:
  - 缺失 report -> `READINESS_EXECUTION_REPORT_NOT_READY`。
  - report 必须声明:
    - `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`
    - `sideEffect=NONE`
    - `readOnly=true`
    - `pollOnly=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `apiKeyPlaceholderOnly=true`
    - `forbiddenActionsEnforced=true`
  - report 必须有:
    - `ready=true`
    - `state=READY`
    - `blockedBy=[]`
    - `deployment.matched=true`
    - `service.serviceUrlReady=true`
    - `health.live=true`
    - `nextPoll.prepared=false`
  - PENDING/BLOCKED/REJECTED/TIMEOUT 或有 blockedBy -> 阻断。
  - report 中含真实凭据 -> `READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET`。
- 扩展 ignored caller claims:
  - `readinessExecutionReport`
  - `readinessExecutor`
  - `readinessReady`
  - `readinessState`
- `requiredStages` 明确要求 future release 前必须有受控 readiness executor READY report。
- 加强状态机 secret 检测:
  - 除 secret-shaped key 外，也拒绝真实 Bearer 值和常见真实 key 形态。
  - 允许占位 `Bearer {input your NGC_API_KEY here}`。

## 安全边界

- 本批不新增 Tool、不新增 Controller、不发 HTTP。
- 本批不访问真实 `8100`。
- 本批不调用 `POST /api/{orgId}/deployment`。
- 本批不轮询真实 NIM 服务。
- `nim_create` 仍保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 即使未来所有其他 fixture 齐全，缺少 READY readiness executor report 仍不得放行。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest" test`
  - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - 真实密钥形态静态扫描: 0 matches
  - `mvn -q test`

全量测试说明: test profile 下 embedding model 下载超时后按既有降级路径继续，最终 Maven 测试结果通过。

## 是否访问真实 8100

否。本批只使用成熟源码证据、前序契约和纯单元/契约测试。

## 下一步建议

1. 设计真实 readiness HTTP adapter 的 mock-first 接口，但仍只执行 M5.21-40/43 允许的 GET/派生步骤。
2. 将 durable audit writer adapter 设计为可替换接口，确认真实审计表/日志后再落地。
3. 继续把 `NimTrustedPolicyProviderSupport` 接入真实 license/user/org 可信读取，但仍先通过 mock contract。
4. 只有 trusted policy、HITL、durable audit receipt、READY readiness report 和受控 body 重建都齐备后，才讨论打开 `nim_create` 写入开关。
