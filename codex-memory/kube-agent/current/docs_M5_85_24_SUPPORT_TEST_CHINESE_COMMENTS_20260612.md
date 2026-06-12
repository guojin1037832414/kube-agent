# M5.85-24 Support 测试中文注释切片

## 目标

继续 Batch 5 支撑层中文教学注释收尾，把 config/store/intent 相关测试也维护成学习文档。测试不仅要证明行为，还要说明输入来自哪里、输出保护什么契约，以及这些测试不会触发哪些真实 Agent 能力。

## 修改范围

- `src/test/java/com/atlas/config/TokenPropagatingTaskDecoratorTest.java`
- `src/test/java/com/atlas/store/ConversationStoreTest.java`
- `src/test/java/com/atlas/intent/core/IntentArbiterTest.java`
- `src/test/java/com/atlas/intent/EmbeddingMatcherMockTest.java`
- `src/test/java/com/atlas/intent/rule/RuleMatcherTest.java`
- `src/test/java/com/atlas/support/Batch5SupportTestChineseCommentContractTest.java`

## 学习要点

- 异步任务必须传播服务端可信 token/orgId 快照，并在执行结束后恢复旧 ThreadLocal，避免跨用户或跨租户污染。
- `conversationId` 只能定位会话资源，详情、改名、删除仍必须按当前可信 owner 过滤；它不是授权凭证、prompt、trace 或长期记忆。
- `IntentArbiter` 的 confidence、crossBoost、matchedLevel、priority 只影响候选路由排序，不能成为 Tool 授权、HITL、audit、release 或 kube-manager 写权限。
- `EmbeddingMatcher` 预计算失败、空缓存或空 query 应 fail-soft 返回 null，让 L2/L3/L4 继续工作；Embedding 相似度不是安全门禁。
- `RuleMatcher` 的关键词、正则和模糊分数只产生 L2/L4 路由证据，不能创建 HITL marker、决定 orgId/token/userId 或批准写操作。

## 验证

```powershell
mvn -q "-Dtest=Batch5SupportTestChineseCommentContractTest,TokenPropagatingTaskDecoratorTest,ConversationStoreTest,IntentArbiterTest,EmbeddingMatcherMockTest" test
mvn -q "-Dtest=RuleMatcherTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过。`git diff --check` 仅有 Windows LF-to-CRLF 提示。`RuleMatcherTest` 会启动 Spring 测试上下文，并在本地 embedding 模型缺失时走配置降级，耗时较长但验证通过。

## 安全边界

本切片只补测试中文教学注释和源码契约测试，不改变生产行为。没有打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续扫描剩余 support/test helper、学习文档示例和高频联调用例，补齐真正涉及身份、租户、Tool、HITL、audit、memory 或 kube-manager 边界的中文教学注释；或在 kube-manager 8100 可用并具备当前用户 token/orgId 后运行 opt-in READ smoke。
