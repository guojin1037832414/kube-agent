# M5.21 第六十八批 NIM durable audit receipt validation probe result binding 契约审计

> 日期: 2026-06-07
> 范围: `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport`、`NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest`
> 约束: 本批只定义 future receipt validation 必须绑定 M5.21-67 storage probe result contract 的迁移契约；不创建真实 DTO/Bean/validator，不绑定 HTTP client，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行真实 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-67 已经把 future server-issued `NimDurableAuditStorageProbeResult` 单独契约化。下一个容易误读的点是：未来 receipt validator 可能只看到 M5.21-56 typed schema 或 M5.21-57 validation gate plan，就开始校验 receipt，跳过真实 storage probe result 的来源、digest 和服务端签发边界。

本批新增一个 binding contract：future `NimDurableAuditReceiptValidator` 在处理 `StorageAvailabilityProbeReceipt`、pre/post durable ack、final durable receipt 之前，必须先绑定 M5.21-67 的 probe result contract 和 M5.21-57 的 validation gate report。

## 本批交付

- 新增 `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport`。
- 新增 `NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest`。
- 输入绑定:
  - `auditContext`
  - `trustedPrincipalSnapshot`
  - `durableAuditStorageProbeResultReport`
  - `durableAuditReceiptValidationGateReport`
  - optional `callerReceiptEvidence`，当前永远非权威
- 输出固定 fail-closed:
  - `executionMode=DURABLE_AUDIT_RECEIPT_VALIDATION_PROBE_RESULT_BINDING_CONTRACT_ONLY`
  - `bindingState=IMPLEMENTATION_HOLD|REJECTED`
  - `storageProbeResultBoundForValidation=false`
  - `serverIssuedProbeResultAccepted=false`
  - `validationCanRunNow=false`
  - `storageProbeReceiptValidated=false`
  - `durableReceiptValidationPassed=false`
  - `releaseEligible=false`
  - `writeExecutionAllowed=false`

## 契约要点

`NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` 同时校验:

- M5.21-67 probe result contract report 仍为 HOLD，且 `probeResultContractDigest` 可复算。
- M5.21-57 receipt validation gate report 仍为 HOLD，且 `validationPlanDigest` 可复算。
- 两份上游报告绑定同一 `sourceAuditEventDigest`、`sourceReceiptSchemaDigest`、`sourceInterfaceSpecDigest`、`sourceBoundaryPlanDigest`、`sourceWriterPlanDigest` 和 `sourceAvailabilityPlanDigest`。
- M5.21-67 `futureProbeReceiptType` 与 M5.21-57 `requiredEvidence.storageProbeReceipt.requiredType` 一致。
- `requiredStatus=STORAGE_AVAILABLE_CONFIRMED` 只能作为 future requirement，不能被当成当前 storage available。
- caller-supplied `probeResult`、`storageProbeResult`、`storageProbeReceipt`、`validationResult` 或 `releaseDecision` 永远不是权威证据。

合法路径只生成 `bindingPlan`，并给出 hold blocker:

- `RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD`

## 安全结论

- 本批没有把 M5.21-67 contract 升级为真实 `NimDurableAuditStorageProbeResult`。
- 本批没有运行 receipt validation，也没有生成 validation pass、release decision 或 release credential。
- 本批没有让 `storageProbeResultBoundForValidation`、`validationCanRunNow`、`durableReceiptValidationPassed`、`releaseEligible` 或 `writeExecutionAllowed` 变成 true。
- 本批新增源码级依赖扫描，防止 support 类绑定 Spring、HTTP、Elasticsearch、`ISysLogService` 或真实存储写调用。

## 验证

已通过:

```bash
mvn -q "-Dtest=NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest" test
```

组合测试、全量 `mvn -q test`、`git diff --check`、production 边界 import 扫描、静态 secret 扫描、H 盘 SHA256 同步校验、commit 和 push 均已通过。全量测试备注：`model.onnx` 下载超时后 Atlas 降级到 L1 embedding mode，但 Maven 退出码为 0；这是当前本地环境可接受的降级测试路径，不是 M5.21-68 失败。

## HOLD 清单

- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 没有真实 `8100` 访问。
- 没有真实 `POST /api/{orgId}/deployment` 执行。
- 没有 Elasticsearch / `ISysLogService` / `sys_log` 写入。
- 没有 Spring Bean、Tool、Controller 或真实 HTTP client 注册。
- `callerReceiptEvidence` 当前永远非权威。

## 学习笔记

顶级 Agent 的安全写链路不能只问“有没有 schema”和“有没有 validation plan”。真正可放行的 validator 必须确认：probe result 是服务端签发的、digest 可复算、与 audit event / principal / writer plan / availability plan / receipt schema 同链，并且 receipt evidence 不是调用方自己塞进来的。

M5.21-68 的核心学习点是区分“未来证据描述字段”和“真实证据实例”。M5.21-57 的 `requiredEvidence.storageProbeReceipt` 是规则描述，不是已经签发的 receipt；M5.21-67 的 `probeResultContract` 是未来结果契约，不是已执行的 storage probe result。

## 下一步

- 将 M5.21-68 binding 继续接入 validation result / release decision migration 的后续契约。
- 在真实 probe 前，定义 server-issued result value type、storage probe receipt value type、canonical digest 和 read-after-write evidence model。
- 保持 NIM create 写链路 HOLD，直到 trusted policy、durable audit writer、probe result、pre/post ack、receipt validation、release decision、state-machine gate 和 code release switch 全部通过 review。
