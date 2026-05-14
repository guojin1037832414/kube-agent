# Atlas v3.1 Tool 注册中心设计报告

> **版本**: 3.1.0  
> **日期**: 2026-05-14  
> **作者**: Hermes Agent (Moonshot Kimi K2.6)  
> **技术栈**: Spring Boot 3.4.4 + Spring AI 1.1.6

---

## 一、背景与问题

Atlas v3.1 有：
- **6 个专业 Agent**：Query / Diag / Deploy / RBAC / Storage / Network
- **25+ 个意图**：需要 25+ 个 Tool 实现
- **后端 API 有权限注解**：`@Isolation(IsolationPolicy.SYS_ADMIN_ONLY)`
- **核心诉求**：Agent 必须"感知用户权限"，才能决定哪些 Tool 可用

---

## 二、ToolRegistry 设计

### 2.1 自动扫描 vs 手动注册

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **手动注册** | 精确控制，启动快 | 代码繁琐，易遗漏 | Agent 核心 Tool |
| **自动扫描** | 零配置，防遗漏 | 需约定规范 | 全部 AtlasTool Bean |
| **混合方案（推荐）** | 自动扫描为主 + 手动注册兜底 | — | 本次采用 |

### 2.2 如何结合 Spring Boot？

```
方案：BeanPostProcessor + SmartLifecycle

  ① Spring 初始化每个 Bean 时
    → BeanPostProcessor#postProcessAfterInitialization 拦截
    → 扫描 @AtlasToolMapping 注解
    → 自动注册到 ToolRegistry

  ② 所有 Bean 初始化完成后
    → SmartLifecycle#start 启动校验
    → 检查每个 Agent 是否都有 Tool 覆盖
    → 检查 intents.yml 中的意图是否都有 Tool 实现（交叉校验预留）
```

### 2.3 Tool → Agent 的归属关系管理

**方案：注解驱动（推荐） > 硬编码 > YAML 配置**

```java
@Component
@AtlasToolMapping(
    name        = "node_query",
    agent       = AtlasAgent.QUERY,       // ← Agent 归属
    description = "查询所有节点状态",
    intentId    = "node_query"            // ← 绑定意图
)
public class NodeQueryTool implements AtlasTool { ... }
```

**为什么不用 YAML？**
- YAML 适合声明式配置，不适合 Java Bean 绑定
- 注解与代码同处，修改时一眼可见，避免配置漂移
- 编译期即可报错（例如 Agent 常量拼写错误）

**为什么不用硬编码？**
- 25+ Tool 时硬编码 registry 会膨胀到不可维护
- 注解方式零侵入扩展

**agent 索引加速：**
```
agentIndex: ConcurrentHashMap<String, List<String>>
  "query"   → ["node_query", "gpu_query", ...]
  "deploy"  → ["deploy_create", "deploy_scale", ...]
```

### 2.4 Tool 动态注册/热重载 可行性

| 能力 | 实现方式 | 可行性 |
|------|----------|--------|
| 手动动态注册 | `register(new CustomTool(), ...)` | ✅ 已实现（运行时无重启） |
| 动态卸载 | `unregister("tool_name")` | ✅ 已实现 |
| 重新扫描 | `rescan()` 遍历 ApplicationContext 获取 BeansOfType | ✅ 已实现 |
| AOP 代理兼容 | `AopProxyUtils.ultimateTargetClass()` 提取真实类 | ✅ 已实现 |
| ClassLoader 热加载 | 需配合 Java Agent 或 Spring Boot DevTools | ⚠️ 预留扩展点 |

---

## 三、权限感知机制设计

### 3.1 用户权限从哪里来？

**方案：登录时缓存 + ThreadLocal 传递（推荐）**

```
登录成功
  → 从 kube-manager API 拉取用户角色
  → UserPermissionContext#onLogin(token, username, role, permissions)
  → 存入 ConcurrentHashMap 内存缓存（TTL=30min）

每次请求
  → AuthTokenFilter 提取 Authorization: Bearer <token>
  → 调用 UserPermissionContext#bind(token)
  → 写入 ThreadLocal

Agent / ToolRegistry 读取
  → UserPermissionContext#current() 读取 ThreadLocal
  → 从缓存获取 UserPermission 快照
```

**为什么不用每次请求查询后端？**
- Agent 内部可能多次调用 Tool，每次查后端性能差
- kube-manager 登录接口是独立的，Token 后端可复用

**为什么不用 JWT 自包含？**
- 当前 kube-manager 使用 Session Token，非 JWT
- 兼容现有后端，不做侵入性改动

### 3.2 如何在 ToolRegistry 中标记"管理员专属 Tool"？

**方案：@ToolPermission 注解（对标后端 @Isolation）**

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolPermission {
    Policy value() default Policy.PUBLIC;
    enum Policy { PUBLIC, AUTHENTICATED, ADMIN_ONLY }
}

// 使用示例
@Component
@AtlasToolMapping(name = "user_delete", agent = AtlasAgent.RBAC, ...)
@ToolPermission(Policy.ADMIN_ONLY)
public class UserDeleteTool implements AtlasTool { ... }
```

**与后端 @Isolation 的映射：**

| 后端注解 | ToolPermission | 含义 |
|----------|----------------|------|
| — | PUBLIC | 任何人可用 |
| — | AUTHENTICATED | 需登录 |
| @Isolation(SYS_ADMIN_ONLY) | ADMIN_ONLY | 仅管理员 |

### 3.3 当普通用户命中了 admin 意图时，如何优雅拒绝？

**三层防线设计：**

```
第一层（预防） — ToolRegistry 过滤
  → Agent 调用 toolRegistry.listByAgent(QUERY)
  → ADMIN_ONLY Tool 被过滤，LLM 的 System Prompt 看不到这些 Tool
  → LLM 生成意图时自然不会选择 admin 操作

第二层（拦截） — Agent 权限预检
  → 如果 LLM 强行选择了 admin Tool
  → Agent.executeTool() 调用 toolRegistry.isVisible(toolName)
  → 非管理员 → gracefulDeny() 返回友好提示

第三层（兜底） — Orchestrator 意图校验
  → 路由出 intentId 后
  → 调用 toolRegistry.canExecuteIntent(intentId)
  → 返回 false → 直接优雅拒绝，不进入 Agent
```

**gracefulDeny 返回格式：**
```json
{
  "success": false,
  "error": "权限不足",
  "message": "当前账户无法执行意图 'user_delete'。该操作需要管理员权限。",
  "deniedIntent": "user_delete",
  "currentRole": "user",
  "suggestion": "请联系管理员升级账户权限，或使用其他查询类功能。"
}
```

---

## 四、参考 Spring AI Alibaba Graph API

### 4.1 Graph API 核心思想

```
State（状态上下文）
  → Node（业务节点 = Tool 调用）
  → Edge（流转条件 = 意图分类 / 仲裁）
  → Graph（编排图 = ReAct 循环）
```

### 4.2 Atlas 映射方案

| Graph 概念 | Atlas 实现 |
|-----------|-----------|
| State | `Map<String, Object> params + IntentResult 意图 + UserPermission 权限` |
| Node | `ToolMetadata`（含 AtlasTool instance） |
| Edge | IntentRouter（L1~L4 分类 + 仲裁） |
| Graph | `ReActEngine`（Thought → Action → Observation 循环） |

### 4.3 Agent 如何调用归属的 Tool 集合？

```java
// ① Agent 获取自己的可见 Tool（已按权限过滤）
List<ToolMetadata> myTools = toolRegistry.listByAgent(getAgentType());

// ② 构造 System Prompt 注入 LLM
String systemPrompt = buildSystemPrompt(myTools);
// 输出示例:
// 你是 Atlas QueryAgent。
// 可用工具列表：
//   - node_query: 查询所有节点状态
//   - gpu_query: 查询GPU使用情况
//   ...（admin 工具被过滤，不会出现在 Prompt 中）

// ③ LLM 选择 Tool → Agent 执行
String selectedTool = llm.call(systemPrompt, userQuery);  // "node_query"
Map<String, Object> result = executeTool(selectedTool, params);

// ④ 结果反馈给 LLM 继续推理（ReAct 循环）
```

---

## 五、新增文件清单

| 路径 | 说明 |
|------|------|
| `com.atlas.tool.annotation.AtlasToolMapping` | Tool 映射注解（name/agent/intentId） |
| `com.atlas.tool.annotation.ToolPermission` | 权限注解（PUBLIC/AUTHENTICATED/ADMIN_ONLY） |
| `com.atlas.agent.AtlasAgent` | 6 大 Agent 枚举常量 |
| `com.atlas.agent.AtlasAgentBase` | Agent 抽象基类（带权限二次校验） |
| `com.atlas.agent.QueryAgent` | 查询 Agent 实现示例 |
| `com.atlas.auth.UserPermissionContext` | 用户权限缓存 + ThreadLocal |
| `com.atlas.auth.AuthTokenFilter` | Spring MVC Token 过滤器 |
| `com.atlas.auth.PermissionTokenFilter` | WebFlux 兼容版过滤器（Future-proof） |
| `com.atlas.tool.core.ToolRegistry` | **核心注册中心**（全部实现） |

---

## 六、ToolRegistry.java 代码结构总结

```
ToolRegistry implements BeanPostProcessor, ApplicationContextAware, SmartLifecycle
├── 核心数据结构
│   ├── registry: Map<String, ToolMetadata>        ← 全局 Tool 注册表
│   ├── agentIndex: Map<String, List<String>>      ← Agent 预分组索引
│   └── lock: ReentrantReadWriteLock               ← 并发安全
│
├── 自动扫描 (BeanPostProcessor)
│   └── postProcessAfterInitialization() → 提取注解 → registerInternal()
│
├── 启动校验 (SmartLifecycle)
│   └── start() → 检查每个 Agent 覆盖情况
│
├── 公共查询 API（带权限过滤）
│   ├── resolve(name) → ToolMetadata
│   ├── listByAgent(agent) → List<ToolMetadata>
│   ├── listAllVisible() → List<ToolMetadata>
│   ├── canExecuteIntent(intentId) → boolean
│   └── isVisible(toolName) → boolean
│
├── 动态管理
│   ├── register(tool, name, agent, intentId, policy)
│   ├── unregister(name)
│   └── rescan()
│
├── 权限判断
│   └── isAccessible(meta) → 根据 Policy 与用户角色判断
│
└── 内部数据结构
    ├── ToolMetadata record (name, agent, description, intentId, policy, instance)
    ├── RegistryListener 接口（预留扩展点）
    ├── ToolNotFoundException
    └── PermissionDeniedException
```

---

## 七、后续建议（P2 阶段）

1. **Agent 具体实现**：补齐 DiagAgent / DeployAgent / RBACAgent / StorageAgent / NetworkAgent
6. **BaseTool 兼容性**：`BaseTool` 已实现 `AtlasTool` 接口，并且通过注解 `@Tool` + `@ToolParam` 与 Spring AI Function Calling 深度集成；`ToolRegistry` 的 `BeanPostProcessor` 会自动扫描 `AtlasTool`（含其子类/实现类）进行注册
2. **注册表管理端点**：暴露 `/actuator/tools` 查看注册状态
3. **权限缓存 TTL**：增加定时清理过期 Token 的线程
4. **Tool 调用监控**：RegisteredListener 接入 Micrometer 指标
5. **ReActEngine 实现**：基于 ToolRegistry + Spring AI ChatClient 构建多步推理
