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
        username = username != null ? username.trim() : "";
        role = normalizeRole(role);
        authorities = immutableSorted(authorities);
        permissions = immutableSorted(permissions);
        source = source != null ? source : Source.UNKNOWN;
    }

    public boolean isAuthenticated() {
        return !username.isBlank();
    }

    public boolean isAdmin() {
        return hasRole("admin") || hasRole("sys_admin")
            || authorities.contains("ROLE_ADMIN")
            || authorities.contains("ROLE_SYS_ADMIN");
    }

    public boolean hasRole(String expectedRole) {
        return normalizeRole(expectedRole).equals(role);
    }

    public boolean hasAuthority(String authority) {
        return authority != null && authorities.contains(authority.trim());
    }

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

    public enum Source {
        SECURITY_CONTEXT,
        USER_PERMISSION_CONTEXT,
        UNKNOWN
    }
}
