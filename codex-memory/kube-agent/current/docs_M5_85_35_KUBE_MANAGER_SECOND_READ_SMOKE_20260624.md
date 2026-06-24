# M5.85-35 kube-manager 第二条 READ smoke

## 交付内容

- `KubeManagerReadOnlySmokeTest` 从单条 `NodeQueryTool` 真实只读链路扩展为两条真实 GET/READ/no-HITL 链路。
- 元数据 guard 现在同时校验：
  - `NodeQueryTool -> GET /api/{orgId}/node`
  - `DashboardDeploymentCountTool -> GET /api/{orgId}/dashboard/deployment/count`
- 两个 smoke target 都必须保持 `AtlasToolMapping.OperationType.READ`、`requiresConfirmation=false`、明确 `ToolPermission.PUBLIC` 和固定 endpoint，避免未来误接写 Tool、敏感读 Tool 或二期域 Tool。
- 真实 opt-in smoke 仍默认跳过；显式开启后先通过 token/orgId pair 或 username/password 登录型路径获得可信身份，再按同一 `UserPermissionContext` 执行两条 Tool 链路。
- 两条 Tool 调用都会传入伪造 `organizationId/orgId/token` 参数，用于证明 Tool 只使用服务端 ThreadLocal 可信上下文，不相信调用方控制字段。

## 安全边界

- 唯一允许的 POST 仍是认证 bootstrap `/api/login`；业务链路只允许 GET。
- `DashboardDeploymentCountTool` 被选为第二条链路，是因为它是低风险 Dashboard 统计读取：GET、READ、无必填参数、无 HITL。
- 没有使用 `UserQueryTool`，因为用户列表虽然是 GET，但属于 `SENSITIVE_READ` 且需要确认，不适合作为低风险 smoke 扩展目标。
- 本切片不修改生产 Tool 行为，不新增 Controller，不打开 kube-manager 写入，不打开 MCP `tools/call`，不创建 HITL marker，不写 audit/memory，不执行 retrieval/vector runtime，不启用 CI blocking，不触碰 NIM/HPC/Slurm/BCM。
- 测试账号凭据只用于本地进程内真实 smoke；不得写入代码、文档、恢复记忆、Git、失败消息或日志输出。

## 验证

- `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`
- 登录型真实 smoke：通过进程环境变量注入测试账号后运行
  `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" "-Datlas.kube-manager.smoke.enabled=true" "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 下一步

- 若继续推进 kube-manager READ E2E，可再挑一个低风险 GET/READ/no-HITL Tool，逐个加入元数据 guard 和真实 smoke；推荐先评估 `NodeRemainingResourceTool` 或 `ClusterOverviewTool`，避免用户、日志、权限、订单、配额等敏感读。
- 若回到 Eval trace evidence，则继续准备真实 reviewed redacted fixture 或 catalog patch review readiness；不要创建 fake fixture，不要写 `eval-trace-sets.json`，不要启用 CI blocking。
