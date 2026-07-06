# M5.85-45 Reviewed Fixture Human Review Package 恢复记忆

## 目标

本切片继续推进 Phase 1 顶级 Agent Core 的 Eval trace evidence 主线。M5.85-44 已经能自动发现首个 redacted trace 候选并生成 candidate preview；M5.85-45 把这个候选草稿进一步整理成人工 Git review 可使用的只读人审包。

## 已完成

- 新增 endpoint：`GET /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-package?limit={limit}`。
- 新增 `AgentReviewedTraceFixtureHumanReviewPackageService`：复用自动 candidate workbench，不重新接受 caller traceId。
- 新增 `AgentReviewedTraceFixtureHumanReviewPackageResponse`：发布 `agent-reviewed-trace-fixture-human-review-package.v1`。
- 响应包含 `manualReviewFields`、`reviewChecklist`、`manifestQualityGatePreview`、suggested fixture filename、candidate fixture draft、endpoint map、policy/safety/privacy proof。
- 更新 workbench capabilities：新增 `workbench-reviewed-fixture-human-review-package`，能力数量从 18 变为 19。
- 更新 workbench overview：`nextActions` 增加 `open-reviewed-fixture-human-review-package`，trace set row 增加 `reviewedFixtureHumanReviewPackagePath`。
- 更新 README、路线图、项目使命记忆和当前恢复状态。

## 验证

- `mvn -q "-Dtest=AgentReviewedTraceFixtureHumanReviewPackageServiceTest,AgentReviewedTraceFixtureCandidateWorkbenchServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest" test`

## 安全不变量

- 人审包是 admin-only / read-only / review-only。
- 不接收 caller traceId，不把 caller 输入提升为 reviewed evidence。
- 不创建 `src/main/resources/observability/reviewed-trace-fixtures/*.json`，不上传 fixture，不写 `eval-trace-sets.json`。
- 不把 candidate 直接变成 `qualityGateStatus=PASS`；`sourceCommitSha`、`reviewer`、`reviewTimestamp`、`evidenceDigest` 仍必须由人工 Git review 补齐。
- 不执行 Tool/MCP/LLM/RAG/kube-manager。
- 不写 HITL/audit/memory。
- 不启用 CI blocking、release authority、依赖升级或 Phase 2 NIM/HPC/Slurm/BCM 能力。
- 响应不嵌入 raw audit、replay steps、eval reports、fixtureRows、raw endpoint、raw reason、raw parameter values、token 或密码。

## 下一步

- 用人审包对真实自动候选做人工 Git review 准备；只有拿到真实 redacted trace、人工 Git review 字段和最终 sha256 evidence digest 后，才提交首个真实 reviewed fixture。
- 或切到 `vue-kube-manager` eval workbench 只读渲染自动候选预检、人审包、readiness、failedQualityGates、checklist 和 nextActions。
