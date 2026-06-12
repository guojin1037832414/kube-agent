# M5.85-16 Graph Execute Node Trusted Org Guard

## 本次完成

- `AtlasGraphConfig.buildExecuteNode` 在 Plan 单步 READ 候选进入 `SafeToolExecutionRequest` 前解析可信 orgId。
- orgId 解析顺序为 Graph State -> 服务端 `UserPermissionContext` ThreadLocal。
- 缺失可信 orgId 时，`execute_node` 返回 `EXECUTE_TRUSTED_ORG_MISSING` 未执行状态，不创建 request、不执行 Tool、不访问 kube-manager。
- `SafeToolExecutionRequest` 现在使用解析后的 `orgId`，不会从 `PlanStep.parameters` 读取租户上下文。
- 新增 `GraphExecuteNodeSafetyGuardTest`，锁定 orgId 恢复、request 创建前阻断和 request 构造语义。

## 学习要点

- `PlanResult.executable=true` 不是最终执行许可，只是进入候选窗口。
- `PlanStep.riskLevel=READ` 仍不等于拥有租户上下文；READ 查询也必须知道属于哪个组织。
- `PlanStep.parameters` 是不可信业务输入，只能携带业务筛选字段，不能提供 orgId、token、userId、conversationId、HITL、audit、release 或 write authority。
- `execute_node` 和 `tool_call` / `react_node` 一样，需要在 Graph 入口先留下清晰的 fail-closed 证据。

## 验证

- `mvn -q "-Dtest=GraphExecuteNodeSafetyGuardTest,M42PlanExecuteSafetyContractTest,SafeToolExecutorTest,ProtectedToolParameterFilterUsageContractTest,SupervisorGraphReactRoutingTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

- 本切片只收紧 Plan 自动执行入口，不扩大原有单步 READ-only gate。
- 真实 Tool 执行仍必须进入 `SafeToolExecutor`。
- 没有打开 MCP `tools/call`、kube-manager 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking、audit/memory 写入或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步

- 继续 Orchestrator hardening：检查 delegate 子图的 fail-closed 展示、merge_result 的结果优先级和 SSE 事件语义。
- 之后进入 kube-manager 8100 的只读 E2E smoke，验证 READ Tool 的 token/orgId/query/path/body 传播。
