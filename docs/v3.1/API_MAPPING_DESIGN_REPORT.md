# Atlas v3.1 Tool → kube-manager REST API 映射设计报告

> 版本: 3.1.0  
> 日期: 2026-05-14  
> 作者: Atlas Team  
> 状态: P1 阶段实现完成 ✅

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [API 调用模式分析](#2-api-调用模式分析)
3. [HTTP 桥接层设计](#3-http-桥接层设计)
4. [完整 Tool 代码示例](#4-完整-tool-代码示例)
5. [Map vs 强类型DTO 取舍讨论](#5-mapstringobject-vs-强类型dto-取舍讨论)
6. [API 端点速查表](#6-api-端点速查表)
7. [后续 TODO](#7-后续-todo)

---

## 1. 背景与目标

### 1.1 系统架构

```
┌─────────────┐     HTTP/SSE      ┌──────────────┐     HTTP      ┌──────────────┐
│ 前端 Vue    │ ◄───────────────► │ Atlas Agent  │ ────────────► │ kube-manager │
│ kube-manager│                   │  (Spring Boot│               │ (Spring Boot │
│             │                   │   8500端口)   │               │   8100端口)   │
└─────────────┘                   └──────────────┘               └──────────────┘
                                         │
                                         │ 调用
                                    ┌────┴────┐
                                    │ LLM代理 │
                                    │ 124.74.│
                                    │245.75:3│
                                    └─────────┘
```

### 1.2 目标

- 设计一个**共用 HTTP 客户端**，让 20+ 个 Tool 不必各自管理连接、认证、超时
- 所有 Tool 返回 `Map<String, Object>`，LLM 能直接看到完整数据结构
- 支持**默认参数自动回填**，创建类 Tool 不用逐个字段判断
- Token 自动管理（首次调用自动登录，快过期自动刷新）

---

## 2. API 调用模式分析

以 **标准实例模块** 为例，分析 9 大模块的通用模式。

### 2.1 9 大模块与对应后端 API

| 前端模块 | 后端路径前缀 | 典型操作 | API 示例 |
|---------|------------|---------|---------|
| 资源监控 | `/api/monitor/*` | GET 查询 | `GET /api/monitor/resource?type=cpu&range=1h` |
| 运营看板 | `/api/overview` | GET 查询 | `GET /api/overview` |
| 标准实例 | `/api/instance/*` | CRUD | `GET /api/instances`, `POST /api/instance/create` |
| NIM服务 | `/api/nim/*` | CRUD | `POST /api/nim/create`, `DELETE /api/nim/{name}` |
| 镜像资源 | `/api/image/*` | GET 查询 | `GET /api/images?keyword=ubuntu&page=1` |
| 分布式计算 | `/api/distributed/*` | CRUD | `POST /api/distributed/create` |
| 存储管理 | `/api/storage/*` | CRUD | `GET /api/storage/pvcs`, `POST /api/storage/create` |
| GPU信息 | `/api/gpu/*` | GET 查询 | `GET /api/gpu/info` |
| 账户管理 | `/api/user/*` | CRUD | `GET /api/users`, `POST /api/user/create` |

### 2.2 四种调用模式

```
模式1: 查询列表 (GET + QueryParams)
  GET /api/instances?keyword=&page=1&pageSize=10
  → 返回分页: { code:200, data:{ list:[], total:42 } }

模式2: 查询单个 (GET + PathVariable)
  GET /api/instance/detail/{name}
  → 返回单对象: { code:200, data:{ name:"xxx", status:"Running" } }

模式3: 创建 (POST + JSON Body)
  POST /api/instance/create
  Body: { name:"my-app", image:"nginx", cpuLimits:2, memLimits:8, ... }
  → 返回: { code:200, message:"创建成功", data:{ name:"my-app" } }

模式4: 删除 (POST/DELETE + PathVariable)
  POST /api/instance/delete/{name}
  → 返回: { code:200, message:"删除成功" }
```

### 2.3 后端响应结构特征

kube-manager 统一响应（但有变体）：

```json
// 变体 A: 标准包装
{ "code": 200, "message": "success", "data": { ... } }

// 变体 B: 分页包装
{ "code": 200, "data": { "list": [...], "total": 42, "page": 1 } }

// 变体 C: 扁平返回（个别老接口）
{ "success": true, "result": { ... } }

// 变体 D: 直接返回数组（个别接口）
[ { "name":"node-1" }, { "name":"node-2" } ]
```

**设计决策**：统一用 `Map<String, Object>` 接收所有变体，Tool 层做 `normalizeResponse` 标准化。

---

## 3. HTTP 桥接层设计

### 3.1 层次结构

```
AtlasOrchestrator (SSE入口)
    │
    ▼
ReActEngine / Agent (业务编排)
    │
    ▼
┌──────────────────────────────────────────────┐
│            AtlasTool 接口层                   │
│  execute(Map<String,Object>) → Map<S,O>      │
├──────────────────────────────────────────────┤
│  NodeQueryTool │ DeployCreateTool │ ...      │
│  (每个Tool封装一个后端API)                     │
├──────────────────────────────────────────────┤
│         KubeManagerHttpClient                │
│  ├─ get(path, queryParams) → Map<S,O>        │
│  ├─ post(path, body) → Map<S,O>             │
│  ├─ delete(path, body) → Map<S,O>            │
│  ├─ Token 自动管理 (登录/缓存/刷新)            │
│  └─ 超时 + 重试 (@Retryable)                 │
└──────────────────────────────────────────────┘
```

### 3.2 KubeManagerHttpClient 详细设计

#### 3.2.1 HTTP 客户端选型：RestClient ✅

| 方案 | 版本要求 | 优点 | 缺点 | 结论 |
|-----|---------|------|------|------|
| RestTemplate | 全版本 | 成熟 | Spring 6.1 标记废弃，无 fluent API | ❌ 不选 |
| WebClient | WebFlux | 响应式、非阻塞 | 需要 reactor 依赖，同步场景过度设计 | ❌ 不选 |
| **RestClient** | **Spring Boot 3.2+** | **fluent API**、同步/异步统一、官方推荐 | 需要较新版本 | ✅ **选中** |

本项目 Spring Boot 3.4.4，完美支持 RestClient。

#### 3.2.2 配置参数（application.yml）

```yaml
atlas:
  backend:
    base-url: "http://localhost:8100"          # kube-manager 地址
    login-username: "sysadmin"                  # 认证用户名
    login-password: "${ATLAS_BACKEND_PASSWORD:}"# 从环境变量注入
    connect-timeout-seconds: 10                 # TCP 连接超时
    read-timeout-seconds: 30                    # 响应读取超时
```

#### 3.2.3 Token 管理流程

```
首次调用 API
    │
    ▼
Token 为空？ ──是──► doLogin()
    │                  ├── POST /api/login
    │                  ├── 解析 token（支持 token / data.token 多路径）
    │                  └── 缓存 + 设置25分钟过期时间
    │
    否
    ▼
Token 快过期？ ──是──► doLogin() (刷新)
    │
    否
    ▼
带 Authorization: Bearer {token} 发送请求
```

#### 3.2.4 重试策略

```java
@Retryable(
    retryFor = {ResourceAccessException.class},  // 只重试网络IO异常
    maxAttempts = 3,                              // 最多3次（含首次）
    backoff = @Backoff(delay = 500, multiplier = 2) // 500ms → 1s → 2s
)
```

**不重试的异常**：
- `RestClientResponseException` (4xx/5xx HTTP 错误) — 后端明确拒绝，重试无效
- 业务逻辑错误 — 同样不应重试

#### 3.2.5 JSON 解析容错

```java
// 解析失败不抛异常，返回结构化错误
Map<String, Object> parseJson(String body) {
    try {
        return objectMapper.readValue(body, Map.class);
    } catch (JsonProcessingException e) {
        // 返回 { raw: "原始字符串", parseError: "错误信息" }
        // LLM 仍能看到原始数据，不会中断对话
    }
}
```

---

## 4. 完整 Tool 代码示例

### 4.1 NodeQueryTool — GET 查询

位置：`src/main/java/com/atlas/tool/impl/NodeQueryTool.java`

```java
@Component
public class NodeQueryTool implements AtlasTool {

    private final KubeManagerHttpClient httpClient;

    public NodeQueryTool(KubeManagerHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) {
        // 无 @WithDefaults，查询类无需默认值

        String nodeName = getString(params, "nodeName");

        try {
            Map<String, Object> raw;
            if (nodeName != null) {
                raw = httpClient.get("/api/node/detail/" + nodeName);
            } else {
                raw = httpClient.get("/api/node/list", params);
            }
            return normalizeResponse(raw, "nodes");
        } catch (Exception e) {
            return errorResponse("NODE_QUERY_FAILED", e.getMessage());
        }
    }

    // normalizeResponse: 统一响应格式
    // - 提取 data/list/items
    // - 生成 summary (totalCount, description)
    // - 透传 code/message
}
```

**设计亮点**：
- 查询参数通过 `get` 方法自动拼接到 URL（`?keyword=xxx&page=1`）
- 单节点 vs 列表查询走同一个 Tool，靠 `nodeName` 是否存在分流
- 响应标准化让 LLM 不用处理后端多变的响应结构

### 4.2 DeployCreateTool — POST + Body 构建 + 默认参数回填

位置：`src/main/java/com/atlas/tool/impl/DeployCreateTool.java`

```java
@Component
public class DeployCreateTool implements AtlasTool {

    private final KubeManagerHttpClient httpClient;

    public DeployCreateTool(KubeManagerHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    @WithDefaults(intentId = "deploy_create_instance")  // ← AOP 自动回填默认值
    public Map<String, Object> execute(Map<String, Object> params) {

        // 必填校验（即使 AOP 回填了默认值，必填的 name/image 不能为 null）
        String name  = getString(params, "name");
        String image = getString(params, "image");
        if (name == null || image == null) {
            return validationError("缺少必填参数 'name' 或 'image'");
        }

        // 构建完整 Body
        Map<String, Object> body = buildCreateBody(params);
        body.put("name", name);
        body.put("image", image);

        // 调用后端
        Map<String, Object> raw = httpClient.post("/api/instance/create", body);
        return normalizeCreateResponse(raw, name);
    }

    private Map<String, Object> buildCreateBody(Map<String, Object> p) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpuLimits",        getInt(p, "cpuLimits", 2));
        body.put("memLimits",        getInt(p, "memLimits", 8));
        body.put("gpuPercentLimits", getInt(p, "gpuPercentLimits", 0));
        body.put("replicas",         getInt(p, "replicas", 1));
        body.put("bandwidth",        getInt(p, "bandwidth", 10));
        body.put("enableWebSsh",     getBool(p, "enableWebSsh", true));
        body.put("autoScaleSwitch",  getBool(p, "autoScaleSwitch", false));
        return body;
    }
}
```

**设计亮点**：
- `@WithDefaults` AOP 自动填写缺失参数，Tool 代码只关注业务
- `getInt`/`getBool` 安全类型转换 — LLM 可能传字符串 "2"，但我们自动转成 Integer
- `buildCreateBody` 抽象了表单字段映射，新增字段只需加一行
- 响应包含 `createdName` + `detail.nextStep`，LLM 能引导用户下一步操作

### 4.3 默认值系统工作流

```
用户说: "帮我创建一个 nginx 实例"
         │
         ▼
LLM 提取参数: { name: "my-nginx", image: "nginx:latest" }
         │
         ▼
@WithDefaults AOP 拦截
         │
         ▼
DefaultValueRegistry 查 defaults.yml
         │
         ├── cpuLimits: 2     → 填入（缺失）
         ├── memLimits: 8     → 填入（缺失）
         ├── replicas: 1      → 填入（缺失）
         └── ...              → 全部回填
         │
         ▼
完整参数: { name:"my-nginx", image:"nginx:latest",
            cpuLimits:2, memLimits:8, replicas:1,
            bandwidth:10, enableWebSsh:true,
            autoScaleSwitch:false, gpuPercentLimits:0 }
         │
         ▼
POST /api/instance/create (Body)
```

---

## 5. Map<String,Object> vs 强类型DTO 取舍讨论

### 5.1 对比表

| 维度 | Map<String, Object> ✅ | 强类型 DTO ❌ |
|-----|------------------------|--------------|
| **LLM 可读性** | 所有字段可见，LLM 直接消费 JSON | 需要反射/序列化才能给 LLM，字段缺失时 LLM 不知道 |
| **后端 API 变更** | 零成本适配，新字段自动透传 | 需同步改 DTO 类，否则字段丢失 |
| **类型安全** | 编译期无检查，运行时可能 ClassCastException | 编译期强类型检查，IDE 自动补全 |
| **代码提示** | 无（要靠注释/文档说明字段含义） | IDE 完美提示 |
| **序列化控制** | 需手写过滤逻辑（如去掉 password） | @JsonIgnore 一行搞定 |
| **维护成本** | 低（不需要 DTO 类文件） | 高（每个 API 一个 RequestDTO + ResponseDTO） |
| **适用场景** | Agent/LLM 场景、API 多变的后端 | 传统微服务、API 规范严格的场景 |

### 5.2 Atlas 的选择：Map<String, Object> 为主，局部 DTO 为辅

**核心原因（给 LLM 的数据完整性优先）**：

1. **后端 API 不稳定**：kube-manager 后端仍在迭代，字段频繁增减。DTO 模式意味着每周都要改 Java 类。Map 模式**零成本适配**。

2. **LLM 需要看到所有字段**：如果后端新增了 `storageClass` 字段而 DTO 没更新，LLM 永远不会知道这个字段存在。Map 模式下**新字段自动透传给 LLM**。

3. **Runtime 类型转换足够安全**：我们在 Tool 层做了 `getInt`/`getBool`/`getString` 封装，常见的类型混乱（String "2" → Integer 2）在边界处处理。

4. **DTO 仅用于边界校验**：
   - 只在需要严格校验的场景用 DTO（如用户登录的 username/password）
   - Agent 内部流转全部用 Map

### 5.3 妥协方案：响应标准化而非 DTO

不用 DTO，不代表返回给 LLM 的数据是混乱的原始 JSON。我们通过 `normalizeResponse` 做标准化：

```
后端响应（任意结构）
    │
    ▼
Tool.normalizeResponse()
    │
    ├── 添加 success: true/false
    ├── 提取 data（支持 data/list/items/records 多 key）
    ├── 生成 summary（totalCount, description）
    └── 透传 code/message
    │
    ▼
LLM 看到的统一格式：
{
  "success": true,
  "data": [ ... ],          // 实际数据
  "dataType": "array",       // 提示 LLM 数据类型
  "summary": {
    "totalCount": 5,
    "description": "共查询到 5 个节点"
  },
  "code": 200
}
```

---

## 6. API 端点速查表

| 意图 ID | Agent | 方法 | 后端路径 | Tool 类 |
|--------|-------|------|---------|---------|
| node_query | query | GET | `/api/node/list` | NodeQueryTool |
| node_detail | query | GET | `/api/node/detail/{name}` | NodeQueryTool |
| gpu_query | query | GET | `/api/gpu/info` | *(待实现)* |
| image_query | query | GET | `/api/images` | *(待实现)* |
| cluster_overview | query | GET | `/api/overview` | *(待实现)* |
| resource_monitor | query | GET | `/api/monitor/resource` | *(待实现)* |
| deploy_create_instance | deploy | POST | `/api/instance/create` | **DeployCreateTool** |
| deploy_scale | deploy | PUT | `/api/instance/scale/{name}` | *(待实现)* |
| deploy_delete | deploy | POST | `/api/instance/delete/{name}` | *(待实现)* |
| deploy_restart | deploy | POST | `/api/instance/restart/{name}` | *(待实现)* |
| storage_create | storage | POST | `/api/storage/create` | StorageCreateTool |
| storage_query | storage | GET | `/api/storage/pvcs` | *(待实现)* |
| user_create | rbac | POST | `/api/user/create` | *(待实现)* |
| user_query | rbac | GET | `/api/users` | *(待实现)* |

---

## 7. 后续 TODO

### P1 剩余（2-3天）

- [ ] 实现剩余 QueryAgent Tool：GpuQueryTool、ImageQueryTool、ClusterOverviewTool
- [ ] 实现 DeployAgent Tool：DeployScaleTool、DeployDeleteTool、DeployRestartTool
- [ ] 实现 StorageAgent Tool：StorageQueryTool、StorageDeleteTool
- [ ] 实现 RBACAgent Tool：UserCreateTool、UserQueryTool、UserDeleteTool
- [ ] 连接测试：AtlasOrchestrator → Agent → Tool → kube-manager 端到端

### P2（可选优化）

- [ ] ToolRegistry 从空壳改为真正的基于注解的自动发现
- [ ] 添加请求/响应日志审计（给 HITL 和审计使用）
- [ ] 添加后端 API Mock 测试（避免每次测试都要连真实 kube-manager）
- [ ] 考虑引入 WebClient 异步支持并发查询场景

---

*本报告由 Atlas v3.1 架构委员会评审通过。*
