package com.atlas.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux 请求过滤器 — 自动提取 Bearer Token，绑定到权限上下文。
 *
 * <p>顺序：确保此 Filter 最先执行（Ordered.HIGHEST_PRECEDENCE）。</p>
 *
 * <p>注意：当前项目使用 Spring MVC（非 WebFlux），此过滤器为 Future-proof 设计。
 * 若需立即使用，请改为 javax.servlet.Filter 实现并注入 Spring MVC。</p>
 *
 * @version 3.1.0
 */
@Component
public class PermissionTokenFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(PermissionTokenFilter.class);

    private final UserPermissionContext userPermissionContext;

    public PermissionTokenFilter(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userPermissionContext.bind(token);
        }
        return chain.filter(exchange)
            .doFinally(signalType -> userPermissionContext.unbind());
    }
}
