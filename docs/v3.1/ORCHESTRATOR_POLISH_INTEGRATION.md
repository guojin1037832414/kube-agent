### AtlasOrchestrator 集成改造示例

本文档说明如何在现有 `AtlasOrchestrator.streamChat()` 中集成 `ToolResultPolishingService`。

---

## 修改 1：新增依赖注入

在 `AtlasOrchestrator.java` 中：

```java
// 新增 import
import com.atlas.orchestrator.polish.ToolResultPolishingService;
import reactor.core.publisher.Flux;

// 新增字段
private final ToolResultPolishingService polishingService;

// 构造方法注入
@Autowired
public AtlasOrchestrator(
    IntentRouter intentRouter,
    StreamingEmitter streamingEmitter,
    ToolRegistry toolRegistry,
    UserPermissionContext userPermissionContext,
    KubeManagerHttpClient kubeManagerClient,
    TimedDecisionCache decisionCache,
    @Qualifier("atlasTaskExecutor") Executor asyncExecutor,
    SessionStore sessionStore,
    @Autowired(required = false) @Qualifier("supervisorGraph") CompiledGraph supervisorGraph,
    @Autowired(required = false) @Qualifier("atlasGraph") CompiledGraph compiledGraph,
    ToolResultPolishingService polishingService  // ← 新增
) {
    // ... 现有赋值 ...
    this.polishingService = polishingService;
}
```

---

## 修改 2：streamChat() Tool执行后接入润色

替换 `streamChat()` 中现有的硬编码格式化逻辑（约第 249-259 行）：

### 原有代码（删除）

```java
// M2.7 Fix: 将 tool_result 转成 content 推送给前端
StringBuilder resultText = new StringBuilder();
if (success) {
    resultText.append("✅ ").append(message);
    if (data != null) {
        resultText.append("\n\n```\n").append(data).append("\n```");
    }
} else {
    resultText.append("❌ ").append(message);
}
emit(emitter, "content", Map.of("content", resultText.toString()));
```

### 新代码（流式润色）

```java
// B方案：Tool结果 → LLM润色 → 流式推SSE
Map<String, Object> toolResult = new java.util.HashMap<>();
toolResult.put("success", success);
toolResult.put("message", message);
toolResult.put("data", data);

// 推送 tool_done 事件，前端展示"正在整理结果..."
emittool_done(emitter, "tool_done", Map.of("tool", result.intentId(), "status", "polishing"));

// 启动流式润色并订阅到SSE
polishingService.polishStream(toolResult, request.userQuery())
    .subscribe(
        chunk -> emit(emitter, "content", Map.of("content", chunk)),
        err -> {
            log.error("[Polish] 润色失败: {}", err.getMessage());
            // fallback：硬编码兜底格式
            String fallback = (success ? "✅ " : "❌ ") + message
                + (data != null ? "\n\n```\n" + data + "\n```" : "");
            emit(emitter, "content", Map.of("content", fallback));
            emit(emitter, "done", Map.of());
        },
        () -> {
            // 润色流完成
            emit(emitter, "done", Map.of());
        }
    );
```

---

## 修改 3：runSupervisorGraph() Graph模式同步润色

在 `runSupervisorGraph()` 的 `tool_result` state 处理逻辑中（约第 528-545 行）：

### 原有代码（部分）

```java
state.value("tool_result")
    .filter(Map.class::isInstance)
    .map(Map.class::cast)
    .ifPresent(tr -> {
        // ... 硬编码格式化 ...
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(success)) {
            sb.append("✅ ").append(msg != null ? msg : "执行完成");
            if (data != null) sb.append("\n\n").append(data);
        } else {
            sb.append("❌ ").append(msg != null ? msg : "执行失败");
        }
        emit(emitter, "content", Map.of("content", sb.toString()));
    });
```

### 新代码（Graph外部流式润色）

```java
state.value("tool_result")
    .filter(Map.class::isInstance)
    .map(Map.class::cast)
    .ifPresent(tr -> {
        @SuppressWarnings("unchecked")
        Map<String, Object> trMap = (Map<String, Object>) tr;

        // 推送 tool_done 事件
        emit(emitter, "tool_done", Map.of(
            "tool", trMap.get("tool"),
            "status", "polishing"
        ));

        // 在Graph外部启动流式润色（绕过Graph同步限制）
        String userQuery = state.value("input")
            .map(Object::toString).orElse("");

        polishingService.polishStream(trMap, userQuery)
            .subscribe(
                chunk -> emit(emitter, "content", Map.of("content", chunk)),
                err -> {
                    log.error("[Graph-Polish] 润色失败: {}", err.getMessage());
                    // fallback
                    Object msg = trMap.get("message");
                    Object data = trMap.get("data");
                    boolean success = Boolean.TRUE.equals(trMap.get("success"));
                    StringBuilder sb = new StringBuilder();
                    sb.append(success ? "✅ " : "❌ ").append(msg);
                    if (data != null) sb.append("\n\n").append(data);
                    emit(emitter, "content", Map.of("content", sb.toString()));
                },
                () -> {
                    // 不在这里发 done，由 Graph 完成回调统一处理
                    log.debug("[Graph-Polish] 润色流完成");
                }
            );
    });
```

---

## 修改 4：新增 AtlasConfiguration Bean

在 `AtlasConfiguration.java` 中新增：

```java
import com.atlas.orchestrator.polish.*;

@Bean
public ToolResultPolishingService toolResultPolishingService(
        @Autowired(required = false) ChatModel chatModel,
        PolishMetrics metrics) {
    if (chatModel == null) {
        log.warn("[AtlasConfiguration] ChatModel 不可用，ToolResultPolishingService 以降级模式运行");
        // 返回一个始终 fallback 的 mock 实现，或抛出异常阻止启动
        // 这里选择：不创建 bean，让调用方判空处理
        return null;
    }
    return new ToolResultPolishingService(chatModel, metrics);
}

@Bean
public PolishMetrics polishMetrics() {
    return new PolishMetrics();
}
```

> **注意**：需要确认项目中已有 `ChatModel` 的自动配置 bean（由 `spring-ai-starter-model-openai` 提供）。
> 若 `ChatModel` 因 API key 缺失未创建，则 `toolResultPolishingService` 返回 null，
> `AtlasOrchestrator` 中需做空值判断并回退到原硬编码逻辑。

---

## 修改 5：pom.xml 确认依赖

确认已有依赖（不应重复添加）：

```xml
<!-- Spring AI OpenAI starter — 已存在 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- Reactor — 已作为 spring-boot-starter-web 传递依赖引入，无需额外添加 -->
```

---

## 前端适配建议

| 事件类型 | 前端行为 |
|---------|---------|
| `tool_done` + `status=polishing` | 展示 "AI 正在整理结果..." loading 态 |
| `content` | 追加渲染 Markdown，支持表格/GFM |
| `done` | 关闭 loading，展示"已完成" |

无需新增事件类型，复用现有 8 种事件类型即可。
