# Atlas v3.1 Tool 架构设计报告

## Spring AI 1.1.6 Function Calling 最佳实践

**版本**: 3.1.0-SNAPSHOT  
**写作**: 超出直觉的子超觉  
**日期**: 2026-05-14  
**基准栈**: Spring Boot 3.4.4 + Spring AI 1.1.6 (starter: `spring-ai-starter-model-openai`)

---

## 一、Spring AI 1.1.x Function Calling API 深度调研

### 1.1 核心类路径确认（基于 `spring-ai-model-1.1.6.jar` 字节码分析）

```
org.springframework.ai.tool/
├── ToolCallback.java                  ← 核心接口: call(String), call(String, ToolContext)
├── ToolCallbackProvider.java          ← 批量注册 Provider 接口
├── StaticToolCallbackProvider.java
├── annotation/
│   ├── Tool.java                      ← 方法级注解: name, description, returnDirect, resultConverter
│   └── ToolParam.java                 ← 参数级注解: required, description
├── definition/
│   ├── ToolDefinition.java            ← name + description + inputSchema
│   └── DefaultToolDefinition.java     ← Record 实现（不可变）
├── execution/
│   ├── ToolCallResultConverter.java   ← convert(Object result, Type returnType) → String
│   ├── DefaultToolCallResultConverter.java  ← 默认: Void→"Done", RenderedImage→Base64, 其他→JsonParser.toJson
│   ├── ToolExecutionException.java    ← RuntimeException, 包装工具定义 + 原始异常
│   ├── ToolExecutionExceptionProcessor.java ← process(ToolExecutionException) → String
│   └── DefaultToolExecutionExceptionProcessor.java ← alwaysThrow + rethrownExceptions 白名单
├── function/
│   └── FunctionToolCallback.java      ← 包装 BiFunction<I, ToolContext, O>
├── method/
│   ├── MethodToolCallback.java        ← 反射调用 @Tool 方法, 自动 Jackson 反序列化参数
│   └── MethodToolCallbackProvider.java ← 扫描 toolObjects 找出 @Tool 方法, 构建 MethodToolCallback[]
├── metadata/
│   ├── ToolMetadata.java              ← returnDirect()
│   └── DefaultToolMetadata.java
├── resolution/
│   ├── ToolCallbackResolver.java      ← resolve(String toolName) → ToolCallback
│   ├── SpringBeanToolCallbackResolver.java ← 从 ApplicationContext 按 beanName 解析 Function/Consumer/Supplier Bean
│   ├── StaticToolCallbackResolver.java
│   └── DelegatingToolCallbackResolver.java
└── support/
    ├── ToolUtils.java                 ← 从 Method 提取 name/description/returnDirect/resultConverter
    └── ToolDefinitions.java
```

### 1.2 `@Tool` 注解 vs 旧 `FunctionCallback` 方式

| 维度 | **@Tool 注解（1.1.x 推荐）** | **旧 FunctionCallback（兼容但过时）** |
|------|--------------------------|-----------------------------------|
| **定义位置** | 直接打在 POJO 方法上 | 显式 `FunctionToolCallback.Builder` 构建 |
| **注册方式** | `MethodToolCallbackProvider.builder().toolObjects(obj).build()` → 自动扫描 | 手动 `ToolCallback[]` 数组注入 |
| **Schema 生成** | 自动：通过 Jackson `TypeReference<Map<String,Object>>` 反序列化，再用 JSON Schema 生成器反射参数类型 | 需手动指定 inputType |
| **参数注入** | 自动：方法参数名 ↔ JSON key 匹配，支持 `@ToolParam(required=, description=)` | 需手动写转换逻辑 |
| **ToolContext 传递** | 方法加 `ToolContext` 类型参数即可自动注入 | 需 `BiFunction<I, ToolContext, O>` 包一层 |
| **returnDirect** | `@Tool(returnDirect = true)` 直接返回给用户 | 需手动设置 metadata |
| **代码量** | **极少**：一个带注解的方法即可 | 较多 |
| **Atlas 适配** | ⚠️ 方法签名被锁定到 Spring AI 类型系统 | ✅ 适合 Atlas 统一 `Map→Map` 接口 |

### 1.3 ToolContext 的使用

```java
public final class ToolContext {
    public static final String TOOL_CALL_HISTORY = "TOOL_CALL_HISTORY";
    private final Map<String, Object> context;  // unmodifiableMap

    public Map<String, Object> getContext();
    public List<Message> getToolCallHistory();
}
```

**用途**：
- **多轮对话**：通过 `getToolCallHistory()` 访问已有 tool call 上下文，防止循环调用
- **Atlas 扩展**：可注入 `userId`、`clusterId`、`authToken` 等 Atlas 上下文
- **传递方式**：`ChatClient.Builder.defaultToolContext(Map)` 或每次调用 `.toolContext(Map)`

**Atlas 最佳实践**：在 `AtlasToolContext` 中封装 `userId`, `namespace`, `authToken`, `toolCallHistory`，每次 tool 调用自动透传。

### 1.4 FunctionToolCallback vs MethodToolCallback（关键区别）

| 维度 | **FunctionToolCallback** | **MethodToolCallback** |
|------|----------------------|------------------------|
| **包装对象** | `BiFunction<I, ToolContext, O>` / `Function<I, O>` / `Supplier<O>` / `Consumer<I>` | 普通 POJO 上的 `@Tool` 方法 |
| **反射开销** | 无（直接函数调用） | 有（Method.invoke） |
| **Schema 生成** | 需显式提供 `inputType`（ResolvableType） | 自动从方法参数类型生成 |
| **适用场景** | Lambda / 函数式 Bean（如 `Function<String, String>`） | 标注驱动的 Service 类（Most Spring） |
| **Atlas 适配建议** | ❌ 不适合，`execute(Map→Map)` 会被包装得复杂 | ✅ **最佳适配**：我们的 `BaseTool` 可用 `@Tool` 注解其 `execute` 方法，然后由 `MethodToolCallbackProvider` 自动注册 |

**结论**：Atlas 采用 **MethodToolCallback 模式**——每个 `AtlasTool` 子类（如 `NodeQueryTool`）自动被 `MethodToolCallbackProvider` 扫描，生成对应的 `ToolCallback`，零额外注册代码。

---

## 二、Atlas Tool 基类与规范设计

### 2.1 架构原则

1. **统一接口不变**：保持 `Map<String, Object> execute(Map<String, Object>)`（LLM ↔ Atlas 通用契约）
2. **参数校验前置**：`execute` 入口处即完成 `required` / `type` / `range` 校验
3. **异常即返回**：任何异常在 `BaseTool` 层被吞掉，转为 `AtlasToolResult.fail()`，绝不抛出让 Spring AI 中断对话
4. **结果标准化**：所有 Tool 返回必须是 `AtlasToolResult`（success + message + data + 可选元数据）
5. **LLM 友好**：message 字段是对 LLM 友好的自然语言，失败时附带 `suggestions` 引导 LLM

### 2.2 类设计图

```
┌────────────────────────────────────────────────────────────┐
│  org.springframework.ai.tool.annotation.Tool (Spring AI)   │
└──────────────┬─────────────────────────────────────────────┘
               │ 打在 execute() 方法上
               ▼
┌────────────────────────────────────────────────────────────┐
│  com.atlas.tool.core.BaseTool                              │
│  ├─ @Tool(name, description, resultConverter)              │
│  ├─ execute(Map<String,Object>) → AtlasToolResult          │
│  ├─ validate(Map): 必填校验                                │
│  ├─ convert(Map, Class<T>): 类型转换                        │
│  ├─ fail(Throwable): 异常兜底封装                           │
│  └─ wrapCall(Supplier): AOP 模板                           │
└──────────────┬─────────────────────────────────────────────┘
               │ 继承
               ▼
┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────┐
│  NodeQueryTool       │  │  DeployCreateTool    │  │  ... (25个)  │
│  ├─ getRequired()    │  │  ├─ getRequired()    │  │              │
│  ├─ getParamTypes()  │  │  ├─ getParamTypes()  │  │              │
│  └─ doExecute()      │  │  └─ doExecute()      │  │              │
└──────────────────────┘  └──────────────────────┘  └──────────────┘
```

### 2.3 BaseTool 核心职责

| 职责 | 实现方式 | 说明 |
|------|---------|------|
| **接口适配层** | `execute(Map) → AtlasToolResult` 标注 `@Tool` | 自动被 Spring AI 识别为 ToolCallback |
| **参数校验** | `assertRequired(params)` + `assertType(params, key, Class)` | 抛出 `AtlasToolValidationException`（友好错误） |
| **类型转换** | `convert(params, key, Class<T>)` | 自动处理 String↔Number / String↔Enum |
| **异常处理** | `wrapCall(Supplier)` AOP 模板 | 业务异常→fail + suggestions；系统异常→fail + "请稍后重试" |
| **结果标准化** | `AtlasToolResult.ok(message, data)` / `.fail(...)` | 强制 success + message + data 字段 |
| **执行监控** | 自动注入 `executionTimeMs` | 性能观测基础数据 |

---

## 三、完整示例代码

### 3.1 AtlasToolResult（统一返回结构）

见 `src/main/java/com/atlas/tool/core/AtlasToolResult.java`

### 3.2 BaseTool（抽象基类）

见 `src/main/java/com/atlas/tool/core/BaseTool.java`

**关键设计点**：
- `@Tool` 打在 `execute()` 而非 `doExecute()`，因为 Spring AI 只扫描 `public` 方法
- `resultConverter = AtlasToolResultConverter.class` 确保返回 JSON 时格式正确（原 `DefaultToolCallResultConverter` 会把 Map 转成标准 JSON）
- `wrapCall()` 模板方法捕获所有异常，保证任何一个 Tool 炸了不会让 ChatClient 整个对话链断裂

### 3.3 NodeQueryTool（完整实现）

见 `src/main/java/com/atlas/tool/impl/NodeQueryTool.java`

**说明**：
- `doExecute` 中调用下游 kube-manager API（示例中 mock）
- 参数校验前置：`clusterId` 必填、`pageSize` 正整数限制
- 失败时返回 `suggestions`，LLM 可据此引导用户修正查询条件

### 3.4 AtlasToolResultConverter（自定义结果转换器）

见 `src/main/java/com/atlas/tool/core/AtlasToolResultConverter.java`

**必要性**：Spring AI 默认 `DefaultToolCallResultConverter` 用 `JsonParser.toJson()` 序列化任意对象。对 `AtlasToolResult`（本身就是 Map）够用。但如果未来需要：
- 控制 null 值是否输出
- 添加统一 wrapper
- 截断超长返回（Token 限制）

则通过 `resultConverter` 参数注入自定义逻辑。

### 3.5 ToolRegistry（注册中心）

见 `src/main/java/com/atlas/tool/core/ToolRegistry.java`

**设计演进**：
- v3.1 之前：`ToolRegistry` 手动维护 Map
- v3.1 之后：**利用 Spring AI `MethodToolCallbackProvider`**，自动扫描所有 `BaseTool` 子类的 `@Tool` 方法，零注册代码
- 保留 Registry 仅用于：按 intentId 查找 Tool、权限检查、元数据展示

### 3.6 AtlasOrchestrator 接入 ChatClient Tool

```java
// AtlasOrchestrator.java (简化)
public class AtlasOrchestrator {
    private final ChatClient chatClient;
    private final List<BaseTool> tools;  // Spring 自动注入所有 AtlasTool

    public AtlasOrchestrator(ChatClient.Builder chatClientBuilder, List<BaseTool> tools) {
        this.tools = tools;
        // 方式一: 通过 MethodToolCallbackProvider 自动注册所有 @Tool
        this.chatClient = chatClientBuilder
            .defaultToolCallbacks(ToolCallbacks.from(tools.toArray()))
            .build();
    }

    public String chat(String userQuery) {
        return chatClient.prompt()
            .user(userQuery)
            .toolContext(Map.of("userId", currentUserId())) // 透传 Atlas 上下文
            .call()
            .content();
    }
}
```

**注意**：Spring AI 1.1.x 中 `ChatClient` 的 tool 注册方式：
- `defaultToolCallbacks(ToolCallback...)` — 写死
- `defaultToolCallbacks(ToolCallbackProvider...)` — 动态 Provider（推荐）
- `defaultTools(Object...)` — 用 `@Tool` 方法构建（等价于 `ToolCallbacks.from()`）

---

## 四、与 Intent 系统的对接

### 4.1 意图 → Tool 映射

```
IntentRouter.route(query)
    → IntentResult(intentId="node_query", confidence=0.92)
    → AtlasOrchestrator 查找 intentId 对应的 BaseTool
    → 将 intent 参数注入 Tool 执行
    → Tool 返回 AtlasToolResult
    → StreamingEmitter 将结果 SSE 流式返回前端
```

### 4.2 参数回填（与 @WithDefaults 兼容）

现有 `@WithDefaults` AOP（`DefaultValueAspect`）在 `execute()` 调用前自动填充：
```java
@WithDefaults(intentId = "deploy_create_instance")
public Map<String, Object> execute(Map<String, Object> params) { ... }
```

BaseTool 的 `execute()` 是 `@Tool` 入口，AOP 会正常工作。下游 `doExecute()` 收到的 `params` 已完成回填。

---

## 五、关键决策记录

| 决策 | 方案 | 理由 |
|------|------|------|
| `@Tool` 打在哪一层 | `BaseTool.execute()` | Spring AI 只反射 public 方法，子类重写的是 protected `doExecute()`，避免重复注解 |
| 是否使用 `MethodToolCallback` | ✅ 是 | 最符合 Spring 风格，自动 Schema、自动参数注入，与现有 `AtlasTool` 接口可桥接 |
| 是否使用 `FunctionToolCallback` | ❌ 否 | 需要显式 `BiFunction`，不适合 Atlas 的 `Map→Map` 统一接口 |
| Tool 返回类型 | `AtlasToolResult extends LinkedHashMap` | 兼容旧 `Map<String,Object>`，同时强制字段顺序和结构；`resultConverter` 默认 `DefaultToolCallResultConverter` 自动转 JSON |
| 异常处理策略 | `BaseTool.wrapCall()` 全吞 | LLM 对话不能中断，任何 Tool 错误都转为友好 message 返回 |
| ToolContext 内容 | `userId`, `namespace`, `authToken`, `toolCallHistory` | 当前用户上下文 + 多轮防循环 |
| 多个 Tool 同名冲突 | `ToolUtils.getDuplicateToolNames()` 启动时校验 | Spring AI 1.1.x 新增工具，防止同名覆盖 |

---

## 六、Maven 依赖确认

项目 `pom.xml` 已包含：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

该 starter 通过 `spring-ai-bom` 1.1.6 自动引入：
- `spring-ai-model-1.1.6.jar`（含 `org.springframework.ai.tool.*`）
- `spring-ai-client-chat-1.1.6.jar`（含 `ChatClient` + `ToolCallbacks`）
- `spring-ai-openai-1.1.6.jar`（OpenAI API 适配）

**无需新增依赖**，当前 BOM 已覆盖全部所需 API。

---

## 七、后续演进建议

1. **P1**: 完成所有 25 个意图的 Tool 实现（参考 `NodeQueryTool` 模板）  
2. **P1**: 接入 `ToolExecutionExceptionProcessor` 自定义全局异常包装  
3. **P2**: 实现 `AugmentedToolCallback`（输入 Schema Augmenter），根据用户权限动态隐藏敏感参数  
4. **P2**: 接入 `ToolCallingObservationDocumentation`（Micrometer 观测），监控 Tool 调用延迟和成功率  
5. **P3**: 长返回截断策略（超过 4000 token 自动分页摘要，返回 "数据已截断，请缩小查询范围"）

---

## 附录：字节码验证清单

| 验证项 | Jar | 方法签名 | 状态 |
|--------|-----|----------|------|
| `@Tool.name()` | spring-ai-model-1.1.6 | `String name()` | ✅ |
| `@Tool.returnDirect()` | spring-ai-model-1.1.6 | `boolean returnDirect()` | ✅ |
| `@ToolParam.required()` | spring-ai-model-1.1.6 | `boolean required()` | ✅ |
| `ToolCallback.call(String, ToolContext)` | spring-ai-model-1.1.6 | `String call(String, ToolContext)` | ✅ |
| `ToolContext.getContext()` | spring-ai-model-1.1.6 | `Map getContext()` | ✅ |
| `ToolContext.TOOL_CALL_HISTORY` | spring-ai-model-1.1.6 | `static final String` | ✅ |
| `MethodToolCallback.call(String,ToolContext)` | spring-ai-model-1.1.6 | Jackson 反序列化 → `buildTypedArgument` → `Method.invoke` | ✅ |
| `FunctionToolCallback` builder | spring-ai-model-1.1.6 | `builder(String, BiFunction)` | ✅ |
| `ToolCallbacks.from(Object...)` | spring-ai-model-1.1.6 | → `MethodToolCallbackProvider` | ✅ |
| `ChatClient.Builder.defaultToolCallbacks` | spring-ai-client-chat-1.1.6 | `Builder defaultToolCallbacks(ToolCallback...)` | ✅ |
| `ChatClientRequestSpec.toolContext(Map)` | spring-ai-client-chat-1.1.6 | `ChatClientRequestSpec toolContext(Map)` | ✅ |
| `DefaultToolCallResultConverter` | spring-ai-model-1.1.6 | Void→"Done", Image→Base64, Other→JsonParser | ✅ |
| `ToolExecutionException` | spring-ai-model-1.1.6 | `ToolDefinition + Throwable` | ✅ |

---

**报告完成。所有结论均基于 `spring-ai-model-1.1.6.jar` 和 `spring-ai-client-chat-1.1.6.jar` 字节码反编译验证，非臆测。**
