# M5.21 第五十批 NIM durable write executor 报告门禁审计

> 日期: 2026-06-07 06:21 Asia/Shanghai  
> 范围: `NimCreateStateMachineSupport`、NIM create 写链路状态机测试、上游 NIM 合同测试夹具  
> 约束: 只新增状态机门禁与纯数据测试；不注册 Tool，不新增 Controller，不持有 HTTP client，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 创建最终写入仍是 kube-manager Deployment 创建接口。
- M5.21-48 已经把写执行交接、服务端幂等键、pre-write audit handoff 和 post-write readiness handoff 合同化。
- M5.21-49 已经新增 durable write executor 合同壳，但合法输入仍返回:
  - `executionState=IMPLEMENTATION_HOLD`
  - `executorImplementationAvailable=false`
  - `networkAccess=NOT_PERFORMED`
  - `writeAttempted=false`
  - `writeExecuted=false`
  - `postWriteReadinessTriggered=false`
- 因此状态机不能再只验证到 handoff 为止；它必须显式要求 durable executor 报告，同时识别当前 shell 报告仍不能作为真实写入放行凭证。

## 多专家会诊

- Backend/API 专家:
  - durable executor 报告必须绑定 `handoffDigest`、`requestSpecDigest`、`bodyDigest`、audit receipt 与服务端幂等键。
  - 当前 shell 的 `executionAttemptSpec` 可以作为未来实现输入形状，但不能表示已经执行 POST。
- Security/RBAC 专家:
  - `writeExecuted=true`、`deploymentId`、`deploymentUid`、`writeResult`、`postWriteReadinessTriggered=true` 在当前版本都必须被视为伪造成功声明。
  - executor 报告中出现 Authorization、token、password、secret 或真实 API Key 形态必须 fail-closed。
- Agent 架构专家:
  - 状态机新增 `durableWriteExecutorReportRequired=true`，把 future writer 从“注释里的下一步”提升为可测试门禁。
  - 当前 shell report 通过形状校验后仍追加 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD` blocker，保持 `writePermitted=false`。
- Test 架构专家:
  - 缺少 executor report 应返回 `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`。
  - 当前 shell report 应被识别为合同壳，并且只剩 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`。
  - 伪造成功 report 应同时触发合同无效和成功声明不可信。
- Documentation/Learning 专家:
  - 本批教学重点是“证据链不是绿灯链”：每增加一个可验证报告，也要明确它是否具备 release 权限。

## 变更摘要

- `NimCreateStateMachineSupport.ReadinessRequest` 新增:
  - `durableWriteExecutorReport`
  - 保留兼容构造器，使旧负例可以继续表达“缺失 durable executor report”。
- 状态机新增输出:
  - `durableWriteExecutorReportRequired=true`
- 状态机新增 durable executor report 校验:
  - 缺失报告: `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`
  - 当前 shell 形状合法: 追加 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`
  - 合同不合法: `DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID`
  - 伪造成功声明: `DURABLE_WRITE_EXECUTOR_SUCCESS_NOT_TRUSTED`
  - secret 泄漏: `DURABLE_WRITE_EXECUTOR_REPORT_CONTAINS_FORBIDDEN_SECRET`
- 状态机会验证 shell report:
  - `durableWriteExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
  - `executionMode=DURABLE_WRITE_EXECUTOR_CONTRACT_SHELL`
  - `executionState=IMPLEMENTATION_HOLD`
  - `networkAccess=NOT_PERFORMED`
  - `sideEffect=NONE`
  - `inputAccepted=true`
  - `executorImplementationAvailable=false`
  - `writeAttempted=false`
  - `writeExecuted=false`
  - `postWriteReadinessTriggered=false`
  - `blockedBy` 只能包含 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`
  - `executionAttemptSpec` 绑定 path、digest、audit receipt、幂等键、kube-manager auth boundary 和 post-write readiness executor。
- 更新测试语义:
  - 过去部分测试把 handoff 完成后视为 `READY_FOR_CONTROLLED_WRITE`。
  - 本批统一改为: handoff 完成但缺少 durable executor report 时保持 `HELD`。
  - 当前 shell report 形状通过时仍保持 `HELD`，且唯一 blocker 是 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`。

## 安全边界

- 本批没有新增任何真实 HTTP 调用、Controller、Tool 注册或后台执行器。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- durable executor report 是状态机必须检查的证据，但当前 shell report 不是 release credential。
- 任何“已经写入成功”的字段在当前版本都不能放行。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimCreateReadinessHttpAdapterSupportTest" test`
  - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
  - `git diff --check`
  - 真实密钥形态静态扫描 0 命中。
  - `mvn -q test`
- 全量测试备注: test profile 下 embedding 模型下载超时后按预期降级，Maven 最终退出码为 0。

## 是否访问真实 8100

否。本批只运行纯单元测试和 mock-first 契约测试。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 识别真实 durable audit log/table/service，为 `NimCreateAuditWriterSupport` 的 mock receipt 替换做准备。
3. 设计真实 durable write executor 的最小实现门禁: 受控 kube-manager HTTP 边界、写前/写后审计、幂等键持久化、POST 响应校验和写后 readiness 触发。
4. 在真实 writer 未实现前，禁止任何 `writeExecuted=true`、deployment ID 或 post-write readiness claim 进入放行路径。
