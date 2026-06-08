package com.atlas.auth;

import com.atlas.store.SessionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
 */
@Configuration
@EnableWebSecurity
public class AgentSecurityConfig {

    @Bean
    UserDetailsService agentUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("kube-agent only accepts kube-manager Bearer sessions");
        };
    }

    @Bean
    SecurityFilterChain agentSecurityFilterChain(HttpSecurity http,
                                                 UserPermissionContext userPermissionContext,
                                                 SessionStore sessionStore) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new AuthTokenFilter(userPermissionContext, sessionStore), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/agent/login", "/api/agent/logout", "/api/agent/me", "/api/agent/health").permitAll()
                .requestMatchers("/api/agent/observability/**").hasAnyRole("ADMIN", "SYS_ADMIN")
                .requestMatchers("/api/agent/memory/**", "/api/agent/mcp/**").authenticated()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SYS_ADMIN")
                .anyRequest().permitAll()
            )
            .build();
    }
}
