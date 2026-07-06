# M5.85-47 Reviewed Fixture Human Review Gate

## 本次目标

把 M5.85-45 的 reviewed fixture human review package 继续推进为可机器校验的人工 Git review 门禁：后端只读校验人审字段和最终 sha256 摘要，为后续真实 reviewed fixture 入库前增加可回归安全闸门。

## 已完成

- 新增 `AgentReviewedTraceFixtureHumanReviewGateRequest`。
- 新增 `AgentReviewedTraceFixtureHumanReviewGateService`。
- 新增 `AgentReviewedTraceFixtureHumanReviewGateResponse`，schema 为 `agent-reviewed-trace-fixture-human-review-gate.v1`。
- 新增 `POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-gate?limit={limit}`。
- gate 会重新读取当前 human review package，并校验：
  - `selectedCandidateTraceId` 必须匹配 package 当前自动候选；
  - `sourceCommitSha` 必须是完整 40 位 Git SHA；
  - `reviewer` 不能像 token/password/secret/bearer；
  - `reviewTimestamp` 必须是 ISO-8601 Instant；
  - `candidateEvidenceDigest` 必须匹配 candidate fixture draft；
  - `evidenceDigest` 必须等于后端按固定字段重新计算的 `sha256:` 摘要。
- human review package 的 `endpointMap`、`reviewChecklist` 和 `nextActions` 已指向新 gate。
- workbench capabilities 新增 `workbench-reviewed-fixture-human-review-gate`，capabilityCount 变为 20。
- overview nextActions 新增 `validate-reviewed-fixture-human-review-gate`。
- trace set row 新增 `reviewedFixtureHumanReviewGatePath` 和 workflow stage。

## 新增/修改测试

- 新增 `AgentReviewedTraceFixtureHumanReviewGateServiceTest`：
  - 成功门禁返回 `READY_FOR_MANUAL_GIT_FIXTURE_COMMIT`；
  - package 未就绪时 fail-closed；
  - 错配/恶意 trace 输入不回显敏感值；
  - 未知 trace set 返回空 Optional。
- 更新：
  - `AgentReviewedTraceFixtureHumanReviewPackageServiceTest`
  - `AgentEvalWorkbenchCapabilitiesServiceTest`
  - `AgentEvalWorkbenchOverviewServiceTest`
  - `ObservabilityControllerTest`
  - `ObservabilityControllerSecurityContractTest`
  - `AgentSecurityConfigWebMvcTest`

## 验证

- 已通过聚焦测试：

```powershell
mvn -q "-Dtest=AgentReviewedTraceFixtureHumanReviewGateServiceTest,AgentReviewedTraceFixtureHumanReviewPackageServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

- 已通过全量测试：`mvn -q test`
- 已通过打包校验：`mvn -q "-DskipTests" package`
- 已通过 `git diff --check`，仅有 Windows LF/CRLF 提示。
- 已完成敏感字面量扫描：针对真实测试密码字面量执行 `rg`，源码、文档和恢复记忆无命中；真实密码未落盘。

## 安全不变量

- `readyForFixtureCommit=true` 只代表人工 Git 提交可以继续，不代表运行时可写。
- `runtimeFixtureCommitAllowed=false`。
- `createsFixtureFile=false`。
- `fixtureUploadAccepted=false`。
- `runtimeCatalogWrite=false`。
- `qualityGateStatusGrantedNow=false`。
- `ciBlockingEnabled=false`。
- `releaseAuthority=false`。
- 请求体不会成为新的 trace evidence。
- 不创建 `src/main/resources/observability/reviewed-trace-fixtures/*.json`。
- 不写 `eval-trace-sets.json`。
- 不执行 Tool/MCP/LLM/RAG/kube-manager。
- 不写 HITL/audit/memory。
- 不开放 Phase 2 NIM/HPC/Slurm/BCM 运行时权力。

## 下一步

1. 若要准备首个真实 reviewed fixture：先由人工 Git review 使用 gate 返回的 `expectedEvidenceDigest` 补齐最终字段，再提交真实 JSON 文件，随后重跑 reviewed fixture manifest 和 catalog patch review。
2. 或切到 `vue-kube-manager` 只读渲染 auto candidate workbench、human review package、human review gate、readiness、checklist、nextActions、failedQualityGates；前端仍不能触发 runtime action。
