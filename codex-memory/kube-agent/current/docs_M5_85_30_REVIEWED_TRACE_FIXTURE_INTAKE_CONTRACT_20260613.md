# M5.85-30 Reviewed trace fixture intake 合同

## 切片目标

为 Phase 1 Eval trace evidence 增加一个 admin-only / read-only / contract-only 的 reviewed redacted trace fixture intake 合同，明确 fixture 在进入人审、Git review 和后续 catalog promotion 前必须满足的字段、质量门、隐私证明和禁止捷径。

## 已完成

- 新增 `AgentReviewedTraceFixtureIntakeContractService`。
- 新增 `AgentReviewedTraceFixtureIntakeContractResponse`，schema 为 `agent-reviewed-trace-fixture-intake-contract.v1`。
- 新增 endpoint：`GET /api/agent/observability/eval/reviewed-trace-fixture-intake-contract`。
- 响应内容包含：
  - required fixture fields。
  - review workflow。
  - quality gates。
  - trace set readiness。
  - forbidden shortcuts。
  - endpoint map。
  - safety proof。
  - privacy proof。
- 新增/更新测试：
  - `AgentReviewedTraceFixtureIntakeContractServiceTest`
  - `Batch4ReviewedTraceFixtureIntakeChineseCommentContractTest`
  - `ObservabilityControllerSecurityContractTest`
  - `ObservabilityControllerTest`
  - `AgentSecurityConfigWebMvcTest`

## 验证

- Focused tests passed:
  - `mvn -q "-Dtest=AgentReviewedTraceFixtureIntakeContractServiceTest,Batch4ReviewedTraceFixtureIntakeChineseCommentContractTest,ObservabilityControllerSecurityContractTest,ObservabilityControllerTest,AgentSecurityConfigWebMvcTest" test`
- Final validation before commit still needs:
  - `mvn -q "-DskipTests" validate`
  - `git diff --check`

## 安全不变量

- 新 endpoint 是 admin-only、read-only、contract-only、intake-spec-only。
- 不接受 fixture upload。
- 不接受 caller traceIds。
- 不接收请求体。
- 不写 `eval-trace-sets.json`。
- 不执行 runtime catalog mutation。
- 不运行 eval/replay。
- 不调用 Tool / MCP / LLM / RAG / kube-manager。
- 不创建 HITL marker。
- 不写 audit / memory。
- 不打开 CI blocking。
- 不授予 release authority。
- 不升级依赖。
- 不触碰二期 NIM / HPC / Slurm / BCM 权力。

## 下一步建议

- 继续补真实 reviewed redacted fixture 文件或 catalog patch review 证据。
- 或继续剩余 support/test-helper 中文教学注释。
- kube-manager 8100 READ smoke 仍只在服务启动且提供当前用户 token/orgId 后运行。
