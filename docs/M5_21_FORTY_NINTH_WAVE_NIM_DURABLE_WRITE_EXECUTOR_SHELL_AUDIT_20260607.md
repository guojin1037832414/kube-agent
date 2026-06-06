# M5.21 第四十九批 NIM durable write executor 合同壳审计

> 日期: 2026-06-07 06:14 Asia/Shanghai
> 范围: `NimCreateDurableWriteExecutorSupport`、`NimCreateStateMachineSupport`、NIM create 写链路相关测试
> 约束: 只新增 mock-first/纯函数 durable write executor 入场合同壳；不注册 Tool，不新增 Controller，不持有 HTTP client，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 成熟项目证据

- mature `vue-kube-manager` 的 NIM 创建最终会落到 kube-manager Deployment 创建接口。
- M5.21-48 已经把 request spec 到 future durable writer 的 handoff 合同化，并声明 `futureExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`。
- 但 handoff 不是执行结果。如果后续开发把 handoff 当成“已经写入”，就会绕过真实 executor 的:
  - HTTP client 边界；
  - 写前/写后审计；
  - 幂等键复用规则；
  - POST 响应验证；
  - 写后 readiness 触发条件。
- 因此本批新增 durable write executor 的 contract shell：它可以验证入场证据，但在真实实现完成前必须 `IMPLEMENTATION_HOLD`。

## 多专家会诊

- Backend/API 专家:
  - executor shell 只能表达未来写入尝试规格，不能调用 `KubeManagerHttpClient`。
  - request spec、handoff plan、resolved path、body digest、request spec digest 必须全部一致。
- Security/RBAC 专家:
  - 即使 handoff 合法，也不能产生 `writeExecuted=true`。
  - caller 提供的 `writeExecuted/deploymentId/writeResult` 必须进入 ignored caller claims。
  - 输入出现 Authorization、token、password、secret 或真实 key 形态必须 fail-closed。
- Agent 架构专家:
  - `NimCreateDurableWriteExecutorSupport` 是 future writer 的边界占位，不是 Tool，不是 release credential。
  - 真正 writer 只能在这个边界内接入 reviewed `KubeManagerHttpClient` 和持久审计。
- Test 架构专家:
  - 正向输入也必须只返回 `executionState=IMPLEMENTATION_HOLD`。
  - 伪造 handoff/request spec 或 secret 泄漏必须 `REJECTED`，且 `writeAttempted=false/writeExecuted=false`。
- Documentation/Learning 专家:
  - 本批教学重点是“合同壳也有价值”：顶级 Agent 在真实副作用尚未实现前，要先把未来入口的不可越线规则写成测试。

## 变更摘要

- 新增 `NimCreateDurableWriteExecutorSupport`。
  - `prepare(...)` 消费:
    - `writeExecutionHandoffReport`
    - `writeRequestSpecReport`
  - 输出:
    - `durableWriteExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
    - `executionMode=DURABLE_WRITE_EXECUTOR_CONTRACT_SHELL`
    - `executionState=IMPLEMENTATION_HOLD|REJECTED`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `inputAccepted`
    - `executorImplementationAvailable=false`
    - `realHttpExecutionAllowed=false`
    - `writeAttempted=false`
    - `writeExecuted=false`
    - `postWriteReadinessTriggered=false`
    - `executionAttemptSpec`
    - `blockedBy`
- 合法 handoff/request spec 输入也只会得到:
  - `inputAccepted=true`
  - `executionState=IMPLEMENTATION_HOLD`
  - `blockedBy=[DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD]`
- `executionAttemptSpec` 只记录未来实现需要消费的证据:
  - target/method/path
  - request spec digest
  - body digest
  - handoff digest
  - server-derived idempotency key
  - durable audit receipt id/digest
  - kube-manager auth boundary
  - post-write readiness executor
  - `writeWillBeAttempted=false`
- `NimCreateStateMachineSupport` ignored caller claims 扩展:
  - `durableWriteExecutorReport`
  - `durableWriteExecutor`
  - `executorImplementationAvailable`
  - `writeAttempted`
  - `writeExecuted`
  - `writeResult`
  - `deploymentId`
  - `deploymentUid`
  - `postWriteReadinessTriggered`
- 新增 `NimCreateDurableWriteExecutorSupportTest`。

## 安全边界

- 本批没有新增任何真实 HTTP 调用、Controller、Tool 注册或后台执行器。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- executor shell 报告不是 release credential，不能替代 trusted policy、HITL、durable audit receipt、body rebuild、request spec、write handoff、READY readiness executor 或 release switch。
- 当前 shell 明确 `executorImplementationAvailable=false`；任何写入成功/部署 ID 都不能来自 Tool 入参。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## 验证

- 已通过:
  - `mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest" test`

## 是否访问真实 8100

否。本批只运行纯单元测试和 mock-first 契约测试。

## 下一步建议

1. 继续保持 `nim_create` HOLD。
2. 下一批可以把状态机扩展为“release 后还需要 durable write executor report，但当前 shell report 仍不能放行”的门禁契约。
3. 或先识别真实 durable audit log 表/服务，为后续真实 writer 的写前/写后审计落点做准备。
