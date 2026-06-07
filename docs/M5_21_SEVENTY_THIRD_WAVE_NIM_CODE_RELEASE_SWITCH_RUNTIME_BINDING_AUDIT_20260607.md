# M5.21 第七十三批 NIM code release switch runtime binding 契约审计

> 约束: 本批只定义 M5.21-72 code release switch contract report 到 `NimCreateStateMachineSupport` / `NimCreateDurableWriteExecutorSupport` 的 runtime binding 契约；不打开真实 switch，不创建 release decision，不签发 release credential，不接入 HTTP client，不注册 Spring Bean/Controller/Tool，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，`nim_create` 继续 HOLD。

## 背景

M5.21-72 已经定义 future `NimCreateDurableAuditCodeReleaseSwitch`，但它仍是值契约。如果未来状态机只看旧的 `nimCreateReleased=true` 布尔值，或者 durable executor 只看 state machine 的 `writePermitted=true`，就可能绕过 reviewed/server-owned code switch digest。

本批新增 runtime binding 契约，把“状态机必须复算 switch contract digest”和“durable executor 必须在真实 POST 前再次复核同一个 switch digest”提前写成可测试代码。

## 本批交付

- 新增 `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`。
- 新增 `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest`。
- 输入:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `durableAuditCodeReleaseSwitchContractReport` from M5.21-72
  - optional `stateMachineReleaseEvidence`
  - optional `durableExecutorReleaseEvidence`
- 输出:
  - `codeReleaseSwitchRuntimeBindingContract=NIM_CREATE_CODE_RELEASE_SWITCH_RUNTIME_BINDING_CONTRACT`
  - `executionMode=CODE_RELEASE_SWITCH_RUNTIME_BINDING_CONTRACT_ONLY`
  - `bindingState=IMPLEMENTATION_HOLD|REJECTED`
  - `targetStateMachine=NimCreateStateMachineSupport`
  - `targetDurableExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
  - `networkAccess=NOT_PERFORMED`
  - `sideEffect=NONE`
- 轻量更新:
  - `NimCreateStateMachineSupport` 输出 `codeReleaseSwitchRuntimeBindingRequired=true`、`codeReleaseSwitchRuntimeBindingInstalled=false`、`legacyNimCreateReleasedBooleanAuthoritative=false`。
  - `NimCreateDurableWriteExecutorSupport` 输出 `codeReleaseSwitchRuntimeBindingRequired=true`、`codeReleaseSwitchDigestVerified=false`、`releaseDecisionDigestVerified=false`、`validationResultDigestVerified=false`、`fallbackToStateMachineWritePermittedAllowed=false`。

## Runtime Binding 要求

未来状态机必须:

- 接收 reviewed/server-owned `codeReleaseSwitchContractReport`。
- 复算 M5.21-72 `codeReleaseSwitchContractDigest`。
- 绑定 server-issued `releaseDecisionDigest` 和 `validationResultDigest`。
- 绑定 write-chain digests: `bodyDigest`、`requestSpecDigest`、`handoffDigest`、`auditReceiptId`、`serverDerivedIdempotencyKey`。
- 禁止把旧 `nimCreateReleased=true` 当成权威开关。
- 禁止环境变量、runtime flag、caller evidence 替代 code switch。

未来 durable executor 必须:

- 在真实 `POST /api/{orgId}/deployment` 前再次复核 code release switch digest。
- 复核同一组 handoff/request/body/idempotency digest。
- 禁止只凭 state machine `writePermitted=true` 直接 POST。
- 禁止用 executor success 反向补齐 code switch evidence。

## HOLD 清单

- 本批没有真实 code release switch。
- 本批没有状态机真实放行。
- 本批没有 durable executor 真实写入。
- 本批没有 HTTP client、Controller、Tool registration、Spring Bean。
- 本批没有 Elasticsearch / `ISysLogService` / `sys_log` 写入。
- 本批没有访问真实 `8100`。
- 本批没有执行 `POST /api/{orgId}/deployment`。

## 8100 测试策略更新

用户于 2026-06-07 明确补充：**kube-manager 的查询类方法可以接入本地 `8100` 端口进行真实查询测试**。

本规则不等于开放写入能力。写入、创建、删除、状态变更、支付、充值、环境安装、集群变更等能力仍必须走 HOLD / HITL / mock-first 契约和显式授权。`nim_create` 属于创建/写链路，本批仍不访问 `8100`。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest,NimCreateDurableAuditCodeReleaseSwitchContractSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest" test
mvn -q test
```

全量测试备注：本次本地 test profile 中 `model.onnx` 下载超时并降级到 L1 embedding mode，Maven 最终退出码为 0；这是当前环境可接受的降级测试路径，不代表 M5.21-73 失败。

最终静态校验已通过:

```bash
git diff --check
production boundary scan
static secret scan
```

静态 secret 扫描只命中文档中的 `sk-REPLACE_WITH_YOUR_KEY` 占位示例和历史说明，不是真实密钥。

## 学习笔记

M5.21-73 的重点是“不要让 release switch 停在纸面契约”。顶级 Agent 的写链路不应只有一个开关对象，还要有运行时绑定：状态机要验证它，执行器要再次验证它，并且两者验证的是同一个 digest 事实。

这是一种常见的高安全工程模式：发布决策、代码开关、状态机放行、执行器执行必须互相独立复核，避免单点布尔值成为越权捷径。
