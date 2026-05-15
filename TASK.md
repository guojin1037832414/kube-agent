# AtlasBrain 编码任务

## 你的身份
你是 Claude Code，负责为 kube-agent 项目编码实现 AtlasBrain 认知决策引擎模块。

## 项目信息
- 工作目录: /home/guojin/kube-agent
- git分支: master (clean state)
- 编译: mvn clean package -DskipTests
- 技术栈: Spring Boot 3.4.4 + Spring AI 1.1.6 + spring-ai-alibaba 1.1.2.2 + Java 17

## 当前架构问题
AtlasGraphConfig.java 中 Supervisor 是 ReactAgent，绑了33个工具，导致：
- _AGENT_MODEL_ 循环50+次，耗时8-10秒
- 最终所有请求 fallback 到 direct_answer
- 原因：ReactAgent ReAct 模式不适合纯分类任务

## 解决方案：AtlasBrain（手写认知编排器）

### 核心设计
AtlasBrain 不是 ReactAgent，而是一个 Spring @Component，内部用 Java while 循环做认知决策：
```
用户输入 → AtlasBrain.cogitate()
  → while (轮次 < MAX=5):
      1. 构建 Cognitive Prompt（系统指令 + 可用工具列表 + 历史 + 当前状态）
      2. 调用 ChatClient.call() 获取结构化 JSON
      3. StructuredOutputParser 解析为 BrainDecision
      4. ActionDispatcher 执行决策
      5. 如果完成 → break；否则观察结果 → 下一轮
  → 返回 ExecutionResult
```

### 文件清单（6个新文件 + 2个修改）

**新建：**
1. `src/main/java/com/atlas/brain/BrainDecision.java` - record: actionType, target, parameters, reasoning, confidence, requiredContext
   - ActionType 枚举: CALL_TOOL, DELEGATE_AGENT, DIRECT_ANSWER, ASK_CLARIFY, HITL_CONFIRM
2. `src/main/java/com/atlas/brain/AtlasBrain.java` - @Component, 核心认知循环
3. `src/main/java/com/atlas/brain/ActionDispatcher.java` - @Component, 决策执行路由
4. `src/main/java/com/atlas/brain/StructuredOutputParser.java` - JSON 解析容错（sanitize + retry 3次 + fallback）
5. `src/main/java/com/atlas/brain/AtlasMessage.java` - 轻量级消息 record
6. `src/main/java/com/atlas/brain/ExecutionResult.java` - 执行结果 record

**修改：**
7. `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java`:
   - 删除 supervisorAgent ReactAgent bean（53-84行）
   - 保留其他 6 个 ReactAgent Worker 不变
   - 把 "supervisor" 节点从 ReactAgent() 改为 AtlasBrain 节点（需要写 NodeAction wrapper）
   - 条件边 supervisor_result 解析：读取 BrainDecision.target 作为 routing key
   
8. `src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java`:
   - /chat/graph 接口适配 AtlasBrain 输出
   - HITL 暂停 → SSE event:hitl
   - ASK_CLARIFY → SSE 反问事件

### 关键约束
- AtlasBrain 只看工具元数据（名称+描述）做决策，不调用工具
- Worker Agent（query/deploy/diag/rbac/storage/network）保持 ReactAgent 不变
- Brain 循环用 ChatClient.call()（非 stream），因为需要结构化输出
- 所有代码必须有详细中文注释
- 不动 src/main/java/com/atlas/tool/ 和 src/main/java/com/atlas/intent/
- 新类放 com.atlas.brain 包

### 执行步骤
1. 读 AtlasGraphConfig.java（完整）理解当前结构
2. 读 AtlasOrchestrator.java（完整，特别是 /chat/graph 方法）
3. 读 AtlasToolCallbackFactory.java（了解工具分组）
4. 读 ToolRegistry.java（了解 Tool 查询接口）
5. 创建 6 个新文件
6. 修改 AtlasGraphConfig.java（删除 supervisor ReactAgent，接入 AtlasBrain）
7. 修改 AtlasOrchestrator.java
8. mvn clean package -DskipTests
9. 编译失败则修复，最多修3轮
10. 最终确认 BUILD SUCCESS

### 设计参考（不直接复制）
/src/main/java/com/atlas/brain/ 目录下之前调研生成过草稿文件（已被清理）。
核心概念参考它们但请重写：BrainDecision, StructuredOutputParser, ExecutionResult
