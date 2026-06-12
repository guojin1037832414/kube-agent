package com.atlas.auth;

import com.atlas.store.SessionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Agent HTTP 安全主干配置。
 *
 * <p>M5.29-1 的目标不是一次性替换全部历史权限逻辑，而是先把最小 Spring Security
 * 主线接进来：Bearer Token 由 {@link AuthTokenFilter} 转成标准 Authentication，
 * 高敏诊断入口由 SecurityFilterChain 保护，旧的 UserPermissionContext/ThreadLocal
 * 继续服务现有 Tool、SSE 和 kube-manager HTTP 兼容路径。</p>
 *
 * <p>中文说明：这份配置是所有 HTTP 请求进入 Agent 后的第一道“门禁地图”。
 * 它回答三个问题：哪些端点允许匿名访问，哪些端点只要求已登录，哪些端点必须管理员。
 * 这里不做业务授权、不执行 Tool、不访问 kube-manager，只把请求拦到正确的安全层级。</p>
 *
 * <p>安全边界：不要在这里添加运行时执行按钮、Tool 调用、MCP tools/call 或 kube-manager 写逻辑。
 * 这个类只能描述 Web 入口权限，真实 Tool 执行仍必须走 SafeToolExecutor、HITL 和审计链路。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AgentSecurityConfig {

    /**
     * 禁用传统用户名密码加载。
     *
     * <p>中文说明：kube-agent 不维护自己的用户密码库，登录事实来自 kube-manager 登录会话。
     * 因此这里故意提供一个永远查不到用户的 UserDetailsService，防止 Spring Security
     * 后续被误配成“本地账号系统”。</p>
     */
    @Bean
    UserDetailsService agentUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("kube-agent only accepts kube-manager Bearer sessions");
        };
    }

    /**
     * 定义 Agent HTTP 入口的认证和授权规则。
     *
     * <p>中文说明：规则顺序非常重要，越具体的路径越靠前。
     * 公开的登录/健康检查必须允许前端启动；观测和治理接口必须 admin-only；
     * MCP 治理面属于管理员读模型；聊天、HITL、Memory、会话接口至少要求已认证；
     * 其余 /api/agent/** 也要求认证兜底。</p>
     */
    @Bean
    SecurityFilterChain agentSecurityFilterChain(HttpSecurity http,
                                                 UserPermissionContext userPermissionContext,
                                                 SessionStore sessionStore) throws Exception {
        return http
            // Agent API 主要由前端携带会话令牌调用，当前不依赖浏览器表单登录或服务端 HTTP Session。
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // AuthTokenFilter 把 kube-manager 登录产生的会话事实桥接进 Spring Security。
            .addFilterBefore(new AuthTokenFilter(userPermissionContext, sessionStore), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 登录、登出、当前用户和健康检查是前端启动/恢复必须能访问的引导入口。
                .requestMatchers("/api/agent/login", "/api/agent/logout", "/api/agent/me", "/api/agent/health").permitAll()
                // Observability 暴露审计、Trace、Eval、治理证据；普通用户不能读取这些诊断面。
                .requestMatchers("/api/agent/observability/**").hasAnyRole("ADMIN", "SYS_ADMIN")
                // MCP manifest/governance 会暴露 Tool 导出策略和被阻断能力，按治理读模型收紧为 admin-only。
                .requestMatchers("/api/agent/mcp/**").hasAnyRole("ADMIN", "SYS_ADMIN")
                .requestMatchers(
                    "/api/agent/chat/stream",
                    "/api/agent/chat/graph",
                    "/api/agent/hitl/**",
                    "/api/agent/memory/**",
                    "/api/agent/conversations",
                    "/api/agent/conversations/**"
                ).authenticated()
                // 兜底保护：后续新增 /api/agent/** 时默认需要登录，避免新端点忘记显式授权。
                .requestMatchers("/api/agent/**").authenticated()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Actuator 可能暴露环境和运行时信息，除健康/基础信息外必须管理员访问。
                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SYS_ADMIN")
                // 非 Agent 路径交给宿主应用或静态资源处理，避免本配置意外接管全站。
                .anyRequest().permitAll()
            )
            .build();
    }
}
