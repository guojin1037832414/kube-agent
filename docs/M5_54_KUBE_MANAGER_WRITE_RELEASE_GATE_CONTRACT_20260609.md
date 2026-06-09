# M5.54 Kube-Manager Write Release Gate Contract

Date: 2026-06-09

## 目标

M5.54 继续推进 Phase 1 顶级 Agent Core 的通用写安全链路：在任何 kube-manager 写执行、写重试或补偿进入运行时之前，先把 durable prewrite receipt 与 HITL/release evidence 变成源码拥有、可审计、可测试、可观测的 release gate 契约。

本轮仍然保持只读、admin-only、not-bound：不调用 kube-manager，不发起 HITL，不写 audit，不签发 durable receipt，不打开 release switch，不启用写重试。

## 本轮新增

- `KubeManagerWriteDurableReceiptContract`
- `KubeManagerWriteReleaseEvidenceContract`
- `KubeManagerWriteReleaseGateCatalog`
- `AgentKubeManagerWriteReleaseGateContractResponse`
- `AgentKubeManagerWriteReleaseGateContractService`
- `GET /api/agent/observability/kube-manager/http-outlet/write-release-gate-contract`

## Durable Receipt Contract

未来如果要允许 generic kube-manager write，必须先有 durable prewrite receipt。M5.54 只定义字段，不签发 receipt。

Required fields:

- `receiptId`
- `auditEventDigest`
- `requestSpecDigest`
- `principalFingerprint`
- `organizationFingerprint`
- `operationType`
- `httpMethod`
- `pathTemplate`
- `requestBodyDigest`
- `idempotencyKeyDigest`
- `hitlConfirmationDigest`
- `releaseEvidenceDigest`
- `createdAt`

当前状态：

```text
contractExists=true
boundToHttpOutlet=false
issuerExists=false
issuedByReadinessEndpoint=false
durableStorageMutationAllowed=false
receiptPhase=PRE_EXECUTION
digestAlgorithm=SHA-256
```

## HITL / Release Evidence Contract

未来如果要打开写 release gate，至少需要以下服务端可信证据：

- `serverHitlConfirmationDigest`
- `releaseReviewerFingerprint`
- `releaseDecisionDigest`
- `evalGateBundleDigest`
- `operationSafetyContractDigest`
- `retryGovernanceContractDigest`
- `operatorIntentDigest`
- `tenantOwnershipEvidenceDigest`

明确拒绝的证据来源：

- LLM-generated approval text
- caller request flag
- frontend checkbox alone
- durable executor success claim
- legacy migration report alone
- post-write success response

当前状态：

```text
contractExists=true
boundToHttpOutlet=false
callerProvidedReleaseEvidenceAccepted=false
canOpenReleaseSwitch=false
runtimeReleaseGateOpenCount=0
```

## Readiness 更新

`AgentKubeManagerWriteRetryReadinessService` 现在能报告：

- `genericDurableReceiptContractExists=true`
- `genericDurableReceiptContractBoundToHttpOutlet=false`
- `genericDurableReceiptIssuerExists=false`
- `genericDurableReceiptIssuedByReadinessEndpoint=false`
- `genericDurableReceiptCanOpenReleaseGate=false`
- `genericReleaseEvidenceContractExists=true`
- `genericReleaseEvidenceContractBoundToHttpOutlet=false`
- `serverHitlConfirmationBoundToHttpOutlet=false`
- `callerProvidedReleaseEvidenceAccepted=false`
- `runtimeReleaseGateSwitchExists=false`
- `runtimeReleaseGateOpenCount=0`

新增 blocked reasons：

- `generic-durable-receipt-contract-not-bound-to-http-outlet`
- `generic-durable-receipt-issuer-missing`
- `generic-release-evidence-contract-not-bound-to-http-outlet`
- `server-hitl-confirmation-not-bound-to-http-outlet`
- `runtime-release-gate-switch-intentionally-absent`

## 安全边界

M5.54 不做以下事情：

- 不调用 kube-manager `8100`
- 不调用 `/api/login`
- 不使用 `KubeManagerHttpClient`
- 不使用 `RestClient`
- 不调用 `executeWrite`
- 不执行 Tool
- 不调用 HITL controller
- 不调用 LLM
- 不访问外部服务
- 不写 audit
- 不签发 durable receipt
- 不写 durable storage
- 不注入 HTTP header
- 不执行 post-write readback
- 不修改 Retry / CircuitBreaker / Bulkhead registry
- 不提供 runtime release switch
- 不执行补偿
- 不启用 write retry
- 不触碰 NIM / HPC / Slurm / BCM

## 教学重点

顶级 Agent 的写能力不能只依赖“用户点了确认”或“LLM 说已批准”。成熟做法是：

- HITL confirmation 必须由服务端生成并摘要化。
- release decision 必须绑定 reviewer、eval gate、operation safety、retry governance、tenant ownership。
- durable prewrite receipt 必须在第一次写尝试前存在。
- 任何 caller-provided approval、前端 checkbox、LLM 文本、executor success claim 都不能倒灌成 release authority。

M5.54 的正确结果是：release gate 契约已经存在，但 release gate 仍然关闭。

## 验证

```powershell
mvn -q "-Dtest=KubeManagerWriteReleaseGateCatalogTest,AgentKubeManagerWriteReleaseGateContractServiceTest,AgentKubeManagerWriteRetryReadinessServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

## 下一步

下一阶段仍应继续补齐 generic manager Agent Core，而不是开启真实写：

- 将 M5.49-M5.54 的 kube-manager observability contracts 暴露给 Vue 页面
- 为 durable receipt binding 设计未来 writer 接口，但先做 contract 和 tests
- 把 release/eval evidence 纳入 CI/release gate
- 继续保持 NIM / HPC / Slurm / BCM 为 Phase 2
