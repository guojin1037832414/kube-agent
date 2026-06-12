# M5.85-22 Intent Routing 中文注释切片

## 目标

继续 Batch 5 支撑层中文教学注释收尾，聚焦意图路由链路：自然语言如何被整理成候选 intent，以及为什么候选 intent 不能成为执行授权。

## 修改范围

- `src/main/java/com/atlas/intent/IntentRouter.java`
- `src/main/java/com/atlas/intent/core/IntentArbiter.java`
- `src/main/java/com/atlas/intent/rule/RuleMatcher.java`
- `src/main/java/com/atlas/intent/EmbeddingMatcher.java`
- `src/main/java/com/atlas/intent/config/IntentsLoader.java`
- `src/main/java/com/atlas/intent/llm/L3IntentClassifier.java`
- `src/test/java/com/atlas/support/Batch5IntentRoutingChineseCommentContractTest.java`

## 学习要点

- `IntentRouter` 是候选意图收集器，不是 Tool 执行器。
- L1 Embedding 高置信短路是性能优化，不是执行许可。
- L2 关键词/正则命中只说明用户表达与某个 intent 定义相符，不能创建 HITL marker、决定 orgId/token/userId 或直接拼接 kube-manager 请求。
- L3 LLM 分类是语义兜底增强，必须经过强类型解析、置信度阈值、unknown 和 intent 白名单，不能动态注册能力。
- L4 fuzzy 是澄清/兜底候选，不能猜测高风险写动作。
- `IntentArbiter` 的 crossBoost 表达多层一致性，只能提高路由置信度，不能成为 ToolPermission、audit prewrite、release evidence 或 kube-manager API 白名单。
- `IntentsLoader` 加载的是路由目录，不是 MCP manifest、ToolRegistry 或 Phase 2 运行时能力开关。

## 验证

```powershell
mvn -q "-Dtest=Batch5IntentRoutingChineseCommentContractTest,Batch5ChineseCommentContractTest,IntentArbiterTest,RuleMatcherTest,EmbeddingMatcherMockTest" test
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过。`RuleMatcherTest` 会启动 Spring 上下文，并在本地 embedding 模型缺失时走配置降级，耗时较长但验证通过。

## 安全边界

本切片只补中文教学注释和源码契约测试，不改变路由算法或运行时执行路径。没有打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续扫描 Batch 5 剩余 support/config/test-helper，或者在 kube-manager 8100 可用并具备当前用户 token/orgId 后运行 opt-in READ smoke。
