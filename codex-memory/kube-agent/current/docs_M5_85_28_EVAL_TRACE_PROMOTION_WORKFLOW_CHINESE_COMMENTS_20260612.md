# kube-agent 当前恢复快照 - M5.85-28

- Date: 2026-06-12
- Branch: codex/m521-29-top-agent-mission
- Latest wave: M5.85-28
- Latest title: Eval trace promotion workflow Chinese comments

## 本次完成

- 为 Eval trace promotion workflow / eval workbench wrapper 补充中文教学注释。
- 覆盖文件：
  - `AgentEvalTraceSetPromotionWorkflowService`
  - `AgentEvalTraceSetPromotionWorkflowArtifact`
  - `AgentEvalTraceSetPromotionWorkflowRequest`
  - `AgentEvalWorkbenchPromotionWorkflowService`
  - `AgentEvalWorkbenchPromotionWorkflowResponse`
  - `AgentEvalTraceSetPromotionWorkflowServiceTest`
  - `AgentEvalWorkbenchPromotionWorkflowServiceTest`
- 新增 `Batch4EvalTracePromotionWorkflowChineseCommentContractTest` 锁定中文教学 marker。

## 关键边界

- promotion workflow 是 read-only / workflow-only / proposal-only。
- candidate discovery -> curation review -> catalog patch proposal -> human Git review 是证据编排，不是 runtime catalog mutation。
- `readyForGitReview=true` 不是 release authority，也不是目录写权限。
- workbench `uiSteps`、`patchSummary`、`candidateGateSummary`、`nextActions` 只是 read-model navigation，不是可执行按钮。

## 验证

- `mvn -q "-Dtest=Batch4EvalTracePromotionWorkflowChineseCommentContractTest,AgentEvalTraceSetPromotionWorkflowServiceTest,AgentEvalWorkbenchPromotionWorkflowServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 安全不变量

- 本切片只改注释和 source-contract 测试，不改生产逻辑。
- 未打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking、runtime catalog write 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步建议

- 继续 Eval trace evidence： reviewed redacted fixture intake / catalog review workflow，或补真实 reviewed trace anchors。
- kube-manager `8100` READ smoke 仍需等服务监听并提供 token/orgId。
