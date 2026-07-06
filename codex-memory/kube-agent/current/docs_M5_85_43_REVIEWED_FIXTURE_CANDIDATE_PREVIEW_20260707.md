# M5.85-43 Reviewed Fixture Candidate Preview 恢复记忆

## 目标

本切片继续推进 Phase 1 顶级 Agent Core 的 Eval trace evidence 主线。目标不是伪造首个真实 reviewed fixture，而是在真实 fixture 入仓前补一站只读预检：管理员给出 trace set 和候选 traceId 后，后端从已有 redacted replay / deterministic eval 读模型整理出可供人审/Git review 使用的候选草稿、质量门摘要和阻断缺口。

## 已完成代码

- 新增 `AgentReviewedTraceFixtureCandidateService`：读取 trace-set catalog，规范化候选 traceId，只选择第一个 W3C-compatible traceId；非法或敏感输入只计数不回显。
- 新增 `AgentReviewedTraceFixtureCandidateResponse`：发布 `agent-reviewed-trace-fixture-candidate.v1`，返回 `candidateFixtureDraft`、`candidateGateSummary`、`replaySource` digest/count、proof、blocking reasons、next actions、safety/privacy。
- 新增 endpoint：`POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate`。
- 更新 workbench capabilities：新增 `workbench-reviewed-fixture-candidate`，能力数量从 16 变为 17。
- 更新 workbench overview：`nextActions` 增加 `preview-reviewed-fixture-candidate-before-git-review`，trace set row 增加 `reviewedFixtureCandidatePath`，workflow stages 增加 `reviewed-fixture-candidate-preview`。
- 更新 Controller、安全合同、服务测试、overview/capability 测试和文档记忆。

## 安全边界

- `readyForFixtureCommit=false`，候选草稿不会成为 reviewed fixture。
- 候选草稿必须经人工 Git review 补齐 `sourceCommitSha`、`reviewer`、`reviewTimestamp`、`evidenceDigest` 后，才可能进入真实 fixture 文件。
- 不创建 `src/main/resources/observability/reviewed-trace-fixtures/*.json`，不上传 fixture，不写 `eval-trace-sets.json`。
- 不运行 Tool/MCP/LLM/RAG/kube-manager，不写 HITL/audit/memory，不启用 eval runtime、CI blocking 或 release authority。
- 响应不嵌入 replay steps、eval reports、fixtureRows、raw endpoint、raw reason、raw parameter values、token 或密码。

## 已验证

- `mvn -q "-Dtest=AgentReviewedTraceFixtureCandidateServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest" test`
- `mvn -q test`
- `mvn -q "-DskipTests" package`
- `git diff --check`
- 真实测试密码字面量扫描：无命中。

## 下一步

1. 用 reviewed fixture candidate preview 找出一个真实可人审 trace 候选。
2. 通过人工 Git review 准备首个 `qualityGateStatus=PASS` 的 reviewed redacted fixture 文件。
3. 或切到 `vue-kube-manager` eval workbench，只读渲染 candidate preview、readiness、failed gates、checklist 和 next actions，并确保没有 runtime action。
