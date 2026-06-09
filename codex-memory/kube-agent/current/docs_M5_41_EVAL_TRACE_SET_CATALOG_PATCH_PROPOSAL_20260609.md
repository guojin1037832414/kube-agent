# M5.41 Eval Trace-Set Catalog Patch Proposal

## 背景

M5.40 已经能从脱敏审计读模型里发现候选 trace，M5.39 已经能对候选 trace 做 deterministic curation review。M5.41 补上的不是“自动写入 catalog”，而是一个更安全的发布治理步骤：生成可提交给人和 Git 审查的 JSON Patch 提案。

这让 eval evidence 的晋升链路变成：

```text
candidate discovery
  -> curation review
  -> catalog patch proposal
  -> human/Git review
  -> eval-trace-sets.json merge
  -> trace-set gate bundle regeneration
  -> future blocking CI
```

## 已交付

- 新增 `AgentEvalTraceSetCatalogPatchProposalArtifact`。
- 新增 `AgentEvalTraceSetCatalogService#catalogPatchProposal(...)`。
- 新增 admin-only `POST /api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal`。
- 复用 curation review：只有 `READY_FOR_CATALOG_REVIEW` 且存在新 trace anchor 时，才返回 `READY_FOR_GIT_REVIEW`。
- 输出 RFC 6902 JSON Patch 风格操作，例如 `replace /0/traceIds`。
- 保持运行时 catalog 只读：不改写 `observability/eval-trace-sets.json`，不把候选 trace 直接提升为发布证据。

## 安全边界

- Endpoint 仍然是 observability admin-only，并保留 method-level `@PreAuthorize`。
- Artifact 是 review-only / artifact-only。
- `catalogMutationAllowed=false`、`catalogMutated=false`、`runtimeCatalogWrite=false`。
- 不嵌入 replay timeline，不嵌入 per-trace report，只嵌入 compact curation review / suite gate。
- 不执行 Tool，不调用 kube-manager，不发起网络调用，不使用 LLM。
- 不暴露 raw principal、organization、conversation、endpoint、reason text、parameter values。
- NIM / HPC / Slurm / BCM 仍保持 Phase 2 暂停范围。

## 学习点

顶级 Agent 的 release evidence 不应该从 runtime request 直接进入 catalog。正确做法是把权力拆开：

- discovery 只负责发现候选。
- review 只负责评估候选。
- patch proposal 只负责生成可审查的变更意图。
- Git review 才是 catalog 变更入口。
- CI gate bundle 只消费已版本化、已审查的 catalog evidence。

这种设计让 Agent 的可观测性数据、评测证据、发布授权之间有明确边界，避免“临时调试输入”变成“发布通过依据”。

## 验证

- `mvn -q "-Dtest=AgentEvalTraceSetCatalogServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test`

## 最新技术基线说明

截至 2026-06-09，本阶段继续采用“稳定主线 + 兼容矩阵”的策略：

- 稳定主线：Spring Boot 3.5.14 / Spring AI 1.1.7 / Micrometer-OpenTelemetry / Resilience4j / deterministic eval gates。
- 兼容矩阵：Spring Boot 4.0.6、Spring AI 2.0.0-RC1、MCP 2025-11-25、OpenTelemetry semantic conventions 1.41.1。
- 引入新技术必须先进入 typed contract、test、docs、CI gate，再进入生产主线。
