# M5.42 Eval Trace-Set Promotion Workflow

## 背景

M5.40、M5.39、M5.41 已经分别提供了候选发现、候选复核、catalog patch proposal。M5.42 把这三步组合成一个只读 workflow artifact，方便未来 Vue eval workbench 用一个接口展示完整证据晋升状态。

```text
POST /api/agent/observability/eval/trace-sets/{traceSetId}/promotion-workflow
    |
    | 1. candidate discovery
    | 2. select recommended trace anchors
    | 3. curation review
    | 4. catalog patch proposal
    v
AgentEvalTraceSetPromotionWorkflowArtifact
```

## 已交付

- 新增 `AgentEvalTraceSetPromotionWorkflowRequest`。
- 新增 `AgentEvalTraceSetPromotionWorkflowArtifact`。
- 新增 `AgentEvalTraceSetPromotionWorkflowService`。
- 新增 admin-only `POST /api/agent/observability/eval/trace-sets/{traceSetId}/promotion-workflow`。
- Workflow 从 candidate discovery 中选择推荐 trace anchors，再调用 catalog patch proposal。
- `maxRecommendedCandidates` 默认 10，最大 25，避免一次 workflow 把过多候选伪装成完整评审集。
- 未发现推荐候选时返回 `NO_RECOMMENDED_CANDIDATES`，不会产生 runtime catalog mutation。

## 安全边界

- Endpoint 是 observability admin-only，并保留 method-level `@PreAuthorize`。
- Workflow 是 orchestration-only / artifact-only。
- `catalogMutationAllowed=false`、`catalogMutated=false`、`runtimeCatalogWrite=false`。
- 不执行 Tool，不调用 kube-manager，不调用外部网络，不使用 LLM。
- 不暴露 raw principal、organization、conversation、endpoint、reason text、parameter values。
- 不嵌入 replay timeline，不嵌入 per-trace reports；需要 drill-down 时继续使用已有 replay/eval endpoints。
- NIM / HPC / Slurm / BCM 仍保持 Phase 2 暂停范围。

## 学习点

前端工作台不应该自己拼多个低层接口来猜“这个 trace set 是否可以晋升”。顶级 Agent 后端应该提供 typed workflow artifact，把发现、复核、提案、权限和隐私证明打包成稳定契约。这样前端只做展示、筛选、复制补丁、触发人工确认；发布权仍然留在 Git review 与 CI gate。

## 验证

- `mvn -q "-Dtest=AgentEvalTraceSetPromotionWorkflowServiceTest,AgentEvalTraceSetCandidateDiscoveryServiceTest,AgentEvalTraceSetCatalogServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`
