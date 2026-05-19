# Atlas v3.1 Tool执行后LLM润色集成方案调研报告

## 一、执行摘要

| 维度 | 结论 |
|------|------|
| **推荐方案** | **B方案：流式润色 (Streaming Polish)** — `ToolResult → ChatClient.stream() → SSE content` |
| **技术选型** | Spring AI `ChatClient` + `stream()` 流式输出，**不选** StateGraph 内置链式编排（当前版本限制） |
| **接入位置** | `AtlasOrchestrator.streamChat()` Tool执行后、emit content前 |
| **延迟影响** | 同步：+800~2000ms；流式：首字延迟+300~500ms，感知更优 |
| **Token开销** | 每请求增加 500~1500 tokens（Prompt模板 + Tool结果JSON） |
| **并发风险** | 可控：借助现有限流（MAX_PER_USER=3）+ 独立线程池隔离 |

---

## 二、Spring AI ChatClient 润色机制调研

### 2.1 ChatClient.call() vs stream() 对比

```java
// ===== call() — 同步阻塞，一次性返回完整润色结果 =====
String polished = chatClient.prompt()
    .system(polishPromptTemplate)
    .user(toolResultJson)
    .call()
    .content();  // ← 阻塞等待完整结果

// 特点：
// ✅ 实现简单，代码侵入性低
// ❌ 用户需等待全部生成完成才能看到任何内容（延迟敏感）
// ❌ 大结果时前端长时间空白，体验差
// 适用：Tool结果<500字、低延迟容忍度场景

// ===== stream() — 异步流式，逐字推送到SSE =====
Flux<String> stream = chatClient.prompt()
    .system(polishPromptTemplate)
    .user(toolResultJson)
    .stream()
    .content();  // ← 返回 Flux<String>，逐token流式输出

// 特点：
// ✅ 首字延迟低（300~500ms），用户即时感知"处理中"
// ✅ 与现有SSE流天然契合，无需额外适配层
// ✅ 大结果时前端逐步渲染，体验接近原生LLM对话
// ⚠️ 需处理Publisher背压 + SSE emitter生命周期
// 适用：当前Atlas架构 **强烈推荐**
```

### 2.2 Tool结果 → ChatClient 的数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                    AtlasOrchestrator.streamChat()                │
│                                                                  │
│  1. ToolRegistry.execute(toolParams)                            │
│     └── Map<String, Object> toolResult                          │
│         ├── "success": true/false                               │
│         ├── "message": "查询完成"                                │
│         └── "data": List<PodInfo> / Map / String                │
│                                                                  │
│  2. ToolResultPolishingService.polish()  ←【新增组件】           │
│     ├── JSON序列化 toolResult → String toolResultJson           │
│     ├── 选择 PromptTemplate（按Tool类型动态路由）               │
│     └── ChatClient.stream(prompt) → Flux<String>                │
│                                                                  │
│  3. StreamingEmitter.emit("content", chunk)  ← 逐字推SSE       │
│     └── 前端实时渲染 Markdown/表格                              │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Prompt 设计 — K8s/运维专用润色模板

```java
// 核心Prompt模板（按数据类型分类）
public class PolishPromptTemplate {

    // 【列表型数据】Pod列表、节点列表 → Markdown表格
    public static final String LIST_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将以下工具返回的原始 JSON 数据，
        转化为简洁、专业的中文回复。要求：
        1. 使用 Markdown 表格展示列表数据（最多显示前20行，超出提示"共N条"）
        2. 状态列用emoji标注：Running 🟢 / Pending 🟡 / Failed 🔴 / Unknown ⚪
        3. 添加一句话摘要：共查询到N条，其中异常X条
        4. 如存在异常项，在最后高亮告警区域列出
        5. 不要暴露原始JSON结构给最终用户

        原始数据：
        {tool_result_json}

        用户原始问题：{user_query}
        """;

    // 【详情型数据】Pod详情、节点详情 → 结构化摘要
    public static final String DETAIL_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将以下资源详情数据转化为
        结构化的中文分析报告，使用以下格式：

        📌 **基本信息**
        - 名称: xxx
        - 命名空间: xxx
        - 状态: xxx (带emoji)

        🔍 **关键指标**
        - CPU: xxx / 内存: xxx
        
        ⚠️ **异常检测**（如无则省略此节）
        - 发现 xxx 问题...

        原始数据：
        {tool_result_json}
        """;

    // 【诊断型数据】Pod诊断、日志分析 → 根因分析格式
    public static final String DIAGNOSE_TEMPLATE = """
        你是 Atlas 故障诊断专家。请基于以下诊断数据提供：
        1. **现象摘要**（1句话）
        2. **根因分析**（按概率排序，最多3条）
        3. **修复建议**（可操作的具体命令或步骤）
        4. **风险等级** 🔴高风险 / 🟡中风险 / 🟢低风险

        原始诊断数据：
        {tool_result_json}
        """;

    // 【错误型数据】Tool执行失败 → 友好化错误
    public static final String ERROR_TEMPLATE = """
        你是 Atlas 运维助手。请将以下错误信息转化为用户友好的中文提示：
        1. 先说明"操作未能完成"，不伤及用户信任
        2. 用通俗语言解释错误原因（避免堆栈trace）
        3. 给出下一步建议（如"请检查权限"、"联系管理员"）

        原始错误：
        {tool_result_json}
        """;
}
```

---

## 三、SSE流中插接LLM润色阶段 — 方案对比

### 3.1 方案A：同步 call() — 阻塞式润色（不推荐）

```java
// 在 AtlasOrchestrator.streamChat() Tool执行后：
String toolResultJson = JsonUtils.toJson(toolResult);

// ❌ 同步阻塞 — 用户需等待完整润色结果生成
String polished = polishService.polishSync(toolResultJson, request.userQuery());
emit(emitter, "content", Map.of("content", polished));
emit(emitter, "done", Map.of());

// 时序：
// [thinking] → [tool_call] → [等待1~3秒] → [content: 完整润色文本] → [done]
```

| 指标 | 数值 |
|------|------|
| 首字延迟 | 1.5~3.0s（整个润色完成后才推送） |
| 用户感知 | "卡顿" — 前端长时间空白 |
| 实现复杂度 | ⭐⭐ 低 |
| 线程占用 | 阻塞当前线程 |

### 3.2 方案B：流式 stream() — Flux逐字推送（✅ 推荐）

```java
// 在 AtlasOrchestrator.streamChat() Tool执行后：
String toolResultJson = JsonUtils.toJson(toolResult);

// 选择模板
String template = selectTemplate(toolResult);

// ✅ 流式异步 — 逐token推送到SSE
polishService.polishStream(toolResultJson, request.userQuery(), template)
    .subscribe(
        chunk -> emit(emitter, "content", Map.of("content", chunk)),
        err  -> emit(emitter, "error", Map.of("content", "润色失败: " + err.getMessage())),
        ()   -> emit(emitter, "done", Map.of())
    );

// 时序：
// [thinking] → [tool_call] → [content: "查"]
//                              [content: "询到"]
//                              [content: "3个Pod..."] → [done]
```

| 指标 | 数值 |
|------|------|
| 首字延迟 | 300~500ms（首个token到达即推） |
| 用户感知 | "实时打字" — 接近原生LLM对话体验 |
| 实现复杂度 | ⭐⭐⭐ 中 |
| 线程占用 | Reactor异步线程，不阻塞业务线程 |

### 3.3 方案C：StateGraph内置链式编排 — 远期规划（待Spring AI Alibaba演进）

当前 `spring-ai-alibaba-graph-core:1.1.2.2` 的 StateGraph 架构：

```
START → supervisor → [conditional] → tool_call → END
                              ↓
                         direct_answer → END
```

**现有局限：**
1. `tool_call` 节点写入 `tool_result` / `answer` 后直接 `→ END`，**没有内置的LLM后处理节点**
2. `ReactAgent` 内部虽支持 Tool → LLM 的 ReAct 循环，但目的是**增量决策**而非**结果润色**
3. Graph 节点间数据传递依赖 State Key 覆盖，Flux 流式输出需自定义 `EmitterNode` 桥接

**可行但复杂的替代：**
```
START → supervisor → tool_call → polish_node → emit_sse → END
```
问题：`polish_node` 要实现 LLM stream → SSE 桥接，需重写大量非业务代码。

**结论：** 当前版本 StateGraph **不原生支持** Tool→LLM润色→Output链式编排。建议沿用 `AtlasOrchestrator` 手动编排，StateGraph 作为 Phase2 远期演进方向。

---

## 四、代码级Pipeline设计与类设计

### 4.1 新增类文件清单

```
com.atlas.orchestrator.polish/
├── ToolResultPolishingService.java      # 润色服务主入口
├── PolishPromptTemplate.java           # Prompt模板库（按数据类型）
├── ToolResultFormatter.java            # Tool结果格式化/截断
├── PolishStreamBridge.java             # Flux<String> → SSE emitter 桥接
└── PolishMetrics.java                  # 润色阶段性能埋点
```

### 4.2 ToolResultPolishingService — 核心服务

```java
package com.atlas.orchestrator.polish;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Tool执行结果LLM润色服务。
 *
 * <p>职责：将Tool返回的原始结构化数据（JSON/Map），通过ChatClient.stream()
 * 转化为面向用户的自然语言表述，支持流式输出到SSE。</p>
 */
@Service
public class ToolResultPolishingService {

    private final ChatClient chatClient;
    private final PolishMetrics metrics;

    public ToolResultPolishingService(ChatModel chatModel, PolishMetrics metrics) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
        this.metrics = metrics;
    }

    /**
     * 【同步润色】返回完整润色文本。
     * 适用：短结果、低延迟容忍、或fallback场景。
     */
    public String polishSync(Map<String, Object> toolResult, String userQuery) {
        long start = System.currentTimeMillis();
        try {
            String resultJson = ToolResultFormatter.format(toolResult);
            String template = PolishPromptTemplate.select(toolResult);

            String polished = chatClient.prompt()
                .system(template)
                .user(buildUserContent(resultJson, userQuery))
                .call()
                .content();

            metrics.recordSync(System.currentTimeMillis() - start, resultJson.length());
            return polished;
        } catch (Exception e) {
            metrics.recordFailure("sync", e);
            // fallback：返回原始message
            return fallbackPolish(toolResult);
        }
    }

    /**
     * 【流式润色】返回 Flux<String>，逐token输出。
     * 适用：主流场景，与SSE流式推送天然契合。
     */
    public Flux<String> polishStream(Map<String, Object> toolResult, String userQuery) {
        long start = System.currentTimeMillis();
        try {
            String resultJson = ToolResultFormatter.format(toolResult);
            // Token保护：若结果过长，截断至MAX_CONTEXT_LENGTH
            if (resultJson.length() > ToolResultFormatter.MAX_CONTEXT_LENGTH) {
                resultJson = ToolResultFormatter.truncate(resultJson, ToolResultFormatter.MAX_CONTEXT_LENGTH);
            }

            String template = PolishPromptTemplate.select(toolResult);

            return chatClient.prompt()
                .system(template)
                .user(buildUserContent(resultJson, userQuery))
                .stream()
                .content()
                .doOnNext(chunk -> metrics.recordChunk(chunk.length()))
                .doOnComplete(() -> metrics.recordStreamComplete(
                    System.currentTimeMillis() - start))
                .doOnError(err -> metrics.recordFailure("stream", err))
                .onErrorResume(err -> {
                    // 流式出错时fallback为单条完整原始message
                    return Flux.just(fallbackPolish(toolResult));
                })
                .subscribeOn(Schedulers.boundedElastic());
                // ^ 在独立弹性线程池执行，不阻塞SSE主线程

        } catch (Exception e) {
            metrics.recordFailure("stream_init", e);
            return Flux.just(fallbackPolish(toolResult));
        }
    }

    // ═══════════════════════════════════════════
    // 私有辅助
    // ═══════════════════════════════════════════

    private String buildUserContent(String resultJson, String userQuery) {
        return """
            用户原始问题：%s
            
            工具返回的原始数据（JSON）：
            %s
            """.formatted(userQuery, resultJson);
    }

    private String fallbackPolish(Map<String, Object> toolResult) {
        // fallback：硬编码格式化（与原M2.7逻辑兼容）
        boolean success = Boolean.TRUE.equals(toolResult.get("success"));
        String message = toolResult.get("message") != null
            ? toolResult.get("message").toString() : "";
        Object data = toolResult.get("data");

        StringBuilder sb = new StringBuilder();
        sb.append(success ? "✅ " : "❌ ").append(message);
        if (data != null) {
            sb.append("\n\n```\n").append(data).append("\n```");
        }
        return sb.toString();
    }
}
```

### 4.3 PolishPromptTemplate — 模板路由

```java
package com.atlas.orchestrator.polish;

import java.util.Map;

/**
 * Prompt模板库 + 动态路由策略。
 *
 * <p>根据 Tool 返回的数据特征（类型、字段名、长度），
 * 自动选择最合适的润色模板。</p>
 */
public final class PolishPromptTemplate {

    private PolishPromptTemplate() {}

    // Token预算上限
    public static final int MAX_CONTEXT_LENGTH = 8000;  // 字符数，约对应2000-3000 tokens

    public static String select(Map<String, Object> toolResult) {
        Object data = toolResult.get("data");
        boolean success = Boolean.TRUE.equals(toolResult.get("success"));

        // 失败场景 → 错误模板
        if (!success) return ERROR_TEMPLATE;

        // 诊断类数据检测
        if (isDiagnoseData(data)) return DIAGNOSE_TEMPLATE;

        // 列表型数据检测
        if (isListData(data)) return LIST_TEMPLATE;

        // 详情型数据（对象/Map）
        if (isObjectData(data)) return DETAIL_TEMPLATE;

        // 默认：简洁模板
        return SIMPLE_TEMPLATE;
    }

    // ========== 模板定义 ==========

    public static final String LIST_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将以下JSON数据转化为中文表格摘要。
        规则：
        - Markdown表格，最多20行
        - 状态：Running🟢 Pending🟡 Failed🔴 Unknown⚪
        - 文末加"共N条，异常X条"
        - 异常项最后高亮
        """;

    public static final String DETAIL_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将资源详情转化为结构化报告：
        📌基本信息 / 🔍关键指标 / ⚠️异常检测（如有）
        """;

    public static final String DIAGNOSE_TEMPLATE = """
        你是Atlas故障诊断专家。提供：现象摘要→根因分析→修复建议→风险等级。
        """;

    public static final String ERROR_TEMPLATE = """
        你是Atlas运维助手。将错误转化为友好提示：说明原因+建议下一步。
        """;

    public static final String SIMPLE_TEMPLATE = """
        你是Atlas助手。简洁回答用户问题，基于提供的数据。
        """;

    // ========== 类型检测辅助 ==========

    private static boolean isListData(Object data) {
        return data instanceof java.util.List && !((java.util.List<?>) data).isEmpty();
    }

    private static boolean isObjectData(Object data) {
        return data instanceof Map || (data != null && !(data instanceof java.util.List));
    }

    private static boolean isDiagnoseData(Object data) {
        if (!(data instanceof Map map)) return false;
        // 诊断数据通常包含 events/warnings/logs 字段
        return map.containsKey("events") || map.containsKey("logs")
            || map.containsKey("diagnosis") || map.containsKey("warnings");
    }
}
```

### 4.4 ToolResultFormatter — 结果格式化与截断

```java
package com.atlas.orchestrator.polish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.*;

/**
 * Tool结果格式化工具。
 *
 * <p>目标：在保留关键信息的前提下，将Tool输出压缩以适配LLM上下文窗口。</p>
 */
public final class ToolResultFormatter {

    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public static final int MAX_CONTEXT_LENGTH = 8000;
    public static final int MAX_LIST_ITEMS = 20;

    /** 将Tool结果转为适合放入Prompt的JSON文本 */
    public static String format(Map<String, Object> toolResult) {
        try {
            Object data = toolResult.get("data");
            // 列表截断
            if (data instanceof List<?> list && list.size() > MAX_LIST_ITEMS) {
                Map<String, Object> truncated = new LinkedHashMap<>(toolResult);
                truncated.put("data", list.subList(0, MAX_LIST_ITEMS));
                truncated.put("_note", "结果过长，仅展示前" + MAX_LIST_ITEMS + "条，共" + list.size() + "条");
                return mapper.writeValueAsString(truncated);
            }
            return mapper.writeValueAsString(toolResult);
        } catch (Exception e) {
            // fallback：toString()
            return toolResult.toString();
        }
    }

    /** 字符级截断（最后防线） */
    public static String truncate(String json, int maxLength) {
        if (json.length() <= maxLength) return json;
        return json.substring(0, maxLength - 100)
            + "\n... [内容过长，已截断，原长度：" + json.length() + "字符]";
    }
}
```

### 4.5 AtlasOrchestrator 改造点（核心接入）

```java
// 新增依赖注入
private final ToolResultPolishingService polishingService;

// 在 AtlasOrchestrator 构造方法中注入
public AtlasOrchestrator(
    // ... 现有参数 ...
    ToolResultPolishingService polishingService  // ← 新增
) {
    // ... 现有赋值 ...
    this.polishingService = polishingService;
}

// ═══════════════════════════════════════════════════════════
// streamChat() 中 Tool 执行后的改造
// ═══════════════════════════════════════════════════════════

// 原有代码（M2.7 硬编码兜底）：
/*
StringBuilder resultText = new StringBuilder();
if (success) { resultText.append("✅ ").append(message); ... }
emit(emitter, "content", Map.of("content", resultText.toString()));
*/

// ✅ 改造后 — 流式润色：
if (success) {
    // 推送 tool_done 事件（前端可展示"正在整理结果..."）
    emit(emitter, "tool_done", Map.of("tool", result.intentId(), "status", "polishing"));

    // 启动流式润色
    polishingService.polishStream(toolResult, request.userQuery())
        .subscribe(
            chunk -> {
                // 逐字推送 content 事件
                emit(emitter, "content", Map.of("content", chunk));
            },
            err -> {
                log.error("[Polish] 润色失败: {}", err.getMessage());
                // fallback：硬编码兜底（与原M2.7兼容）
                String fallback = fallbackFormat(toolResult);
                emit(emitter, "content", Map.of("content", fallback));
                emit(emitter, "done", Map.of());
            },
            () -> {
                // 流式完成
                emit(emitter, "done", Map.of());
            }
        );
} else {
    // 失败场景也走润色（友好化错误提示）
    emit(emitter, "tool_done", Map.of("tool", result.intentId(), "status", "error"));
    polishingService.polishStream(toolResult, request.userQuery())
        .subscribe(
            chunk -> emit(emitter, "content", Map.of("content", chunk)),
            err -> {
                String fallback = fallbackFormat(toolResult);
                emit(emitter, "content", Map.of("content", fallback));
                emit(emitter, "done", Map.of());
            },
            () -> emit(emitter, "done", Map.of())
        );
}
```

### 4.6 SupervisorGraph 改造点（Graph模式兼容）

```java
// 在 AtlasGraphConfig.supervisorGraph() 的 tool_call 节点中
// 将原有的硬编码 summary 替换为 polishService 调用

// 原有代码：
/*
String summary = data instanceof List
    ? String.format("✅ %s（共 %d 条数据）", message, ((List<?>) data).size())
    : "✅ " + message;
updates.put("answer", summary);
*/

// ✅ 改造后（Graph节点内同步call版本）：
// 注意：Graph节点为同步执行，此处使用 polishSync
String polished = polishingService.polishSync(toolResult, state.value("input")
    .map(Object::toString).orElse(""));
updates.put("answer", polished);
updates.put("tool_result", Map.of(
    "success", success,
    "message", message,
    "tool", intentId,
    "data", data
));
```

> **Graph模式流式限制**：当前 `node_async` 返回 `Map<String, Object>`，不支持 Flux 流式输出到 SSE。若要实现 Graph 模式流式润色，需在 `AtlasOrchestrator.runSupervisorGraph()` 的 `.subscribe()` 回调中增加对 `tool_result` 状态的流式处理逻辑。

---

## 五、性能指标与风险分析

### 5.1 Token开销估算（Kimi k2.6）

| 场景 | System Prompt | Tool结果JSON | 输出预估 | 总计 |
|------|--------------|-------------|---------|------|
| 小型列表（<10条） | ~200 tokens | ~500 tokens | ~300 tokens | ~1000 tokens |
| 中型列表（10~50条） | ~200 tokens | ~1500 tokens | ~500 tokens | ~2200 tokens |
| 大型列表（>50条，截断后） | ~200 tokens | ~2500 tokens | ~800 tokens | ~3500 tokens |
| Pod详情/诊断 | ~200 tokens | ~1000 tokens | ~600 tokens | ~1800 tokens |
| 错误友好化 | ~150 tokens | ~300 tokens | ~200 tokens | ~650 tokens |

**成本控制建议：**
1. `MAX_CONTEXT_LENGTH = 8000` 字符（约2000-2500 tokens），超长自动截断
2. 列表型数据只取前20条，加 `_note` 标注总数
3. 高频查询场景可考虑 **润色结果缓存**（Redis/Mem，TTL=5min）

### 5.2 延迟分析

| 阶段 | call() 同步 | stream() 流式 |
|------|------------|--------------|
| Prompt构建 | ~1ms | ~1ms |
| 首字延迟（TTFB） | 等于总延迟 | 300~600ms |
| 完整生成（500字输出） | 800~2000ms | 1500~3000ms（总耗时） |
| 用户感知延迟 | 800~2000ms | 300ms（首字即显示） |
| 线程阻塞时间 | 全程阻塞 | 0（异步） |

**结论：** `stream()` 虽然总耗时略长，但 **首字延迟低、用户体验好**，与 SSE 流式架构完美契合。

### 5.3 并发风险评估与缓解

| 风险点 | 等级 | 缓解措施 |
|--------|------|---------|
| LLM调用超时/挂起 | 🔴 高 | 1. ChatClient 配置 `timeout=10s` <br>2. `Flux.timeout(Duration.ofSeconds(15))` <br>3. 降级到 fallbackFormat |
| 并发突增压垮LLM代理 | 🔴 高 | 1. 现有限流 `MAX_PER_USER=3` <br>2. 独立线程池 `Schedulers.boundedElastic()` <br>3. 全局限流计数器 |
| 长JSON导致Token超限 | 🟡 中 | 1. `MAX_CONTEXT_LENGTH=8000` <br>2. 列表截断至20条 |
| 润色失败导致无输出 | 🟡 中 | fallback 机制：返回原始 message + data |
| 上下文污染（多轮对话） | 🟢 低 | 每次润色独立调用，不携带历史消息 |
| 线程泄漏 | 🟢 低 | Reactor 自动管理订阅生命周期 |

### 5.4 线程模型

```
【Tomcat线程】 request → 创建 SseEmitter
    ↓
【asyncExecutor线程】 AtlasOrchestrator.asyncTask
    ↓
【Tool执行】 tool.execute()  ← 同步，可能发起HTTP到kube-manager
    ↓
【boundedElastic线程】 ChatClient.stream()  ← Reactor异步，不阻塞asyncExecutor
    ↓ 逐字回调
【asyncExecutor线程】 emit(emitter, "content", chunk)
    ↓
【Tomcat NIO线程】 SSE推送至客户端
```

---

## 六、实施路线图

| 阶段 | 任务 | 预估工期 | 负责人 |
|------|------|---------|--------|
| **M1** | 新增 `ToolResultPolishingService` + `PolishPromptTemplate` + `ToolResultFormatter` | 1d | 后端 |
| **M2** | `AtlasOrchestrator` 改造：Tool执行后接入 `polishStream()` | 0.5d | 后端 |
| **M3** | Prompt模板调优：3种数据类型的样例数据 + 效果评测 | 1d | 全栈/算法 |
| **M4** | 前端适配：`tool_done` 事件响应 + loading态"正在整理结果..." | 0.5d | 前端 |
| **M5** | 压测：并发50/100/200下的延迟与成功率 | 1d | 测试 |
| **M6** | `supervisorGraph` Graph模式同步润色适配 | 0.5d | 后端 |
| **M7** | 润色结果缓存（Redis，高频查询场景） | 1d | 后端 |

---

## 七、参考资料

1. Spring AI 1.1.x Reference — ChatClient / Function Calling / Streaming
2. Spring AI Alibaba Graph Core — `StateGraph`, `CompiledGraph`, `ReactAgent`
3. 项目源码：`AtlasOrchestrator.java`, `AtlasGraphConfig.java`, `StreamingEmitter.java`
4. kimi-k2.6 API 文档 — OpenAI兼容接口 /sse 流式输出
