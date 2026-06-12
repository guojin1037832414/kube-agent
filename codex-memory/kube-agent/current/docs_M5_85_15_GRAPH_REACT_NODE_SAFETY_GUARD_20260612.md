# M5.85-15 Graph ReAct Node Safety Guard

## 本次完成

- `AtlasGraphConfig.buildReActNode` 现在从 Graph State 读取服务端 `traceId`，并写入 ReAct `initialParams`。
- `react_node` 在缺少 Graph State orgId 时会尝试从 `UserPermissionContext` 恢复可信 orgId；如果仍缺失，则在 `ReActEngine.runWithEvents` 前 fail-closed。
- 新增 `failClosedGraphReActUpdates`，返回 `answer`、`react_node_result`、`tool_error_code`、`execute_result.executed=false`、source 和 traceId。
- 新增 `GraphReActNodeSafetyGuardTest`，保护 trace 注入、缺失 orgId 的调用前阻断、未执行结果不伪装成 `tool_result`。

## 学习要点

- ReAct `initialParams` 是服务端可信上下文容器，包含 token、organizationId、conversationId、userId、traceId。
- LLM 每轮 `Action.params` 只能补充业务字段，不能声明身份、租户、trace、HITL、audit、release 或写入许可。
- `traceId` 只用于日志、SSE、审计和未来 OTel 关联，不是 Tool 业务参数，也不是授权证据。
- 缺失可信 orgId 时必须在 Graph 节点入口停止，不能先调用 LLM 或 Tool 再靠下游补救。

## 验证

- `mvn -q "-Dtest=GraphReActNodeSafetyGuardTest,M523TracePropagationContractTest,ReActEngineParamMergeTest,ReActEngineMultiStepE2ETest,SupervisorGraphReactRoutingTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

- 本切片只收紧 ReAct 入口，不打开新运行时权力。
- `SafeToolExecutor` 仍是 ReAct 内真实 Tool 执行的统一边界。
- 没有打开 MCP `tools/call`、kube-manager 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking、audit/memory 写入或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步

- 继续 Orchestrator hardening：检查 `execute_node`、delegate 子图、merge_result 和 SSE 事件语义是否还需要更细的契约。
- 之后进入 kube-manager 8100 的只读 E2E smoke，优先验证 READ Tool 的 token/orgId/query/path/body 传播。
