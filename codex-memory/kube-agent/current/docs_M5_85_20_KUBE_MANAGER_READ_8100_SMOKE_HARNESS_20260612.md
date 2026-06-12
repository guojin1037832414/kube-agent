# M5.85-20 kube-manager READ 8100 Smoke Harness

## 本次完成

- 新增 `src/test/java/com/atlas/e2e/KubeManagerReadOnlySmokeTest.java`。
- 始终运行的契约测试锁定真实 smoke 目标为 `NodeQueryTool`，并要求它保持 `GET`、`READ`、不需要 HITL、路径模板 `/api/{orgId}/node`。
- 新增源码级守卫，防止 smoke harness 被扩展为 POST/PUT/PATCH/DELETE、MCP runtime、HITL 或 NIM/HPC/Slurm/BCM 二期 Tool 联调。
- 新增 opt-in 真实 8100 smoke：只有显式设置 `atlas.kube-manager.smoke.enabled=true` 或 `ATLAS_KUBE_MANAGER_SMOKE_ENABLED=true`，并提供当前用户 token/orgId 后才会访问 kube-manager。
- README、路线图、变更日志、项目记忆和架构学习地图已同步运行方式与边界。

## 学习要点

- 顶级 Agent 需要“可重复的真实出口验证”，但不能让普通测试依赖本地外部服务。
- READ smoke 验证的是 `Tool -> KubeManagerHttpClient -> kube-manager` 的 token/orgId/query/path 传播，不是写入放行。
- 测试里故意传入伪造 `organizationId/orgId/token` 参数，用来证明 Tool 仍使用 `UserPermissionContext` 的服务端可信上下文。

## 验证

- `Test-NetConnection localhost -Port 8100` 返回 `TcpTestSucceeded=False`，当前环境未执行真实外部 smoke。
- `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest,KubeManagerHttpClientUrlContractTest,KubeManagerHttpClientTracePropagationTest,VirtualMachineReadToolHttpContractTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 安全边界

- 本切片只新增测试入口，不打开生产运行时权力。
- 默认测试不会访问 kube-manager `8100`。
- 真实 smoke 必须显式开启，并提供当前用户 token/orgId。
- smoke 固定为 GET/READ/no-HITL 的节点查询，不调用写方法、MCP runtime、HITL、audit/memory 写入、retrieval/vector runtime、A2A handoff 或二期 NIM/HPC/Slurm/BCM 能力。

## 下一步

- 当 kube-manager `8100` 启动并拿到当前用户 token/orgId 后，运行 opt-in smoke 命令完成真实链路确认。
- 若真实 smoke 暴露路径、认证、orgId 或 query 参数问题，再以小切片修复对应 READ Tool / HTTP outlet。
