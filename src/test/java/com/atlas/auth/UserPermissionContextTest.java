package com.atlas.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserPermissionContext 纯单元测试 — 验证权限缓存、ThreadLocal管理和权限查询。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>登录/登出流程</li>
 *   <li>ThreadLocal bind/unbind/getCurrentToken</li>
 *   <li>current() 查询权限</li>
 *   <li>Admin/Authenticated 判断</li>
 *   <li>UserPermission record 不可变性</li>
 * </ul>
 *
 * @version 3.1.0-M2
 */
class UserPermissionContextTest {

    private UserPermissionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new UserPermissionContext();
        // 清理 ThreadLocal，避免测试污染
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    // ═══════════════════════════════════════════════════════════
    // 登录/登出
    // ═══════════════════════════════════════════════════════════

    @Test
    void testLogin_cachesPermission() {
        ctx.onLogin("token-abc", "zhangsan", "user", Set.of("perm1"), "100002");

        ctx.bind("token-abc");
        Optional<UserPermissionContext.UserPermission> p = ctx.current();

        assertTrue(p.isPresent());
        assertEquals("zhangsan", p.get().username());
        assertEquals("user", p.get().role());
        assertEquals("100002", p.get().organizationId(), "可信 orgId 应随登录快照一起缓存");
        assertEquals("100002", UserPermissionContext.getCurrentOrgId(), "Bearer bind 应恢复 token+orgId 原子上下文");
        assertTrue(p.get().hasPermission("perm1"));
    }

    @Test
    void testLogin_nullPermissions_defaultsToEmpty() {
        ctx.onLogin("token-xyz", "lisi", "admin", null);
        ctx.bind("token-xyz");

        Optional<UserPermissionContext.UserPermission> p = ctx.current();
        assertTrue(p.isPresent());
        assertEquals(0, p.get().permissions().size(), "null权限应默认为空Set");
    }

    @Test
    void testLogout_removesPermission() {
        ctx.onLogin("token-123", "wangwu", "user", Set.of());
        ctx.onLogout("token-123");
        ctx.bind("token-123");

        assertFalse(ctx.current().isPresent(), "登出后应无缓存");
    }

    @Test
    void testLogout_unknownToken_noError() {
        // 登出未登录的token不应报错
        assertDoesNotThrow(() -> ctx.onLogout("token-nonexistent"),
            "登出未知token不应抛异常");
    }

    // ═══════════════════════════════════════════════════════════
    // ThreadLocal 管理
    // ═══════════════════════════════════════════════════════════

    @Test
    void testBind_setsToken() {
        ctx.bind("my-token");
        assertEquals("my-token", UserPermissionContext.CURRENT_TOKEN.get());
        assertEquals("my-token", ctx.getCurrentToken());
    }

    @Test
    void testBind_unknownToken_clearsStaleOrgId() {
        // ThreadLocal 会被线程池复用；绑定未知 token 时必须清掉旧 orgId，不能继承上一个请求的租户。
        UserPermissionContext.CURRENT_ORG_ID.set("stale-org");
        ctx.bind("unknown-token");

        assertEquals("unknown-token", UserPermissionContext.CURRENT_TOKEN.get());
        assertNull(UserPermissionContext.getCurrentOrgId(), "未知 token 不能保留旧组织上下文");
    }

    @Test
    void testUnbind_clearsToken() {
        ctx.bind("my-token", "100002");
        ctx.unbind();
        assertNull(UserPermissionContext.CURRENT_TOKEN.get());
        assertNull(UserPermissionContext.CURRENT_ORG_ID.get());
    }

    @Test
    void testGetCurrentToken_noBind_returnsNull() {
        assertNull(ctx.getCurrentToken(), "未bind时应返回null");
    }

    @Test
    void testCurrent_noToken_returnsEmpty() {
        assertFalse(ctx.current().isPresent(), "未bind时current应返回empty");
    }

    @Test
    void testCurrent_blankToken_returnsEmpty() {
        UserPermissionContext.CURRENT_TOKEN.set("");
        assertFalse(ctx.current().isPresent(), "空白token应返回empty");
    }

    // ═══════════════════════════════════════════════════════════
    // 权限判断
    // ═══════════════════════════════════════════════════════════

    @Test
    void testIsAdmin_sysAdmin_returnsTrue() {
        ctx.onLogin("admin-token", "boss", "sys_admin", Set.of());
        ctx.bind("admin-token");
        assertTrue(ctx.isAdmin(), "sys_admin角色应为管理员");
    }

    @Test
    void testIsAdmin_adminRole_returnsTrue() {
        ctx.onLogin("admin-token", "admin", "admin", Set.of());
        ctx.bind("admin-token");
        assertTrue(ctx.isAdmin(), "admin角色也应为管理员");
    }

    @Test
    void testIsAdmin_regularUser_returnsFalse() {
        ctx.onLogin("user-token", "worker", "user", Set.of());
        ctx.bind("user-token");
        assertFalse(ctx.isAdmin(), "普通用户不应为管理员");
    }

    @Test
    void testIsAdmin_anonymous_returnsFalse() {
        assertFalse(ctx.isAdmin(), "匿名用户不应为管理员");
    }

    @Test
    void testIsAuthenticated_loggedIn_returnsTrue() {
        ctx.onLogin("token-1", "alice", "user", Set.of());
        ctx.bind("token-1");
        assertTrue(ctx.isAuthenticated(), "已登录用户应为认证状态");
    }

    @Test
    void testIsAuthenticated_anonymous_returnsFalse() {
        assertFalse(ctx.isAuthenticated(), "匿名用户应为未认证状态");
    }

    @Test
    void testIsAuthenticated_boundButNotCached_returnsFalse() {
        // bind了一个token，但该token从未onLogin（过期或无效）
        ctx.bind("expired-token");
        assertFalse(ctx.isAuthenticated(), "已bind但缓存中不存在应为未认证");
    }

    // ═══════════════════════════════════════════════════════════
    // UserPermission record
    // ═══════════════════════════════════════════════════════════

    @Test
    void testUserPermission_isAdminCaseInsensitive() {
        UserPermissionContext.UserPermission p1 =
            new UserPermissionContext.UserPermission("t1", "u1", "SYS_ADMIN", Set.of());
        UserPermissionContext.UserPermission p2 =
            new UserPermissionContext.UserPermission("t1", "u1", "Admin", Set.of());
        assertTrue(p1.isAdmin(), "大小写不敏感匹配");
        assertTrue(p2.isAdmin(), "大小写不敏感匹配");
    }

    @Test
    void testUserPermission_hasRoleCaseInsensitive() {
        UserPermissionContext.UserPermission p =
            new UserPermissionContext.UserPermission("t1", "u1", "USER", Set.of());
        assertTrue(p.hasRole("user"), "角色匹配应大小写不敏感");
    }

    @Test
    void testUserPermission_immutable() {
        Set<String> perms = new java.util.HashSet<>();
        perms.add("perm1");
        UserPermissionContext.UserPermission p =
            new UserPermissionContext.UserPermission("t1", "u1", "user", perms);

        assertThrows(UnsupportedOperationException.class,
            () -> p.permissions().add("perm2"),
            "权限Set应不可变");
    }
}
