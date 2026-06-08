package com.atlas.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 统一解析当前 Agent 安全主体。
 *
 * <p>优先读取 Spring Security {@link Authentication}，这是后续 endpoint authorization、
 * 方法级授权和审计 actor 的主线；当请求尚未完成迁移时，回落到
 * {@link UserPermissionContext}，保证 Tool / SSE / kube-manager HTTP 兼容路径仍可运行。</p>
 */
@Component
public class AgentPrincipalResolver {

    private final UserPermissionContext userPermissionContext;

    public AgentPrincipalResolver(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
    }

    public Optional<AgentPrincipal> current() {
        Optional<AgentPrincipal> securityPrincipal = fromSecurityContext();
        if (securityPrincipal.isPresent()) {
            return securityPrincipal;
        }
        return fromUserPermissionContext();
    }

    public boolean isCurrentAdmin() {
        return current().map(AgentPrincipal::isAdmin).orElse(false);
    }

    private Optional<AgentPrincipal> fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isRealAuthentication(authentication)) {
            return Optional.empty();
        }

        Set<String> authorities = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority == null || authority.getAuthority() == null || authority.getAuthority().isBlank()) {
                continue;
            }
            String value = authority.getAuthority().trim();
            authorities.add(value);
            if (!value.startsWith("ROLE_")) {
                permissions.add(value);
            }
        }

        return Optional.of(new AgentPrincipal(
            authentication.getName(),
            primaryRole(authorities),
            authorities,
            permissions,
            UserPermissionContext.getCurrentOrgId(),
            AgentPrincipal.Source.SECURITY_CONTEXT
        ));
    }

    private Optional<AgentPrincipal> fromUserPermissionContext() {
        return userPermissionContext.current()
            .map(permission -> new AgentPrincipal(
                permission.username(),
                permission.role(),
                roleAuthority(permission.role()),
                permission.permissions(),
                UserPermissionContext.getCurrentOrgId(),
                AgentPrincipal.Source.USER_PERMISSION_CONTEXT
            ));
    }

    private boolean isRealAuthentication(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && authentication.getName() != null
            && !authentication.getName().isBlank();
    }

    private String primaryRole(Set<String> authorities) {
        if (authorities.contains("ROLE_SYS_ADMIN")) {
            return "sys_admin";
        }
        if (authorities.contains("ROLE_ADMIN")) {
            return "admin";
        }
        return authorities.stream()
            .filter(value -> value.startsWith("ROLE_"))
            .findFirst()
            .map(AgentPrincipal::normalizeRole)
            .orElse("user");
    }

    private Set<String> roleAuthority(String role) {
        String normalizedRole = AgentPrincipal.normalizeRole(role).toUpperCase(java.util.Locale.ROOT);
        return Set.of("ROLE_" + normalizedRole);
    }
}
