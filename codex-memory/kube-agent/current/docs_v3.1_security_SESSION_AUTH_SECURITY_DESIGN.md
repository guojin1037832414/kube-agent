# kube-agent 登录与会话管理 — 安全架构设计方案 v3.1

> 调研范围：`com.atlas.auth.*`、`security/` 目录及关联文件
> 项目：kube-agent (Atlas v3.1.0-SNAPSHOT, Spring Boot 3.4.4)
> 日期：2026-05-19
> 目标：为前端补齐登录与会话管理 API 提供安全设计建议

---

## 2026-06-09 M5.29-6 状态更新

M5.29-6 已把 Chat/SSE/Graph/HITL 执行入口迁移到可信运行时身份主线：

- `/api/agent/chat/stream`、`/api/agent/chat/graph`、`/api/agent/hitl/**` 已进入 Spring Security `.authenticated()`。
- `X-Session-Id` 只作为 `SessionStore` 索引；无 Bearer 时可恢复服务端 `SessionData` 并桥接到 `Authentication`，但不能作为 userId/owner/role/orgId。
- `AtlasOrchestrator` 使用 `AgentPrincipalResolver` 决定当前主体，并从服务端 session 补齐 token/orgId。
- 请求体 `userId` 不再参与运行时身份决策；请求体 `conversationId` 只有通过 `ConversationStore.findByUserAndId(principal, conversationId)` 后才能进入 Graph/ReAct/SafeToolExecutor。
- SSE/Graph/HITL 使用 `run-*` / `graph-*` 作为运行关联 ID，不复用 raw `ses_*` 登录会话 ID，降低 session locator 暴露面。
- HITL confirm/clarify 恢复会读取 checkpoint `user_id` 并要求等于当前 trusted principal，避免其他登录用户凭 `threadId + confirmToken` 恢复别人的执行。

学习结论：`X-Session-Id`、`conversationId`、`threadId` 都是 locator/correlation id。顶级 Agent 的授权事实必须来自服务端可信主体和服务端状态校验，而不是来自 LLM、前端请求体或可猜测/可复制的关联 ID。

## 一、现有安全架构速览

### 已具备的安全组件
| 组件 | 职责 | 状态 |
|------|------|------|
| `AuthTokenFilter` | MVC Filter 提取 `Authorization: Bearer ***` 绑定 ThreadLocal | ✅ 已上线 |
| `UserPermissionContext` | `ThreadLocal` Token + `ConcurrentHashMap` 权限缓存 | ⚠️ 缺 TTL 机制 |
| `AsyncContextHolder` + `DelegatingExecutor` + `TokenPropagatingTaskDecorator` | 异步线程 Token 透传（三层防护） | ✅ 非常完善 |
| `KubeManagerHttpClient` | 透传用户 Token + sysadmin Fallback 登录 | ✅ P1.4 已修复 |
| `TimedDecisionCache` | Caffeine TTL 5min + 幂等性 Token 校验 | ✅ 安全标杆 |
| `PermissionTokenFilter` | WebFlux 风格 Bearer 过滤（Future-proof） | ✅ 保留备用 |

### 已发现的架构文档
- `docs/v3.1/security/P1.4_PERMISSION_SECURITY_REVIEW.md` — 权限+异步安全审查（深度很好）
- `docs/auth-session-api-design.md` — 登录与会话管理 API 设计（非常完整，已覆盖大部分安全点）

---

## 二、核心安全建议（五大调研点）

### 1. 代理登录时如何避免明文密码泄露

**当前状态分析：**
- `KubeManagerHttpClient.doFallbackLogin()` 中密码从 `@Value` 注入（环境变量或配置文件），通过 `URLEncoder.encode()` 编码后发给 kube-manager。
- Future 设计的前端登录通过 `AuthController`，由后端代理到 kube-manager `/api/login`（form-urlencoded），密码从前端传来。

**攻击面：**
1. 日志泄露：`log.debug("[HTTP POST] {} body={}")` 会记录完整 body（含密码）
2. 堆栈泄露：异常堆栈中可能包含 form body
3. 内存残留：`String` 在 JVM 中不可变，密码串长期驻留堆中
4. 传输层：开发环境 HTTP（无 TLS）明文传输

#### 建议方案

| 措施 | 优先级 | 具体做法 |
|------|--------|----------|
| **密码字段脱敏** | P0 | `LoginRequest` 自定义 `toString()` 排除 password；控制器层 `log.info()` 不打印完整 request |
| **RestClient 代理时不打印含 password 的 body** | P0 | 在 `RestClient` 发请求前，用临时变量替换 password 为 `***`，仅对日志脱敏 |
| **登录接口 HTTPS 强制** | P0 | 生产环境网关/NGINX 强制 443，`X-Forwarded-Proto` 校验 |
| **密码输入 `char[]` 而非 `String`** | P1 | `LoginRequest` 用 `char[] password`，代理完成后 `Arrays.fill(pw, '0')` 覆写；JVM 虽有 JIT 优化可能 copy，但仍是深度防御 |
| **日志审计：记录登录事件而非凭证** | P1 | 只记录 `user=XXX login_result=success/fail ip=xxx duration=xxms`，绝不记录凭证 |
| **kube-manager 返回 body 中的 token 同样脱敏** | P1 | `parseJson` 后 token 写入 log 时只保留前 8 位 + "..."（已有部分实现） |

**关键代码模式（不可接受）：**
```java
// ❌ 不可：会在日志中暴露明文密码
log.debug("Login body: {}", request); // 默认 toString 包含 password
```
```java
// ✅ 正确：自定义脱敏
toString() { return "LoginRequest{username=" + username + ", password=***}"; }
```

---

### 2. Session ID 生成策略

**当前状态分析：**
- `AuthSessionApiDesign.md` 已提出使用 `conv- + UUID 前 8 位` 或类似方案
- `TimedDecisionCache.generateToken()` 使用 `SHA-256(sessionId + timestamp + UUID)`，这是确认 Token 的生成方式
- 系统当前没有一个正式的 "Session ID" 用于整个认证会话，只有 conversationId

#### 建议方案：双 Token 分层设计

```
┌─────────────────────────────────────────────────────────┐
│  用户浏览器                                              │
│  ─────────────────────────────────────────────────────  │
│  Authorization: Bearer <kube-manager-JWT>               │  ← 身份凭证 Token
│  X-Session-Id: <kube-agent-session-id>                  │  ← 业务会话标识
└─────────────────────────────────────────────────────────┘
```

| Token 类型 | 用途 | 生成方式 | 格式建议 |
|------------|------|----------|----------|
| **Authentication Token** | 身份凭证（透传给 kube-manager） | kube-manager 返回的 JWT | 保持原样，不做二次包装 |
| **Session ID (X-Session-Id)** | 业务会话标识、kub-agent 内部 conversation 关联 | SecureRandom 生成 128-bit，Base64 URL-safe 编码 | `ses_<22 chars>` (如 `ses_Aq9xLp3vMnK8WzQrT2YjF`) |

**为什么不用纯 UUID？**
- UUID v4 是 122-bit 随机值，但标准格式含 hyphen 且较长，URL 中不友好
- 标准 Base64(16 bytes) = 22 chars，足够安全（128 bit 暴力破解不可行）
- 前缀 `ses_` 便于日志识别和调试

**为什么不用 JWT 作为 Session ID？**
- kube-manager 的 JWT 已承担身份凭证职责
- kube-agent 如果为 Session ID 再发 JWT，既增加复杂度也增加攻击面
- Session ID 只需**不可预测**即可，无需自包含声明

**生成代码模式：**
```java
import java.security.SecureRandom;
import java.util.Base64;

public class SessionIdGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static String generate() {
        byte[] bytes = new byte[16]; // 128 bit
        SECURE_RANDOM.nextBytes(bytes);
        return "ses_" + ENCODER.encodeToString(bytes);
    }
}
```

---

### 3. 会话/Token 生命周期管理

**当前状态分析：**
- `KubeManagerHttpClient` 的 fallback token 有 25 分钟 TTL（`TOKEN_TTL_MS`）
- `UserPermissionContext` 仅使用裸 `ConcurrentHashMap`，无 TTL → **这是最大的安全隐患**
- `TimedDecisionCache` 有完善的 Caffeine TTL（5分钟）+ eviction listener
- 无 Token 刷新/续期机制
- 无并发会话控制（同一用户多设备登录无限制）
- 无明确的登出失效机制

#### 建议方案

#### 3.1 Token 缓存必须引入 TTL（P0）

当前 `UserPermissionContext.cache` 是裸 `ConcurrentHashMap`，用户登出之外的唯一清理方式是 JVM 重启。**必须改为 Caffeine：**

```java
// 修改 UserPermissionContext
private final Cache<String, UserPermission> cache = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(Duration.ofMinutes(30))   // 30分钟无操作过期
    .expireAfterAccess(Duration.ofMinutes(10))  // 每次访问续期10分钟
    .evictionListener((key, value, cause) -> {
        if (cause == RemovalCause.EXPIRED) {
            log.info("[Session] Token 过期自动清理: {}", value.username());
        }
    })
    .recordStats()
    .build();
```

**为什么不用 `expireAfterAccess` 作主 TTL？**
- 主 TTL 30min 保证 Token 最长存活窗口，防止 Token 被窃取后长期可用
- `expireAfterAccess` 10min 保证僵尸会话（如用户关闭浏览器）快速清理

#### 3.2 登录响应中显式声明过期时间

前端需要知道 Token 何时过期以便提前刷新：
```json
{
  "data": {
    "token": "eyJhbG...",
    "tokenType": "Bearer",
    "expiresIn": 1800,  // 秒，与后端 TTL 一致
    "refreshAfter": 1500 // 秒，建议前端在此时间后刷新
  }
}
```

#### 3.3 Token 续期（Refresh）机制（P1）

**方案选择：**

| 方案 | 实现 | 优劣 |
|------|------|------|
| **A: 滑动续期（推荐）** | 每次请求带有效 Token → 后端 `expireAfterAccess` 自动续期 | 简单、无感知，但 Token 本身不变，被窃取后风险窗口固定 |
| **B: 定时刷新** | 后端在 Token 快过期时，用旧 Token 调 kube-manager 刷新接口获取新 JWT | 需要 kube-manager 支持 refresh token，当前未知 |
| **C: 短 Token + 长 Session** | kube-agent 自身维护 session mapping，定期向 kube-manager re-login 刷新内部缓存 | 可行，但增加 kube-manager 负载 |

**推荐：方案 A（滑动续期）+ 补充登出即失效**
- 前端每 5 分钟发一次心跳（`GET /api/agent/me`）以维持会话活跃
- 服务端 Caffeine `expireAfterAccess(10min)` 保证无心跳自动清理
- 登出调用 `/logout` 主动 `cache.invalidate(token)`

#### 3.4 并发会话控制（P2）

当同一用户多浏览器/标签页登录时：

```java
// ConversationStore 或新增 SessionStore 中维护
// userId -> Set<sessionId>

// 策略选择（可配置化）：
// 1. 允许并发（默认）— 不限制
// 2. 踢旧留新 — 新登录使旧 Token 失效（仅适合单设备场景）
// 3. 限制数量 — 最多 N 个并发会话，超出拒绝或踢最早
```

建议内部平台用**策略 1（允许并发）**，因为用户可能在不同工具/标签中同时操作。

---

### 4. 与现有 PermissionTokenFilter 的集成方式

**现状：**
- `AuthTokenFilter`（MVC）已工作，从 `Authorization` header 提取 Bearer Token
- `PermissionTokenFilter`（WebFlux）已存在但项目实际用 Spring MVC
- 前端指定：纯 `X-Session-Id` header（不依赖 Cookie），`Authorization` 仍用 Bearer Token
- M5.29-4 更新：`AuthTokenFilter` 已注册进 Spring Security filter chain；当请求没有 Bearer header 时，可以用 `X-Session-Id -> SessionStore -> SessionData` 恢复标准 `Authentication`。如果 Bearer header 存在，则 Bearer 是本次请求的身份权威，未知 Bearer 不自动降级到 SessionId。

#### 4.1 X-Session-Id 的鉴权整合

**关键决策：`X-Session-Id` 是否替代 `Authorization`？**

| 方案 | 描述 | 评估 |
|------|------|------|
| **A: X-Session-Id 作服务端会话索引** | Authorization/Bearer 仍是优先身份来源；无 Bearer 时，X-Session-Id 可反查服务端 SessionData 并恢复 kube-agent 内部 Authentication | ✅ 当前 M5.29-4 路线 — 职责清晰，SessionId 不自包含身份声明 |
| **B: 双 Token 都鉴权** | 要求同时携带有效 Bearer + 有效 Session ID | 过度设计，增加失败面 |
| **C: X-Session-Id 替代 Bearer** | kube-agent 自己发 Session ID，用户用它鉴权 | ❌ 不推荐 — 信任链断裂，kube-manager 不认识 Session ID |

**推荐架构：**
```
前端请求:
  Authorization: Bearer <kube-manager JWT>
  X-Session-Id: <kube-agent 业务会话 ID>

后端处理:
  AuthTokenFilter  → Bearer 优先：Bearer -> UserPermissionContext -> SecurityContext
                   → 无 Bearer 时：X-Session-Id -> SessionStore -> SecurityContext + ThreadLocal

AtlasOrchestrator:
  → 用 Bearer Token 调用 kube-manager（身份鉴权由 kube-manager 做）
  → 用 X-Session-Id 做 conversation 元数据管理和 HITL thread 映射
```

M5.29-4 的关键安全边界：`X-Session-Id` 不是替代 kube-manager JWT 的业务凭证，也不能被 LLM 或前端当成 `userId` 声明。它只能作为 `SessionStore` 的不透明索引。Spring Security 端点授权看到的是服务端恢复出的 `Authentication`；Tool/HTTP 兼容层继续通过 `UserPermissionContext` 获取真实 kube-manager token 做下游转发。

#### 4.2 新增 SessionValidationFilter（轻量）

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 90)  // 在 AuthTokenFilter(100) 之前
public class SessionValidationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse res,
                                     FilterChain chain) {
        // 仅对 /api/agent/* 路径生效（排除 /login 等匿名接口）
        String sessionId = req.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            // 校验格式（防止注入）
            if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
                res.sendError(400, "Invalid X-Session-Id format");
                return;
            }
            // 可选：校验 conversationStore 中存在（严格模式）
            // 移交给 ConversationController 做业务校验更合理
        }
        chain.doFilter(req, res);
    }
}
```

**注意：** Session ID 格式校验使用白名单正则：`^ses_[A-Za-z0-9_-]{22}$`

---

### 5. 会话数据是否需要后端持久化

**现状：**
- `TimedDecisionCache` 用 Caffeine 内存缓存（最大 1000 条，5分钟 TTL）
- `ConversationStore`（设计中）拟用 Caffeine（最大 5000 条，24h TTL）
- 前端用 Pinia 管理会话列表和消息内容

#### 5.1 分层存储决策矩阵

| 数据类型 | 存储位置 | 后端是否持久化 | 理由 |
|----------|----------|----------------|------|
| **LLM 消息内容** | 前端 Pinia + localStorage/IndexedDB | ❌ 不持久化 | 消息是前端的核心状态，后端无需求；未来可扩展 |
| **会话元数据** (title, createdAt, model) | 后端 `ConversationStore` (Caffeine) | ⚠️ 内存即可 | 量小（<5000条），Caffeine 24h TTL 足够；多实例时才需 Redis |
| **HITL 决策** | `TimedDecisionCache` | ⚠️ 5分钟内存 | 临时性极强，Caffeine 最合适 |
| **权限缓存** (UserPermission) | `UserPermissionContext` | ⚠️ 30分钟内存 | 纯透传 kube-manager 的 JWT，无需后端持久 |
| **登录凭证** (password) | 绝不存储 | ❌ 绝不 | 只代理转发，不落盘 |

#### 5.2 何时需要 Redis？

当前 kube-agent 是**单实例**部署（WSL/本地开发，或单容器）。

当满足以下任一条件时引入 Redis：
1. **多实例部署**（负载均衡后多个 kube-agent 实例）→ Session 数据必须共享
2. **会话需要跨天持久**（用户希望今天创建的会话明天还能看到）→ Caffeine 24h TTL 不够
3. **故障恢复要求**（实例重启后会话不丢失）→ 内存易失

**Phase 1（当前）：** 纯内存 Caffeine，满足内部平台单实例运行。
**Phase 2（可选）：** 将 `ConversationStore` 的 `Cache<String, Conversation>` 替换为 `StringRedisTemplate`，接口不变。

---

## 三、新增 API 的安全检查清单

基于 `docs/auth-session-api-design.md` 已规划 8 个 API，补充安全要求：

| # | 端点 | 方法 | 匿名允许？ | 额外安全检查 |
|---|------|------|-----------|-------------|
| 1 | `/api/agent/login` | POST | ✅ 是 | rate limit（登录失败 5 次/分钟封 IP）；密码脱敏日志 |
| 2 | `/api/agent/logout` | POST | ❌ 否 | 校验 Authorization；invalidate 当前 Token 缓存；可选通知 kube-manager 踢出 |
| 3 | `/api/agent/me` | GET | ❌ 否 | 从 ThreadLocal 读取，零外部调用 |
| 4 | `/api/agent/conversation` | POST | ❌ 否 | 生成 Session ID 用 SecureRandom；绑定当前 userId |
| 5 | `/api/agent/conversation` | GET | ❌ 否 | 仅返回当前 userId 的会话（强制隔离） |
| 6 | `/api/agent/conversation/{id}` | GET | ❌ 否 | 校验 `conversation.userId == currentUserId` |
| 7 | `/api/agent/conversation/{id}` | PUT | ❌ 否 | 同上 + 仅允许改 title（白名单字段） |
| 8 | `/api/agent/conversation/{id}` | DELETE | ❌ 否 | 同上 + 管理员可删除任意会话（可选） |
| 9 | `/api/agent/chat/stream` | POST | ❌ 否 | Session 激活 `touch()`；Tool 调用 Token 透传 |

---

## 四、关键安全加固代码模式

### 4.1 UserPermissionContext — 引入 Caffeine TTL（P0）

```java
@Component
public class UserPermissionContext {

    // 替换裸 ConcurrentHashMap 为 Caffeine Cache
    private final Cache<String, UserPermission> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .expireAfterAccess(Duration.ofMinutes(10))
        .evictionListener((key, value, cause) -> {
            if (cause == RemovalCause.EXPIRED) {
                log.info("[Session] Token 过期自动清理: user={}",
                    value != null ? ((UserPermission)value).username() : "unknown");
            }
        })
        .recordStats()
        .build();

    public void onLogin(String token, String username, String role, Set<String> permissions) {
        UserPermission perm = new UserPermission(token, username, role, permissions);
        cache.put(token, perm);
        log.info("[UserPermissionContext] 用户登录缓存: {} (role={})", username, role);
    }

    public void onLogout(String token) {
        UserPermission removed = cache.asMap().remove(token); // Caffeine 兼容
        if (removed != null) {
            log.info("[UserPermissionContext] 用户登出清除: {}", removed.username());
        }
    }

    public Optional<UserPermission> current() {
        String token = CURRENT_TOKEN.get();
        if (token == null || token.isBlank()) return Optional.empty();
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    // ... 其余方法不变
}
```

### 4.2 登录接口 — 日志脱敏 + 失败 rate limit

```java
@RestController
@RequestMapping("/api/agent")
public class AuthController {

    private final RateLimiter loginRateLimiter = RateLimiter.create(5.0); // 每 IP 5次/分钟

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpReq) {

        // 1. Rate limit（按 IP）
        String clientIp = getClientIp(httpReq);
        if (!loginRateLimiter.tryAcquire()) {
            log.warn("[Login] IP {} 登录请求过频", clientIp);
            return ResponseEntity.status(429)
                .body(ApiResponse.err(429001, "请求过于频繁，请稍后再试"));
        }

        // 2. 日志脱敏（绝不打印密码）
        log.info("[Login] 用户 {} 尝试登录 (orgId={}, type={})",
            request.username(),
            request.organizationId(),
            request.loginType());

        // 3. 代理到 kube-manager（RestClient）
        // 4. 缓存权限 + 生成 Session
        // 5. 返回（token + expiresIn）
    }

    /** LoginRequest 必须脱敏 toString */
    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,  // 绝不入日志
        String organizationId,
        String loginType
    ) {
        @Override
        public String toString() {
            return "LoginRequest{username='" + username + "', password=***, organizationId='"
                + organizationId + "', loginType='" + loginType + "'}";
        }
    }
}
```

### 4.3 AuthTokenFilter — 兼容 `X-Session-Id` + `Authorization`

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) {

    // ① 提取 Bearer Token（身份凭证）
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        userPermissionContext.bind(token);
    }

    // ② 提取 X-Session-Id（业务会话，可选校验）
    String sessionId = request.getHeader("X-Session-Id");
    if (sessionId != null && !sessionId.isBlank()) {
        // 存入独立的 ThreadLocal（供 AtlasOrchestrator 读取）
        SessionContext.CURRENT_SESSION_ID.set(sessionId);
    }

    try {
        filterChain.doFilter(request, response);
    } finally {
        userPermissionContext.unbind();
        SessionContext.CURRENT_SESSION_ID.remove();
    }
}
```

---

## 五、风险矩阵与缓解措施

| 风险 | 严重性 | 可能性 | 缓解措施 |
|------|--------|--------|----------|
| Token 在裸 ConcurrentHashMap 中永不过期 | 高 | 高 | P0：改为 Caffeine Cache + 30min TTL |
| 登录代理时密码落入日志 | 高 | 中 | P0：LoginRequest 脱敏 toString + 控制器层过滤日志 |
| Session ID 可预测（如用简单自增 ID） | 中 | 低 | P0：使用 SecureRandom 128-bit + Base64 |
| 同一 Token 多线程并发修改缓存 | 中 | 中 | 已缓解：ConcurrentHashMap/Caffeine 线程安全 |
| 登出后 Token 仍可用（无主动通知 kube-manager） | 中 | 中 | P1：登出时调用 kube-manager 踢 Token 接口（如有） |
| XSS 窃取前端 localStorage 中的 Token | 中 | 低 | P1：短 TTL + 心跳维持；生产环境 CSP 策略 |
| 多实例部署时 Session 数据隔离 | 低 | 低 | P2：Phase 2 引入 Redis StringRedisTemplate |
| ThreadLocal 未清理（Filter 异常路径） | 低 | 低 | 已缓解：`try/finally` 中 `unbind()` |

---

## 六、实施优先级（Action Items）

| 优先级 | 任务 | 文件/模块 | 工作量 | Owner |
|--------|------|-----------|--------|-------|
| **P0** | `UserPermissionContext.cache` 改为 Caffeine Cache（30min TTL） | `UserPermissionContext.java` | 小 | Backend |
| **P0** | `LoginRequest` 脱敏 + 登录接口 rate limit | `AuthController.java`（新增） | 小 | Backend |
| **P0** | 引入 `SessionIdGenerator`（SecureRandom 128-bit） | 新增 `SessionIdGenerator.java` | 小 | Backend |
| **P0** | `AuthTokenFilter` 兼容 `X-Session-Id` header | `AuthTokenFilter.java` | 小 | Backend |
| **P1** | 登录响应增加 `expiresIn` + 前端心跳机制 | `AuthController` + 前端 | 中 | Frontend/Backend |
| **P1** | 登出时主动使 kube-manager Token 失效（如 API 存在） | `AuthController` | 小 | Backend |
| **P1** | `ConversationStore` 按 userId 强制隔离 + 权限校验 | `ConversationController` | 中 | Backend |
| **P2** | 多实例时 Redis 替换 Caffeine（ConversationStore） | `ConversationStore.java` | 中 | Infra |
| **P2** | 集成测试：模拟 Token 过期、并发登录、跨用户访问 | `src/test` | 中 | QA |

---

*报告完成。本方案与现有 `docs/auth-session-api-design.md` 兼容，主要在其基础上补充了安全加固细节。*
