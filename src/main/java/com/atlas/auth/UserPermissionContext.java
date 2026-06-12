package com.atlas.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 用户权限上下文 — 登录时缓存用户权限，避免每次请求查询后端。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>登录成功后，从 kube-manager 拉取用户角色，写入本缓存</li>
 *   <li>后续每次请求通过 {@link #resolve} 提取当前用户 Token</li>
 *   <li>Token 失效 / 登出时从缓存移除</li>
 *   <li>支持 ThreadLocal 传递（WebFilter 写入，ToolRegistry 读取）</li>
 * </ul>
 *
 * <p><b>P1.4 变更说明：</b></p>
 * <ul>
 *   <li>{@code CURRENT_TOKEN} 改为 {@code public static final}，供异步透传层直接读取/写入</li>
 *   <li>新增 {@link #getCurrentToken()} 便捷方法，供主线程捕获 Token</li>
 *   <li>新增 {@link #bind(String)} / {@link #unbind()} 仍由 WebFilter 调用，语义不变</li>
 * </ul>
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>ConcurrentHashMap（线程安全）</li>
 *   <li>TTL=30分钟（无访问自动失效，可配置化）</li>
 *   <li>最大条目=10000（避免内存溢出）</li>
 * </ul>
 *
 * <p>中文说明：这是历史兼容层，也是当前 Tool / HTTP 出口仍然依赖的请求上下文。
 * 它保存的是“登录成功后服务端缓存的权限快照”，不是前端声明的权限。
 * 新代码应优先通过 {@link AgentPrincipalResolver} 读取当前主体，只有需要透传会话令牌或组织上下文时才直接使用这里。</p>
 *
 * <p>安全边界：ThreadLocal 必须成对 bind/unbind；任何异步执行都必须显式复制并恢复上下文。
 * 不能把 LLM 参数、请求体里的 userId/orgId/role 写入这里当成可信身份。</p>
 *
 * @version 3.1.0-P1.4
 */
@Component
public class UserPermissionContext {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionContext.class);

    /**
     * ThreadLocal — 当前请求上下文中的用户 Token。
     *
     * <p><b>为什么不 private？</b> 因为 {@link com.atlas.auth.async.AsyncContextHolder}
     * 需要在异步线程中直接 set/remove，避免通过实例方法增加不必要的耦合。
     * 该字段为 {@code final}，引用不可变，仅值线程隔离，因此暴露是安全的。</p>
     */
    public static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    /**
     * ThreadLocal — 当前请求上下文中的组织 ID（P3.1 orgId 链路修复新增）。
     * <p>与 CURRENT_TOKEN 配对使用，保证 orgId 在异步线程中不丢失。</p>
     */
    public static final ThreadLocal<String> CURRENT_ORG_ID = new ThreadLocal<>();

    /** 内存权限缓存：token → 用户权限快照（Caffeine，30min TTL） */
    private final Cache<String, UserPermission> cache;

    public UserPermissionContext() {
        this.cache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10000)
            .build();
    }

    // ═══════════════════════════════════════════════════════════
    // ① 登录时写入 / 登出时清除
    // ═══════════════════════════════════════════════════════════

    /**
     * 用户登录成功后调用 — 缓存权限。
     *
     * <p>中文说明：只有 AuthController 在 kube-manager 登录成功并完成组织上下文确认后才应该调用。
     * 这里不再次调用外部系统，也不扩大权限；它只是把服务端已经确认的登录结果放入短期缓存。</p>
     *
     * @param token      会话 Token（Bearer 或 JWT）
     * @param username   用户名
     * @param role       角色标识（如 sys_admin / user / viewer）
     * @param permissions 额外权限列表（可选）
     */
    public void onLogin(String token, String username, String role, Set<String> permissions) {
        onLogin(token, username, role, permissions, null);
    }

    /**
     * 用户登录成功后调用 — 缓存权限与可信组织上下文。
     *
     * <p>中文说明：这是新链路首选入口。orgId 必须来自 kube-manager 响应或 token 反查结果，
     * 不能来自前端请求体。缓存 orgId 的目的，是让 Authorization: Bearer 路径也能恢复 token+orgId
     * 原子上下文，而不是只认证用户却在 Tool/kube-manager 出口处丢失租户边界。</p>
     */
    public void onLogin(String token, String username, String role, Set<String> permissions, String organizationId) {
        UserPermission perm = new UserPermission(token, username, role, permissions, organizationId);
        cache.put(token, perm);
        log.info("[UserPermissionContext] 用户登录缓存: {} (role={}, orgId={}, TTL=30min)",
            username, role, maskOrgId(organizationId));
    }

    /**
     * 用户登出时调用 — 清除缓存。
     *
     * <p>中文说明：登出清理必须幂等，未知 token 不报错。
     * 这样前端重复点击退出或会话已经过期时，不会把安全清理流程变成异常流程。</p>
     */
    public void onLogout(String token) {
        UserPermission removed = cache.getIfPresent(token);
        cache.invalidate(token);
        if (removed != null) {
            log.info("[UserPermissionContext] 用户登出清除: {}", removed.username);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ② ThreadLocal 绑定（WebFilter 每层请求入口）
    // ═══════════════════════════════════════════════════════════

    /**
     * WebFilter 调用 — 将 token 绑定到当前线程。
     *
     * <p>中文说明：这个方法只绑定当前请求线程，不证明 token 有效。
     * 是否有效仍由 {@link #current()} 能否从缓存读到 UserPermission 决定。</p>
     */
    public void bind(String token) {
        CURRENT_TOKEN.set(token);
        if (token == null || token.isBlank()) {
            CURRENT_ORG_ID.remove();
            return;
        }
        Optional<String> cachedOrgId = Optional.ofNullable(cache.getIfPresent(token))
            .map(UserPermission::organizationId)
            .filter(value -> value != null && !value.isBlank());
        if (cachedOrgId.isPresent()) {
            CURRENT_ORG_ID.set(cachedOrgId.get());
        } else {
            CURRENT_ORG_ID.remove();
        }
    }

    /**
     * 同时绑定 token 和 orgId 到当前线程（P3.1 orgId 链路修复新增）。
     * <p>用于 AtlasOrchestrator.streamChat() 在认证成功后手动绑定上下文。</p>
     *
     * <p>中文说明：token 与 orgId 必须作为一组服务端可信上下文一起传播。
     * 只传播 token 会让 kube-manager 调用缺少组织边界；只传播 orgId 又无法代表真实登录会话。</p>
     *
     * @param token  JWT Token
     * @param orgId  组织 ID
     */
    public void bind(String token, String orgId) {
        CURRENT_TOKEN.set(token);
        if (orgId != null && !orgId.isBlank()) {
            CURRENT_ORG_ID.set(orgId);
        } else {
            CURRENT_ORG_ID.remove();
        }
    }

    /**
     * 请求结束后清除 ThreadLocal（防止线程池复用导致信息泄漏）。
     * <p>P3.1：同时清除 CURRENT_ORG_ID。</p>
     *
     * <p>中文说明：这是这个类最重要的安全动作之一。
     * 如果忘记 unbind，后续复用同一线程的请求可能继承上一个用户的身份和组织上下文。</p>
     */
    public void unbind() {
        CURRENT_TOKEN.remove();
        CURRENT_ORG_ID.remove();
    }

    /**
     * 便捷方法：获取当前线程绑定的 Token（供主线程在发起异步任务前捕获）。
     *
     * <p>典型用法：</p>
     * <pre>{@code
     * String token = userPermissionContext.getCurrentToken();
     * CompletableFuture.runAsync(
     *     AsyncContextHolder.wrap(() -> { ... }, token),
     *     executor
     * );
     * }</pre>
     *
     * @return 当前线程的 token，null 表示未绑定或请求未经过 AuthTokenFilter
     */
    public String getCurrentToken() {
        return CURRENT_TOKEN.get();
    }

    /**
     * 便捷方法：获取当前线程绑定的组织ID（P3.1 orgId 链路修复新增）。
     *
     * @return 当前线程的 orgId，null 表示未绑定
     */
    public static String getCurrentOrgId() {
        return CURRENT_ORG_ID.get();
    }

    // ═══════════════════════════════════════════════════════════
    // ③ 权限查询接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取当前请求用户权限。
     *
     * <p>中文说明：返回 empty 是安全结果，不是错误。
     * 它表示当前线程没有绑定有效登录快照，上层应该按匿名或未授权处理。</p>
     */
    public Optional<UserPermission> current() {
        String token = CURRENT_TOKEN.get();
        if (token == null || token.isBlank()) return Optional.empty();
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    /**
     * 判断当前用户是否为管理员。
     *
     * <p>中文说明：缺失登录快照时必须返回 false，不能为了兼容旧链路而乐观放行。</p>
     */
    public boolean isAdmin() {
        return current()
            .map(UserPermission::isAdmin)
            .orElse(false);
    }

    /**
     * 判断当前用户是否已认证。
     *
     * <p>中文说明：只有 token 已绑定并且缓存中仍有权限快照，才算已认证。</p>
     */
    public boolean isAuthenticated() {
        return current().isPresent();
    }

    // ═══════════════════════════════════════════════════════════
    // ④ 内部数据结构
    // ═══════════════════════════════════════════════════════════

    /**
     * 用户权限快照（不可变）。
     *
     * <p>中文说明：record 内部复制权限集合，避免调用方保存 Set 引用后继续修改权限。</p>
     */
    public record UserPermission(
        String token,
        String username,
        String role,
        Set<String> permissions,
        String organizationId
    ) {
        public UserPermission {
            permissions = permissions != null
                ? Set.copyOf(permissions)
                : Set.of();
            organizationId = organizationId != null ? organizationId.trim() : "";
        }

        /**
         * 兼容旧测试和历史调用方的构造器。
         *
         * <p>中文说明：旧链路没有把 orgId 放进权限缓存，只能依赖 ThreadLocal 额外绑定。
         * 新代码应使用带 organizationId 的 onLogin 重载，避免 Bearer-only 请求丢失租户上下文。</p>
         */
        public UserPermission(String token, String username, String role, Set<String> permissions) {
            this(token, username, role, permissions, "");
        }

        public boolean isAdmin() {
            return "sys_admin".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role);
        }

        public boolean hasRole(String roleCode) {
            return roleCode != null && roleCode.equalsIgnoreCase(role);
        }

        public boolean hasPermission(String perm) {
            return permissions.contains(perm);
        }
    }

    private String maskOrgId(String organizationId) {
        return organizationId == null || organizationId.isBlank() ? "<missing>" : organizationId;
    }
}
