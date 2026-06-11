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
 *
 * <p>中文说明：这个组件是身份来源的“适配器”，不是授权决策中心。
 * Controller 和审计代码只问它“当前服务端可信主体是谁”，不用关心请求是通过 Authorization
 * 头还是 X-Session-Id 进入系统。真正的端点访问控制仍由 Spring Security 配置和注解决定。</p>
 *
 * <p>安全边界：不能从请求体、LLM 参数或前端声明中构造 Principal。
 * 如果 SecurityContext 和历史 ThreadLocal 都没有可信身份，就必须返回 empty，而不是猜测一个用户。</p>
 */
@Component
public class AgentPrincipalResolver {

    private final UserPermissionContext userPermissionContext;

    public AgentPrincipalResolver(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
    }

    /**
     * 解析当前用户，优先使用 Spring Security，再回落到历史权限上下文。
     *
     * <p>中文说明：优先级不能反过来。SecurityContext 代表当前 HTTP 请求已经过过滤链处理，
     * 如果同时存在旧 ThreadLocal，仍应以标准安全上下文为准，减少迁移期身份冲突。</p>
     */
    public Optional<AgentPrincipal> current() {
        Optional<AgentPrincipal> securityPrincipal = fromSecurityContext();
        if (securityPrincipal.isPresent()) {
            return securityPrincipal;
        }
        return fromUserPermissionContext();
    }

    /** 给只需要 admin 布尔值的调用方提供简洁入口；缺失身份时保守返回 false。 */
    public boolean isCurrentAdmin() {
        return current().map(AgentPrincipal::isAdmin).orElse(false);
    }

    /** 从标准 SecurityContext 提取主体；匿名、空用户名或未认证对象一律忽略。 */
    private Optional<AgentPrincipal> fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isRealAuthentication(authentication)) {
            return Optional.empty();
        }

        // ROLE_* 保留为 authorities；非 ROLE_* 同时作为业务 permission 暴露给上层读取。
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

    /**
     * 从历史 UserPermissionContext 回落解析。
     *
     * <p>中文说明：这是兼容路径，主要服务 Tool、SSE 和 kube-manager HTTP 旧链路。
     * 回落不等于放宽权限；如果缓存中没有登录事实，仍然返回 empty。</p>
     */
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

    /** 过滤 Spring Security 的匿名主体，防止 anonymousUser 被当成真实操作者写入审计。 */
    private boolean isRealAuthentication(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && authentication.getName() != null
            && !authentication.getName().isBlank();
    }

    /** 从 authority 集合中提取最重要的角色；SYS_ADMIN 优先级高于 ADMIN。 */
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

    /** 将历史 role 包装成 Spring 风格 ROLE_*，让上层判断逻辑保持一致。 */
    private Set<String> roleAuthority(String role) {
        String normalizedRole = AgentPrincipal.normalizeRole(role).toUpperCase(java.util.Locale.ROOT);
        return Set.of("ROLE_" + normalizedRole);
    }
}
