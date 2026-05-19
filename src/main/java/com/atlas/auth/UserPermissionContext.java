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
     * @param token      会话 Token（Bearer 或 JWT）
     * @param username   用户名
     * @param role       角色标识（如 sys_admin / user / viewer）
     * @param permissions 额外权限列表（可选）
     */
    public void onLogin(String token, String username, String role, Set<String> permissions) {
        UserPermission perm = new UserPermission(token, username, role, permissions);
        cache.put(token, perm);
        log.info("[UserPermissionContext] 用户登录缓存: {} (role={}, TTL=30min)", username, role);
    }

    /**
     * 用户登出时调用 — 清除缓存。
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
     */
    public void bind(String token) {
        CURRENT_TOKEN.set(token);
    }

    /**
     * 同时绑定 token 和 orgId 到当前线程（P3.1 orgId 链路修复新增）。
     * <p>用于 AtlasOrchestrator.streamChat() 在认证成功后手动绑定上下文。</p>
     *
     * @param token  JWT Token
     * @param orgId  组织 ID
     */
    public void bind(String token, String orgId) {
        CURRENT_TOKEN.set(token);
        if (orgId != null && !orgId.isBlank()) {
            CURRENT_ORG_ID.set(orgId);
        }
    }

    /**
     * 请求结束后清除 ThreadLocal（防止线程池复用导致信息泄漏）。
     * <p>P3.1：同时清除 CURRENT_ORG_ID。</p>
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
     */
    public Optional<UserPermission> current() {
        String token = CURRENT_TOKEN.get();
        if (token == null || token.isBlank()) return Optional.empty();
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    /**
     * 判断当前用户是否为管理员。
     */
    public boolean isAdmin() {
        return current()
            .map(UserPermission::isAdmin)
            .orElse(false);
    }

    /**
     * 判断当前用户是否已认证。
     */
    public boolean isAuthenticated() {
        return current().isPresent();
    }

    // ═══════════════════════════════════════════════════════════
    // ④ 内部数据结构
    // ═══════════════════════════════════════════════════════════

    /**
     * 用户权限快照（不可变）。
     */
    public record UserPermission(
        String token,
        String username,
        String role,
        Set<String> permissions
    ) {
        public UserPermission {
            permissions = permissions != null
                ? Set.copyOf(permissions)
                : Set.of();
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
}
