# M5.21 第六十六批 NIM durable audit storage probe executor 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateDurableAuditStorageProbeExecutorSupport`、`NimCreateDurableAuditStorageProbeExecutorSupportTest`
> 约束: 本批只新增 storage probe executor 的 contract-only 壳；不创建 Spring Bean，不绑定 HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-53 已经生成 storage availability gate plan，M5.21-54 已经生成 dedicated durable audit writer boundary 与 test double contract。剩余风险是未来实现者可能把 availability gate plan 或 writer boundary test double 当成“探测已成功”，从而绕过真实服务端 storage probe。

本批新增一层 probe executor contract shell，用来明确:

- 探测执行器必须位于 dedicated writer boundary 内。
- 合法输入也只能进入 `IMPLEMENTATION_HOLD`。
- availability gate plan 和 writer boundary plan 都不是真实 probe result。
- 任何调用方或 mock 自称 `storageAvailable=true`、`durableAckVerified=true`、`readAfterWriteVerified=true`、`writeExecutionAllowed=true` 都是伪造成功声明。

## 本批交付

- 新增 `NimCreateDurableAuditStorageProbeExecutorSupport`。
- 新增 `NimCreateDurableAuditStorageProbeExecutorSupportTest`。
- 输入绑定:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `storageAvailabilityGateReport`
  - `dedicatedAuditWriterBoundaryReport`
  - 可选 `probeExecutionSnapshot`，仅诊断，不具权威性
- 输出固定 fail-closed:
  - `executionMode=DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_CONTRACT_ONLY`
  - `probeExecutorState=IMPLEMENTATION_HOLD|REJECTED`
  - `networkAccess=NOT_PERFORMED`
  - `sideEffect=NONE`
  - `springBeanRegistered=false`
  - `httpClientBound=false`
  - `storageClientBound=false`
  - `storageProbeExecuted=false`
  - `realStorageTouched=false`
  - `storageAvailable=false`
  - `durableAckVerified=false`
  - `readAfterWriteVerified=false`
  - `preWriteAllowed=false`
  - `writeExecutionAllowed=false`
  - `realHttpExecutionAllowed=false`
  - `durableReceiptCanBeIssued=false`

## 契约要点

`NimCreateDurableAuditStorageProbeExecutorSupport` 只接受仍为 HOLD 的 M5.21-53/M5.21-54 报告，并重新校验:

- availability gate digest: `sourceAvailabilityPlanDigest`
- writer boundary digest: `sourceBoundaryPlanDigest`
- audit event digest: `sourceAuditEventDigest`
- trusted principal: org/user/username 必须来自 `SERVER_SESSION_CONTEXT`
- availability gate 必须仍然 `storageProbeExecuted=false`、`storageAvailable=false`
- writer boundary 必须仍然 `preWritePersisted=false`、`postWritePersisted=false`、`durableReceiptCanBeIssued=false`

合法路径只生成 `probeExecutorPlan` / `probeAttemptSpec`，并给出一个 hold blocker:

- `STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD`

非法路径返回具体 blocker，且不附加 hold blocker。

## 多专家结论

- 安全/RBAC: probe executor 不能脱离 dedicated writer boundary 单独成立，否则会出现“旁路探测成功凭证”。
- 测试架构: 合法输入也必须证明所有 success flags 为 false，并增加 source-level guard 禁止真实客户端依赖。
- 文档/恢复: M5.21-66 必须更新 changelog、wave index、项目记忆、会话进度、开发指南，并同步到 `H:\codex重要文件\kube-agent`。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeExecutorSupportTest" test
mvn -q "-Dtest=NimCreateDurableAuditStorageProbeExecutorSupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditWriterPlanSupportTest,NimCreateDurableAuditStorageSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按既有设计降级到 L1 embedding mode；Maven 退出码为 0。

最终收尾还包括 `git diff --check`、production 边界 import/注解/真实调用扫描、静态 secret 扫描、H 盘 SHA256 同步校验、commit 和 push。

## HOLD 清单

- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 没有真实 `8100` 访问。
- 没有 `POST /api/{orgId}/deployment`。
- 没有 Elasticsearch / `ISysLogService` / `sys_log` 写入。
- 没有 Spring Bean、Tool、Controller 或真实 HTTP client 注册。
- `probeExecutionSnapshot` 只能是诊断输入，不能签发 pass、ack、receipt 或 write permission。

## 学习笔记

storage probe executor 是“计划到真实世界”的最后几道门之一。顶级 Agent 不能因为上游报告里出现 `available` 或 `accepted` 这类词就信任它，而要强制绑定:

- 谁生成了报告；
- 报告属于哪个边界；
- digest 是否能复算；
- 成功态是否来自服务端真实执行；
- 当前代码是否真的实现了该执行器。

本批把这些要求提前固化成 contract shell。这样未来实现真实 probe 时，必须先让契约测试从 HOLD 迁移到 reviewed implementation，而不是悄悄把 mock 结果当成 release credential。

## 下一步

- 继续在 HOLD 前提下推进真实 dedicated durable audit writer 的服务端实现边界设计。
- 在任何真实 probe 前，先定义 `NimDurableAuditStorageProbeResult` 的 server-issued DTO、digest binding 和 failure model。
- 真实开放前仍需 trusted policy、durable writer、durable write executor、readiness aftercare 和 code release switch 全部通过 review。
