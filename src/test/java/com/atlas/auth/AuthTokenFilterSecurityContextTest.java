package com.atlas.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.29-1 Spring Security 身份桥接测试。
 *
 * <p>教学重点：旧系统的 Tool/HTTP 仍依赖 {@link UserPermissionContext} ThreadLocal，
 * 新的端点授权、审计 actor 和方法级授权则应该逐步读取标准 {@link Authentication}。
 * 这个测试保证两条路径在同一个请求生命周期内保持一致，并在请求结束后清理干净。</p>
 */
class AuthTokenFilterSecurityContextTest {

    private UserPermissionContext userPermissionContext;
    private AuthTokenFilter filter;

    @BeforeEach
    void setUp() {
        userPermissionContext = new UserPermissionContext();
        filter = new AuthTokenFilter(userPermissionContext);
        clearThreadContext();
    }

    @AfterEach
    void tearDown() {
        clearThreadContext();
    }

    @Test
    void shouldBridgeCachedBearerTokenToSecurityContextDuringRequest() throws Exception {
        userPermissionContext.onLogin("token-admin", "alice", "sys_admin", Set.of("agent:observe"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/observability/snapshot");
        request.addHeader("Authorization", "Bearer token-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("alice");
            assertThat(authentication.getCredentials()).isNull();
            assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_SYS_ADMIN", "agent:observe");
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isEqualTo("token-admin");
        };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
        assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
    }

    @Test
    void shouldNotAuthenticateUnknownBearerTokenAndStillClearThreadLocals() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/observability/snapshot");
        request.addHeader("Authorization", "Bearer missing-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isEqualTo("missing-token");
        };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
        assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
    }

    @Test
    void shouldKeepAnonymousRequestUnauthenticatedEvenWhenThreadHasStaleContext() throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(
                "stale-user", "stale-token", "ROLE_ADMIN"));
        UserPermissionContext.CURRENT_TOKEN.set("stale-token");
        UserPermissionContext.CURRENT_ORG_ID.set("stale-org");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
            assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
        };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
        assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
    }

    private void clearThreadContext() {
        SecurityContextHolder.clearContext();
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }
}
