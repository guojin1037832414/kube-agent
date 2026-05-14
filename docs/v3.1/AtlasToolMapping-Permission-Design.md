# Atlas v3.1 P1.4 — @AtlasToolMapping 权限字段设计方案

> 角色：后端权限注解设计专家  
> 日期：2026-05-14  
> 版本：v3.1-P1.4

---

## 一、现状诊断

当前系统中 **23 个 Tool 全部对所有人可见**，`ToolRegistry.isVisible()` 仅检查 Tool 是否已注册：

```java
public boolean isVisible(String toolName) {
    return toolByName.containsKey(toolName); // ← 权限真空
}
```

但项目已具备权限感知的基础设施：
- `UserPermissionContext` — ThreadLocal 绑定 Token，提供 `isAdmin()` 方法
- `PermissionDeniedException` — 越权异常类
- `@ToolPermission` — 已定义的权限注解（**但没有任何 Tool 在使用**）

---

## 二、方案对比：侵入式 vs 非侵入式

| 维度 | 方案A：侵入式（在 `@AtlasToolMapping` 中加字段） | 方案B：非侵入式（使用已有 `@ToolPermission` 独立注解） |
|---|---|---|
| **改动范围** | 需改注解定义、注册中心、23个Tool | 不改注解，只改注册中心和Tool标注 |
| **语义清晰度** | ✅ name/agent/permission 在一处，直观 | ⚠️ 两个注解分离，需看两处 |
| **职责分离** | ⚠️ 映射信息 + 权限信息混合 | ✅ 符合 Spring `@RequestMapping` + `@PreAuthorize` 范式 |
| **未来扩展性** | 修改注解 = 重新编译所有调用方 | 新增 `@ToolPermission` 策略不影响现有Tool |
| **与 RBAC 后端对齐** | 需自定义 role 列表映射逻辑 | 已有 `Policy` 枚举可扩展为对接后端角色码 |
| **当前项目状态** | 零代码成本（注解还未使用） | ✅ **已有 `@ToolPermission` 注解，可直接启用** |
| **LLM 系统提示词** | 一张元数据表即含权限，方便给 LLM 过滤提示 | 注册中心需合并两个注解信息 |

### 结论与推荐

**推荐方案B（非侵入式）作为首选实施路径**，理由：
1. 项目中 `@ToolPermission` 注解已存在、已设计 `Policy` 枚举，只是未被任何 Tool 标注
2. 权限策略本质上与映射策略是**正交维度**，分离有利于后续 RBAC 后端（如 `@Isolation` 体系）的无缝对接
3. 如果后续需要更细粒度的 roles 列表（如 `["ops", "admin"]`），方案B 更容易扩展为 `@ToolPermission(roles = {"ops"})`

**方案A可作为降级备选**（如果团队坚持"一个注解管所有"），具体见第6节。

---

## 三、详细设计（以方案B为首选）

### 3.1 升级 `@ToolPermission`（扩展 roles 支持）

文件：`src/main/java/com/atlas/tool/annotation/ToolPermission.java`

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolPermission {

    /**
     * 权限策略，默认 PUBLIC。
     */
    Policy value() default Policy.PUBLIC;

    /**
     * 显式角色列表（可选，覆盖 value 的粗粒度判断）。
     * 例: roles = {"sys_admin", "ops_admin"}
     */
    String[] roles() default {};

    enum Policy {
        /** 公共 — 所有用户（含匿名）均可调用 */
        PUBLIC,
        /** 需登录 — 仅认证用户可调用，不限制角色 */
        AUTHENTICATED,
        /** 管理员专属 — 仅 sys_admin / admin 可调用 */
        ADMIN_ONLY
    }
}
```

**为什么这样设计？**
- `value()` 保留三种粗粒度策略，覆盖 90% 场景（PUBLIC / AUTHENTICATED / ADMIN_ONLY）
- `roles()` 提供细粒度扩展，后续对接 RBAC 后端时可直接传入角色码数组
- 注解的默认值 `Policy.PUBLIC` 保证**未标注 `@ToolPermission` 的 Tool 向后兼容**（即默认公开）

---

### 3.2 `ToolMetadata` 扩展权限信息

文件：`src/main/java/com/atlas/tool/core/ToolRegistry.java`

```java
// 内部类：ToolMetadata — 增加权限字段
public static class ToolMetadata {
    private final String name;
    private final String description;
    private final String intentId;
    private final String agent;
    private final AtlasTool instance;

    // ── 新增：权限字段 ──
    private final ToolPermission.Policy permissionPolicy;
    private final Set<String> requiredRoles;
    private final boolean adminOnly;

    public ToolMetadata(String name, String description, String intentId,
                        String agent, AtlasTool instance,
                        ToolPermission.Policy permissionPolicy,
                        Set<String> requiredRoles,
                        boolean adminOnly) {
        this.name = name;
        this.description = description;
        this.intentId = intentId;
        this.agent = agent;
        this.instance = instance;
        this.permissionPolicy = permissionPolicy;
        this.requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
        this.adminOnly = adminOnly;
    }

    // ── Getters ──
    public String name() { return name; }
    public String description() { return description; }
    public String intentId() { return intentId; }
    public String agent() { return agent; }
    public AtlasTool instance() { return instance; }

    // 新增 getters
    public ToolPermission.Policy permissionPolicy() { return permissionPolicy; }
    public Set<String> requiredRoles() { return requiredRoles; }
    public boolean isAdminOnly() { return adminOnly; }

    /**
     * 判断当前 Tool 是否对指定用户可见。
     */
    public boolean isVisibleTo(UserPermissionContext.UserPermission user) {
        switch (permissionPolicy) {
            case PUBLIC:
                return true;
            case AUTHENTICATED:
                return user != null; // 只要登录即可
            case ADMIN_ONLY:
                return user != null && user.isAdmin();
            default:
                return false;
        }
    }
}
```

---

### 3.3 `ToolRegistry` 注册与权限检查改造

#### 步骤1：`init()` 方法中提取 `@ToolPermission`

```java
@PostConstruct
public void init() {
    for (BaseTool tool : tools) {
        AtlasToolMapping mapping = tool.getClass().getAnnotation(AtlasToolMapping.class);
        ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);

        String name = mapping != null && !mapping.name().isBlank()
            ? mapping.name() : tool.getToolName();
        String agent = mapping != null && !mapping.agent().isBlank()
            ? mapping.agent() : "query";
        String intentId = mapping != null && !mapping.intentId().isBlank()
            ? mapping.intentId() : tool.getToolName();
        String description = mapping != null && !mapping.description().isBlank()
            ? mapping.description() : tool.getDescription();

        // ── 新增：解析权限策略 ──
        ToolPermission.Policy policy = (perm != null) ? perm.value() : ToolPermission.Policy.PUBLIC;
        Set<String> requiredRoles = (perm != null && perm.roles().length > 0)
            ? Set.of(perm.roles()) : Set.of();
        boolean adminOnly = (policy == ToolPermission.Policy.ADMIN_ONLY);

        toolByName.put(name, tool);
        if (!intentId.isBlank()) {
            toolByIntentId.put(intentId, tool);
        }

        // 修改：传入权限字段
        ToolMetadata meta = new ToolMetadata(
            name, description, intentId, agent, tool,
            policy, requiredRoles, adminOnly
        );
        agentIndex.computeIfAbsent(agent, k -> new ArrayList<>()).add(meta);

        // 名称冲突检测逻辑保持原样...
    }

    log.info("[ToolRegistry] 已注册 {} 个Tool, {} 个Agent分组", toolByName.size(), agentIndex.size());
}
```

#### 步骤2：`isVisible()` 改为用户感知

**原代码：**
```java
public boolean isVisible(String toolName) {
    return toolByName.containsKey(toolName);
}
```

**改为：**
```java
/**
 * 判断指定 Tool 对当前请求用户是否可见。
 * 权限上下文通过 ThreadLocal（UserPermissionContext）自动获取。
 */
public boolean isVisible(String toolName) {
    BaseTool tool = toolByName.get(toolName);
    if (tool == null) return false;

    ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
    ToolPermission.Policy policy = (perm != null) ? perm.value() : ToolPermission.Policy.PUBLIC;

    // 公开 = 无条件可见
    if (policy == ToolPermission.Policy.PUBLIC) return true;

    // 获取当前用户权限（ThreadLocal）
    Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();

    if (policy == ToolPermission.Policy.AUTHENTICATED) {
        return userOpt.isPresent();
    }

    if (policy == ToolPermission.Policy.ADMIN_ONLY) {
        return userOpt.map(UserPermissionContext.UserPermission::isAdmin).orElse(false);
    }

    return false;
}
```

> **注意**：`ToolRegistry` 需要注入 `UserPermissionContext`：
> ```java
> private final UserPermissionContext userPermissionContext;
> public ToolRegistry(List<BaseTool> tools, UserPermissionContext userPermissionContext) {
>     this.tools = tools != null ? tools : List.of();
>     this.userPermissionContext = userPermissionContext;
> }
> ```

#### 步骤3：`listByAgent()` 增加权限过滤

```java
/**
 * 按 Agent 类型列出当前用户**可见**的 ToolMetadata。
 */
public List<ToolMetadata> listByAgent(String agentCode) {
    List<ToolMetadata> all = agentIndex.getOrDefault(agentCode, List.of());
    Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();

    return all.stream()
        .filter(meta -> meta.isVisibleTo(userOpt.orElse(null)))
        .toList();
}
```

**这里关键的架构意义**：`AtlasAgentBase.getAvailableTools()` 返回的列表已经**按用户权限过滤**，因此 LLM 系统提示词中不会包含越权 Tool，从源头上减少 LLM 选择越权工具的概率。

#### 步骤4：`resolve()` 增加权限预检

```java
public ToolMetadata resolve(String toolName) {
    BaseTool tool = toolByName.get(toolName);
    if (tool == null) {
        throw new IllegalArgumentException("Tool '" + toolName + "' 未注册");
    }

    // 权限预检：越权时抛出 PermissionDeniedException
    if (!isVisible(toolName)) {
        AtlasToolMapping mapping = tool.getClass().getAnnotation(AtlasToolMapping.class);
        ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
        String required = (perm != null) ? perm.value().name() : "PUBLIC";

        Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();
        String currentRole = userOpt.map(UserPermissionContext.UserPermission::role).orElse("anonymous");

        throw new PermissionDeniedException(
            "无权调用 Tool '" + toolName + "'，需要权限: " + required,
            toolName, required, currentRole
        );
    }

    // 重建 ToolMetadata（也可从缓存读取，性能优化点）
    AtlasToolMapping mapping = tool.getClass().getAnnotation(AtlasToolMapping.class);
    ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
    String name = mapping != null && !mapping.name().isBlank() ? mapping.name() : tool.getToolName();
    String agent = mapping != null && !mapping.agent().isBlank() ? mapping.agent() : "query";
    String intentId = mapping != null && !mapping.intentId().isBlank() ? mapping.intentId() : name;
    String desc = mapping != null && !mapping.description().isBlank() ? mapping.description() : tool.getDescription();

    ToolPermission.Policy policy = (perm != null) ? perm.value() : ToolPermission.Policy.PUBLIC;
    Set<String> roles = (perm != null && perm.roles().length > 0) ? Set.of(perm.roles()) : Set.of();
    boolean adminOnly = (policy == ToolPermission.Policy.ADMIN_ONLY);

    return new ToolMetadata(name, desc, intentId, agent, tool, policy, roles, adminOnly);
}
```

---

### 3.4 `AtlasAgentBase` 适配

`AtlasAgentBase.executeTool()` 中已有权限检查：
```java
if (!toolRegistry.isVisible(toolName)) {
    return gracefulDeny(toolName, "Tool未注册或不可见");
}
```

只需更新返回值语义区分"未注册"vs"无权"：
```java
public Map<String, Object> executeTool(String toolName, Map<String, Object> params) {
    // ① 检查Tool是否存在
    if (!toolRegistry.findByName(toolName).isPresent()) {
        log.warn("[{}] Tool '{}' 未注册", getAgentName(), toolName);
        return gracefulDeny(toolName, "Tool未注册");
    }

    // ② 检查权限（用户对该Tool是否可见）
    if (!toolRegistry.isVisible(toolName)) {
        log.warn("[{}] 用户越权尝试调用 Tool '{}'", getAgentName(), toolName);
        return gracefulDeny(toolName, "权限不足：需要管理员权限");
    }

    // ③ resolve 并执行
    ToolMetadata meta = toolRegistry.resolve(toolName);
    log.info("[{}] 执行 Tool: {} (intent={}, policy={})",
        getAgentName(), toolName, meta.intentId(), meta.permissionPolicy());
    return meta.instance().execute(params);
}
```

---

### 3.5 `AtlasOrchestrator` 中的 `ChatRequest` 增强（可选但推荐）

当前 `ChatRequest` 只有 `userId` 没有角色信息。如果前端能提供 role 则可以减少一次后端查询：

```java
// 向后兼容的扩展（如果前端暂时不传 role，降级为从 UserPermissionContext 查）
public record ChatRequest(
    String conversationId,
    String userQuery,
    String userId,
    String role        // ← 新增（optional）
) {}
```

如果 `role` 为空，注册中心仍然通过 `ThreadLocal` 从 `UserPermissionContext` 查询。如果传入，可以在 Filter 中提前 `bind` 模拟登录态。

---

## 四、23个 Tool 权限分级建议

按 Tool 操作的危险性和常识性 RBAC 规则分级：

| Tool名称 | Agent | 推断操作类型 | 建议权限策略 | 标注示例 |
|---|---|---|---|---|
| `cluster_overview` | query | 读 | `PUBLIC` | 默认即可 |
| `deploy_create_instance` | deploy | 写（创建） | `AUTHENTICATED` | 需登录 |
| `deploy_delete` | deploy | 写（删除）⚠️ | `ADMIN_ONLY` | **危险操作** |
| `deploy_restart` | deploy | 写（重启） | `ADMIN_ONLY` | 影响业务 |
| `deploy_scale` | deploy | 写（扩缩容） | `AUTHENTICATED` | 需登录 |
| `diagnose_pod` | diag | 读（诊断） | `AUTHENTICATED` | 含敏感日志信息 |
| `distributed_create` | deploy | 写（创建） | `AUTHENTICATED` | 需登录 |
| `gpu_query` | query | 读 | `PUBLIC` | 资源查询 |
| `image_query` | query | 读 | `PUBLIC` | 资源查询 |
| `ingress_query` | network | 读 | `PUBLIC` | 网络查询 |
| `log_query` | diag | 读（日志） | `AUTHENTICATED` | 可能含敏感日志 |
| `network_query` | network | 读 | `PUBLIC` | 资源查询 |
| `nim_create` | deploy | 写（创建） | `AUTHENTICATED` | 需登录 |
| `node_detail` | query | 读 | `PUBLIC` | 节点详情 |
| `node_query` | query | 读 | `PUBLIC` | 资源查询 |
| `resource_monitor` | query | 读 | `PUBLIC` | 监控数据 |
| `role_query` | rbac | 读（角色） | `AUTHENTICATED` | 普通用户可查看角色 |
| `storage_create` | storage | 写（创建） | `AUTHENTICATED` | 需登录 |
| `storage_delete` | storage | 写（删除）⚠️ | `ADMIN_ONLY` | **数据丢失风险** |
| `storage_query` | storage | 读 | `PUBLIC` | 资源查询 |
| `user_create` | rbac | 写（创建）⚠️ | `ADMIN_ONLY` | **RBAC 管理** |
| `user_delete` | rbac | 写（删除）⚠️ | `ADMIN_ONLY` | **RBAC 管理** |
| `user_query` | rbac | 读 | `AUTHENTICATED` | 普通用户可查看 |

### 统计

| 策略 | 数量 | Tool |
|---|---|---|
| `PUBLIC` | 10 | cluster_overview, gpu_query, image_query, ingress_query, network_query, node_detail, node_query, resource_monitor, storage_query |
| `AUTHENTICATED` | 8 | deploy_create_instance, deploy_scale, diagnose_pod, distributed_create, log_query, nim_create, role_query, storage_create, user_query |
| `ADMIN_ONLY` | 5 | **deploy_delete, deploy_restart, storage_delete, user_create, user_delete** |

---

## 五、具体代码修改清单（可直接编码）

### 修改点1：升级 `ToolPermission.java`
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolPermission {
    Policy value() default Policy.PUBLIC;
    String[] roles() default {};

    enum Policy { PUBLIC, AUTHENTICATED, ADMIN_ONLY }
}
```

### 修改点2：修改 `ToolRegistry.java`

- 构造函数注入 `UserPermissionContext`
- `init()` 中解析 `@ToolPermission`
- `isVisible(String toolName)` 接入用户权限判断
- `listByAgent(String agentCode)` 增加权限过滤流
- `resolve(String toolName)` 越权抛异常
- `ToolMetadata` 内部类增加 `permissionPolicy`、`requiredRoles`、`adminOnly` 三个字段

### 修改点3：给 5 个 ADMIN_ONLY Tool 标注注解

以 `DeployDeleteTool.java` 为例：
```java
@Component
@AtlasToolMapping(name = "deploy_delete", agent = "deploy", ...)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)  // ← 新增
public class DeployDeleteTool extends BaseTool { ... }
```

同法标注：
- `StorageDeleteTool`
- `UserCreateTool`
- `UserDeleteTool`
- `DeployRestartTool`

### 修改点4：给 8 个 AUTHENTICATED Tool 标注注解（可选，因为默认即 PUBLIC）

标注价值：**显式声明意图**，方便团队审查：
```java
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
```

---

## 六、备选方案：侵入式 `@AtlasToolMapping` 内部加字段

如果不接受独立注解，可在 `@AtlasToolMapping` 中新增字段：

```java
public @interface AtlasToolMapping {
    String name();
    String agent();
    String description() default "";
    String intentId() default "";

    // ── 新增 ──
    /** 是否需要管理员权限，默认 false（向后兼容） */
    boolean needAdmin() default false;

    /** 允许的角色列表，空数组表示按 needAdmin 判断 */
    String[] roles() default {};
}
```

**优点**：
- 一个注解搞定所有元数据，简洁直观
- 修改成本在单个文件

**缺点**：
- 注解职责不单一（映射 vs 权限混为一谈）
- 未来需要接入 `@Isolation` 等 RBAC 体系时，必须再次改动注解
- Tool 的"身份"与"权限策略"耦合，不利于不同环境（测试/预发/生产）配置不同权限策略

---

## 七、实施路线图

| 阶段 | 动作 | 文件 |
|---|---|---|
| Step 1 | 升级 `ToolPermission` 注解，增加 `roles()` 数组 | `annotation/ToolPermission.java` |
| Step 2 | 修改 `ToolRegistry` 注册、查询、权限检查逻辑 | `core/ToolRegistry.java` |
| Step 3 | 给 5 个危险/管理 Tool 加 `@ToolPermission(ADMIN_ONLY)` | `impl/*DeleteTool.java`, `*CreateTool.java` |
| Step 4 | 给 8 个需要登录的 Tool 加 `@ToolPermission(AUTHENTICATED)` | `impl/DeployCreateTool.java`, etc. |
| Step 5 | 确认 `AtlasAgentBase` 中的 `isVisible` 分支语义正确 | `agent/AtlasAgentBase.java` |
| Step 6 | 集成测试：普通用户请求 → 看不到 admin Tool；管理员请求 → 全部可见 | `*Test.java` |
| Step 7 | 扩展 `ChatRequest` 传 role（可选） | `orchestrator/AtlasOrchestrator.java` |

---

## 八、兼容性说明

- **未标注 `@ToolPermission` 的 Tool**：默认 `PUBLIC`，行为与原系统完全一致（向后兼容）
- **`isVisible()` 的旧调用方**：签名不变（`String toolName`），内部行为增强为按当前用户判断
- **`ToolMetadata` 的字段新增**：不影响现有 getter 使用（新增字段只追加，不修改已有接口）

---

*报告完成。本方案可直接用于编码实施。*
