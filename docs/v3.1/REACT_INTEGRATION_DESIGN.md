# ReAct 引擎与 kube-agent 集成方案设计文档

> 版本: v1.0 - 草案  
> 作者: Atlas Team  
> 日期: 2026-05-20  

---

## 1. 执行摘要

本方案旨在将 **ReAct（Reasoning + Acting）多步推理引擎** 集成到现有 kube-agent v3.1 架构中，实现诊断类复杂查询的自动化多步推理（如 Pod 故障排查：查状态 → 查日志 → 查 GPU → 综合分析），同时：

- **零侵入**：不破坏现有 L1→L2→L3→L4 单轮路由流程
- **安全优先**：危险操作必须通过 HITL 人工确认
- **体验优先**：SSE 流式推送每步执行进度，消除白屏
- **成本可控**：max_step 限制 + 历史截断 + 快速失败

---

## 2. 总体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户 / 前端                                       │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ POST /api/agent/chat/stream
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AtlasOrchestrator                                   │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  streamChat()                                                          ││
│  │   ├── SSE 认证 (SessionStore → token + orgId)                          ││
│  │   ├── 限流检查                                                          ││
│  │   └── 分流决策: shouldUseReAct(request) ★                              ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│         │                              │                                    │
│         │ NO                           │ YES                                │
│         ▼                              ▼                                    │
│  ┌──────────────┐            ┌──────────────────────┐                       │
│  │ 传统单轮流程  │            │   ReAct 引擎          │                       │
│  │ L1→L2→L3→L4 │            │   ReActEngine.java    │                       │
│  │ 执行单 Tool  │            │   (max_steps ≤ 8)    │                       │
│  └──────────────┘            └──────────────────────┘                       │
│                                              │                              │
└──────────────────────────────────────────────┼──────────────────────────────┘
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ReAct 循环内部架构                                    │
│                                                                             │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐         │
│   │  Step 1  │────▶│  Step 2  │────▶│  Step N  │────▶│  Answer  │         │
│   │  (LLM)   │     │  (Tool)  │     │  (LLM)   │     │  (LLM)   │         │
│   │ Thought1 │     │ Action1  │     │ ThoughtN │     │  Final   │         │
│   │ Action1  │     │ Result   │     │ Answer   │     │  Polish  │         │
│   └──────────┘     └──────────┘     └──────────┘     └──────────┘         │
│                                                                             │
│   每步 LLM 调用携带:                                                        │
│   • System Prompt (ReAct 指令 + 可用 Tool 列表)                             │
│   • History [UserQuery, Thought1, Action1, Result1, Thought2, ...]          │
│   • User 当前查询                                                           │
│                                                                             │
│   每步 Tool 调用携带:                                                       │
│   • ThreadLocal token + orgId 透传                                          │
│   • 权限预检 (ToolRegistry.canExecuteIntent)                                │
│   • 危险操作 HITL 拦截 (AtlasBrain.isHighRisk 复用)                         │
│                                                                             │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼ SSE 流式事件
┌─────────────────────────────────────────────────────────────────────────────┐
│                         前端事件序列（ReAct 模式）                              │
│                                                                             │
│   {type: "thinking",   step: "react_init",    content: "开始诊断..."}        │
│   {type: "thinking",   step: "llm_think",     content: "分析Pod状态..."}     │
│   {type: "tool_start", tool:  "podQuery",     content: "调用工具 podQuery"}  │
│   {type: "tool_done",  tool:  "podQuery",     content: "..."}                │
│   {type: "thinking",   step: "llm_think",     content: "分析日志..."}        │
│   {type: "tool_start", tool:  "logQuery",     content: "调用工具 logQuery"}  │
│   {type: "tool_done",  tool:  "logQuery",     content: "..."}                │
│   {type: "thinking",   step: "llm_synthesize", content: "综合分析中..."}     │
│   {type: "content",    content: "最终润色回答..."}                            │
│   {type: "done"}                                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 数据流图（详细）

```
[用户输入]
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│ AtlasOrchestrator.streamChat(ChatRequest, HttpServlet) │
│  1. 从 X-Session-Id 反查 SessionData (token + orgId)  │
│  2. 绑定 ThreadLocal: UserPermissionContext.bind()     │
│  3. 创建 SseEmitter (永不超时)                          │
│  4. shouldUseReAct(request) ★ 分流判断                 │
└─────────────────────────────────────────────────────────┘
         │
    ┌────▼────┬─────────────┐
    │         │             │
    │ NO      │             │ YES
    │         │             │
┌───▼───┐    │       ┌─────▼────────────────────────────┐
│ 传统   │    │       │ ReActEngine.execute()             │
│ 单轮   │    │       │                                   │
│ 流程   │    │       │  ┌─────────────────────────┐     │
└───────┘    │       │  │ ReActPromptBuilder      │     │
             │       │  │ 构建 ReAct System Prompt │     │
             │       │  └────────────┬────────────┘     │
             │       │               │                  │
             │       │  ┌────────────▼────────────┐     │
             │       │  │ LLM 第 1 轮调用          │     │
             │       │  │ chatClient.call(prompt) │     │
             │       │  └────────────┬────────────┘     │
             │       │               │ parse Thought/Action│
             │       │  ┌────────────▼────────────┐     │
             │       │  │ ActionExecutor          │     │
             │       │  │  权限预检 + HITL 拦截    │     │
             │       │  │  ThreadLocal 透传        │     │
             │       │  │  ToolRegistry.execute()  │     │
             │       │  │  → 返回 Observation      │     │
             │       │  └────────────┬────────────┘     │
             │       │               │                  │
             │       │  ┌────────────▼────────────┐     │
             │       │  │ ReActMemory.append()    │     │
             │       │  │ Thought/Action/Obs    │     │
             │       │  │ 追加到 ReActHistory      │     │
             │       │  └────────────┬────────────┘     │
             │       │               │ loop (max_steps) │
             │       │               │                  │
             │       │  ┌────────────▼────────────┐     │
             │       │  │ [循环结束条件]           │     │
             │       │  │ action=FINAL_ANSWER      │     │
             │       │  │   OR steps >= max_steps  │     │
             │       │  │   OR error               │     │
             │       │  └────────────┬────────────┘     │
             │       │               │                  │
             │       │  ┌────────────▼────────────┐     │
             │       │  │ ResultPolisher          │     │
             │       │  │  ToolResultPolishing    │     │
             │       │  │  .polishSync()/Stream()  │     │
             │       │  └────────────┬────────────┘     │
             │       │               │                  │
             │       └───────────────┼──────────────────┘
             │                       │
             │            ┌──────────▼──────────┐
             │            │ SSE 流式输出         │
             │            │ emit(thinking)      │
             │            │ emit(tool_start)    │
             │            │ emit(tool_done)     │
             │            │ emit(content)       │
             │            │ emit(done)          │
             │            └─────────────────────┘
             │                       │
             └───────────────────────┘
                                   ▼
                            [用户收到响应]
```

---

## 4. 关键集成点决策分析

### 4.1 入口分流：`shouldUseReAct()` 放在哪里？

**推荐方案：放在 AtlasBrain.decide() 中，新增 `DELEGATE_REACT` ActionType**

```java
// BrainDecision.ActionType 新增
public enum ActionType {
    CALL_TOOL,
    DELEGATE_AGENT,
    DELEGATE_REACT,        // ★ 新增
    DIRECT_ANSWER,
    ASK_CLARIFY,
    HITL_CONFIRM
}
```

**理由：**

| 维度 | AtlasBrain.decide() 中 | AtlasOrchestrator.chat() 中 |
|------|----------------------|----------------------------|
| 职责边界 | ✅ Brain 负责"如何回答"的决策，ReAct 是一种回答策略 | ❌ Orchestrator 只负责编排，不应包含业务决策 |
| 复用性 | ✅ AtlasGraph / SupervisorGraph 同样依赖 BrainDecision，可复用 | ❌ chat() 方法硬编码分流逻辑，不可复用 |
| 可维护性 | ✅ 决策集中一处，新增策略只需改 Brain | ❌ 分流逻辑散落在 Orchestrator |
| 测试性 | ✅ Brain 是独立 Bean，可单独单元测试 | ❌ Orchestrator 依赖大量 Stub 才能测试 |
| 演进性 | ✅ 未来可引入多 Agent 路由（DELEGATE_MULTI_AGENT 等） | ❌ 扩展性差 |

**具体实现：**

```java
public AtlasBrain {
    public BrainDecision decide(ExecutionContext ctx) {
        String visibleTools = toolRegistry.buildSystemPromptForCurrentUser();
        String systemPrompt = buildSystemPrompt(ctx, visibleTools);
        
        BrainDecision decision = parser.parse(chatClient, ctx.userQuery(), BrainDecision.class, systemPrompt);
        
        // ★ 新增：如果决策是 CALL_TOOL 但意图属于诊断类，升级至 DELEGATE_REACT
        if (decision.actionType() == ActionType.CALL_TOOL && isDiagnosticIntent(decision.target())) {
            return decision.withActionType(ActionType.DELEGATE_REACT);
        }
        
        validateDecision(decision, ctx);
        return decision;
    }
    
    private boolean isDiagnosticIntent(String toolName) {
        // 诊断类意图白名单：涉及 Pod/Node/GPU/日志/事件等多源关联查询
        Set<String> diagnosticIntents = Set.of(
            "podDiagnose",       // Pod 故障诊断
            "nodeDiagnose",      // 节点诊断
            "gpuDiagnose",       // GPU 故障排查
            "resourceAnomaly",   // 资源异常分析
            "trainingDebug"      // 训练任务调试
        );
        return diagnosticIntents.contains(toolName);
    }
}
```

**AtlasOrchestrator 中的响应：**

```java
// AtlasOrchestrator.streamChat() 中
if (supervisorGraph != null) {
    runSupervisorGraph(request, emitter, ...);
    return;
}

// ★ 新增：传统 IntentRouter 之后，检查是否需要 ReAct
IntentResult result = intentRouter.route(request.userQuery());

// 如果意图命中诊断类，触发 ReAct
if (isReActEligible(result)) {
    emit(emitter, "thinking", Map.of("step", "react_init", "content", "开始多步诊断..."));
    
    new ReActEngine(chatModel, toolRegistry, polishingService, streamingEmitter)
        .execute(request.userQuery(), result, emitter, finalToken, finalOrgId);
    return;
}
```

---

### 4.2 历史管理：ReAct 每轮需要把 Thought/Action/Observation 追加到 LLM 历史

**推荐方案：新建 `ReActMemory` 类，独立于现有 SessionStore**

```java
package com.atlas.react;

/**
 * ReAct 会话内存 — 管理 Think/Action/Observation 三元组
 * 生命周期：一次 ReAct 执行（单请求内），不跨请求持久化
 */
public class ReActMemory {
    
    private final List<ReActStep> steps = new ArrayList<>();
    private final String userQuery;
    private final int maxHistoryLength;  // 默认 4000 tokens 估算
    
    public record ReActStep(
        int stepNumber,
        String thought,           // LLM 推理
        String action,            // 工具名
        Map<String, Object> actionInput, // 工具参数
        String observation        // 工具返回
    ) {}
    
    public void append(String thought, String action, Map<String, Object> input, String observation) {
        steps.add(new ReActStep(steps.size() + 1, thought, action, input, observation));
    }
    
    /**
     * 构建 LLM Prompt 中的历史上下文
     * 自动截断：保留最近 N 步（默认全部保留，超出 maxHistoryLength 时做智能摘要）
     */
    public String buildHistoryContext() {
        if (steps.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder();
        for (ReActStep step : steps) {
            sb.append("Thought: ").append(step.thought()).append("\n");
            sb.append("Action: ").append(step.action())
              .append("(").append(formatParams(step.actionInput())).append(")\n");
            sb.append("Observation: ").append(truncateObservation(step.observation())).append("\n\n");
        }
        return sb.toString();
    }
    
    /**
     * Observation 截断控制：单条结果不超过 2000 字符
     */
    private String truncateObservation(String obs) {
        if (obs == null || obs.length() <= 2000) return obs;
        return obs.substring(0, 2000) + "\n... (结果截断，共" + obs.length() + "字符)";
    }
    
    /**
     * 获取当前步数
     */
    public int currentStep() { return steps.size(); }
}
```

**理由：**

| 维度 | 新建 ReActMemory | 复用现有 SessionStore |
|------|----------------|---------------------|
| 数据结构 | ✅ 专为 `Thought/Action/Observation` 设计，语义清晰 | ❌ SessionData 存储 token/username/orgId，不适合存储推理链路 |
| 生命周期 | ✅ 单次请求内，执行完毕即 GC | ❌ SessionStore TTL 30min，ReAct 中间态不需要持久化 |
| 内存效率 | ✅ 不共享，无并发竞争 | ❌ Caffeine 全局缓存，写入需同步 |
| 历史截断 | ✅ 内置 Observation 截断、智能摘要 | ❌ 需额外逻辑 |
| 跨请求 | ✅ 明确不跨请求，避免上下文污染 | ❌ 持久化可能导致上下文泄漏 |

---

### 4.3 工具执行：复用现有的 ToolRegistry 还是新建 ReactToolCatalog？

**推荐方案：复用现有的 `ToolRegistry`，通过 `executeTool()` 适配器模式调用**

```java
package com.atlas.react;

/**
 * ReAct 工具执行器 — 复用 ToolRegistry，增加 ReAct 专属逻辑
 */
@Component
public class ReActToolExecutor {
    
    private final ToolRegistry toolRegistry;
    private final UserPermissionContext permissionContext;
    
    /**
     * 执行 ReAct Action，返回 Observation
     */
    public String execute(String actionName, Map<String, Object> params, String token, String orgId) {
        // 1. 权限预检（复用现有逻辑）
        if (!toolRegistry.canExecuteIntent(actionName)) {
            throw new PermissionDeniedException("ReAct 无权调用: " + actionName);
        }
        
        // 2. HITL 拦截：危险操作前置确认
        if (isDangerousAction(actionName, params)) {
            throw new ReActHitlRequiredException(actionName, params, "ReAct 检测到危险操作");
        }
        
        // 3. ThreadLocal 透传（必须在 execute 之前设置）
        UserPermissionContext.bind(token, orgId);
        try {
            // 4. 查找并执行工具
            BaseTool tool = toolRegistry.findByIntentId(actionName)
                .orElseThrow(() -> new IllegalArgumentException("未知工具: " + actionName));
            
            // 补充用户上下文参数
            params.put("userId", extractUserId(token));
            params.put("organizationId", orgId);
            
            Map<String, Object> result = tool.execute(params);
            
            // 5. 格式化 Observation（JSON 摘要）
            return formatObservation(result);
            
        } finally {
            UserPermissionContext.clear();  // 必须清理 ThreadLocal
        }
    }
    
    private boolean isDangerousAction(String action, Map<String, Object> params) {
        Set<String> dangerousTools = Set.of("podDelete", "nodeDrain", "resourceScaleDown", "rbacRevoke");
        if (!dangerousTools.contains(action)) return false;
        
        // 对于 ReAct 自动执行，禁止所有危险操作
        // 如需开放，可降级至 HITL_CONFIRM 而非完全禁止
        return true;
    }
}
```

**理由：**

| 维度 | 复用 ToolRegistry | 新建 ReactToolCatalog |
|------|------------------|----------------------|
| 代码复用 | ✅ 109 个工具零改动 | ❌ 需要重新封装所有工具 |
| 权限系统 | ✅ 自动继承 P1.4 权限感知 | ❌ 需要重新实现权限 |
| 注解扫描 | ✅ 自动扫描 AtlasToolMapping | ❌ 需要重新索引 |
| 演进同步 | ✅ ToolRegistry 新增工具，ReAct 自动可用 | ❌ 双份维护 |
| 风险 | ✅ 统一管控点 | ❌ 可能存在权限差异 |

---

### 4.4 结果润色：ReAct 最终结果要不要走 ToolResultPolishingService？

**推荐方案：ReAct 最终 Answer 走独立的 `ReActResultSynthesizer`，仅在最后一步润色**

```java
package com.atlas.react;

/**
 * ReAct 结果综合润色器
 * 
 * 策略：
 * 1. 如果 ReAct 已经生成了自然语言的 Final Answer → 直接返回（ReAct 本身就是一种润色）
 * 2. 如果 Final Answer 是结构化数据 → 调用 ToolResultPolishingService 润色
 * 3. 如果 ReAct 中途失败 → 返回已收集的 Observation 摘要 + fallback 提示
 */
@Component
public class ReActResultSynthesizer {
    
    private final ToolResultPolishingService polishingService;
    
    public String synthesize(ReActMemory memory, String rawAnswer, String userQuery) {
        // 情况 1: ReAct 已经输出自然语言（通常如此）
        if (isNaturalLanguage(rawAnswer)) {
            // 可选：追加一个轻量级 "formatting" prompt 统一风格
            return lightFormat(rawAnswer, memory);
        }
        
        // 情况 2: ReAct 输出的是结构化数据（不常见，但可以发生）
        if (looksLikeJson(rawAnswer)) {
            Map<String, Object> pseudoResult = Map.of(
                "success", true,
                "message", "ReAct 多步诊断完成",
                "data", rawAnswer,
                "steps", memory.currentStep()
            );
            return polishingService.polishSync(pseudoResult, userQuery);
        }
        
        // 情况 3: ReAct 达到 max_steps 但未完成
        return generatePartialSummary(memory, userQuery);
    }
    
    private String lightFormat(String rawAnswer, ReActMemory memory) {
        return "🔍 **诊断过程**（共 " + memory.currentStep() + " 步）\n\n" + rawAnswer;
    }
    
    private String generatePartialSummary(ReActMemory memory, String userQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 诊断进行中，已执行 ").append(memory.currentStep()).append(" 步:\n\n");
        for (ReActStep step : memory.getSteps()) {
            sb.append("**步骤 ").append(step.stepNumber()).append("**: ")
              .append(step.thought()).append("\n");
        }
        sb.append("\n建议提供更具体的查询以获取完整诊断。");
        return sb.toString();
    }
}
```

**理由：**

| 维度 | ReAct 专用 Synthesizer + 可选 Polish | 全部走 ToolResultPolishingService |
|------|-----------------------------------|---------------------------------|
| 语义匹配 | ✅ ReAct 输出已是自然语言，无需重复润色 | ❌ polish 会让 LLM 做无意义二次转换 |
| Token 成本 | ✅ ReAct 已完成大部分推理，仅需轻量格式化 | ❌ 额外消耗 token，性价比低 |
| 一致性 | ✅ 保持 ReAct 风格统一 | ❌ polish 模板可能破坏 ReAct 的推理链展示 |
| 失败兜底 | ✅ 支持部分结果摘要 | ❌ polish 要求完整 Map 输入，不灵活 |

---

### 4.5 会话持久：Caffeine SessionStore 重启清空，ReAct 中间态是否需要持久化？

**推荐方案：ReAct 中间态不持久化，单次请求内完成，超时/失败即终止**

**理由：**

| 维度 | 不持久化 | 持久化到 Redis/DB |
|------|---------|------------------|
| 实现复杂度 | ✅ 零额外依赖 | ❌ 需引入分布式缓存 |
| 故障恢复 | ✅ ReAct 从用户角度看只是"一次查询"，失败可重试 | ❌ 恢复后 LLM 上下文已丢失，意义不大 |
| 一致性 | ✅ 单 JVM 内完成，无分布式一致性问题 | ❌ 需处理并发、竞态条件 |
| 安全 | ✅ 中间态不落地，降低数据泄漏风险 | ❌ ReAct 可能携带 Pod 日志等敏感信息 |
| max_steps | ✅ max_steps=8，超时 30s，单次请求可完成 | ❌ 跨请求持久化引入了"长时间运行任务"的复杂度 |

**但需保证：**

1. **幂等性**：ReAct 单次执行失败时，用户可重新发送相同查询，系统重新执行
2. **优雅终止**：如果 SSE 连接断开，ReAct 应立即终止（通过 `SseEmitter.onCompletion()` 回调）
3. **资源清理**：ReAct 执行完毕后清理 `ReActMemory`，防止长时间引用

```java
// AtlasOrchestrator 中绑定 emitter 生命周期
emitter.onCompletion(() -> {
    // 通知 ReActEngine 终止（如果还在运行）
    ReActEngineRegistry.cancel(sessionId);
    userConnections.merge(finalUserId, -1, Integer::sum);
});

// ReActEngine.execute() 中检查取消标志
for (int step = 0; step < maxSteps; step++) {
    if (Thread.currentThread().isInterrupted() || cancelFlag.get()) {
        log.warn("[ReAct] 会话 {} 被取消", sessionId);
        emit(emitter, "content", Map.of("content", "诊断已取消"));
        emit(emitter, "done", Map.of());
        return;
    }
    // ... 执行步骤
}
```

---

## 5. ReAct Prompt 模板设计

```
你是 Atlas K8s 集群管理助手，具备 ReAct（Reasoning + Acting）多步诊断能力。

## 你的工作方式
面对复杂诊断任务时，你需要：
1. **分析现状**（Thought）：基于已有信息分析当前状态
2. **采取行动**（Action）：调用合适的工具获取更多信息
3. **观察结果**（Observation）：获取工具返回的数据
4. **循环以上步骤**，直到收集到足够信息做出判断

## 可用工具
{visible_tools}  ← 复用 toolRegistry.buildSystemPromptForCurrentUser()

## 输出格式（严格遵循）
每一轮你必须以如下格式输出：

Thought: [你的推理分析，说明你当前掌握了什么信息、还需要什么信息]
Action: [工具名]
Action Input: {"param1": "value1", "param2": "value2"}

工具返回后，你会看到 Observation，然后继续 Thought → Action → Observation 循环。

当信息充足时，输出：
Thought: [综合分析]
Final Answer: [给用户的专业诊断结论和建议]

## 规则
1. 如果不知道某参数值，不要猜测，先调用查询工具获取
2. Observation 已截断（最多 2000 字符），关键信息通常在开头
3. max_steps = {max_steps}，请在有限步骤内得出结论
4. 危险操作（删除/扩容/变更权限）绝对禁止，如果用户要求此类操作，请礼貌拒绝
5. 使用中文回答，保持专业 K8s 运维语言风格

## 当前对话历史
{history}  ← ReActMemory.buildHistoryContext()
```

---

## 6. SSE 事件映射表

ReAct 执行期间，向前端推送的标准事件：

| 后端事件名 | 前端 type | 触发时机 | payload 字段 |
|-----------|----------|---------|-------------|
| `react_thinking` | `thinking` | 每次 LLM 生成 Thought | `{step, thought}` |
| `react_tool_start` | `tool_start` | 开始调用工具 | `{tool, params}` |
| `react_tool_done` | `tool_done` | 工具返回 Observation | `{tool, observation_preview}` |
| `react_content` | `content` | ReAct 生成 Final Answer | `{content}` |
| `react_error` | `error` | 执行异常 | `{message}` |
| `done` | `done` | 全部完成 | `{}` |

---

## 7. 风险评估与缓解措施

### 7.1 最大风险：LLM 在 ReAct 循环中调用危险操作

**风险等级：🔴 P0 - 严重**

**缓解策略（多层防御）：**

```
┌──────────────────────────────────────────────────────────┐
│ 多层安全防御体系                                           │
├──────────────────────────────────────────────────────────┤
│ Layer 1: Prompt 层                                         │
│   System Prompt 中明确禁止危险操作                          │
│   "5. 危险操作（删除/扩容/变更权限）绝对禁止"               │
├──────────────────────────────────────────────────────────┤
│ Layer 2: ToolRegistry 权限层                              │
│   canExecuteIntent() 预检 — 越权工具不可见                   │
│   ReactToolExecutor 执行前二次检查                          │
├──────────────────────────────────────────────────────────┤
│ Layer 3: ReAct 执行层                                      │
│   ReActToolExecutor.isDangerousAction() 拦截               │
│   危险工具白名单：podDelete, nodeDrain, resourceScaleDown   │
│   命中时抛出 ReActHitlRequiredException                    │
├──────────────────────────────────────────────────────────┤
│ Layer 4: HITL 确认层                                       │
│   危险操作降级为 hitl_request 事件                          │
│   用户通过 /api/hitl/confirm 确认后才执行                   │
│   confirmToken + TTL + 幂等性保证                          │
├──────────────────────────────────────────────────────────┤
│ Layer 5: 审计日志层                                        │
│   所有 ReAct 步骤记录到 AuditLog                           │
│   including: tool, params_hash, userId, timestamp          │
└──────────────────────────────────────────────────────────┘
```

---

### 7.2 性能风险：每步都要 LLM 调用，延迟 30-60 秒

**风险等级：🟡 P1 - 高**

**缓解策略：**

| 策略 | 实现 | 预期收益 |
|------|------|---------|
| **max_steps 限制** | max_steps = 6（可配置） | 最坏情况 6 次 LLM 调用 ≈ 18-30s |
| **Observation 截断** | 单条结果 ≤ 2000 字符 | 减少 LLM 输入 token，加速推理 |
| **工具并行** | 不相依的工具并行调用 | Future.allOf()，潜在 30-50% 加速 |
| **LLM 缓存** | 重复 Thought/Action 复用缓存 | 高频查询 Cache Hit 可降至 1-2s |
| **轻量模型** | 推理步骤可用轻量模型（如 Qwen-7B） | 成本降低 60%，速度提升 2x |
| **预热连接** | ChatClient 长连接池化 | 减少 TCP/SSL 握手开销 |

---

### 7.3 体验风险：用户看到白屏 30 秒

**风险等级：🟡 P1 - 高**

**缓解策略（SSE 流式推送）：**

```java
// ReAct 引擎每步都 emit 事件
private void executeStep(ReActMemory memory, SseEmitter emitter) {
    // 1. 发送 thinking 事件 —— 用户看到"正在诊断 Pod 状态..."
    emit(emitter, "thinking", Map.of(
        "step", "llm_think",
        "content", "第 " + (memory.currentStep() + 1) + " 步: 正在分析..."
    ));
    
    // 2. LLM 调用（异步，用户等待时有 throbber）
    String llmOutput = chatClient.call(buildPrompt(memory));
    
    // 3. 解析 Action
    ReActAction action = parseAction(llmOutput);
    
    // 4. 发送 tool_start —— 用户看到"正在调用 podQuery..."
    emit(emitter, "tool_start", Map.of(
        "tool", action.toolName(),
        "content", "调用工具: " + action.toolName()
    ));
    
    // 5. 执行工具
    String observation = toolExecutor.execute(action.toolName(), action.params(), token, orgId);
    
    // 6. 发送 tool_done —— 用户看到"Pod 状态: Running..."
    emit(emitter, "tool_done", Map.of(
        "tool", action.toolName(),
        "observation_preview", truncate(observation, 200)
    ));
    
    // 7. 追加到 memory
    memory.append(parseThought(llmOutput), action.toolName(), action.params(), observation);
}
```

**前端渲染建议：**

```
┌────────────────────────────────────────────┐
│ 🤖 Atlas 正在诊断...                          │
│                                              │
│ Step 1: 分析 Pod 状态                         │
│   [throbber] → PodQuery 返回: Running         │
│ Step 2: 分析日志                              │
│   [throbber] → LogQuery 返回: Error found     │
│ Step 3: 诊断 GPU                              │
│   [throbber] → GPUQuery 返回: Normal          │
│                                              │
│ 📝 正在生成诊断结论...                         │
│                                              │
│ 最终答案: 您的 Pod xxx 因内存不足被 OOMKilled  │
│ 建议: 1) 增加内存 limit  2) 检查并发任务       │
└────────────────────────────────────────────┘
```

---

### 7.4 Token 成本风险

**风险等级：🟡 P2 - 中**

**成本控制表：**

| 项目 | 估算值 | 控制策略 |
|------|--------|---------|
| max_steps | 6 步 | 配置化 max.steps=6 |
| 每步 LLM 输入 Token | ~3000（含历史 + Prompt） | Observation 截断 2000 字符 |
| 每步 LLM 输出 Token | ~200 | 限制输出格式（Thought/Action/Final） |
| 单次 ReAct 总 Token | ~19,200 input + ~1,200 output | max_steps 首限 |
| Token: 成本比（vs 单次 CALL_TOOL） | ~8x（6步）/ ~5x（3步） | 仅诊断类触发，非全部查询 |
| 断路器 | — | 日限额/用户限额超限时降级为 DIRECT_ANSWER |

**预算控制代码：**

```java
public class ReActBudgetManager {
    private final Map<String, AtomicInteger> dailyUsage = new ConcurrentHashMap<>();
    private static final int DAILY_LIMIT_PER_USER = 50;  // 每天最多 50 次 ReAct
    
    public boolean canAffordReAct(String userId) {
        return dailyUsage.computeIfAbsent(userId, k -> new AtomicInteger(0)).get() < DAILY_LIMIT_PER_USER;
    }
    
    public void recordUsage(String userId, int steps) {
        dailyUsage.get(userId).addAndGet(steps);
    }
}
```

---

## 8. 产出文件清单

### 8.1 新建文件（8 个）

| 文件 | 路径 | 职责 |
|------|------|------|
| `ReActEngine.java` | `com.atlas.react.ReActEngine` | ReAct 主引擎，管理循环逻辑 |
| `ReActMemory.java` | `com.atlas.react.ReActMemory` | Thought/Action/Observation 历史管理 |
| `ReActPromptBuilder.java` | `com.atlas.react.ReActPromptBuilder` | 构建 ReAct System Prompt |
| `ReActToolExecutor.java` | `com.atlas.react.ReActToolExecutor` | 工具执行适配器（权限 + HITL） |
| `ReActParser.java` | `com.atlas.react.ReActParser` | 解析 LLM 输出的 Thought/Action/Final |
| `ReActResultSynthesizer.java` | `com.atlas.react.ReActResultSynthesizer` | 最终结果综合润色 |
| `ReActConfig.java` | `com.atlas.react.ReActConfig` | 配置类（max_steps, timeout, budget） |
| `ReActBudgetManager.java` | `com.atlas.react.ReActBudgetManager` | Token 成本控制 |

### 8.2 修改文件（6 个）

| 文件 | 修改内容 |
|------|---------|
| `BrainDecision.java` | ActionType 枚举新增 `DELEGATE_REACT` |
| `AtlasBrain.java` | 新增 isDiagnosticIntent() 判断，升级 CALL_TOOL → DELEGATE_REACT |
| `AtlasOrchestrator.java` | streamChat() 新增 ReAct 分流逻辑；SupervisorGraph 新增 ReAct 节点 |
| `StreamingEmitter.java` | 新增 react_thinking / react_tool_start / react_tool_done 事件支持（可选，复用现有即可） |
| `application.yml` | 新增 react.max-steps, react.timeout, react.daily-limit 配置 |
| `AtlasGraphConfig.java` | Supervisor Graph 新增 `react_node` 节点（如果走 Graph 模式） |

### 8.3 可选文件（2 个）

| 文件 | 路径 | 职责 |
|------|------|------|
| `ReActController.java` | `com.atlas.controller.ReActController` | 独立的 /chat/react 实验接口（PoC 用） |
| `ReActEngineTest.java` | `src/test/java/com/atlas/react/` | 单元测试 |

---

## 9. 预估工期

| 阶段 | 任务 | 工期 | 负责人 |
|------|------|------|--------|
| **Phase 1: 基础设施** | ReActEngine, ReActMemory, ReActParser | 3 天 | 后端 |
| | ReActPromptBuilder, ReActConfig | 1 天 | 后端 |
| **Phase 2: 集成层** | AtlasBrain 分流改造 | 1 天 | 后端 |
| | AtlasOrchestrator ReAct 分流 | 1 天 | 后端 |
| | ReActToolExecutor（权限 + HITL） | 2 天 | 后端 |
| **Phase 3: 润色层** | ReActResultSynthesizer | 1 天 | 后端 |
| | 与 ToolResultPolishingService 集成 | 1 天 | 后端 |
| **Phase 4: 安全层** | 危险操作拦截 + HITL 降级 | 2 天 | 后端 |
| | 审计日志接入 | 1 天 | 后端 |
| **Phase 5: 优化层** | Observation 截断 + Token 优化 | 1 天 | 后端 |
| | 并行工具执行 | 2 天 | 后端 |
| **Phase 6: 测试** | 单元测试 + 集成测试 | 2 天 | QA |
| | 压力测试（延迟/token 消耗） | 1 天 | QA |
| **合计** | | **约 18 人天** | |

**建议：** 先实现 **Phase 1-2（7 天）** 作为 MVP，支持基础 ReAct 能力上线；后续逐步迭代安全层和优化层。

---

## 10. 附录：ReActEngine 伪代码

```java
@Component
public class ReActEngine {
    
    private final ChatClient chatClient;
    private final ReActToolExecutor toolExecutor;
    private final ReActResultSynthesizer synthesizer;
    private final ReActConfig config;
    
    public void execute(String userQuery, IntentResult intent, 
                        SseEmitter emitter, String token, String orgId) {
        
        ReActMemory memory = new ReActMemory(userQuery, config.getMaxHistoryLength());
        
        for (int step = 0; step < config.getMaxSteps(); step++) {
            // 检查取消
            if (Thread.interrupted()) return;
            
            // 1. 发送 thinking
            emit(emitter, "thinking", Map.of("step", "llm_think", "content", "第" + (step+1) + "步推理中..."));
            
            // 2. LLM 调用
            String prompt = buildPrompt(userQuery, memory, toolRegistry);
            String llmOutput = chatClient.prompt().system(prompt).user(userQuery).call().content();
            
            // 3. 解析输出
            ReActParseResult parse = ReActParser.parse(llmOutput);
            
            if (parse.isFinalAnswer()) {
                // 最终答案 — 润色后输出
                String synthesized = synthesizer.synthesize(memory, parse.finalAnswer(), userQuery);
                emit(emitter, "content", Map.of("content", synthesized));
                emit(emitter, "done", Map.of());
                return;
            }
            
            // 4. 执行工具
            emit(emitter, "tool_start", Map.of("tool", parse.action()));
            
            try {
                String observation = toolExecutor.execute(
                    parse.action(), parse.actionInput(), token, orgId);
                
                emit(emitter, "tool_done", Map.of("tool", parse.action(), 
                    "preview", truncate(observation, 200)));
                
                memory.append(parse.thought(), parse.action(), parse.actionInput(), observation);
                
            } catch (ReActHitlRequiredException e) {
                // 危险操作降级为 HITL
                emit(emitter, "hitl_request", Map.of(
                    "target", parse.action(),
                    "reasoning", "ReAct 检测到危险操作: " + parse.action()
                ));
                return;
                
            } catch (Exception e) {
                emit(emitter, "error", Map.of("message", "工具执行失败: " + e.getMessage()));
                memory.append(parse.thought(), parse.action(), parse.actionInput(), 
                    "Error: " + e.getMessage());
            }
        }
        
        // max_steps 耗尽 — 返回部分结果
        String partial = synthesizer.generatePartialSummary(memory, userQuery);
        emit(emitter, "content", Map.of("content", partial));
        emit(emitter, "done", Map.of());
    }
}
```

---

## 11. 总结

本方案通过将 ReAct 引擎作为 **现有 AtlasBrain 决策体系的一种新 ActionType（DELEGATE_REACT）** 引入，实现了：

1. **零破坏**：L1→L2→L3→L4 单轮流程完全保留
2. **高安全**：5 层防御（Prompt → 权限 → 执行拦截 → HITL → 审计）
3. **好体验**：SSE 流式推送每步进度，消除白屏
4. **可控制**：max_steps + Observation 截断 + Token 预算，成本可控
5. **可渐进**：MVP 仅需 7 天即可上线基础能力

**下一步建议**：
1. 优先实现 `ReActEngine` + `AtlasBrain` 分流（Phase 1-2）
2. 选择 1-2 个诊断类意图（如 `podDiagnose`）作为 PoC 验证
3. 在沙箱环境跑通后逐步放开更多诊断场景
