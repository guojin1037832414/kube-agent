package com.atlas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring MVC 请求过滤器 — 每请求自动提取 Bearer Token 并绑定到权限上下文。
 *
 * <p>顺序：优先于业务 Filter 执行（Ordered.HIGHEST_PRECEDENCE + 100）。</p>
 *
 * @version 3.1.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
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

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userPermissionContext.bind(token);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            userPermissionContext.unbind();
        }
    }
}
