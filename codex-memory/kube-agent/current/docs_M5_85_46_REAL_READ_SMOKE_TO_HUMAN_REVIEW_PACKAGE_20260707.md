# M5.85-46 Real READ Smoke To Human Review Package 恢复记忆

## 目标

本切片把真实 kube-manager 只读链路和 reviewed fixture 人审包主线接起来。目标不是提交真实 fixture，而是证明 opt-in 真实 READ smoke 产生的 redacted audit 可以继续进入 replay/eval/candidate discovery，并产出可人工 Git review 的候选包。

## 已完成

- 更新 `src/test/java/com/atlas/e2e/KubeManagerReadOnlySmokeTest.java`。
- `SafeToolExecutor` READ smoke 的 traceId 改为 W3C-compatible `trc_` anchor，避免 candidate discovery 丢弃真实 smoke 证据。
- 新增 `assertRealReadSmokeCanProduceReviewedFixtureHumanReviewPackage(...)`：
  - 使用真实 READ smoke 的 `InMemoryAgentAuditRecorder`。
  - 构造 `AgentReplayTimelineService`、`AgentEvalReportService`、`AgentEvalTraceSetCandidateDiscoveryService`。
  - 构造 `AgentReviewedTraceFixtureCandidateWorkbenchService` 和 `AgentReviewedTraceFixtureHumanReviewPackageService`。
  - 断言 `phase1-core-golden` 返回 `READY_FOR_HUMAN_GIT_REVIEW_PACKAGE`。
- 更新 README、路线图、项目使命与当前记忆、当前恢复状态。

## 验证

- 默认离线 smoke：`mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`。
- 登录型真实 smoke：临时进程环境变量注入测试账号，连接 `http://localhost:8100`，执行 `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`。
- 真实 smoke 结果：可信 orgId 解析为 `100002`；6 条 `SafeToolExecutor` READ 链路通过；reviewed fixture human review package 数据面断言通过。

## 安全不变量

- 默认测试仍不访问真实后端；只有 opt-in smoke 才连接 `localhost:8100`。
- 唯一允许 POST 仍是 `/api/login` 认证 bootstrap。
- 业务链路只允许已审阅的 6 条 GET/READ/no-HITL Tool。
- 测试账号密码和临时 token 只存在于进程环境/内存，没有写入源码、文档、恢复记忆或 Git。
- 人审包只读生成，不创建 fixture 文件，不上传 fixture，不写 `eval-trace-sets.json`，不把 candidate 直接提升成 reviewed fixture。
- 人审包本身不再次执行 Tool，不调用 kube-manager，不调用 MCP/LLM/RAG，不写 HITL/audit/memory。
- 不启用 CI blocking、release authority、依赖升级或 Phase 2 NIM/HPC/Slurm/BCM 能力。

## 下一步

- 若要准备首个真实 reviewed fixture，必须人工 Git review 补齐 `sourceCommitSha`、`reviewer`、`reviewTimestamp`、`evidenceDigest`，并计算最终 sha256 digest。
- 也可以切到 `vue-kube-manager` 只读渲染自动候选工作台、人审包、readiness、failedQualityGates、checklist 和 nextActions。
