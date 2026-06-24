# M5.85-33 Reviewed trace fixture manifest

## 目标

为 Phase 1 Eval trace evidence 增加一个 admin-only / read-only / manifest-only 的 reviewed redacted trace fixture manifest。它承接上一层 fixture intake 合同，但不直接把 traceId 写入 `eval-trace-sets.json`，也不创建占位 fixture；它只扫描已经随 Git 提交、位于 classpath 约定目录的 fixture JSON 文件，并报告 trace set 覆盖缺口。

## 已交付

- 新增 `AgentReviewedTraceFixtureManifestService`。
  - 扫描 `classpath*:observability/reviewed-trace-fixtures/*.json`。
  - 使用 repo/classpath 文件作为唯一输入来源。
  - 与 `AgentEvalTraceSetCatalogService.catalog()` 返回的 trace-set catalog 做覆盖比对。
- 新增 `AgentReviewedTraceFixtureManifestResponse`。
  - schema：`agent-reviewed-trace-fixture-manifest.v1`。
  - 输出 fixture rows、trace set coverage、required fixture fields、forbidden shortcuts、next actions、endpoint map、safety proof 和 privacy proof。
- 新增 endpoint：`GET /api/agent/observability/eval/reviewed-trace-fixture-manifest`。
  - Controller 方法级 `@PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")`。
  - 继续复用 `requireAdmin()` 作为方法内保护。
- 新增和更新测试。
  - `AgentReviewedTraceFixtureManifestServiceTest`
  - `Batch4ReviewedTraceFixtureManifestChineseCommentContractTest`
  - `ObservabilityControllerTest`
  - `ObservabilityControllerSecurityContractTest`
  - `AgentSecurityConfigWebMvcTest`

## 当前 manifest 状态

当前仓库没有放入 authoritative reviewed fixture 文件，因此端点应报告：

- `manifestStatus=NO_REVIEWED_FIXTURE_FILES_FOUND`
- `traceSetCount=7`
- `fixtureFileCount=0`
- `matchedFixtureTraceSetCount=0`
- `missingFixtureTraceSetCount=7`

服务测试用内存 `Resource` 模拟一个已提交 fixture，验证 `phase1-core-golden` 会显示为 `REVIEWED_FIXTURE_PRESENT_AWAITING_CATALOG_PATCH`，同时仍不会把 traceId 写回 catalog。

## 安全边界

- admin-only / read-only / manifest-only / classpath-scan-only。
- 不上传 fixture。
- 不接收 caller traceId。
- 不接收请求体。
- 不修改 `eval-trace-sets.json`。
- 不进行 runtime catalog write。
- 不运行 eval/replay。
- 不调用 Tool、MCP、LLM、RAG、kube-manager 或外部网络。
- 不创建 HITL marker。
- 不写 audit/memory。
- 不打开 CI blocking 或 release authority。
- 不触碰 Phase 2 NIM/HPC/Slurm/BCM 权力。
- 同步把旧恢复指南里的明文测试账号密码样例改为 `<redacted-password>`，真实 smoke 凭证只能来自当前进程环境变量或 system property。

## 验证

- `mvn -q "-Dtest=AgentReviewedTraceFixtureManifestServiceTest,Batch4ReviewedTraceFixtureManifestChineseCommentContractTest,ObservabilityControllerSecurityContractTest,ObservabilityControllerTest,AgentSecurityConfigWebMvcTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 下一步

下一步可以准备真实 reviewed redacted fixture 文件，但必须经过人审和 Git review；随后再做 catalog patch review。即使 fixture 文件齐全，也只能进入人工 catalog patch 流程，不能在运行时写 catalog，也不能顺手打开 CI blocking 或 release authority。
