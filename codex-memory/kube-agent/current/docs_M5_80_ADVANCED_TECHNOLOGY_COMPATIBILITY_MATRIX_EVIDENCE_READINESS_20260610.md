# M5.80 Advanced Technology Compatibility Matrix Evidence Readiness

## 目标

M5.80 新增一个只读证据就绪层，把 M5.77 的先进技术兼容矩阵从“有哪些候选技术”推进到“每条候选技术进入主线或运行时之前还缺哪些证据”。

新增接口：

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness
```

这个接口服务一期顶级 Agent 目标：一期仍然追求 top-tier Agent Core；NIM / HPC / Slurm / BCM 只是二期暂停，不降低一期对安全、可观测、评测、前端工作台、文档教学和恢复记忆的要求。

## 交付内容

- 新增 `AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse`。
- 新增 `AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService`。
- 新增 admin-only Controller 方法 `advancedTechnologyCompatibilityMatrixEvidenceReadiness()`。
- 新增 `AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessServiceTest`。
- 更新 Controller、安全合同、WebMvc 安全测试。
- 更新 advanced technology adoption、official watch、compatibility matrix、Vue binding spec、top-tier readiness overview、Phase 1 roadmap、Vue readiness control plane、top-tier Vue workbench implementation package 的 endpoint map / workflow / API binding。
- 将 M5.79 Vue workbench package 扩展为三页：
  - official technology watch
  - advanced technology compatibility matrix
  - advanced technology evidence readiness

## 当前响应状态

当前 catalog 中还没有真实 reviewed trace anchors，所以 M5.80 正确返回 blocked 状态：

```text
schemaVersion=agent-advanced-technology-compatibility-matrix-evidence-readiness.v1
readinessStatus=EVIDENCE_READINESS_BLOCKED_BY_REVIEWED_TRACE_GAPS
matrixItemCount=10
evidenceRowCount=10
blockedEvidenceRowCount=10
reviewedTraceSetCount=0
reviewedTraceAnchorCount=0
memoryRagRequiredTraceSetCount=3
memoryRagReviewedTraceSetCount=0
runtimeControlAllowed=false
runtimeUpgradeAllowedNow=false
dependencyUpgradeAllowedNow=false
ciBlockingAllowedNow=false
catalogMutationAllowed=false
```

这不是失败，而是顶级 Agent 的健康状态：没有真实 reviewed evidence 时，不允许 runtime、dependency、retrieval、MCP tools/call、A2A、CI blocking、kube-manager write 任何一项被提前打开。

## 证据模型

M5.80 组合三个只读事实源：

```text
AgentAdvancedTechnologyCompatibilityMatrixService.matrix()
        |
        v
AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService.readiness()
        ^
        |
AgentReviewedEvalTraceEvidenceService.evidence()
        ^
        |
AgentMemoryRagReviewedTraceEvidenceManifestService.manifest()
```

每条 matrix lane 都映射到统一证据：

- official source review date and URL
- reviewed redacted eval trace evidence
- Memory/RAG reviewed trace fixtures, when relevant
- focused compatibility and security tests
- Vue read-only visibility
- workspace recovery memory
- human Git review
- release gate evidence, when runtime influence is involved

## 十条先进技术 Lane

M5.80 覆盖 M5.77 的全部 10 条 lane：

- `java-runtime-toolchains`
- `spring-boot-framework`
- `spring-ai-access-layer`
- `openai-responses-agents`
- `mcp-runtime-call-plane`
- `a2a-multi-agent-provenance`
- `otel-genai-mcp-semconv`
- `memory-rag-graphrag-reranker-vectorstore`
- `kubernetes-manager-control-plane`
- `supply-chain-ci-quality`

其中高风险 lane 的额外证据要求：

- MCP runtime call plane: explicit consent UI、SafeToolExecutor binding proof、tenant tool policy、durable audit prewrite proof、release-gate eval evidence。
- Memory/RAG/GraphRAG/reranker/vector store: reviewed Memory/RAG trace fixtures、citation/source digest、tenant privacy negative retrieval proof、durable memory lifecycle proof。
- kube-manager control plane: idempotency contract、operation safety allowlist、readback contract、release-gate receipt proof。
- supply chain / CI quality: SBOM review、dependency diff review、real reviewed trace evidence before CI blocking。

## 教学重点

### 1. 最新技术不能直接等于运行时权限

“使用最新技术”在顶级 Agent 项目里应先变成 evidence lane：

```text
official source -> compatibility matrix -> evidence readiness -> reviewed tests -> release gate -> runtime binding
```

不能跳成：

```text
latest label -> pom upgrade -> runtime button
```

### 2. 前端工作台也是治理边界

M5.80 让 `vue-kube-manager` 能显示每条先进技术为什么还不能启用。前端应该渲染 evidence gaps、blocking gates、disabled runtime actions，而不是发明 enable 按钮。

### 3. Reviewed trace 是从 contract 走向 release gate 的桥

当前 eval / Memory-RAG / release gate 都已经有合同，但真实 reviewed redacted trace anchors 仍为 0。M5.80 的作用就是把这个缺口集中暴露出来，避免后续误以为“有合同”就等于“可以 release blocking”。

### 4. OTel GenAI / MCP / A2A 等新协议要 adapter-first

OTel GenAI / MCP semantic conventions、MCP `tools/call`、A2A handoff 都是先进方向，但在一期主线中必须 adapter / provenance / contract first，不替代本地 `atlas.agent.*` 稳定字段、SafeToolExecutor 权限边界、Spring Security 身份、审计和 release gate。

## 安全边界

M5.80 是：

- admin-only
- read-only
- evidence-readiness-only
- source read-model composition only
- no caller trace IDs accepted
- no runtime control
- no catalog mutation
- no CI blocking

M5.80 不做：

- 不修改 `pom.xml`
- 不升级 Java / Spring Boot / Spring AI
- 不调用 kube-manager 或 `8100`
- 不运行 LLM
- 不执行 Tool
- 不调用 `SafeToolExecutor`
- 不调用 HITL
- 不运行 eval suite / trace-set gate / curation review / candidate discovery
- 不查询 raw audit / JSONL recent events
- 不写 audit / memory / durable receipt
- 不执行 retrieval / vector store / embedding / reranker / GraphRAG
- 不开启 MCP runtime `tools/call`
- 不运行 A2A handoff
- 不触碰 NIM / HPC / Slurm / BCM 二期范围

## 验证命令

本切片验证：

```powershell
mvn -q "-DskipTests" validate
mvn -q "-Dtest=AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessServiceTest,AgentAdvancedTechnologyCompatibilityMatrixServiceTest,AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecServiceTest,AgentTopTierVueWorkbenchImplementationPackageServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentOfficialVersionProtocolWatchServiceTest,AgentOfficialVersionProtocolWatchDashboardServiceTest,AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

提交前仍需运行：

```powershell
git diff --check
```

## 下一步

- 将 `vue-kube-manager` 绑定到 M5.79/M5.80 的三页 workbench package。
- 继续采集真实 reviewed redacted eval traces。
- 补 Memory/RAG 三类 reviewed trace fixtures。
- 等 reviewed evidence 完成后，再单独推进 release-blocking eval gate promotion。
- 继续保持 MCP runtime、A2A handoff、retrieval runtime、CI blocking、kube-manager writes、NIM/HPC/Slurm/BCM 二期范围关闭。
