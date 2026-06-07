# M5.21 第六十七批 NIM durable audit storage probe result 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateDurableAuditStorageProbeResultSupport`、`NimCreateDurableAuditStorageProbeResultSupportTest`
> 约束: 本批只定义 future server-issued `NimDurableAuditStorageProbeResult` 的合同；不创建真实 DTO/Bean，不绑定 HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-66 已经定义 storage probe executor contract shell，但它仍只是未来执行器计划。下一处容易误读的点是：未来代码可能把 executor plan、availability gate plan、writer boundary test double 或 typed schema 当成真实 probe result。

本批把 future server-issued `NimDurableAuditStorageProbeResult` 单独合同化，明确它不是 availability plan，不是 schema，不是 receipt，也不是 release credential。

## 本批交付

- 新增 `NimCreateDurableAuditStorageProbeResultSupport`。
- 新增 `NimCreateDurableAuditStorageProbeResultSupportTest`。
- 输入绑定:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `storageProbeExecutorReport`
  - `durableAuditReceiptAckSchemaReport`
  - optional `callerProbeResult`，当前永远非权威
- 输出固定 fail-closed:
  - `executionMode=DURABLE_AUDIT_STORAGE_PROBE_RESULT_CONTRACT_ONLY`
  - `probeResultState=IMPLEMENTATION_HOLD|REJECTED`
  - `serverIssuedProbeResultAccepted=false`
  - `storageProbeExecuted=false`
  - `storageAvailable=false`
  - `durableAckVerified=false`
  - `readAfterWriteVerified=false`
  - `storageProbeReceiptIssued=false`
  - `preWriteAllowed=false`
  - `writeExecutionAllowed=false`
  - `realHttpExecutionAllowed=false`
  - `durableReceiptCanBeIssued=false`

## 契约要点

`NimCreateDurableAuditStorageProbeResultSupport` 同时校验:

- M5.21-66 probe executor report 仍为 HOLD，且 `probeExecutorPlanDigest` 可复算。
- M5.21-56 typed ack/receipt schema report 仍为 HOLD，且 `schemaDigest` 可复算。
- 两份上游报告绑定同一 `sourceAuditEventDigest`、`sourceWriterPlanDigest`、`sourceAvailabilityPlanDigest` 和 `sourceBoundaryPlanDigest`。
- `trustedPrincipalDigest` 来自 `SERVER_SESSION_CONTEXT` 的 canonical digest。
- caller-supplied `probeResult` / `storageProbeReceipt` 即使形状为空，也不能作为 server-issued result。

合法路径只生成 `probeResultContract`，并给出 hold blocker:

- `STORAGE_PROBE_RESULT_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有把 M5.21-66 executor plan 升级为真实 probe result。
- 本批没有签发 `StorageAvailabilityProbeReceipt`。
- 本批没有让 `preWriteAllowed`、`writeExecutionAllowed`、`releaseEligible` 或 `durableReceiptCanBeIssued` 变成 true。
- 本批新增源码级依赖扫描，防止 support 类绑定 Spring、HTTP、Elasticsearch、`ISysLogService` 或真实存储写调用。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeResultSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditStorageProbeExecutorSupportTest,NimCreateDurableAuditReceiptSchemaSupportTest,NimCreateDurableAuditWriterInterfaceSpecSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest" test
```

最终收尾验证也已通过：全量 `mvn -q test`、`git diff --check`、production 边界 import 扫描、静态 secret 扫描、H 盘 SHA256 同步校验、commit 和 push。全量测试备注：`model.onnx` 下载超时后 Atlas 降级到 L1 embedding mode，但 Maven 退出码为 0；这是当前本地环境可接受的降级测试路径，不是 M5.21-67 失败。

## HOLD 清单

- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 没有真实 `8100` 访问。
- 没有 `POST /api/{orgId}/deployment`。
- 没有 Elasticsearch / `ISysLogService` / `sys_log` 写入。
- 没有 Spring Bean、Tool、Controller 或真实 HTTP client 注册。
- `callerProbeResult` 当前永远非权威。

## 学习笔记

顶级 Agent 的写链路不能让“计划对象”“schema 对象”和“真实执行结果”混在一起。M5.21-67 的核心学习点是：结果必须由服务端签发，并绑定执行器计划、schema、审计事件和可信身份。只要真实执行器还没有实现，任何 result-shaped 输入都应该 fail closed。

这让未来实现真实 storage probe 时有一个清晰迁移点：从 `IMPLEMENTATION_HOLD` 迁移到 reviewed server-issued result，而不是让调用方或测试替身悄悄制造成功凭证。

## 下一步

- 继续把 M5.21-67 的 result contract 接入后续 durable ack / receipt validation 迁移计划。
- 在真实 probe 前，定义服务端签发 result 的 Java value type、canonical digest 和 read-after-write evidence model。
- 保持 NIM create 写链路 HOLD，直到 trusted policy、durable audit writer、probe result、pre/post ack、release decision、state-machine gate 和 code release switch 全部通过 review。
