# M5.85-19 Supervisor Graph SSE Final Content Dedupe

## 本次完成

- `AtlasOrchestrator` 新增 `emitSupervisorDisplayContent` 和 `registerSupervisorDisplayContent`，统一处理主 `supervisorGraph` 的最终展示内容。
- direct_answer、Tool 展示、Tool fail-closed `answer`、delegate 展示、ReAct `content` 事件和 ReAct state fallback 现在共享一次 run 内的内容去重集合。
- `direct_answer` 节点现在会把 `answer` 明确推送为 SSE `content`，避免普通回答路径只有 thinking 事件。
- 新增/扩展测试：`AtlasOrchestratorJsonTest` 覆盖最终内容去重、占位过滤、不同内容保留和实际 `StreamingEmitter.send` 调用次数；`Batch3ChineseCommentContractTest` 保护 direct_answer / 去重教学 marker。

## 学习要点

- Graph State 常常会把同一份答案写入多个 key，例如 `answer`、`react_node_result`、`query_result`。
- 顶级 Agent 不只要“能执行”，还要保证前端协议稳定：用户不应看到同一份最终答案重复冒泡，也不应看到 `{}` 这类内部占位。
- `direct_answer` 是不需要 Tool 的普通回答路径，也必须显式投影为 SSE `content`，否则对话体验会像“思考完但没说话”。

## 验证

- `mvn -q "-Dtest=AtlasOrchestratorJsonTest,Batch3ChineseCommentContractTest,ToolResultMergeNodeTest,GraphToolCallSafetyGuardTest,GraphReActNodeSafetyGuardTest,GraphExecuteNodeSafetyGuardTest,SupervisorGraphReactRoutingTest" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

- 本切片只修正 SSE 展示投影和重复抑制，不打开新运行时权力。
- 去重后的自然语言内容不是 Tool 成功、HITL 确认、audit 回执、release gate 或写入完成证明。
- 没有执行 Tool、调用 LLM、访问 kube-manager、创建 HITL marker、写 audit/memory、授予 release 或写权限。
- 没有打开 MCP `tools/call`、kube-manager 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步

- 进入 kube-manager `8100` 只读 E2E smoke，验证 READ Tool 的 token/orgId/query/path/body 传播。
- 如果联调中发现 SSE 展示仍有漏发或重复，再以小切片继续收敛 Orchestrator hardening。
