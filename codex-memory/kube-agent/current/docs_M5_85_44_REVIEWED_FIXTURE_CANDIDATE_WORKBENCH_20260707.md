# M5.85-44 Reviewed Fixture Candidate Workbench 恢复记忆

## 目标

本切片继续推进 Phase 1 顶级 Agent Core 的 Eval trace evidence 主线。M5.85-43 已有手动 candidate preview，但还需要调用方自己提供 traceId；M5.85-44 新增一个只读自动工作台，从 redacted candidate discovery 中选出首个推荐 trace，再生成 reviewed fixture candidate preview，帮助后续准备真实 reviewed fixture。

## 已完成代码

- 新增 `AgentReviewedTraceFixtureCandidateWorkbenchService`：只组合 redacted candidate discovery 和 reviewed fixture candidate preview。
- 新增 `AgentReviewedTraceFixtureCandidateWorkbenchResponse`：发布 `agent-reviewed-trace-fixture-candidate-workbench.v1`，包含 discovery summary、selected candidate、candidate preview、blocking reasons、next actions、endpoint map、policy/safety/privacy。
- 新增 endpoint：`GET /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate-workbench?limit={limit}`。
- 更新 workbench capabilities：新增 `workbench-reviewed-fixture-candidate-autopreview`，能力数量从 17 变为 18。
- 更新 workbench overview：`nextActions` 增加 `open-reviewed-fixture-candidate-workbench`，trace set row 增加 `reviewedFixtureCandidateWorkbenchPath`。
- 更新 Controller、安全合同、服务测试、overview/capability 测试和文档记忆。

## 安全边界

- 不接收 caller traceId；自动选择的 traceId 只来自 redacted candidate discovery 的推荐结果。
- `candidatePreview.readyForFixtureCommit=false`，候选草稿不会成为 reviewed fixture。
- 不创建 `src/main/resources/observability/reviewed-trace-fixtures/*.json`，不上传 fixture，不写 `eval-trace-sets.json`。
- 不运行 Tool/MCP/LLM/RAG/kube-manager，不写 HITL/audit/memory，不启用 eval runtime、CI blocking 或 release authority。
- 响应不嵌入 raw audit、replay steps、eval reports、fixtureRows、raw endpoint、raw reason、raw parameter values、token 或密码。

## 已验证

- `mvn -q "-Dtest=AgentReviewedTraceFixtureCandidateWorkbenchServiceTest,AgentReviewedTraceFixtureCandidateServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest" test`
- `mvn -q test`
- `mvn -q "-DskipTests" package`
- `git diff --check`
- 真实测试密码字面量扫描：无命中。

## 下一步

1. 使用自动 candidate workbench 找出真实可人审 trace 候选。
2. 通过人工 Git review 准备首个 `qualityGateStatus=PASS` 的 reviewed redacted fixture 文件。
3. 或切到 `vue-kube-manager` eval workbench，只读渲染自动 candidate workbench、candidate preview、readiness、failed gates、checklist 和 next actions，并确保没有 runtime action。
