# kube-agent Tool 开发规范 v3.1

> 基于 BaseTool + @AtlasToolMapping + @ToolPermission + defaults.yml 的 Tool 开发标准指南。

---

## 一、核心组件速查

| 组件 | 职责 | 位置 |
|------|------|------|
| `BaseTool` | 抽象基类：校验 → 类型转换 → `doExecute` → 异常兜底 | `com.atlas.tool.core.BaseTool` |
| `@AtlasToolMapping` | 声明 Tool 的 name / agent / intentId / description | `com.atlas.tool.annotation.AtlasToolMapping` |
| `@ToolPermission` | 声明权限策略（PUBLIC / AUTHENTICATED / ADMIN_ONLY） | `com.atlas.tool.annotation.ToolPermission` |
| `@WithDefaults` | 标记需要默认值回填的 execute 方法 | `com.atlas.tool.annotation.WithDefaults` |
| `AtlasToolResult` | 统一响应结构：success / message / data / errorCode / suggestions | `com.atlas.tool.core.AtlasToolResult` |
| `ToolRegistry` | 启动时扫描所有 Tool Bean，建立索引，权限预检 | `com.atlas.tool.core.ToolRegistry` |
| `DefaultValueRegistry` | 加载 `defaults.yml`，为 `@WithDefaults` 提供回填数据 | `com.atlas.tool.defaults.DefaultValueRegistry` |
| `DefaultValueAspect` | AOP 拦截器：在 `execute()` 前自动填充缺失参数 | `com.atlas.tool.core.DefaultValueAspect` |
| `KubeManagerHttpClient` | 封装对 kube-manager 后端 API 的 HTTP 调用 | `com.atlas.http.KubeManagerHttpClient` |

---

## 二、新增一个 Tool 的标准步骤

### Step 1：创建 Tool 类（继承 BaseTool）

- 包路径：`com.atlas.tool.impl`
- 命名规范：`{资源}{操作}Tool.java`，例如 `PodQueryTool.java`、`DeployCreateTool.java`
- 必须加 `@Component`，让 Spring 自动注入

### Step 2：添加类级注解

| 注解 | 必填 | 说明 |
|------|------|------|
| `@AtlasToolMapping` | **是** | `name` 全局唯一，`agent` 对应 Agent 分组，`intentId` 绑定意图，`description` 给 LLM 看 |
| `@ToolPermission` | **否** | 不写默认 `PUBLIC`。创建/删除/修改类操作建议 `AUTHENTICATED` 或 `ADMIN_ONLY` |
| `@WithDefaults` | **否** | 仅当此 Tool 需要 `defaults.yml` 回填默认值时才加（常见于 Create 类） |

### Step 3：实现构造方法 + 抽象方法

- 构造方法调用 `super(toolName, description)`，名称应与 `@AtlasToolMapping#name` 保持一致
- 实现 `getRequiredParams()`：返回 LLM 必须提供的参数名集合；空集合表示无必填项
- 实现 `doExecute(Map)`：写业务逻辑，返回 `AtlasToolResult.ok()` 或 `fail()`
- 可选重写 `getParamTypes()`：对参数做自动类型转换（如 `String→Integer`）

### Step 4：如需默认值回填 → 在 defaults.yml 注册

- 在 `src/main/resources/defaults.yml` 的 `defaults:` 节点下追加：

```yaml
  {intentId}:
    paramName1: defaultValue1
    paramName2: defaultValue2
```

- 并在 Tool 类或 `execute()` 方法上加 `@WithDefaults(intentId = "xxx")`
- **原则**：默认只填充 `null` / 缺失的 key；设置 `override = true` 可强制覆盖已有值

### Step 5：响应标准化

- **成功**：`AtlasToolResult.ok("人类可读摘要", data)`
- **失败**：`AtlasToolResult.fail("错误信息", "ERROR_CODE", List.of("修复建议1", "修复建议2"))`
- 直接在 `doExecute` 内部使用 `try-catch` 捕获 API 异常并返回 `fail()` 也是允许的（BaseTool 的 `wrapCall` 会兜底）

---

## 三、注解详解

### 3.1 @AtlasToolMapping

```java
@AtlasToolMapping(
    name        = "node_query",          // 全局唯一 Tool 名称（英文+下划线）
    agent       = "query",               // 所属 Agent：query / diag / deploy / rbac / storage / network
    intentId    = "node_query",          // 绑定意图 ID（对应 intents.yml）
    description = "查询 Kubernetes 集群所有节点的状态、资源使用情况"   // LLM 可见的描述
)
```

- `name` 同时作为 LLM function calling 中的工具标识，必须唯一
- `intentId` 支持多意图绑定（逗号分隔），若为空字符串表示不绑定特定意图

### 3.2 @ToolPermission

```java
@ToolPermission(ToolPermission.Policy.PUBLIC)          // 任何人可用（默认）
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)   // 需登录，不区分角色
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)      // 仅 sys_admin / admin
```

- 可选 `roles = {"sys_admin", "ops_admin"}` 做更细粒度角色控制
- `ToolRegistry` 在构建 System Prompt 时会自动按权限过滤，越权 Tool 对 LLM 不可见

### 3.3 @WithDefaults（默认值回填）

```java
@WithDefaults(
    intentId = "deploy_create_instance",   // 对应 defaults.yml 中的顶级 key
    override = false                        // 是否覆盖已有值，默认 false
)
```

- 可标注在 **类** 或 **execute 方法** 上
- AOP 拦截器 `DefaultValueAspect` 会在 `execute()` 执行前自动回填第一个 `Map<String, Object>` 参数
- `DefaultValueRegistry` 启动时加载 `defaults.yml`，运行时按 `intentId` 查找

---

## 四、响应标准化（normalizeResponse）

虽然项目中没有一个显式的 `normalizeResponse()` 方法，但**标准化由 `AtlasToolResult` 工厂方法统一实现**：

| 场景 | 调用方式 |
|------|----------|
| 成功有数据 | `AtlasToolResult.ok("摘要", data)` |
| 成功无数据 | `AtlasToolResult.ok("摘要")` |
| 业务失败（含建议） | `AtlasToolResult.fail("信息", "ERROR_CODE", List.of("建议1"))` |
| 业务失败（简化） | `AtlasToolResult.fail("信息")` / `fail("信息", "建议")` |

**结果会被 `BaseTool#execute` 自动追加元数据：**
- `toolName` — 工具名称
- `executionTimeMs` — 执行耗时

**Spring AI 转换：**
- `AtlasToolResultConverter` 作为 `ToolCallResultConverter`，将 Map 转为 JSON 返回给 LLM

### 标准化返回结构示例

```json
{
  "success": true,
  "message": "集群共有 3 个节点",
  "data": [ { "name": "node-1", "status": "Ready" } ],
  "toolName": "node_query",
  "executionTimeMs": 145
}
```

```json
{
  "success": false,
  "message": "缺少必填参数: name",
  "errorCode": "MISSING_REQUIRED_PARAMS",
  "suggestions": ["请提供以下参数: name"],
  "data": {}
}
```

---

## 五、权限与注册流程

1. **Spring 启动**：所有 `@Component + 继承 BaseTool` 的 Bean 被收集进 `List<BaseTool>`
2. **ToolRegistry#init**：`@PostConstruct` 扫描 `@AtlasToolMapping` 和 `@ToolPermission`，建立索引
3. **System Prompt 构建**：`ToolRegistry#buildSystemPromptForCurrentUser()` 只包含当前用户可见的 Tool
4. **调用时**：`AtlasToolCallback` 反射调用 `BaseTool#execute` → 校验 → AOP 默认值回填 → `doExecute` → `wrapCall` 兜底

---

## 六、代码模板

### 6.1 查询类 Tool 模板（推荐作为最小实现）

```java
package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * {资源}查询 Tool — {一句话描述}。
 *
 * <p>意图映射: {@code intentId = "{intentId}"}</p>
 * <p>API 路径：GET /api/{orgId}/{resource}</p>
 */
@Component
@AtlasToolMapping(
    name        = "{tool_name}",
    agent       = "{agent}",
    intentId    = "{intentId}",
    description = "{给LLM看的描述}"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)   // 查询类通常 PUBLIC
public class {Resource}QueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public {Resource}QueryTool(KubeManagerHttpClient httpClient) {
        super("{tool_name}", "{给LLM看的描述}");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of(); // 查询类通常无必填；如有限定条件则写这里
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/{resource}";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;

            String summary = data instanceof java.util.List
                ? String.format("查询到 %d 条记录", ((java.util.List<?>) data).size())
                : "查询完成";

            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[{tool_name}] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
```

### 6.2 创建类 Tool 模板（含默认值回填）

```java
package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.annotation.WithDefaults;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * {资源}创建 Tool — {一句话描述}。
 *
 * <p>意图映射: {@code intentId = "{intentId}"}</p>
 * <p>API 路径：POST /api/{orgId}/{resource}</p>
 */
@Component
@AtlasToolMapping(
    name        = "{tool_name}",
    agent       = "{agent}",
    intentId    = "{intentId}",
    description = "{给LLM看的描述}"
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)   // 创建类建议 AUTHENTICATED 或 ADMIN_ONLY
@WithDefaults(intentId = "{intentId}")                   // 启用默认值回填
public class {Resource}CreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public {Resource}CreateTool(KubeManagerHttpClient httpClient) {
        super("{tool_name}", "{给LLM看的描述}");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "image"); // 根据实际业务调整
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("cpuLimits", Integer.class),
            Map.entry("memLimits", Integer.class),
            Map.entry("replicas", Integer.class),
            Map.entry("enableWebSsh", Boolean.class)
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        // 1. 必要参数二次校验（BaseTool 已做一次，可再做业务层校验）
        String name = getParam(params, "name", "").trim();
        String image = getParam(params, "image", "").trim();
        if (name.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name", "MISSING_NAME",
                List.of("请提供实例名称，例如: my-app"));
        }

        // 2. 取参（含默认值兜底，与 AOP 回填不冲突）
        int cpu = getIntParam(params, "cpuLimits", 2);
        int mem = getIntParam(params, "memLimits", 8);
        boolean ssh = getBoolParam(params, "enableWebSsh", true);

        log.info("[{tool_name}] 创建资源 name={}, image={}, cpu={}, mem={}", name, image, cpu, mem);

        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/{resource}";
            Map<String, Object> body = buildCreateBody(params, name, image, cpu, mem, ssh);
            Map<String, Object> response = httpClient.post(path, body);
            Object data = response.containsKey("result") ? response.get("result") : response;

            String summary = String.format("'%s' 创建任务已提交 (镜像: %s, CPU: %d核, 内存: %dGB)", name, image, cpu, mem);
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[{tool_name}] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("创建失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildCreateBody(Map<String, Object> params,
                                                 String name, String image,
                                                 int cpu, int mem, boolean ssh) {
        Map<String, Object> body = filterNullParams(params);
        body.put("name", name);
        body.put("image", image);
        body.put("cpuLimits", cpu);
        body.put("memLimits", mem);
        body.put("enableWebSsh", ssh);
        return body;
    }

    private Map<String, Object> filterNullParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        params.forEach((key, value) -> { if (value != null) body.put(key, value); });
        return body;
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }

    // ── 参数安全读取辅助 ──
    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private boolean getBoolParam(Map<String, Object> params, String key, boolean defaultVal) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return "true".equalsIgnoreCase(v.toString().trim());
        return defaultVal;
    }
}
```

### 6.3 defaults.yml 追加示例

```yaml
  {intentId}:
    cpuLimits: 2
    memLimits: 8
    replicas: 1
    enableWebSsh: true
```

---

## 七、 checklist（自测清单）

- [ ] 类加了 `@Component`
- [ ] 类加了 `@AtlasToolMapping`，且 `name` 全局唯一
- [ ] `agent` 值在允许范围内（query / diag / deploy / rbac / storage / network）
- [ ] `intentId` 与 `intents.yml`（如有）保持一致
- [ ] 构造方法调用了 `super(name, description)`
- [ ] 实现了 `getRequiredParams()` 和 `doExecute()`
- [ ] `doExecute` 内所有异常被捕获并返回 `AtlasToolResult.fail(...)`
- [ ] 返回值使用 `AtlasToolResult.ok(...)` 或 `fail(...)`，不直接返回裸 Map
- [ ] 如需默认值：类/方法加了 `@WithDefaults`，且 `defaults.yml` 已追加对应节点
- [ ] 权限策略符合预期：查询类 PUBLIC，创建/修改类 AUTHENTICATED，删用户/高危操作 ADMIN_ONLY
- [ ] 编译通过，启动日志显示 `[ToolRegistry] 已注册 ... 个Tool`

---

## 八、常见问题

**Q1：`execute()` 上需要加 `@Tool` 吗？**
> 不需要。kube-agent 使用 `ToolRegistry` 手动构建 `MethodToolCallback` 并注册到 `ChatClient`，`@Tool` 由框架内部处理。

**Q2：默认值没生效？**
> 检查三点：1) `@WithDefaults(intentId)` 是否 IntentId 拼写正确；2) `defaults.yml` 中是否有对应节点；3) AOP 是否被 Spring 扫描到（`DefaultValueAspect` 必须有 `@Aspect + @Component`）。

**Q3：如何看 Tool 是否注册成功？**
> 启动日志会输出 `[ToolRegistry] 已注册 X 个Tool, Y 个Agent分组`。也可访问健康检查端点查看 `toolRegistry.health()`。

**Q4：权限不对，LLM 仍然能看到越权 Tool？**
> `ToolRegistry#buildSystemPromptForCurrentUser()` 已按权限过滤。如果仍然可见，检查 `UserPermissionContext` 是否正确写入当前用户角色。

---

> 文档生成时间：2026-05-15
> 基于 kube-agent v3.1.0-P1.4 代码结构
