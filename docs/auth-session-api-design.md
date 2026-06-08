# kube-agent 登录与会话管理 API 设计方案

> 版本：v3.1-M2
> 作者：Atlas 后端架构组
> 日期：2025-05-19

---

## 一、现状分析

### 1.1 已有能力盘点

| 模块 | 现状 |
|------|------|
| **认证过滤** | `AuthTokenFilter`（MVC）从 `Authorization: Bearer xxx` 提取 Token 写入 `UserPermissionContext.CURRENT_TOKEN`（ThreadLocal） |
| **权限缓存** | `UserPermissionContext` 内置 `ConcurrentHashMap<String, UserPermission>`，支持 `onLogin/onLogout` |
| **异步透传** | `AsyncContextHolder` + `DelegatingExecutor` 已解决 CompletableFuture 线程切换 Token 丢失问题 |
| **HTTP 客户端** | `KubeManagerHttpClient` 已有 `doFallbackLogin()` 方法，会用 sysadmin 账号向 `POST /api/login` 发送 `application/x-www-form-urlencoded` |
| **会话标识** | M5.29-6 起流式运行 ID 使用非敏感 `run-*` / `graph-*`，登录 `ses_*` 只作为 `SessionStore` 索引 |
| **会话状态** | 仅 Caffeine `TimedDecisionCache` 存 HITL 决策，无 conversation 元数据存储 |

### 1.2 缺失清单

1. **前端代理登录**：前端期望 `POST /api/agent/login`（JSON），但 kube-manager 只认 `POST /api/login`（form-urlencoded）
2. **登出接口**：无 `/api/agent/logout` 端点，无法清理 `UserPermissionContext` 缓存
3. **会话 CRUD**：无 conversation 列表、详情、删除接口，前端无法管理历史会话
4. **会话绑定**：历史设计阶段 `X-Session-Id` 只做会话标识；M5.29-4 起它已可通过 `SessionStore` 桥接到 Spring Security `Authentication`；M5.29-5 已完成 conversation metadata owner 迁移；M5.29-6 已完成 Chat/SSE runtime identity 迁移

---

## 二、设计目标

1. **流式接口安全主线化**：`/api/agent/chat/stream`、`/api/agent/chat/graph` 和 `/api/agent/hitl/*` 必须消费服务端可信 runtime identity
2. **最小化状态存储**：尽量避免引入 Redis / DB； conversation 元数据优先内存，可选 Caffeine TTL
3. **Token 链路自闭环**：登录 → kube-manager 返回 JWT → kube-agent 缓存 → ThreadLocal 透传 → Tool 调用，全程无断点
4. **安全合规**：密码不落地日志、Token 带 TTL、登出即失效、幂等保护

---

## 三、核心方案：登录代理 + 内存会话管理

### 3.1 架构总览

```
┌─────────────┐     POST /api/agent/login (JSON)      ┌─────────────────┐
│  前端 (Vue)  │ ────────────────────────────────────▶ │  AuthController │
│  Pinia 会话  │                                      │  (kube-agent)   │
└─────────────┘                                      └────────┬────────┘
       │                                                      │
       │  ① 接收 JSON 用户名/密码/验证码                         │
       │  ② 转换为 form-urlencoded                              │
       │  ③ 代理到 kube-manager POST /api/login                 ▼
       │                                               ┌─────────────────┐
       │                                               │  kube-manager   │
       │  ④ 返回 {token, user, orgId, ...}              │  (localhost:8100)│
       │◀──────────────────────────────────────────────└─────────────────┘
       │
       │  ⑤ kube-agent 写入 UserPermissionContext.onLogin()
       │  ⑥ kube-agent 生成 / 复用 conversationId 返回前端
       │
       ▼
   POST /api/agent/chat/stream
   Header: Authorization: Bearer <token>
   Header: X-Session-Id: <conversationId>
```

> M5.29-4 更新：当前实现返回的是 `ses_*` sessionId，并保存到 `SessionStore`。后续请求若没有 `Authorization: Bearer`，`AuthTokenFilter` 会用 `X-Session-Id` 反查服务端 `SessionData`，再生成 Spring Security `Authentication`。如果请求显式带了 Bearer，则 Bearer 是本次请求身份权威；未知 Bearer 不自动降级到 SessionId。`X-Session-Id` 是服务端会话索引，不是用户或 LLM 可自声明的身份。
>
> M5.29-6 更新：Chat/SSE 运行时不再把 `X-Session-Id`、请求体 `userId` 或 `conversationId` 当作身份事实。`AtlasOrchestrator` 先通过 `AgentPrincipalResolver` 取得当前主体，再从 `SessionStore` 补齐 token/orgId，并用 `ConversationStore.findByUserAndId(...)` 校验 conversation owner。SSE/Graph/HITL 使用新生成的 `run-*` / `graph-*` 作为运行关联 ID，不复用 raw `ses_*` 登录会话 ID。

---

### 3.2 关键决策：会话数据存储策略

#### ❌ 方案 A：纯前端 Pinia（排除）
- 优点：零后端状态
- 缺点：刷新页面或换设备会话丢失；多标签页无法同步；HITL 的 `threadId` 与 `conversationId` 映射在前端容易错乱

#### ❓ 方案 B：`ChatMemory` / Spring AI MessageStore（不推荐）
- Spring AI 1.1.6 的 `ChatMemory` 接口（如 `InMemoryChatMemory`）设计目的是存储 LLM 对话消息轮次，不是业务会话元数据
- conversation 的标题、创建时间、最后活跃时间等不属于 message 范畴，过度耦合

#### ✅ 方案 C：后端内存 Caffeine 缓存（推荐）
- 与现有 `TimedDecisionCache` 技术栈统一（已引入 Caffeine）
- 存 conversation 元数据（id, userId, title, createdAt, lastActiveAt, messageCount）
- TTL = 24h 或最大条数 5000，满足内部平台量级
- 不存消息内容（消息内容留给前端本地 IndexedDB 或未来扩展）
- 若未来需多实例部署，可无缝替换为 Redis，接口不变

---

## 四、AuthController 设计

### 4.1 文件位置

```
src/main/java/com/atlas/controller/AuthController.java
```

### 4.2 API 端点

```java
@RestController
@RequestMapping("/api/agent")
public class AuthController {
```

#### 4.2.1 POST `/api/agent/login` — 代理登录

**请求体（JSON）**：
```json
{
  "username": "zhaotiandi",
  "password": "xxx",
  "organizationId": "100001",      // 可选，不传则后端自动解析
  "loginType": "local_login",      // 可选，默认 local_login
  "captcha": "",                   // 可选，预留验证码
  "uuid": ""                       // 可选，验证码唯一标识
}
```

**后端处理流程**：
1. 参数校验（`@NotBlank username/password`）
2. 将 JSON body 转换为 `application/x-www-form-urlencoded`：
   ```
   username=zhaotiandi&password=xxx&organizationId=100001&loginType=local_login
   ```
3. 通过 `RestClient` 代理请求 `POST http://localhost:8100/api/login`
   - Content-Type: `application/x-www-form-urlencoded`
   - 不携带任何 Token（登录前无 Token）
4. 解析 kube-manager 响应：
   - 成功：`{"result":"jwt...","success":true}` 或 `{"token":"...","user":{...}}`
   - 失败：透传 HTTP status + message
5. kube-agent 侧额外处理：
   - 提取 JWT → 调用 `UserPermissionContext.onLogin(token, username, role, permissions)`
   - 若响应含 `organizationId`，一并缓存到 `UserPermissionContext.UserPermission`
   - 生成 conversationId（`conv-` + UUID 前 8 位）并初始化会话元数据存入 `ConversationStore`
   - 可选择设置 HTTP-only Cookie（见安全章节）

**响应体（200）**：
```json
{
  "success": true,
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "user": {
      "userId": "12345",
      "username": "zhaotiandi",
      "realName": "赵天尊",
      "role": "admin",
      "organizationId": "100001"
    },
    "conversationId": "conv-a1b2c3d4"
  }
}
```

**错误响应（401/400）**：
```json
{
  "success": false,
  "code": 401001,
  "message": "用户名或密码错误"
}
```

#### 4.2.2 POST `/api/agent/logout` — 登出

**请求头**：
```
Authorization: Bearer <token>
X-Session-Id: <conversationId>
```

**后端处理**：
1. 从 Header 提取 Token
2. `UserPermissionContext.onLogout(token)` — 清除权限缓存
3. `ConversationStore.remove(conversationId)` — 清理会话元数据（可选，仅清当前前端会话）
4. （可选）向 kube-manager 发送登出通知（如 kube-manager 有踢 Token 接口）

**响应体**：
```json
{
  "success": true,
  "code": 200,
  "data": null
}
```

#### 4.2.3 GET `/api/agent/me` — 当前登录用户信息

**请求头**：`Authorization: Bearer <token>`

**响应体**：
```json
{
  "success": true,
  "data": {
    "userId": "12345",
    "username": "zhaotiandi",
    "realName": "赵天尊",
    "role": "admin",
    "organizationId": "100001",
    "permissions": ["deploy:create", "user:read"]
  }
}
```

> 直接从 `UserPermissionContext.current()` 读取，零外部调用，性能最优。

---

## 五、ConversationController 设计

### 5.1 文件位置

```
src/main/java/com/atlas/controller/ConversationController.java
```

### 5.2 数据模型

```java
public record Conversation(
    String id,            // X-Session-Id 值，如 conv-a1b2c3d4
    String userId,        // 所属用户
    String title,         // 会话标题（首条用户消息前 20 字或默认"新会话"）
    long createdAt,       // 创建时间戳
    long lastActiveAt,    // 最后活跃时间
    int messageCount,     // 消息轮次（前端上报或流式结束后自增）
    String model,         // 使用的模型（如 moonshotai/kimi-k2.6）
    Map<String, Object> metadata  // 扩展字段
) {}
```

### 5.3 存储层：`ConversationStore`（Caffeine）

```java
@Component
public class ConversationStore {
    private final Cache<String, Conversation> cache = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterAccess(Duration.ofHours(24))
        .build();

    private final Map<String, List<String>> userIndex = new ConcurrentHashMap<>();
    // userId -> List<conversationId>，用于快速查询用户会话列表

    public void save(Conversation c) { ... }
    public Optional<Conversation> findById(String id) { ... }
    public List<Conversation> findByUserId(String userId) { ... }
    public void remove(String id) { ... }
    public void touch(String id) { ... }  // 更新 lastActiveAt
}
```

### 5.4 API 端点

```java
@RestController
@RequestMapping("/api/agent/conversation")
public class ConversationController {
```

#### 5.4.1 GET `/api/agent/conversation/list` — 列表

**请求头**：`Authorization: Bearer <token>`

**查询参数**（可选）：
```
?pageNum=1&pageSize=20&keyword=部署
```

**响应体**：
```json
{
  "success": true,
  "data": {
    "total": 156,
    "pageNum": 1,
    "pageSize": 20,
    "list": [
      {
        "id": "conv-a1b2c3d4",
        "title": "部署 NIM 服务到生产集群",
        "createdAt": 1716192000000,
        "lastActiveAt": 1716195600000,
        "messageCount": 12,
        "model": "moonshotai/kimi-k2.6"
      }
    ]
  }
}
```

#### 5.4.2 GET `/api/agent/conversation/{conversationId}` — 详情

**响应体**：
```json
{
  "success": true,
  "data": {
    "id": "conv-a1b2c3d4",
    "title": "部署 NIM 服务到生产集群",
    "createdAt": 1716192000000,
    "lastActiveAt": 1716195600000,
    "messageCount": 12,
    "model": "moonshotai/kimi-k2.6",
    "metadata": {}
  }
}
```

#### 5.4.3 PUT `/api/agent/conversation/{conversationId}` — 更新标题

**请求体**：
```json
{
  "title": "新的会话标题"
}
```

#### 5.4.4 DELETE `/api/agent/conversation/{conversationId}` — 删除

**权限检查**：仅会话所有者或管理员可删除

#### 5.4.5 POST `/api/agent/conversation` — 主动创建新会话

前端在点击"新建会话"时可调用，预分配 `conversationId`，后续 `chat/stream` 直接携带。

**响应体**：
```json
{
  "success": true,
  "data": {
    "conversationId": "conv-e5f6g7h8",
    "title": "新会话",
    "createdAt": 1716199200000
  }
}
```

---

## 六、X-Session-Id 透传机制

### 6.1 历史问题

历史版本的 `AtlasOrchestrator.streamChat()` 中 `conversationId` 来自 `ChatRequest` body，`userId` 也可能由前端传入：
```java
public record ChatRequest(String conversationId, String userQuery, String userId) {}
```

这种设计对早期 demo 足够，但对顶级 Agent 不够安全：请求体字段属于 caller-supplied input，不能决定资源 owner、运行时用户或审计 actor。

### 6.2 M5.29-6 当前实现

1. **身份来源**：`AgentPrincipalResolver` 解析当前可信主体，缺主体 fail-closed。

2. **会话恢复**：`X-Session-Id` 只用于 `SessionStore.findById(...)`，补齐 server-side token / orgId / role；Bearer 仍保持优先。

3. **Conversation 校验**：请求体 `conversationId` 只是前端选择的业务会话 locator，进入 Graph/ReAct/SafeToolExecutor 前必须校验属于当前 principal。

4. **运行 ID 脱敏**：SSE / Graph / HITL 使用 `run-*` 或 `graph-*`，不复用 raw `ses_*` 登录会话 ID。

5. **HITL 恢复**：confirm / clarify 读取 checkpoint 的 `user_id`，必须等于当前 trusted principal，否则拒绝恢复。

---

## 七、Multi-part Form / 文件上传注意事项

当前 kube-manager `/api/login` 使用 `application/x-www-form-urlencoded`，非 `multipart/form-data`。但如果未来需要支持**头像上传**或**附件上传**到会话：

### 7.1 影响点

| 场景 | 处理方式 |
|------|----------|
| **登录代理** | 仅 `x-www-form-urlencoded`，无需 `MultipartFile`，`RestClient` 直接 `.body(formBody)` 即可 |
| **未来文件上传** | 如需 `multipart/form-data`，Spring Boot 3 使用 `MultipartFile` 接收，再转发到 kube-manager 时构造 `MultiValueMap` + `RestClient` 或 `WebClient` |

### 7.2 登录代理代码示例

```java
private Map<String, Object> proxyLoginToKubeManager(LoginRequest req) {
    String formBody = UriComponentsBuilder.newInstance()
        .queryParam("username", req.username())
        .queryParam("password", req.password())
        .queryParam("organizationId", req.organizationId())
        .queryParam("loginType", req.loginType())
        .build()
        .getQuery();

    return restClient.post()
        .uri("/api/login")
        .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .body(formBody)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
}
```

---

## 八、安全性设计

### 8.1 Token 安全

| 措施 | 实现 |
|------|------|
| **密码不落地** | `LoginRequest` 字段在日志中使用 `@ToString.Exclude` 或 record 自定义 toString |
| **Token 缓存 TTL** | `UserPermissionContext` 中增加 `expireAfterWrite(Duration.ofMinutes(30))`，目前只用裸 Map，建议改为 Caffeine cache |
| **登出失效** | `/logout` 调用 `onLogout()` 立即从内存移除 |
| **传输加密** | 生产环境 HTTPS 终止于网关，kube-agent 内部 HTTP 可接受 |

### 8.2 会话安全

| 措施 | 实现 |
|------|------|
| **conversationId 不可预测** | 使用 `SecureRandom` 或 `UUID` 生成，非自增 ID |
| **跨用户隔离** | `ConversationStore.findByUserId()` 仅返回当前用户会话，每次操作前检查 `conversation.userId.equals(currentUserId)` |
| **X-Session-Id 校验** | AtlasOrchestrator 在 `streamChat` 开头校验该 conversation 是否属于当前用户（如 conversationStore 中存在） |

### 8.3 Cookie vs Header 方案

| 方案 | 优点 | 缺点 |
|------|------|------|
| **纯 Header**（Bearer + X-Session-Id） | 前后端分离清爽、无 CSRF 烦恼、移动端友好 | XSS 需前端妥善保管 Token |
| **HTTP-Only Cookie** | XSS 无法盗 Token | 需处理 CSRF、跨域配置复杂 |

**建议**：保持当前 **纯 Header 方案**，Token 存前端 `localStorage`（Pinia + persistedstate），`X-Session-Id` 同样前端保管。这是 SPA + SSE 场景的最简方案。

---

## 九、对现有代码的改动点

### 9.1 零侵入清单（不改现有文件）
- `AtlasOrchestrator.java`：流式核心逻辑不动
- `HITLController.java`：HITL 逻辑不动
- `AuthTokenFilter.java`：Bearer 提取逻辑不动
- `AsyncContextHolder.java`：Token 透传逻辑不动

### 9.2 轻量修改清单
- `AtlasOrchestrator.ChatRequest`：支持从 `X-Session-Id` header 读取 `conversationId`
- `AtlasOrchestrator.streamChat()`：开头增加 `conversationStore.touch(conversationId)`

### 9.3 新增文件清单

```
controller/
  AuthController.java           # 登录/登出/用户信息
  ConversationController.java   # 会话 CRUD

service/
  AuthService.java              # 登录代理 + Token 解析 + 权限缓存写入（可选，如逻辑简单可直接放 Controller）

store/
  ConversationStore.java        # Caffeine 内存会话存储

dto/
  LoginRequest.java             # username, password, orgId, loginType, captcha
  LoginResponse.java            # token, user, conversationId
  ApiResponse.java              # 统一响应包装 {success, code, message, data}
  ConversationListResponse.java # 分页响应
```

---

## 十、RESTful vs RPC 风格选择

### 10.1 结论：采用 **RESTful 资源风格** + 统一响应包装

本项目其他接口已是 RESTful（`GET /health`, `POST /chat/stream`），新增接口保持一致：

| 操作 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 登录 | `/api/agent/login` | POST | RPC 感最强的操作，保留动词 |
| 登出 | `/api/agent/logout` | POST | 同上 |
| 获取当前用户 | `/api/agent/me` | GET | 资源风格 |
| 会话列表 | `/api/agent/conversation` | GET | 资源集合 |
| 创建会话 | `/api/agent/conversation` | POST | 资源创建 |
| 获取会话 | `/api/agent/conversation/{id}` | GET | 单体资源 |
| 更新会话 | `/api/agent/conversation/{id}` | PUT | 资源更新 |
| 删除会话 | `/api/agent/conversation/{id}` | DELETE | 资源删除 |

所有响应统一包装为：
```java
public record ApiResponse<T>(boolean success, int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, 200, "ok", data); }
    public static <T> ApiResponse<T> err(int code, String message) { return new ApiResponse<>(false, code, message, null); }
}
```

---

## 十一、接口时序图

```
前端              AuthController        KubeManagerHttpClient      kube-manager
 |                       |                        |                     |
 |--POST /api/agent/login--> |                       |                     |
 | {username,pwd}        |                       |                     |
 |                       |--代理 POST /api/login--> |                     |
 |                       | (form-urlencoded)       |                     |
 |                       |                        |--POST /api/login---> |
 |                       |                        | (x-www-form-urlencoded)
 |                       |                        |                     |
 |                       |                        |<---200 {token}------|
 |                       |<---返回 token-----------|                     |
 |                       |                       |                     |
 |                       |--UserPermissionContext.onLogin()               |
 |                       |--ConversationStore.save()                      |
 |                       |                       |                     |
 |<--200 {token,convId}-|                       |                     |
 |                       |                       |                     |
 |                       |                       |                     |
 |--GET /api/agent/conversation/list--> |          |                     |
 | Authorization: Bearer |                       |                     |
 |                       |--从 cache 查用户会话列表  |                     |
 |<--200 {list}---------|                       |                     |
 |                       |                       |                     |
 |--SSE /api/agent/chat/stream--> |             |                     |
 | X-Session-Id: conv-xxx|                       |                     |
 |                       |--conversationStore.touch()                   |
 |                       |--AuthTokenFilter 提取 Token                    |
 |                       |--ThreadLocal 透传                              |
 |                       |--Graph 执行 / Tool 调用                         |
 |                       |                        |--getCurrentToken()   |
 |                       |                        |--HTTP X-Token-------->|
 |                       |                        |                     |
 |<--SSE events---------|                        |                     |
```

---

## 十二、风险与回退方案

| 风险 | 影响 | 回退 |
|------|------|------|
| kube-manager `/api/login` 返回格式变更 | 登录失败 | `AuthService` 中对多种字段名做兼容解析（已有 `KubeManagerHttpClient` 的 `result/token/data` 兼容经验） |
| `ConversationStore` 内存溢出 | OOM | Caffeine `maximumSize(5000)` + `expireAfterAccess(24h)` 已兜底 |
| 多实例部署 | 会话数据实例隔离 | Phase 2 将 `ConversationStore` 中的 `Cache` 替换为 Redis `StringRedisTemplate`，接口不变 |
| XSS 盗 Token | 安全风险 | 保持短 TTL（30分钟）+ `/logout` 即时失效 + HTTPS 全链路 |

---

## 十三、总结：给前端的最小可用 API 集

| # | 端点 | 方法 | 用途 |
|---|------|------|------|
| 1 | `/api/agent/login` | POST | 登录获取 token + conversationId |
| 2 | `/api/agent/logout` | POST | 登出清除服务端缓存 |
| 3 | `/api/agent/me` | GET | 获取当前用户信息 |
| 4 | `/api/agent/conversation` | POST | 新建会话 |
| 5 | `/api/agent/conversation` | GET | 查询会话列表 |
| 6 | `/api/agent/conversation/{id}` | GET | 查询单个会话 |
| 7 | `/api/agent/conversation/{id}` | PUT | 重命名会话 |
| 8 | `/api/agent/conversation/{id}` | DELETE | 删除会话 |

以上就是 kube-agent v3.1 登录与会话管理的完整设计方案。
