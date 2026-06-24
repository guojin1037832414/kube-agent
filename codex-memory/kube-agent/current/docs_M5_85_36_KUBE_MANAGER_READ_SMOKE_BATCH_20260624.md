# M5.85-36 kube-manager READ smoke 批量扩展

## 交付内容

- `KubeManagerReadOnlySmokeTest` 从两条真实只读链路扩展为 6 条稳定 GET/READ/no-HITL 链路。
- 元数据 guard 现在覆盖：
  - `NodeQueryTool -> GET /api/{orgId}/node`
  - `NodeRemainingResourceTool -> GET /api/{orgId}/node/remaining`
  - `DashboardDeploymentCountTool -> GET /api/{orgId}/dashboard/deployment/count`
  - `DashboardImageCountTool -> GET /api/{orgId}/dashboard/image/count`
  - `DashboardEasyFlowCountTool -> GET /api/{orgId}/dashboard/easy-flow/count`
  - `DashboardEasyFlowTool -> GET /api/{orgId}/dashboard/easy-flow`
- 每个目标都必须保持 `AtlasToolMapping.OperationType.READ`、`requiresConfirmation=false`、明确 `ToolPermission` 和固定 endpoint。
- 真实 opt-in smoke 仍默认跳过；显式开启后先通过 token/orgId pair 或 username/password 登录型路径获得可信身份，再按同一 `UserPermissionContext` 执行 6 条 Tool 链路。
- 每条 Tool 调用都会传入伪造 `organizationId/orgId/token` 和探测性分页/搜索字段，用于证明 Tool 只使用服务端 ThreadLocal 可信上下文，不相信调用方控制字段。

## 安全边界

- 唯一允许的 POST 仍是认证 bootstrap `/api/login`；业务链路只允许 GET。
- `NodeRemainingResourceTool` 是 authenticated READ，不是匿名能力；它被纳入 smoke 是为了验证登录用户只读容量链路，不代表前端或 LLM 可绕过认证。
- `ClusterOverviewTool -> /api/{orgId}/dashboard/resources` 单独探测曾返回 200，但在 JUnit smoke 的 8 秒读超时内出现不稳定超时，本批暂不纳入稳定 smoke。
- 没有使用 `UserQueryTool`、日志、权限、订单、配额等敏感读 Tool；这些即使是 GET 也需要单独评审。
- 本切片不修改生产 Tool 行为，不新增 Controller，不打开 kube-manager 写入，不打开 MCP `tools/call`，不创建 HITL marker，不写 audit/memory，不执行 retrieval/vector runtime，不启用 CI blocking，不触碰 NIM/HPC/Slurm/BCM。
- 测试账号凭据只用于本地进程内真实 smoke；不得写入代码、文档、恢复记忆、Git、失败消息或日志输出。

## 验证

- `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`
- 登录型真实 smoke：通过进程环境变量注入测试账号后运行
  `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" "-Datlas.kube-manager.smoke.enabled=true" "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 下一步

- kube-manager READ smoke 这一批已经形成稳定基础覆盖；后续可再评估一个低风险 GET/READ/no-HITL Tool，但必须先做真实 timing probe，再纳入 JUnit smoke。
- Eval trace evidence 仍是另一条重要主线：继续准备真实 reviewed redacted fixture 或 catalog patch review readiness；不要创建 fake fixture，不要写 `eval-trace-sets.json`，不要启用 CI blocking。
