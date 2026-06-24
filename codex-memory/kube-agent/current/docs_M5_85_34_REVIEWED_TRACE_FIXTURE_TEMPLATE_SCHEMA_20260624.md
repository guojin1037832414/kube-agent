# M5.85-34 Reviewed trace fixture template/schema

## 目标

以功能推进为主，把 reviewed trace fixture 从 “manifest 能看到缺口” 推进到 “人审者和前端知道真实 fixture 应该怎么写”。本切片提供 template/schema 只读端点和仓库目录 README，但不创建真实 fixture JSON、不提交 fake traceId、不写 `eval-trace-sets.json`。

## 已交付

- 新增 `AgentReviewedTraceFixtureTemplateService`。
  - 只读取 `AgentEvalTraceSetCatalogService.catalog()`。
  - 生成作者模板，不接收请求体，不接受 caller traceId，不访问外部系统。
- 新增 `AgentReviewedTraceFixtureTemplateResponse`。
  - schema：`agent-reviewed-trace-fixture-template.v1`。
  - 输出 JSON Schema、example fixture skeleton、structured proof blocks、traceSetTemplates、file naming rules、authoring workflow、forbidden shortcuts、endpoint map、safety proof 和 privacy proof。
- 新增 endpoint：`GET /api/agent/observability/eval/reviewed-trace-fixture-template`。
  - Controller 方法级 `@PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")`。
  - 继续复用 `requireAdmin()`。
- 新增 `src/main/resources/observability/reviewed-trace-fixtures/README.md`。
  - 该目录只允许未来提交真实人工 reviewed fixture JSON。
  - 明确禁止模板、占位 JSON、fake traceId、raw audit、token/password 或 CI/release 权力声明。
- 更新现有 endpoint map。
  - fixture intake contract 指向 template 和 manifest。
  - fixture manifest 指向 template。
- 新增和更新测试。
  - `AgentReviewedTraceFixtureTemplateServiceTest`
  - `Batch4ReviewedTraceFixtureTemplateChineseCommentContractTest`
  - `ObservabilityControllerTest`
  - `ObservabilityControllerSecurityContractTest`
  - `AgentSecurityConfigWebMvcTest`

## 当前状态

本切片只新增 README，没有新增 `.json` fixture 文件。因此：

- manifest 仍应报告当前真实 fixture 缺口。
- example skeleton 中的 `traceId` 是 `<reviewed-w3c-trace-id>` 占位符，不能作为真实 fixture 提交。
- 每个 trace set 会得到 suggested filename，例如 `phase1-core-golden.reviewed-trace-fixture.json`，但这只是人工准备指引。

## 安全边界

- admin-only / read-only / template-only / schema-only。
- 不创建 fixture 文件。
- 不提交 fake traceId。
- 不接收 runtime upload。
- 不接收 caller traceId。
- 不写 `eval-trace-sets.json`。
- 不进行 runtime catalog write。
- 不运行 eval/replay。
- 不调用 Tool、MCP、LLM、RAG、kube-manager 或外部网络。
- 不创建 HITL marker。
- 不写 audit/memory。
- 不打开 CI blocking 或 release authority。
- 不触碰 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 验证

- `mvn -q "-Dtest=AgentReviewedTraceFixtureTemplateServiceTest,Batch4ReviewedTraceFixtureTemplateChineseCommentContractTest,ObservabilityControllerSecurityContractTest,ObservabilityControllerTest,AgentSecurityConfigWebMvcTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 下一步

下一步有两条路：

- 如果已经有真实 redacted trace 和人审证据，可以准备首个 reviewed fixture JSON，并让 manifest 从 `NO_REVIEWED_FIXTURE_FILES_FOUND` 进入 partial 状态。
- 如果还没有真实 reviewed trace，则先推进 catalog patch review readiness：当 manifest 没有 ready fixture 时，把 catalog patch review 的 blocked reason 做得更直接给前端展示。
