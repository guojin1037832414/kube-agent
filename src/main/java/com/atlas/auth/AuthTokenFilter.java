package com.atlas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 请求过滤器 — 每请求自动提取 Bearer Token 并绑定到标准安全上下文。
 *
 * <p>M5.29-1 起，这个过滤器不再只是“普通 Servlet Filter + ThreadLocal”。它会把
 * {@link UserPermissionContext} 中的登录权限快照桥接成 Spring Security
 * {@link Authentication}，让后续端点保护、方法级授权、审计 actor 提取都能逐步转向
 * 标准 `SecurityContext`。ThreadLocal 仍保留为 legacy Tool/HTTP 兼容层。</p>
 *
 * @version 3.1.0
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    private final UserPermissionContext userPermissionContext;

    public AuthTokenFilter(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {

        // 入口先清理一次，防止线程池复用或上游测试夹具遗留身份进入本次请求链路。
        SecurityContextHolder.clearContext();
        userPermissionContext.unbind();

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userPermissionContext.bind(token);
            userPermissionContext.current()
                .map(this::toAuthentication)
                .ifPresent(authentication ->
                    SecurityContextHolder.getContext().setAuthentication(authentication));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            userPermissionContext.unbind();
        }
    }

    private Authentication toAuthentication(UserPermissionContext.UserPermission permission) {
        String role = permission.role() != null && !permission.role().isBlank()
            ? permission.role().trim().toUpperCase(java.util.Locale.ROOT)
            : "USER";
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        permission.permissions().stream()
            .filter(item -> item != null && !item.isBlank())
            .map(item -> new SimpleGrantedAuthority(item.trim()))
            .forEach(authorities::add);
        // SecurityContext 只承载身份和权限；真实 Bearer Token 仍由 ThreadLocal 兼容层负责向 kube-manager 透传。
        return new UsernamePasswordAuthenticationToken(permission.username(), null, authorities);
    }
}
