# M5.77 Advanced Technology Compatibility Matrix

## 背景

用户的终极目标是一期就构建顶级 Agent Core，并把项目作为 Agent 开发学习工程。因此“使用最新技术、最新框架”不能等价为直接升级 `pom.xml`。顶级 Agent 的升级方式应该是：

```text
官方来源复核
  -> 候选技术线
  -> 兼容性矩阵
  -> 安全/隐私/评测/恢复门禁
  -> Vue 只读证据面板
  -> Git reviewed release decision
  -> separate runtime/dependency slice
```

M5.77 新增后端权威兼容矩阵，把 Java、Spring Boot、Spring AI、OpenAI Agents/Responses、MCP、A2A、OTel GenAI、Memory/RAG/GraphRAG/reranker/vector store、kube-manager 写路径、供应链/CI 等最新技术路线纳入可审计契约。

## 本次交付

新增只读接口：

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix
```

新增后端契约：

- `AgentAdvancedTechnologyCompatibilityMatrixResponse`
- `AgentAdvancedTechnologyCompatibilityMatrixService`
- Controller 方法 `advancedTechnologyCompatibilityMatrix()`

该接口只组合 M5.74/M5.75/M5.76 官方源 Watch：

```text
AgentOfficialVersionProtocolWatchService.watch()
        |
        v
AgentAdvancedTechnologyCompatibilityMatrixService.matrix()
```

M5.77 不升级依赖、不修改 `pom.xml`、不运行外部兼容性测试、不调用 LLM、不执行 Tool、不访问 kube-manager、不打开 MCP/A2A/RAG/CI 运行时权限。

## 当前契约状态

```text
schemaVersion=agent-advanced-technology-compatibility-matrix.v1
matrixStatus=MATRIX_DEFINED_NOT_EXECUTED
sourceReviewDate=2026-06-10
sourceBaselineCount=8
matrixItemCount=10
migrationGateCount=8
blockedShortcutCount=7
testLaneCount=8
runtimeUpgradeAllowedNow=false
dependencyUpgradeAllowedNow=false
runtimeControlAllowed=false
```

## Matrix Items

M5.77 定义 10 条高级技术兼容矩阵项：

- `java-runtime-toolchains`: Java 17 mainline，Java 21 / Java 25 compatibility lanes。
- `spring-boot-framework`: Spring Boot 3.5.x mainline，Spring Boot 4 / Framework 7 compatibility lane。
- `spring-ai-access-layer`: Spring AI 1.1.7 stable，Spring AI 2.0.0-RC1 preview lane。
- `openai-responses-agents`: Responses / Agents SDK / tools / handoffs / guardrails / tracing / evals。
- `mcp-runtime-call-plane`: MCP manifest/governance 到 future tools/list / tools/call。
- `a2a-multi-agent-provenance`: A2A Agent Card / task / artifact / streaming。
- `otel-genai-mcp-semconv`: stable `atlas.agent.*` telemetry 到 OTel GenAI/MCP semantic adapter。
- `memory-rag-graphrag-reranker-vectorstore`: Memory/RAG contracts 到 future GraphRAG / reranker / vector store runtime。
- `kubernetes-manager-control-plane`: kube-manager read/governance 到 future write release gate。
- `supply-chain-ci-quality`: SBOM / dependency audit / CI blocking / compatibility matrix automation。

每个矩阵项都保持：

```text
mainlineAllowedNow=false
runtimeControlAllowed=false
```

## Migration Gates

M5.77 定义 8 个迁移门：

- `official-source-rechecked`
- `compatibility-branch-created`
- `build-and-focused-tests-green`
- `security-boundary-regression-green`
- `privacy-redaction-regression-green`
- `vue-readonly-evidence-updated`
- `recovery-memory-updated`
- `git-reviewed-release-decision`

学习要点：顶级 Agent 的升级不是“版本号领先”，而是“每个候选技术都有证明链路”。没有这些门禁的升级只会制造不可恢复风险。

## Blocked Shortcuts

M5.77 明确禁止 7 个捷径：

- `upgrade-pom-from-readiness-page`
- `treat-rc-preview-as-mainline`
- `trust-mcp-tool-annotations`
- `delegate-authority-to-external-agent`
- `enable-retrieval-before-reviewed-traces`
- `use-otel-experimental-fields-as-contract`
- `enable-ci-blocking-with-empty-fixtures`

这些捷径都 `allowed=false`，且 `blocksTopTierClaim=true`。

## Test Lanes

M5.77 定义 8 条测试 lane：

- `current-mainline`
- `java-21-candidate`
- `java-25-candidate`
- `boot-4-candidate`
- `spring-ai-2-candidate`
- `mcp-runtime-prototype`
- `a2a-provenance-prototype`
- `memory-rag-runtime-prototype`

当前只有 `current-mainline` 是必须保持绿色的主线。其他 lane 都是 future compatibility / release-gated work，不代表本轮已经开启运行时。

## 安全边界

M5.77 是 admin-only、read-only、matrix-only、Vue-workbench-ready。

它不会：

- 改 `pom.xml`；
- 下载或升级依赖；
- 切换 Java/Spring/Spring AI 主线；
- 调用 LLM；
- 执行 Tool；
- 调用 `SafeToolExecutor`；
- 触发 HITL；
- 调用 kube-manager 或 `8100`；
- 暴露 MCP runtime `tools/call`；
- 执行 A2A runtime handoff；
- 执行 retrieval / vector search / reranker / GraphRAG；
- 写 audit / memory；
- 签发 durable receipt；
- 修改 eval catalog；
- 启用 CI blocking；
- 重开 NIM / HPC / Slurm / BCM 二期范围。

## 学习要点

顶级 Agent 的技术栈治理可以分成三层：

```text
Stable mainline:
  Java 17 / Spring Boot 3.5.x / Spring AI 1.1.x / SafeToolExecutor /
  redacted audit / replay / deterministic eval / Vue read models

Compatibility matrix:
  Java 21/25 / Spring Boot 4 / Spring AI 2 /
  MCP runtime / A2A / OTel GenAI adapter /
  GraphRAG / reranker / vector stores / CI blocking

Release-gated runtime:
  only after official source review, compatibility tests,
  security/privacy regression, reviewed traces, eval gates,
  Vue evidence, recovery memory, and Git review
```

这就是“学习最新 Agent 技术”的正确工程形态：先知道技术是什么，再知道它为什么危险，再知道怎样证明它能安全进入系统。

## 验证

已通过：

```bash
mvn -q "-Dtest=AgentAdvancedTechnologyCompatibilityMatrixServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

