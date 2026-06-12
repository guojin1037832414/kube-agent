# M5.85-26 Eval Trace Evidence / Curation 中文注释切片

## 目标

推进 Phase 1 的 Eval trace evidence 方向，把 trace set catalog、candidate discovery、curation review 和 catalog patch proposal 这条 release-gate 证据链补成中文教学代码。

## 修改范围

- `src/main/java/com/atlas/observability/AgentEvalTraceSetCatalogService.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetDefinition.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetCandidateDiscoveryService.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetCandidate.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetCandidateDiscoveryResponse.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetCurationReviewArtifact.java`
- `src/main/java/com/atlas/observability/AgentEvalTraceSetCatalogPatchProposalArtifact.java`
- `src/test/java/com/atlas/observability/Batch4EvalTraceCurationChineseCommentContractTest.java`

## 学习要点

- Eval trace set catalog 描述“需要哪些 reviewed redacted trace evidence”，不是 release authority。
- candidate discovery 只从 redacted audit read model 推荐候选 traceId，不读取 raw audit，不写 catalog，不运行 eval。
- curation review 是 review-only artifact，候选 traceId 通过 deterministic gate 之后仍然需要人工 Git review。
- catalog patch proposal 只是 RFC6902 JSON Patch 建议，不执行文件写入，不打开 CI blocking。
- traceIds 是 replay evidence anchor，不是用户身份、租户、Tool 参数、HITL token、audit receipt、kube-manager path 或 retrieval runtime 开关。

## 验证

```powershell
mvn -q "-Dtest=Batch4EvalTraceCurationChineseCommentContractTest,AgentEvalTraceSetCatalogServiceTest,AgentEvalTraceSetCandidateDiscoveryServiceTest,AgentMemoryRagReviewedTraceEvidenceManifestServiceTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过。`git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

本切片只补中文注释和源码契约测试，不改变生产行为。没有打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续 Eval trace evidence：可以补真实 reviewed redacted fixtures，或者先做更安全的 fixture intake / catalog patch review 工作流；若 kube-manager 8100 启动并具备当前用户 token/orgId，也可以运行 opt-in READ smoke。
