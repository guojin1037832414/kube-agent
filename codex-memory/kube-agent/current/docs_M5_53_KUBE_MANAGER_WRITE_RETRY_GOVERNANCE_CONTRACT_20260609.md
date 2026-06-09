# M5.53 Kube-Manager Write Retry Governance Contract

Date: 2026-06-09

## 目标

M5.53 继续推进 Phase 1 顶级 Agent Core 的写安全链路：在任何 kube-manager 写重试进入运行时之前，先把失败分类、有界重试谓词和补偿策略沉淀成源码拥有、可审计、可测试、可观测的契约。

本轮仍然保持只读、admin-only、not-bound：不调用 kube-manager，不启用写重试，不执行补偿，不恢复 NIM / HPC / Slurm / BCM。

## 本轮新增

- `KubeManagerWriteRetryFailureClass`
- `KubeManagerWriteRetryPredicateContract`
- `KubeManagerWriteCompensationPolicy`
- `KubeManagerWriteRetryGovernanceCatalog`
- `AgentKubeManagerWriteRetryGovernanceContractResponse`
- `AgentKubeManagerWriteRetryGovernanceContractService`
- `GET /api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract`

## 契约语义

### Failure Class

失败分类分成两类：

- 未来可候选重试：传输超时、网关 502/503/504、受限的 429。
- 永不自动重试：调用方参数错误、认证/授权失败、租户或所有权不匹配、冲突/重复状态、没有 readback 的 unknown acceptance。

当前所有 failure class 都是：

```text
runtimeRetryableNow=false
runtimeRetryableFailureClassCount=0
```

这意味着它们只是未来 release review 的候选知识，不是运行时 retry predicate。

### Bounded Retry Predicate

未来如果要开启受控写重试，最低要求包括：

- maxAttempts = 2
- bounded jittered exponential backoff
- same server-derived idempotency key
- durable prewrite receipt
- operation allowlist
- RBAC/tenant evidence
- post-write readback before success
- caller override 不被接受

当前状态：

```text
boundToHttpOutlet=false
runtimePredicateExists=false
writeRetryEnabled=false
```

### Compensation Policy

补偿策略覆盖 create/update/delete/action 的未知或部分状态，但只给 operator review guidance：

- create unknown acceptance review
- update partial state review
- delete unknown state review
- action unknown effect review

当前状态：

```text
automaticCompensationAllowed=false
automaticCompensationPolicyCount=0
compensationExecutorExists=false
compensationExecuted=false
canOpenReleaseSwitch=false
```

## Readiness 更新

`AgentKubeManagerWriteRetryReadinessService` 现在能报告：

- `retryFailureClassificationContractExists=true`
- `retryPredicateContractExists=true`
- `retryPredicateBoundToHttpOutlet=false`
- `runtimeRetryableFailureClassCount=0`
- `callerProvidedRetryPredicateAccepted=false`
- `compensationPolicyContractExists=true`
- `compensationPolicyBoundToHttpOutlet=false`
- `compensationExecutorExists=false`
- `automaticCompensationPolicyCount=0`
- `compensationCanOpenReleaseSwitch=false`

新增 blocked reasons：

- `write-retry-predicate-contract-not-bound-to-http-outlet`
- `no-runtime-retryable-failure-class`
- `compensation-policy-contract-not-bound-to-http-outlet`
- `compensation-executor-missing`

## 安全边界

M5.53 不做以下事情：

- 不调用 kube-manager `8100`
- 不调用 `/api/login`
- 不使用 `KubeManagerHttpClient`
- 不使用 `RestClient`
- 不调用 `executeWrite`
- 不执行 Tool
- 不调用 LLM
- 不访问外部服务
- 不写 audit
- 不签发 durable receipt
- 不注入 HTTP header
- 不执行 post-write readback
- 不修改 Retry / CircuitBreaker / Bulkhead registry
- 不提供 runtime enable switch
- 不执行补偿
- 不启用 write retry
- 不触碰 NIM / HPC / Slurm / BCM

## 教学重点

顶级 Agent 的写重试不是简单的“遇到 502 就 retry”。写操作 retry 会放大副作用，因此必须先把以下问题变成可验证契约：

- 请求有没有被服务端接受？
- 幂等 key 是否来自服务端可信证据？
- 是否已经拿到 durable prewrite receipt？
- 操作是否在 allowlist 且绑定 RBAC/tenant evidence？
- 成功声明是否经过 readback？
- unknown side effect 是否进入人工补偿，而不是自动重放？

M5.53 的正确结果是：治理契约已经存在，但运行时能力仍然关闭。

## 验证

```powershell
mvn -q "-Dtest=KubeManagerWriteRetryGovernanceCatalogTest,AgentKubeManagerWriteRetryGovernanceContractServiceTest,AgentKubeManagerWriteOperationSafetyContractServiceTest,AgentKubeManagerWriteRetryReadinessServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## 下一步

下一阶段仍应继续补齐 generic manager Agent Core，而不是开启真实写重试：

- durable receipt 与 generic kube-manager HTTP outlet 的绑定契约
- HITL/release evidence 与写路径的绑定契约
- frontend observability 页面读取本轮 contract
- CI/eval gate 把写安全前置条件纳入 release evidence
- 继续保持 NIM / HPC / Slurm / BCM 为 Phase 2
