# M5.85-17 Graph Merge Result Display Priority

## 本次完成

- `ToolResultMergeNode` 改为按明确展示优先级选择 `final_answer`。
- 优先级为：已有 `final_answer` -> `react_node_result` -> 专业 Agent result -> `answer` -> `supervisor_result` -> 兜底错误。
- 新增 `ToolResultMergeNodeTest`，覆盖 direct_answer 保留、ReAct 结果优先、安全停止原因展示和无结果兜底。
- 扩展 `Batch3ChineseCommentContractTest`，保护 `ToolResultMergeNode` 的中文教学 marker。

## 学习要点

- `merge_result` 是展示投影节点，不执行 Tool、不调用 LLM、不访问 kube-manager。
- `final_answer` 是 SSE 展示文本，不代表 Tool 成功、HITL 已确认、audit 已落盘、release gate 已通过或写操作完成。
- 顶级 Agent 的 fail-closed 必须可解释；安全停止原因如果在 merge/SSE 最后一跳丢失，用户和开发者都无法学习“为什么没有执行”。

## 验证

- `mvn -q "-Dtest=ToolResultMergeNodeTest,Batch3ChineseCommentContractTest,GraphToolCallSafetyGuardTest,GraphReActNodeSafetyGuardTest,GraphExecuteNodeSafetyGuardTest,SupervisorGraphReactRoutingTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

- 本切片只修正展示合并，不打开新运行时权力。
- 没有执行 Tool、调用 LLM、访问 kube-manager、创建 HITL marker、写 audit/memory、授予 release 或写权限。
- 没有打开 MCP `tools/call`、kube-manager 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步

- 继续 Orchestrator hardening：delegate 子图 fail-closed 展示和 SSE 事件语义。
- 之后进入 kube-manager 8100 的只读 E2E smoke，验证 READ Tool 的 token/orgId/query/path/body 传播。
