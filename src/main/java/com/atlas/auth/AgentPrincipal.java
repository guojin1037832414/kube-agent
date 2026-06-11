package com.atlas.auth;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agent 当前安全主体快照。
 *
 * <p>M5.29-2 起，Controller / audit / 后续方法级授权不再直接关心身份事实来自
 * Spring Security 还是历史 ThreadLocal。这个 record 是两条身份轨道之间的稳定桥：
 * Web 入口优先使用 {@code SecurityContext}，Tool / HTTP 兼容路径仍可从
 * {@link UserPermissionContext} 回落。</p>
 *
 * <p>中文说明：AgentPrincipal 是“当前操作者是谁”的只读快照。
 * 它不发起登录、不刷新令牌、不调用 kube-manager，也不决定 Tool 能否执行；
 * 它只把用户名、角色、权限、组织上下文和来源统一包装，供 Controller、审计和安全判断读取。</p>
 *
 * <p>安全边界：不要把前端传入的 userId、role、organizationId 直接包装成 AgentPrincipal。
 * 这个对象应该只由 {@link AgentPrincipalResolver} 根据服务端可信上下文创建。</p>
 */
public record AgentPrincipal(
    String username,
    String role,
    Set<String> authorities,
    Set<String> permissions,
    String organizationId,
    Source source
) {

    public AgentPrincipal {
        // 规范化输入，避免大小写、空白字符串和 null 让后续权限判断出现两套语义。
        username = username != null ? username.trim() : "";
        role = normalizeRole(role);
        authorities = immutableSorted(authorities);
        permissions = immutableSorted(permissions);
        source = source != null ? source : Source.UNKNOWN;
    }

    /** 当前主体是否代表一个已认证用户；匿名请求会返回 false。 */
    public boolean isAuthenticated() {
        return !username.isBlank();
    }

    /**
     * 判断是否为管理员。
     *
     * <p>中文说明：这里同时兼容业务角色和 Spring Security authority。
     * 原因是迁移期存在两条身份来源：历史 UserPermissionContext 提供 role，
     * 新的 SecurityContext 提供 ROLE_* authority。</p>
     */
    public boolean isAdmin() {
        return hasRole("admin") || hasRole("sys_admin")
            || authorities.contains("ROLE_ADMIN")
            || authorities.contains("ROLE_SYS_ADMIN");
    }

    /** 角色比较统一走 normalizeRole，避免 ROLE_ADMIN / admin / Admin 被当成不同语义。 */
    public boolean hasRole(String expectedRole) {
        return normalizeRole(expectedRole).equals(role);
    }

    /** 检查细粒度权限；这里不做通配符扩展，避免把模糊权限解释成更大权限。 */
    public boolean hasAuthority(String authority) {
        return authority != null && authorities.contains(authority.trim());
    }

    /** 将角色统一为小写业务角色名，并剥离 Spring Security 的 ROLE_ 前缀。 */
    static String normalizeRole(String value) {
        if (value == null || value.isBlank()) {
            return "user";
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /** 清理并冻结权限集合，保证 Principal 创建后不会被调用方继续篡改。 */
    private static Set<String> immutableSorted(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> cleaned = new TreeSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return Set.copyOf(cleaned);
    }

    /** 记录主体来源，方便审计和排查迁移期身份链路。 */
    public enum Source {
        SECURITY_CONTEXT,
        USER_PERMISSION_CONTEXT,
        UNKNOWN
    }
}
