# M5.85-38 真实 Agent 执行链 READ smoke 恢复说明

## 目标

把 kube-manager `8100` 只读 smoke 从直接 `tool.execute(...)` 推进到真实 Agent 执行入口，验证 `SafeToolExecutor` 和代表性 `AtlasToolCallback -> SafeToolExecutor` 链路都能在真实后端、真实 token/orgId、真实 ToolRegistry 可见性和审计事件下跑通。

## 已完成

- `KubeManagerReadOnlySmokeTest` 新增 opt-in `SafeToolExecutor` smoke。
- 同一批 6 个稳定 GET/READ/no-HITL Tool 通过 `SafeToolExecutor.executeIntent(...)` 访问真实 kube-manager：
  - `NodeQueryTool`
  - `NodeRemainingResourceTool`
  - `DashboardDeploymentCountTool`
  - `DashboardImageCountTool`
  - `DashboardEasyFlowCountTool`
  - `DashboardEasyFlowTool`
- 新增代表性 `AtlasToolCallback -> SafeToolExecutor` smoke：使用 `DashboardImageCountTool` 证明模型 ToolCallback 入口不会绕过统一安全执行边界。
- smoke 身份同时写入 `UserPermissionContext.onLogin(...)` 与 ThreadLocal token/orgId，覆盖 `AUTHENTICATED` Tool 可见性与 kube-manager HTTP 凭据传播。
- 新增断言覆盖 `SafeToolExecutionResult`、traceId、`REACT_ENGINE` / `TOOL_CALLBACK` 来源、`AgentAuditOutcome.SUCCESS`、trusted organizationId、no-HITL 和受保护参数摘要。

## 安全边界

- 仍是 opt-in 测试；默认单测不访问真实 kube-manager。
- 唯一允许的 POST 仍是 `/api/login` 认证 bootstrap。
- 业务链路仍是 GET-only、READ、no-HITL。
- 伪造 `organizationId/orgId/userId/token/writeAllowed/hitlApproved/auditReceipt/releaseDecision` 只进入审计脱敏摘要，不能覆盖服务端可信上下文。
- 未开放 kube-manager 写入、敏感读 Tool、MCP `tools/call`、HITL marker、audit/memory 写入、retrieval/vector runtime、CI blocking、release authority、依赖升级或二期 NIM/HPC/Slurm/BCM 权力。
- 测试密码和临时 token 只放在进程环境/内存，不写入源码、文档、恢复记忆或 Git。

## 已跑验证

- `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`
- 登录型真实 smoke：`http://localhost:8100` 上完成登录、可信 orgId 解析、直接 Tool、6 条 `SafeToolExecutor` READ 链路和代表性 ToolCallback 链路验证。
- `mvn -q test`
- `mvn -q "-DskipTests" package`
- `git diff --check`
- 敏感扫描只命中 `<当前密码>`、`password=xxx` 等占位符，没有真实测试密码或 token 落盘。
- 远端分支拓扑仍只有 `develop` 和 `master`。

## 下一步

1. 提交并推送 `develop`。
2. 下一批功能优先回到 Eval trace evidence / catalog patch review readiness。
3. 如果继续扩 kube-manager READ E2E，只能一条一条加入低风险 GET/READ/no-HITL Tool，并保留元数据守卫、执行来源审计和真实后端只读验证。
