# M5.85-21 Tool Core Adapter 中文注释切片

## 目标

继续 Batch 5 支撑层中文教学注释收尾，聚焦 Tool core adapter 层。该层连接 LLM Tool JSON、Spring AI ToolCallback、Tool schema、默认值切面、ToolContext、ToolResult、Graph/ReAct 和 SafeToolExecutor，虽然大多是“胶水代码”，但很容易被误解成权限或执行边界。

## 修改范围

- `src/main/java/com/atlas/tool/core/AtlasTool.java`
- `src/main/java/com/atlas/tool/core/AtlasToolCallback.java`
- `src/main/java/com/atlas/tool/core/ToolInputSchemaBuilder.java`
- `src/main/java/com/atlas/tool/core/AtlasToolResultConverter.java`
- `src/main/java/com/atlas/tool/core/AtlasToolResult.java`
- `src/main/java/com/atlas/tool/core/AtlasToolContext.java`
- `src/main/java/com/atlas/tool/core/DefaultValueAspect.java`
- `src/test/java/com/atlas/support/Batch5CoreAdapterChineseCommentContractTest.java`

## 学习要点

- `AtlasTool` 只是最小 Tool 接口，不是执行授权、HITL、audit、release 或 kube-manager token/orgId 来源。
- legacy `AtlasToolCallback` 的输入来自 LLM JSON，必须视为不可信候选业务参数；它只能委托 `SafeToolExecutor`，不能直接执行 `BaseTool`。
- `ToolInputSchemaBuilder` 生成的 inputSchema 只是给 LLM 的字段提示，不能替代权限、HITL、审计、受保护字段过滤或业务校验。
- `AtlasToolContext` 只服务早期 Spring AI ToolContext 兼容，不允许从前端、LLM、PlanStep 或 MCP 参数中恢复身份与租户权限。
- `DefaultValueAspect` 只补普通业务表单草稿字段，不能生成、覆盖或信任 token/orgId/userId/sessionId/HITL/audit/release/writeAllowed。
- `AtlasToolResult.success=true` 只说明当前 Tool 返回成功结构，不能反向证明 HITL、audit prewrite、release gate 或后续写授权。
- `AtlasToolResultConverter` 当前只透传默认转换；未来若加脱敏/截断，也必须保持 redacted-only，不能把失败改成成功。

## 验证

```powershell
mvn -q "-Dtest=Batch5CoreAdapterChineseCommentContractTest,Batch5ChineseCommentContractTest,Batch5SupportContractBehaviorTest,ToolInputSchemaBuilderTest,AtlasToolCallbackSafeExecutorTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过，`git diff --check` 只有 Windows LF-to-CRLF 提示。

## 安全边界

本切片只补中文教学注释和源码契约测试，不改变运行时行为。`SafeToolExecutor` 仍是唯一真实 Tool 执行边界。没有打开 MCP `tools/call`、kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续 Batch 5 剩余 support/config/test-helper 扫描，或在 kube-manager 8100 启动并提供当前用户 token/orgId 后运行 opt-in READ smoke。
