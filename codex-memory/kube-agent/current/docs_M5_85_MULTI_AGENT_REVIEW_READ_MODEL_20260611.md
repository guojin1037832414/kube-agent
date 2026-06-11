# M5.85 Multi-Agent Review Read Model

## 目标

M5.85 给 Phase 1 顶级 Agent 增加一个后端拥有的 Multi-Agent / Expert Review 聚合读模型。

这个切片解决的是“如何让前端和学习文档看到多专家多轮审阅证据”的问题，而不是打开真正的 A2A runtime handoff。顶级 Agent 需要多 Agent 思维，但第一步必须是可审计、可测试、可恢复的只读证据层。

## Endpoint

```text
GET /api/agent/observability/top-tier/multi-agent-review
```

返回 schema:

```text
agent-multi-agent-review.v1
```

Controller 方法:

```text
ObservabilityController.multiAgentReview()
```

## 后端实现

新增文件:

- `AgentMultiAgentReviewService`
- `AgentMultiAgentReviewResponse`
- `AgentMultiAgentReviewServiceTest`

更新文件:

- `ObservabilityController`
- `ObservabilityControllerSecurityContractTest`
- `ObservabilityControllerTest`

聚合的数据源:

- `AgentTopTierTechnologyIntroductionPlaybookService`
- `AgentPhase1ExecutionRoadmapService`
- `AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService`
- `AgentOfficialVersionProtocolWatchDashboardService`
- `AgentBackendTechnologyModernizationDecisionService`

## 当前响应内容

M5.85 暴露以下核心证据:

- 6 个专家审阅轮次: architecture, security, frontend, eval, memory/rag, release.
- 8 个 Phase 1 roadmap orchestration rows.
- 5 条 A2A / handoff provenance evidence rows.
- 40 条 review gate rows.
- 25 条 blocked runtime shortcut rows.
- 30 条 disabled runtime action rows.
- 推荐实现顺序、学习笔记、blocked actions、endpoint map、review policy、safety、privacy。
- 嵌入源读模型: playbook、phase1 roadmap、compatibility evidence、official watch dashboard、backend decision。

## 安全边界

M5.85 是 admin-only、GET-only、read-only、aggregate-read-model-only。

它明确保持以下能力关闭:

- runtime mutation
- runtime control
- dependency upgrade
- compatibility branch creation
- Tool execution
- SafeToolExecutor invocation
- HITL invocation
- kube-manager calls
- MCP tools/call
- A2A runtime handoff
- LLM calls
- external calls
- audit write
- durable receipt issuance
- memory write
- retrieval execution
- vector store calls
- embedding model calls
- reranker calls
- eval runtime execution
- CI blocking change
- Phase 2 NIM / HPC / Slurm / BCM reopening

## 学习要点

Multi-Agent 不是先把 runtime 打开。顶级 Agent 的正确学习顺序是:

1. 先把多专家角色、审阅轮次、证据来源、阻断项和安全边界做成后端读模型。
2. 再让 Vue 以只读方式展示这些证据，让学习者看到“为什么还不能执行”。
3. 然后补齐 reviewed traces、eval gates、audit/replay、source custody、artifact provenance。
4. 最后才在单独 release-gated slice 里讨论 A2A handoff、MCP tools/call、retrieval runtime 或 kube-manager writes。

一句话: Multi-Agent review is evidence before orchestration, and provenance before runtime handoff.

## 验证

本切片验证命令:

```powershell
mvn -q "-Dtest=AgentMultiAgentReviewServiceTest,ObservabilityControllerSecurityContractTest,ObservabilityControllerTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果:

- 定向测试通过。
- Maven validate 通过。
- `git diff --check` 通过，仅有 Windows LF-to-CRLF 提示。

## 下一步建议

后端继续优先推进:

- reviewed redacted eval trace evidence curation.
- Memory/RAG reviewed trace fixtures.
- release-blocking eval gates from real reviewed evidence.
- A2A provenance contract 的下一层 typed DTO，但仍不打开 runtime handoff。

前端由独立会话继续:

- 绑定 `GET /api/agent/observability/top-tier/multi-agent-review`。
- 渲染专家审阅轮次、A2A provenance rows、review gates、blocked shortcuts、disabled actions。
- 保持无执行按钮、无 upgrade 按钮、无 MCP tools/call、无 A2A handoff、无 retrieval runtime、无 kube-manager mutation。
