# M5.82 Top-tier Technology Introduction Playbook

## 目标

M5.82 新增一个“顶级 Agent 最新技术引入手册”端点，用来回答用户最新要求：

> 引入全部最先进的技术，然后完成最新修订的终极目标。

这个端点不是升级按钮，也不是运行时开关。它把所有先进技术统一放进一条可教学、可审计、可恢复的工程路径：

```text
official source
  -> compatibility matrix
  -> evidence readiness
  -> compatibility branch
  -> focused regression tests
  -> Vue read-only workbench
  -> multi-expert release review
  -> runtime binding slice
```

新增接口：

```text
GET /api/agent/observability/top-tier/technology-introduction-playbook
```

## 交付内容

- 新增 `AgentTopTierTechnologyIntroductionPlaybookResponse`。
- 新增 `AgentTopTierTechnologyIntroductionPlaybookService`。
- 新增 admin-only Controller 方法 `topTierTechnologyIntroductionPlaybook()`。
- 新增 `AgentTopTierTechnologyIntroductionPlaybookServiceTest`。
- 将 Playbook 接入：
  - advanced technology adoption contract
  - official version/protocol watch
  - official version/protocol watch dashboard
  - official version/protocol watch Vue binding spec
  - advanced technology compatibility matrix
  - advanced technology compatibility matrix Vue binding spec
  - advanced technology compatibility matrix evidence readiness
  - backend technology modernization decision
  - top-tier readiness overview
  - Phase 1 execution roadmap
  - Vue readiness control plane
  - top-tier Vue workbench implementation package
  - Observability controller security contract
- 将 Vue readiness dashboard count 从 `17` 扩展到 `18`。
- 将 top-tier Vue workbench implementation package 从 4 页扩展到 5 页：
  - technology introduction playbook
  - official version/protocol watch
  - advanced technology compatibility matrix
  - advanced technology evidence readiness
  - backend technology modernization decision
- 将 Vue workbench package 扩展为 5 routes、7 API clients、5 page assemblies、10 shared components、9 fixtures。

## 当前响应状态

```text
schemaVersion=agent-top-tier-technology-introduction-playbook.v1
playbookStatus=PLAYBOOK_READY_EVIDENCE_GAPS_BLOCK_RUNTIME
officialSourceCount=8
technologyLaneCount=10
playbookStageCount=8
releaseGateCount=10
expertReviewRoundCount=6
learningModuleCount=8
forbiddenShortcutCount=10
vueRouteCount=5
phase1TopTierGoalPreserved=true
javaSpringControlPlanePreserved=true
phase2NimHpcSlurmBcmPaused=true
runtimeControlAllowed=false
runtimeUpgradeAllowedNow=false
dependencyUpgradeAllowedNow=false
ciBlockingAllowedNow=false
```

这表示：一期仍然追求顶级 Agent，不降低目标；Java/Spring 继续作为 typed control plane；所有先进技术都进入视野，但当前仍因 reviewed trace、Memory/RAG fixture、compatibility branch、release gate、Vue 只读可见性等证据缺口而阻断 runtime。

## 技术引入手册

### 1. 官方源优先

Playbook 明确记录：

```text
officialSourceWinsOverConversationMemory=true
```

意思是：当对话记忆、旧文档、搜索摘要和官方源冲突时，以重新复核的官方源为准。比如 Spring AI preview lane、MCP specification、A2A latest spec、OTel GenAI semconv 这类信息都必须先进入 official watch，再进入 compatibility matrix。

### 2. 技术 lane 不是运行时开关

M5.82 继续保留 10 个 advanced technology lanes：

- Java runtime toolchains: Java 21 / Java 25。
- Spring Boot / Framework: Spring Boot 4 / Framework 7。
- Spring AI access layer: Spring AI 2.x preview lane。
- OpenAI Responses / Agents patterns。
- MCP runtime call plane。
- A2A multi-Agent provenance。
- OTel GenAI / MCP semconv adapter。
- Memory/RAG / GraphRAG / reranker / vector store。
- kube-manager control plane writes。
- supply chain / SBOM / dependency audit / CI quality gate。

每个 lane 都会返回：

- current baseline
- candidate target
- introduction mode
- evidence readiness
- backend decision
- required proofs
- related endpoints
- teaching focus
- runtime flags

这些字段用于学习和审计，不用于触发升级。

### 3. 多专家评审成为默认路径

M5.82 把多专家评审写进 read model：

- architecture-review
- security-review
- frontend-vue-review
- eval-quality-review
- memory-rag-review
- release-manager-review

这让“多专家多轮参与”不再只是聊天约定，而是后端契约的一部分。后续任何真正 runtime binding 都应该能回答：哪个专家角色审核了什么证据？

### 4. 学习模块显式化

M5.82 暴露 8 个学习模块：

- control-plane-mindset
- official-source-literacy
- compatibility-matrix-practice
- tool-authority-and-hitl
- trace-replay-eval-loop
- mcp-a2a-protocol-governance
- advanced-memory-rag
- vue-operator-learning-workbench

学习重点是：顶级 Agent 的先进性不是“版本号最新”，而是每个先进能力都能被解释、测试、审计、回放、恢复、发布。

## 禁止捷径

M5.82 明确禁止：

- treat-latest-as-safe
- replace-java-spring-control-plane
- upgrade-pom-from-ui
- open-mcp-tools-call-before-consent
- run-a2a-handoff-before-provenance
- enable-rag-before-reviewed-fixtures
- use-otel-development-fields-as-primary-contract
- enable-ci-blocking-on-empty-catalogs
- open-kube-manager-write-before-release-gate
- reopen-phase2-domain-plugins

这些都是“看起来先进、实际不稳”的捷径。顶级 Agent 要做的是把它们变成证据链，而不是按钮。

## 安全边界

M5.82 是：

- admin-only
- read-only
- playbook-only
- source-read-model composition only
- external-call-free at request time

它只组合：

- `officialVersionProtocolWatchService.watch()`
- `compatibilityMatrixService.matrix()`
- `evidenceReadinessService.readiness()`
- `backendTechnologyModernizationDecisionService.decision()`

它不做：

- 不修改 `pom.xml`
- 不升级 Java / Spring Boot / Spring AI
- 不创建 compatibility branch
- 不调用 kube-manager 或 `8100`
- 不运行 LLM
- 不执行 Tool
- 不调用 `SafeToolExecutor`
- 不调用 HITL
- 不运行 eval suite / trace-set gate / curation review / candidate discovery
- 不写 audit / memory / durable receipt
- 不执行 retrieval / vector store / embedding / reranker / GraphRAG
- 不开启 MCP runtime `tools/call`
- 不运行 A2A runtime handoff
- 不开启 CI blocking
- 不触碰 NIM / HPC / Slurm / BCM 二期范围

## 验证命令

```powershell
mvn -q "-DskipTests" validate
mvn -q "-Dtest=AgentTopTierTechnologyIntroductionPlaybookServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentAdvancedTechnologyCompatibilityMatrixServiceTest,AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecServiceTest,AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessServiceTest,AgentBackendTechnologyModernizationDecisionServiceTest,AgentTopTierVueWorkbenchImplementationPackageServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
git diff --check
```

## 下一步

- 将 `vue-kube-manager` 接入 5 页 latest-technology workbench。
- 补真实 reviewed redacted eval trace evidence。
- 补 Memory/RAG 三类 reviewed fixtures。
- 在当前主线持续绿色后，再分别建立 Java 21/25、Spring Boot 4、Spring AI 2 compatibility branches。
- MCP/A2A/RAG 原型继续排在 SafeToolExecutor、release gate、reviewed evidence、Vue 可见性和 recovery memory 之后。
- NIM / HPC / Slurm / BCM 继续作为二期暂停。
