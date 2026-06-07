# M5.21 第六十五批 NIM accepted boolean source guard 契约审计

> 日期: 2026-06-07
> 范围: `M521NimAcceptedBooleanSourceContractTest`
> 约束: 本批只新增源码级回归护栏；不修改生产 release 逻辑，不创建 release decision，不创建 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 kube-manager `8100`，不执行 `POST /api/{orgId}/deployment`，不开放 `nim_create`。

## 背景

M5.21-64 已经把 `releaseDecisionGateReportAccepted` 标记为兼容字段、非权威、不得单独消费。剩余风险是未来生产代码可能直接搜索这个旧字段并写出:

- `report.get("releaseDecisionGateReportAccepted")`
- `containsKey("releaseDecisionGateReportAccepted")`
- `Boolean.TRUE.equals(...)`

这类代码会绕开 M5.21-63/M5.21-64 的 scope、digest、code release switch 和 durable executor 二次校验要求。

## 本批交付

- 新增 `M521NimAcceptedBooleanSourceContractTest`。
- 扫描 `src/main/java` 生产源码，禁止单独读取/消费 `releaseDecisionGateReportAccepted`。
- 允许 `NimCreateStateMachineReleaseDecisionRequirementSupport` 继续输出兼容字段和非权威伴随字段。
- 锁定合同壳必须保留:
  - `releaseDecisionGateReportAcceptedFieldIsCompatibilityOnly=true`
  - `releaseDecisionGateReportAcceptedIsAuthoritative=false`
  - `releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed=false`
  - `releaseDecisionGateReportAcceptedRequiredCompanionSignals`
  - `fallbackToReleaseDecisionGateReportAcceptedAllowed=false`
  - `RELEASE_DECISION_GATE_REPORT_ACCEPTED_FLAG_NOT_AUTHORITATIVE`

## 安全结论

- 本批没有修改 `NimCreateStateMachineSupport` 或真实状态机放行条件。
- 本批没有新增 HTTP client、Elasticsearch writer、`ISysLogService`、Spring Bean、Tool 或 Controller。
- 本批没有访问真实 `8100`，没有执行 `POST /api/{orgId}/deployment`，没有写 `sys_log`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。
- 生产代码当前没有单独消费 `releaseDecisionGateReportAccepted`。

## 验证

已通过:

```bash
mvn -q "-Dtest=M521NimAcceptedBooleanSourceContractTest" test
mvn -q "-Dtest=M521NimAcceptedBooleanSourceContractTest,NimCreateStateMachineReleaseDecisionRequirementSupportTest" test
mvn -q test
```

全量测试中 `model.onnx` 下载超时，Atlas 按现有设计降级到 L1 embedding mode；Maven 退出码为 0，本批源码护栏与回归测试通过。

本轮最终收尾还执行了边界 import 扫描、secret 扫描、H 盘同步校验和 git push，确认本批没有新增真实写执行链路或密钥材料。

## 学习笔记

顶级 Agent 的安全边界不能只靠运行时数据结构。对于容易被未来误用的字段，需要增加源码级契约测试，把“禁止直接消费某个字段”变成自动回归门禁。

本批展示了一个常用模式：先用数据契约声明字段非权威，再用源码契约禁止生产代码绕开伴随信号直接读取旧字段。
