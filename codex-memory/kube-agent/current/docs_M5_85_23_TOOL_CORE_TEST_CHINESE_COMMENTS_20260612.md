# M5.85-23 Tool Core 测试中文注释切片

## 目标

继续 Batch 5 支撑层中文教学注释收尾，把关键测试也作为学习文档维护。测试不仅证明行为，还要解释安全契约、输入来源、输出保护内容，以及不会触发哪些真实能力。

## 修改范围

- `src/test/java/com/atlas/tool/core/ToolRegistryPromptContractTest.java`
- `src/test/java/com/atlas/tool/core/ToolRegistryPermissionTest.java`
- `src/test/java/com/atlas/tool/core/ToolParameterNormalizerTest.java`
- `src/test/java/com/atlas/tool/core/ToolInputSchemaBuilderTest.java`
- `src/test/java/com/atlas/tool/core/ProtectedToolParameterFilterTest.java`
- `src/test/java/com/atlas/tool/core/ProtectedToolParameterFilterUsageContractTest.java`
- `src/test/java/com/atlas/tool/core/BaseToolOrganizationIdGovernanceTest.java`
- `src/test/java/com/atlas/tool/core/AtlasToolCallbackSafeExecutorTest.java`
- `src/test/java/com/atlas/support/Batch5ToolCoreTestChineseCommentContractTest.java`

## 学习要点

- Prompt 可见性不是执行授权；LLM 只能看到权限过滤和脱敏后的工具目录。
- ToolRegistry 可见性按匿名、认证用户、管理员分层，但真实调用仍要走 SafeToolExecutor。
- 参数归一化只做 alias/canonical 兼容，不生成身份、租户、HITL、audit、release 或写权限。
- inputSchema 只是 LLM 参数提示，不是权限系统。
- 受保护参数过滤器必须作为共享组件被 ReAct、Graph、SafeToolExecutor 复用。
- organizationId 是多租户安全边界，只能来自服务端可信上下文，缺失必须 fail-closed。
- legacy core callback 也必须委托 SafeToolExecutor，不能回到裸 `BaseTool.execute`。

## 验证

```powershell
mvn -q "-Dtest=Batch5ToolCoreTestChineseCommentContractTest,ToolParameterNormalizerTest,ToolInputSchemaBuilderTest,ProtectedToolParameterFilterTest,ProtectedToolParameterFilterUsageContractTest,BaseToolOrganizationIdGovernanceTest,AtlasToolCallbackSafeExecutorTest" test
mvn -q "-Dtest=ToolRegistryPermissionTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过。`ToolRegistryPermissionTest` 会启动 Spring 上下文，并在本地 embedding 模型缺失时走配置降级，耗时较长但验证通过。

## 安全边界

本切片只补测试中文教学注释和源码契约测试，不改变生产行为。没有打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续扫描 config/store/contract test helper 的教学注释缺口，或在 kube-manager 8100 可用并具备当前用户 token/orgId 后运行 opt-in READ smoke。
