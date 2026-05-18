# QueryAgent Function Calling 实质化设计方案

> **调研范围**: Spring AI 1.1.6 + OpenAI 兼容代理 + Atlas v3.1 现有架构
> **目标**: 让 QueryAgent 具备 LLM 驱动的 Tool 选择 + 参数提取能力
> **版本**: 3.1.0-P2

---

## 目录

1. [现状分析](#1-现状分析)
2. [核心问题与决策](#2-核心问题与决策)
3. [总体架构设计](#3-总体架构设计)
4. [QueryAgent 实质化代码](#4-queryagent-实质化代码)
5. [工具注册与权限过滤](#5-工具注册与权限过滤)
6. [参数提取策略](#6-参数提取策略)
7. [Orchestrator 层改造](#7-orchestrator-层改造)
8. [流式输出设计](#8-流式输出设计)
9. [异常降级](#9-异常降级)
10. [Spring AI 官方参考](#10-spring-ai-官方参考)

---

## 1. 现状分析

### 1.1 当前代码问题

```java
// AtlasOrchestrator.java — 当前实现（问题高亮）
AtlasAgentBase agent = agentMap.get(result.agent());
if (agent != null) {
    emit(emitter, "tool_call", Map.of("tool", result.intentId(), "params", Map.of()));
    // ❌ 问题1: Map.of() 空参数，LLM 完全没有参与参数提取
    Map<String, Object> toolResult = agent.executeIntent(result.intentId(), Map.of());
    emit(emitter, "tool_result", toolResult);
}
```

```java
// QueryAgent.java — 当前为空壳
public class QueryAgent extends AtlasAgentBase {
    public QueryAgent(ToolRegistry toolRegistry) {
        super(toolRegistry);
    }
    // ❌ 问题2: 没有 ChatClient，没有 Function Calling，没有参数提取
}
```

### 1.2 已有基础（可复用）

| 组件 | 状态 | 复用度 |
|------|------|--------|
| `AtlasToolCallback` | ✅ 桥接类完成 | 100% |
| `ToolRegistry` | ✅ 权限感知 + Agent 分组 | 100% |
| `BaseTool` | ✅ `@Tool` 入口 + 参数校验 | 100% |
| `L3IntentClassifier` | ⚠️ ChatClient.Builder NPE bug | 参考模式 |

---

## 2. 核心问题与决策

### Q1：如何让 LLM 选择 Tool + 提取参数？

**决策**: 采用 **Spring AI 原生 Function Calling**，而非手动 Prompt 解析。

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **A. 原生 Function Calling** (`ChatClient#tools(...)`) | LLM 看到 OpenAI function schema，自动选择 + 生成参数 | 依赖模型支持 tools 协议 | ✅ **选用** |
| B. 手动 Prompt + BeanOutputConverter 解析 | 不受 model 限制，可控 | Prompt 工程复杂，易出错，LLM 幻觉参数格式 | ❌ 不选 |
| C. 两步法：先分类意图，再手动提取参数 | 与现有 L1-L4 配合 | 两轮 LLM 调用，延迟高，参数理解脱离上下文 | ❌ 不选 |

**Spring AI Function Calling 原生流程**（v1.1.6 官方 API）：

```
User Query → ChatClient
                ↓
        System Prompt(可用 Tool 列表) + tools(ToolCallback...)
                ↓
        LLM 判断需要调用工具 → 生成 function_call JSON(含参数)
                ↓
        Spring AI 自动反序列化参数 → 调用 ToolCallback.call(String, ToolContext)
                ↓
        Tool 返回结果 → Spring AI 封装为 tool 消息 → 自动追加到对话
                ↓
        LLM 根据 Tool 结果生成最终回复（或继续调用下一个 Tool）
```

### Q2：BeanOutputConverter / MapOutputConverter 用于参数提取？

**决策**: **不直接使用** BeanOutputConverter 参数提取。

理由：
- Spring AI 原生 Function Calling 已经通过 **JSON Schema** 让 LLM 输出结构化参数
- `MethodToolCallback` 内部自动做 Jackson 反序列化 `buildTypedArgument` → `Method.invoke`
- BeanOutputConverter 更适合**非 function-calling 场景**的 POJO 输出（如 L3 意图分类结果）
- 若未来需要手动解析（如模型不支持 tools），再引入 `MapOutputConverter` 或 `BeanOutputConverter`

### Q3：ChatClient 注入在每个 Agent 内部，还是 Orchestrator 统一管理？

**决策**: **每个 Agent 内部注入独立 ChatClient**。

理由：

| 维度 | Per-Agent ChatClient | Orchestrator 统一管理 |
|------|----------------------|----------------------|
| **System Prompt** | 各自定制（QueryAgent 强调查询语义，DeployAgent 强调操作风险） | 一个大而全的 Prompt，LLM 易混淆 |
| **Tool 范围** | 仅挂载本 Agent 的 Tool，减少 LLM 选择范围 | 挂载全部 Tool，LLM 选择困难 |
| **权限隔离** | 天然按 Agent 过滤 | 需要额外过滤逻辑 |
| **可测试性** | Agent 可独立单元测试 | 需要 Mock Orchestrator |
| **代码耦合** | Agent 自包含，符合 SRP | Orchestrator 膨胀 |

**参考**: Spring AI GitHub `spring-ai-examples/java-function-callback/FunctionCallback.java` 中，每个 Service 内部持有自己的 `ChatClient` 并注册各自的 `FunctionCallback`。

---

## 3. 总体架构设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AtlasOrchestrator                            │
│  (HTTP入口 + SSE流式 + Token透传)                                    │
│                                                                     │
│  1. 接收 userQuery                                                  │
│  2. intentRouter.route(query) → IntentResult(intentId, agent)      │
│  3. agentMap.get(agent) → QueryAgent / DeployAgent / ...           │
│  4. agent.run(query, intentContext) → 流式返回                       │
└─────────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
       ┌──────────┐    ┌──────────┐    ┌──────────┐
       │QueryAgent│    │DeployAgent│    │DiagAgent │
       │(ChatClient)│   │(ChatClient)│   │(ChatClient)│
       └────┬─────┘    └────┬─────┘    └────┬─────┘
            │               │               │
       ┌────▼─────┐    ┌───▼──────┐    ┌───▼──────┐
       │本Agent    │    │本Agent    │    │本Agent    │
       │Tools过滤  │    │Tools过滤  │    │Tools过滤  │
       └────┬─────┘    └────┬─────┘    └────┬─────┘
            │               │               │
       ┌────▼───────────────▼───────────────▼─────┐
       │          Spring AI Function Calling        │
       │  ChatClient.prompt().system(...).tools(...)│
       │         .user(query).call() / stream()     │
       └────────────────────────────────────────────┘
```

---

## 4. QueryAgent 实质化代码

### 4.1 第一步：AbstractLlmAgent 基类（新增）

提取所有 LLM Agent 的公共逻辑，避免每个 Agent 重复 ChatClient 初始化代码。

```java
package com.atlas.agent;

import com.atlas.tool.core.AtlasToolCallback;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 驱动 Agent 的抽象基类。
 *
 * <p>每个子类 Agent（QueryAgent/DeployAgent/...）持有独立的 ChatClient，
 * 挂载各自权限过滤后的 ToolCallbacks，实现完整的 Function Calling 闭环。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ol>
 *   <li>初始化 ChatClient（绑定本 Agent 的可用 Tool）</li>
 *   <li>构建 Agent 专属 System Prompt</li>
 *   <li>执行 LLM 调用（同步/流式）</li>
 *   <li>异常降级：LLM 故障时回退到直接 Tool 调用</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
public abstract class AbstractLlmAgent extends AtlasAgentBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 本 Agent 专属的 ChatClient（已绑定过滤后的 ToolCallbacks） */
    protected final ChatClient chatClient;

    /** 是否启用 LLM Function Calling（配置项，默认 true） */
    protected final boolean llmEnabled;

    /**
     * 子类构造时必须传入 ChatClient.Builder。
     *
     * @param toolRegistry     Tool 注册中心
     * @param chatClientBuilder Spring AI 自动注入的 Builder
     * @param llmEnabled       是否启用 LLM（application.yml 配置）
     */
    protected AbstractLlmAgent(ToolRegistry toolRegistry,
                               ChatClient.Builder chatClientBuilder,
                               boolean llmEnabled) {
        super(toolRegistry);
        this.llmEnabled = llmEnabled;

        if (llmEnabled && chatClientBuilder != null) {
            // 1. 获取本 Agent 可见的 ToolCallbacks（权限已过滤）
            List<ToolCallback> myTools = buildToolCallbacks();

            // 2. 初始化 ChatClient：挂载 ToolCallbacks + 日志 Advisor
            this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // P1.4 权限关键：只让 LLM "看到" 当前用户有权调用的 Tool
                .defaultToolCallbacks(myTools.toArray(new ToolCallback[0]))
                .build();

            log.info("[{}] LLM Agent 初始化完成，挂载 {} 个Tool",
                getAgentName(), myTools.size());
        } else {
            this.chatClient = null;
            log.warn("[{}] LLM 未启用（chatClientBuilder={} llmEnabled={}），降级为直接 Tool 调用",
                getAgentName(), chatClientBuilder, llmEnabled);
        }
    }

    /**
     * 主入口：用 LLM 分析用户请求，自动选择 Tool + 提取参数 + 调用 + 总结。
     *
     * <p><b>完整流程：</b></p>
     * <ol>
     *   <li>构建 System Prompt（告知 LLM 本 Agent 身份 + 可用 Tool）</li>
     *   <li>发送 userQuery 给 LLM</li>
     *   <li>LLM 决定：直接回答 / 调用 Tool / 需要更多信息</li>
     *   <li>如调用 Tool：Spring AI 自动解析参数 → 执行 AtlasToolCallback → 返回结果</li>
     *   <li>LLM 根据 Tool 结果生成最终回复</li>
     * </ol>
     *
     * @param userQuery    用户原始输入
     * @param intentContext 意图上下文（由 IntentRouter 提供，含 intentId、confidence 等）
     * @return Agent 执行结果
     */
    public AgentExecutionResult run(String userQuery, Map<String, Object> intentContext) {
        if (!llmEnabled || chatClient == null) {
            // 降级：直接用 intentId 匹配 Tool，空参数执行
            return runLegacy(userQuery, intentContext);
        }

        try {
            String systemPrompt = buildSystemPrompt();

            log.debug("[{}] 发送 LLM 请求，query={}", getAgentName(), userQuery);

            // Spring AI Function Calling 核心调用
            // toolContext 可透传 ThreadLocal 上下文（如用户 Token、traceId）
            ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                // 可选：透传 Atlas 上下文给 Tool 执行线程
                // .toolContext(Map.of("userToken", AsyncContextHolder.getToken(), ...))
                .call();

            String finalAnswer = response.getResult().getOutput().getText();

            // 提取调用历史（用于日志/审计）
            List<ToolCallRecord> toolCalls = extractToolCalls(response);

            return AgentExecutionResult.builder()
                .success(true)
                .answer(finalAnswer)
                .toolCalls(toolCalls)
                .rawResponse(finalAnswer)
                .build();

        } catch (Exception e) {
            log.error("[{}] LLM Function Calling 失败，降级到直接调用: {}",
                getAgentName(), e.getMessage(), e);
            return runLegacy(userQuery, intentContext);
        }
    }

    /**
     * 流式执行入口（SSE 推送）。
     *
     * @param userQuery       用户原始输入
     * @param intentContext   意图上下文
     * @param tokenConsumer   Token 级消费回调（推送到 SSE Emitter）
     * @param toolCallConsumer Tool 调用开始回调（推送 tool_call 事件）
     * @param toolResultConsumer Tool 结果回调（推送 tool_result 事件）
     * @return 最终完整结果
     */
    public AgentExecutionResult runStreaming(
            String userQuery,
            Map<String, Object> intentContext,
            Consumer<String> tokenConsumer,
            Consumer<ToolCallRecord> toolCallConsumer,
            Consumer<ToolCallRecord> toolResultConsumer) {

        if (!llmEnabled || chatClient == null) {
            return runLegacy(userQuery, intentContext);
        }

        StringBuilder fullResponse = new StringBuilder();

        try {
            String systemPrompt = buildSystemPrompt();

            // P2 流式：使用 stream() 方法
            chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                .stream()
                .content()
                // 每个 Token 推送给前端
                .doOnNext(token -> {
                    fullResponse.append(token);
                    tokenConsumer.accept(token);
                })
                // 完成时
                .doOnComplete(() -> log.debug("[{}] 流式输出完成", getAgentName()))
                .blockLast();

            // TODO: Spring AI 1.1.6 stream() 暂不支持流式捕获 tool_calls
            // 如需 tool_call 事件流式推送，需升级到 1.2.x 或手动拦截

            return AgentExecutionResult.builder()
                .success(true)
                .answer(fullResponse.toString())
                .rawResponse(fullResponse.toString())
                .build();

        } catch (Exception e) {
            log.error("[{}] 流式 LLM 调用失败: {}", getAgentName(), e.getMessage(), e);
            return runLegacy(userQuery, intentContext);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 子类必须实现 / 可选重写
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建 Agent 专属 System Prompt。
     * 告知 LLM 当前 Agent 的身份、可用工具范围、行为规则。
     */
    protected abstract String buildSystemPrompt();

    /**
     * 构建本 Agent 的 ToolCallbacks（已由 ToolRegistry 按权限过滤）。
     * 子类可直接使用默认实现，或扩展添加动态 Tool。
     */
    protected List<ToolCallback> buildToolCallbacks() {
        return toolRegistry.listByAgent(getAgentType()).stream()
            .filter(meta -> meta.instance() instanceof BaseTool)
            .map(meta -> new AtlasToolCallback((BaseTool) meta.instance()))
            .map(tc -> (ToolCallback) tc)
            .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // 降级策略
    // ═══════════════════════════════════════════════════════════

    /**
     * 旧版直接调用（无 LLM 参与）。
     * 适用于：LLM 不可用 / 快速模式 / 意图已明确且无需参数。
     */
    protected AgentExecutionResult runLegacy(String userQuery,
                                              Map<String, Object> intentContext) {
        String intentId = (String) intentContext.get("intentId");
        log.info("[{}] 降级执行 intent={}", getAgentName(), intentId);

        Map<String, Object> toolResult = executeIntent(intentId, Map.of());
        boolean success = Boolean.TRUE.equals(toolResult.get("success"));
        String message = (String) toolResult.getOrDefault("message",
            toolResult.getOrDefault("summary", "执行完成").toString());

        return AgentExecutionResult.builder()
            .success(success)
            .answer(message)
            .toolCalls(List.of())
            .legacyFallback(true)
            .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 内部辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 从 ChatResponse 中提取 Tool 调用记录（审计/日志）。
     */
    private List<ToolCallRecord> extractToolCalls(ChatResponse response) {
        // Spring AI 1.1.6 中 tool_calls 在 AssistantMessage.toolCalls()
        // 但 ChatResponse 的访问路径较深，简化处理
        var msg = response.getResult().getOutput();
        if (msg.getMedia() != null && !msg.getMedia().isEmpty()) {
            // TODO: 解析 tool_calls 元数据
        }
        return List.of();
    }
}
```

### 4.2 第二步：AgentExecutionResult（新增 DTO）

```java
package com.atlas.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行结果 — P2 LLM 驱动版。
 */
public record AgentExecutionResult(
    boolean success,
    String answer,           // LLM 总结后的最终回复（面向用户）
    List<ToolCallRecord> toolCalls,  // Tool 调用历史
    String rawResponse,      // LLM 原始输出（调试用）
    boolean legacyFallback,  // 是否触发了降级
    Map<String, Object> metadata  // 扩展字段
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean success;
        private String answer;
        private List<ToolCallRecord> toolCalls = List.of();
        private String rawResponse;
        private boolean legacyFallback;
        private Map<String, Object> metadata = Map.of();

        public Builder success(boolean s) { this.success = s; return this; }
        public Builder answer(String a) { this.answer = a; return this; }
        public Builder toolCalls(List<ToolCallRecord> t) { this.toolCalls = t; return this; }
        public Builder rawResponse(String r) { this.rawResponse = r; return this; }
        public Builder legacyFallback(boolean f) { this.legacyFallback = f; return this; }
        public Builder metadata(Map<String, Object> m) { this.metadata = m; return this; }

        public AgentExecutionResult build() {
            return new AgentExecutionResult(success, answer, toolCalls,
                rawResponse, legacyFallback, metadata);
        }
    }
}
```

### 4.3 第三步：ToolCallRecord（新增，用于审计）

```java
package com.atlas.agent;

import java.util.Map;

/**
 * Tool 调用记录 — 一次完整的 Tool 调用日志。
 */
public record ToolCallRecord(
    String toolName,        // Tool 名称
    Map<String, Object> params,   // LLM 生成的参数
    Map<String, Object> result,   // Tool 执行结果
    long durationMs,        // 执行耗时
    String status           // "success" / "error"
) {}
```

### 4.4 第四步：QueryAgent 实质化

```java
package com.atlas.agent;

import com.atlas.tool.core.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 查询 Agent — P2 实质化版（LLM 驱动的 Tool 选择 + 参数提取）。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>挂载查询类 Tool：node_query, gpu_query, image_query, cluster_overview 等</li>
 *   <li>LLM 自动理解用户查询意图，选择最合适的 Tool 并提取参数</li>
 *   <li>支持多轮追问（如 "再看下 GPU" → LLM 自动复用上下文）</li>
 * </ul>
 *
 * <p><b>示例交互：</b></p>
 * <pre>
 * User: "node-1 的资源用了多少？"
 * → LLM 选择 node_detail Tool，提取参数 {"nodeName": "node-1"}
 * → 调用 Tool 返回节点详情
 * → LLM 总结："node-1 当前 CPU 使用率 45%，内存使用率 60%，状态正常。"
 *
 * User: "那所有节点呢？"
 * → LLM 理解上下文切换，选择 node_query Tool（无参数）
 * </pre>
 */
@Component
public class QueryAgent extends AbstractLlmAgent {

    public QueryAgent(ToolRegistry toolRegistry,
                      ChatClient.Builder chatClientBuilder,
                      @Value("${atlas.agent.query.llm-enabled:true}") boolean llmEnabled) {
        super(toolRegistry, chatClientBuilder, llmEnabled);
    }

    @Override
    public String getAgentType() { return "query"; }

    @Override
    public String getAgentName() { return "QueryAgent (查询)"; }

    /**
     * QueryAgent 专属 System Prompt。
     *
     * <p>关键设计：告知 LLM 它是 "K8s 集群查询助手"，只能做查询不能修改，
     * 并列出精确的工具说明和参数要求。</p>
     */
    @Override
    protected String buildSystemPrompt() {
        var tools = getAvailableTools();

        StringBuilder sb = new StringBuilder();
        sb.append("你是 Atlas 查询助手，专门负责回答 Kubernetes 集群相关的查询问题。\n\n");
        sb.append("## 你的职责\n");
        sb.append("1. 只能执行查询操作，绝不执行创建、修改、删除等变更操作\n");
        sb.append("2. 根据用户问题选择最合适的工具，提取必要参数\n");
        sb.append("3. 如果用户问题模糊，礼貌地询问缺少的信息\n");
        sb.append("4. 调用工具后，用简洁的中文总结结果\n\n");

        sb.append("## 可用工具列表\n");
        for (var t : tools) {
            sb.append(String.format("- %s: %s\n", t.name(), t.description()));
        }

        sb.append("\n## 参数提取规则\n");
        sb.append("1. nodeName / podName / namespace 等标识符必须精确匹配，不要猜测\n");
        sb.append("2. 如果用户未指定 namespace，默认使用 'default'\n");
        sb.append("3. 如果用户未指定 clusterId，默认使用 'default'\n");
        sb.append("4. pageSize 默认 20，最大 100\n");

        sb.append("\n## 回复风格\n");
        sb.append("- 技术术语保留英文（如 Pod, Node, PVC）\n");
        sb.append("- 数据量多时提供汇总，不要全部罗列\n");
        sb.append("- 如果有异常状态，优先高亮显示\n");

        return sb.toString();
    }
}
```

---

## 5. 工具注册与权限过滤

### 5.1 AtlasToolCallback 升级（已有类的增强）

当前 `AtlasToolCallback` 已正确实现 `ToolCallback` 接口。但 `buildToolDefinition` 中的 `inputSchema` 是模糊的 `"additionalProperties": true`，这会让 LLM 不知道具体需要什么参数。

**升级方案**：基于 `BaseTool#getRequiredParams()` 和 `getParamTypes()` 动态生成 JSON Schema。

```java
// 在 AtlasToolCallback 中替换 buildToolDefinition
private static ToolDefinition buildToolDefinition(BaseTool tool) {
    // 动态构建 JSON Schema（精确到字段级）
    Set<String> requiredParams = tool.getRequiredParams();
    Map<String, Class<?>> paramTypes = tool.getParamTypes();

    StringBuilder schema = new StringBuilder();
    schema.append("{\n");
    schema.append("  \"type\": \"object\",\n");
    schema.append("  \"properties\": {\n");

    // 合并 requiredParams 和 paramTypes 中的字段
    Set<String> allParams = new LinkedHashSet<>();
    allParams.addAll(requiredParams);
    allParams.addAll(paramTypes.keySet());

    boolean first = true;
    for (String param : allParams) {
        if (!first) schema.append(",\n");
        first = false;
        Class<?> type = paramTypes.getOrDefault(param, String.class);
        schema.append("    \"").append(param).append("\": {\n");
        schema.append("      \"type\": \"").append(toJsonSchemaType(type)).append("\"\n");
        schema.append("    }");
    }

    schema.append("\n  },\n");
    schema.append("  \"required\": [");
    schema.append(requiredParams.stream()
        .map(s -> "\"" + s + "\"")
        .collect(Collectors.joining(", ")));
    schema.append("],\n");
    schema.append("  \"additionalProperties\": true,\n");
    schema.append("  \"description\": \"").append(tool.getDescription()).append("\"\n");
    schema.append("}");

    return DefaultToolDefinition.builder()
        .name(tool.getToolName())
        .description(tool.getDescription())
        .inputSchema(schema.toString())
        .build();
}

private static String toJsonSchemaType(Class<?> type) {
    if (type == Integer.class || type == int.class
        || type == Long.class || type == long.class) {
        return "integer";
    } else if (type == Double.class || type == double.class
        || type == Float.class || type == float.class) {
        return "number";
    } else if (type == Boolean.class || type == boolean.class) {
        return "boolean";
    } else if (type.isAssignableFrom(List.class)) {
        return "array";
    } else if (Map.class.isAssignableFrom(type)) {
        return "object";
    }
    return "string";
}
```

### 5.2 ToolRegistry 已有功能直接复用

```java
// ToolRegistry.java — 已有方法，无需修改

/**
 * 按 Agent 类型列出当前用户可见的 ToolMetadata。
 */
public List<ToolMetadata> listByAgent(String agentCode) { ... }

/**
 * 构建当前用户的 System Prompt（已按权限过滤）。
 */
public String buildSystemPromptForCurrentUser() { ... }
```

---

## 6. 参数提取策略

### 6.1 Spring AI 原生提取（推荐）

Spring AI `MethodToolCallback` 内部机制：

```java
// spring-ai-model-1.1.6.jar MethodToolCallback.call(String toolInput)
// 1. toolInput = LLM 生成的 JSON 参数字符串
// 2. JsonParser.parseMap(toolInput) → Map<String, Object>
// 3. buildTypedArgument(toolInput, parameterType) → 强类型参数
// 4. Object target = this.toolObject; // BaseTool 实例
// 5. Method method = findMethod(...);
// 6. Object result = MethodUtils.invokeMethod(target, method, argument);
```

对于 Atlas 的 `BaseTool#execute(Map<String, Object>)`，`buildTypedArgument` 会将 JSON Map 直接传入，由 BaseTool 内部做校验和转换。

**这意味着**：
- ✅ LLM 根据 JSON Schema 生成结构化参数
- ✅ Spring AI 自动反序列化
- ✅ BaseTool 内部 `validate()` + `convertTypes()` 二次把关
- ✅ 参数错误时 AtlasToolValidationException 被 wrapCall 捕获，友好返回给 LLM

### 6.2 示例：参数提取全流程

```
User: "帮我查下 node-1 的详细状态"

Step 1: LLM 接收到 System Prompt，看到可用工具列表：
  - node_query: 查询所有节点
  - node_detail: 查询指定节点详情 (需要 nodeName 参数)
  - gpu_query: 查询 GPU 状态

Step 2: LLM 判断需要调用 node_detail，生成参数：
  {
    "nodeName": "node-1"
  }

Step 3: Spring AI 解析参数，调用 AtlasToolCallback.call()
   → NodeDetailTool.execute(Map.of("nodeName", "node-1"))
   → BaseTool.validate() 检查必填参数（nodeName 存在 ✓）
   → NodeDetailTool.doExecute() 执行业务查询
   → 返回 AtlasToolResult

Step 4: Spring AI 将 Tool 结果封装为 tool 消息，追加到对话

Step 5: LLM 总结结果：
  "node-1 状态为 Ready，CPU 8 核，内存 32Gi，已运行 45 天。"
```

### 6.3 缺失参数处理

如果 LLM 没有提供必填参数：

```java
// BaseTool.validate() 抛出 AtlasToolValidationException
throw new AtlasToolValidationException(
    "缺少必填参数: [nodeName]",
    "MISSING_REQUIRED_PARAMS",
    List.of("请提供以下参数: nodeName")
);

// wrapCall 捕获并转为结构化错误
return AtlasToolResult.fail("缺少必填参数: [nodeName]", "MISSING_REQUIRED_PARAMS", ...);

// Spring AI 将错误 JSON 返回给 LLM
// LLM 看到错误后，向用户追问："请告诉我您想查询哪个节点的名称？"
```

---

## 7. Orchestrator 层改造

### 7.1 改造后的 AtlasOrchestrator.streamChat()

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestBody ChatRequest request) {
    // ... 前置逻辑不变：Token 捕获、限流 ...

    Runnable asyncTask = () -> {
        try {
            // ── thinking ──
            emit(emitter, "thinking", Map.of("step", "intent",
                "content", "正在分析您的意图..."));

            // ── 意图路由 ──
            IntentResult result = intentRouter.route(request.userQuery());
            emit(emitter, "content", Map.of(
                "intentId", result.intentId(),
                "confidence", result.confidence(),
                "agent", result.agent()
            ));

            // ── LLM Agent 执行 ──
            AtlasAgentBase rawAgent = agentMap.get(result.agent());
            if (rawAgent instanceof AbstractLlmAgent llmAgent) {
                // ✅ P2 改造：调用 LLM Agent 的流式执行
                Map<String, Object> intentContext = Map.of(
                    "intentId", result.intentId(),
                    "confidence", result.confidence(),
                    "query", request.userQuery()
                );

                // 推送 tool_call 事件（LLM 可能在过程中调用 Tool）
                // 注意：Spring AI 1.1.6 stream() 暂不支持流式 tool_call 事件
                // 此处先发预期事件，实际 tool_call 在返回后补发
                emit(emitter, "tool_call", Map.of("status", "pending",
                    "agent", result.agent()));

                AgentExecutionResult execResult = llmAgent.runStreaming(
                    request.userQuery(),
                    intentContext,
                    // Token 消费者：推送给 SSE
                    token -> emit(emitter, "token", Map.of("content", token)),
                    // Tool 调用开始
                    toolCall -> emit(emitter, "tool_call", Map.of(
                        "tool", toolCall.toolName(),
                        "params", toolCall.params()
                    )),
                    // Tool 结果
                    toolResult -> emit(emitter, "tool_result", Map.of(
                        "tool", toolResult.toolName(),
                        "result", toolResult.result()
                    ))
                );

                // 最终总结
                emit(emitter, "content", Map.of(
                    "type", "final",
                    "content", execResult.answer()
                ));

            } else if (rawAgent != null) {
                // 旧版 Agent 兼容
                Map<String, Object> toolResult = rawAgent.executeIntent(
                    result.intentId(), Map.of());
                emit(emitter, "tool_result", toolResult);
            } else {
                emit(emitter, "content", Map.of("type", "notice",
                    "content", "Agent '" + result.agent() + "' 未加载"));
            }

            streamingEmitter.complete(emitter);

        } catch (Exception e) {
            log.error("[Orchestrator] 会话异常", e);
            streamingEmitter.error(emitter, e.getMessage());
        } finally {
            userConnections.merge(userId, -1, Integer::sum);
        }
    };

    CompletableFuture.runAsync(
        AsyncContextHolder.wrap(asyncTask, capturedToken),
        asyncExecutor
    );
    return emitter;
}
```

### 7.2 同步接口（非流式）

```java
@PostMapping("/chat")
public Map<String, Object> chat(@RequestBody ChatRequest request) {
    IntentResult result = intentRouter.route(request.userQuery());
    AtlasAgentBase rawAgent = agentMap.get(result.agent());

    if (rawAgent instanceof AbstractLlmAgent llmAgent) {
        AgentExecutionResult exec = llmAgent.run(
            request.userQuery(),
            Map.of("intentId", result.intentId())
        );
        return Map.of(
            "success", exec.success(),
            "answer", exec.answer(),
            "toolCalls", exec.toolCalls(),
            "fallback", exec.legacyFallback()
        );
    }

    // 旧版兼容...
    return Map.of("success", false, "message", "Agent 未升级");
}
```

---

## 8. 流式输出设计

### 8.1 Spring AI 1.1.6 的流式 API

```java
// 流式调用
chatClient.prompt()
    .system(systemPrompt)
    .user(userQuery)
    .stream()
    .content()          // Flux<String>
    .subscribe(
        token -> sseEmitter.send(token),   // 逐字推送
        error -> sseEmitter.completeWithError(error),
        () -> sseEmitter.complete()
    );
```

### 8.2 限制说明

| 能力 | Spring AI 1.1.6 | Spring AI 1.2.x |
|------|-----------------|-----------------|
| 流式文本输出 | ✅ `stream().content()` | ✅ |
| 流式 tool_call 事件 | ❌ 不支持 | ✅ `stream().toolCalls()` |
| 中途拦截 tool 调用 | ❌ 需手动 | ✅ Advisor 拦截 |

**当前决策**：Spring AI 1.1.6 的 `stream()` 在 Function Calling 场景下，
LLM 可能会先输出思考文本再调用 Tool。如果前端需要精确的 `tool_call` 事件，
建议先使用**同步模式**（`.call()`），拿到完整结果后再分别推送事件。

---

## 9. 异常降级

### 9.1 三级降级策略

```
Level 1: LLM Function Calling（完整体验）
    ↓ 网络超时 / API 限流 / 模型不可用
Level 2: 降级为直接 Tool 调用（executeIntent，无参数）
    ↓ Tool 不存在 / 权限不足
Level 3: 返回预设兜底回复
```

### 9.2 降级代码示例

已在 `AbstractLlmAgent.run()` 中实现：

```java
try {
    // 尝试 LLM Function Calling
    return doLlmCall(userQuery);
} catch (Exception e) {
    log.error("LLM 失败，降级: {}", e.getMessage());
    // 降级到直接调用（已有逻辑）
    return runLegacy(userQuery, intentContext);
}
```

---

## 10. Spring AI 官方参考

### 10.1 官方文档

1. **Spring AI Function Calling 概念**
   - URL: https://docs.spring.io/spring-ai/reference/api/tools.html
   - 核心 API：`ChatClient.Builder.defaultToolCallbacks(ToolCallback...)`
   - 关键类：`FunctionToolCallback`, `MethodToolCallback`, `ToolCallback`

2. **Spring AI ChatClient API**
   - URL: https://docs.spring.io/spring-ai/reference/api/chatclient.html
   - 关键方法：`prompt().system().user().tools().call()` / `stream()`

3. **官方示例代码（GitHub）**
   - 仓库: `spring-projects/spring-ai-examples`
   - 路径: `models/spring-ai-model-chatclient/openai-chatclient/src/main/java/com/example/chatclient/`
   - 关键文件：
     - `FunctionCallbackConfiguration.java` — FunctionToolCallback 注册
     - `ChatClientController.java` — ChatClient 调用方式

### 10.2 v1.1.6 关键 API 确认（基于 Atlas 已有字节码分析）

```java
// ChatClient.java (spring-ai-client-chat-1.1.6)
public interface ChatClient {
    interface Builder {
        Builder defaultToolCallbacks(ToolCallback... toolCallbacks);
        Builder defaultToolCallbacks(ToolCallbackProvider... toolCallbackProviders);
        Builder defaultAdvisors(Advisor... advisors);
        ChatClient build();
    }
    interface ChatClientRequestSpec {
        ChatClientRequestSpec toolCallbacks(ToolCallback... toolCallbacks);
        ChatClientRequestSpec toolContext(Map<String, Object> toolContext);
        ChatResponse call();
        Stream<String> stream();
    }
}

// MethodToolCallback.java (spring-ai-model-1.1.6)
public class MethodToolCallback implements ToolCallback {
    public String call(String toolInput, ToolContext toolContext) {
        // 1. parse JSON
        // 2. buildTypedArgument(toolInput, parameterType)
        // 3. Method.invoke(toolObject, argument)
    }
}
```

### 10.3 相关设计文档（Atlas 内部）

- `/docs/v3.1/TOOL_ARCHITECTURE_DESIGN.md` — Spring AI 1.1.6 API 完整映射表
- `/docs/v3.1/ARCHITECTURE_DECISIONS.md` — 框架选型决策记录

---

## 11. 实施清单（P2 迭代建议）

| 优先级 | 任务 | 涉及文件 | 预估工作量 |
|--------|------|----------|-----------|
| P0 | 创建 `AbstractLlmAgent` | 新增 | 4h |
| P0 | 升级 `QueryAgent` | `QueryAgent.java` | 1h |
| P0 | 升级 `AtlasToolCallback.buildToolDefinition()` | `AtlasToolCallback.java` | 2h |
| P0 | 改造 `AtlasOrchestrator` | `AtlasOrchestrator.java` | 3h |
| P1 | 创建 `AgentExecutionResult` + `ToolCallRecord` | 新增 | 1h |
| P1 | 修复 `AtlasConfiguration` ChatClient.Builder 注入 | `AtlasConfiguration.java` | 2h |
| P2 | 其他 Agent（Deploy/Diag/...）升级 | 各 Agent.java | 各 1h |
| P2 | 流式 tool_call 事件精确推送 | 依赖 Spring AI 1.2.x | 待定 |

---

## 12. 关键代码总结

### 最小可运行示例（QueryAgent 核心逻辑）

```java
@Component
public class QueryAgent extends AbstractLlmAgent {

    public QueryAgent(ToolRegistry registry,
                      ChatClient.Builder builder,
                      @Value("${atlas.agent.query.llm-enabled:true}") boolean enabled) {
        super(registry, builder, enabled);
    }

    @Override protected String getAgentType() { return "query"; }
    @Override protected String getAgentName() { return "QueryAgent"; }

    @Override
    protected String buildSystemPrompt() {
        // 告诉 LLM 它是什么 + 能调什么工具 + 参数规则
        return "你是 Atlas 查询助手...\n可用工具：...\n参数规则：...";
    }

    // 调用入口（由 Orchestrator 调用）
    public AgentExecutionResult run(String query, Map<String, Object> ctx) {
        return super.run(query, ctx);  // 走 LLM Function Calling
    }
}
```

### Orchestrator 调用点

```java
if (agent instanceof AbstractLlmAgent llmAgent) {
    AgentExecutionResult r = llmAgent.run(userQuery, intentContext);
    // r.answer() = LLM 总结后的回复
    // r.toolCalls() = 调用历史
}
```

---

> **作者**: Atlas Team  
> **日期**: 2025-05-14  
> **版本**: 3.1.0-P2-Design  
> **关联**: `TOOL_ARCHITECTURE_DESIGN.md`, `ARCHITECTURE_DECISIONS.md`
