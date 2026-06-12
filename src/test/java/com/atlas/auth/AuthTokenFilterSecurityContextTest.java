package com.atlas.auth;

import jakarta.servlet.FilterChain;
import com.atlas.store.SessionStore;
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
    private SessionStore sessionStore;
    private AuthTokenFilter filter;

    @BeforeEach
    void setUp() {
        userPermissionContext = new UserPermissionContext();
        sessionStore = new SessionStore();
        filter = new AuthTokenFilter(userPermissionContext, sessionStore);
        clearThreadContext();
    }

    @AfterEach
    void tearDown() {
        clearThreadContext();
    }

    @Test
    void shouldBridgeCachedBearerTokenToSecurityContextDuringRequest() throws Exception {
        userPermissionContext.onLogin("token-admin", "alice", "sys_admin", Set.of("agent:observe"), "100002");
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
            assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isEqualTo("100002");
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
    void shouldBridgeSessionIdToSecurityContextWhenBearerHeaderIsMissing() throws Exception {
        String sessionId = sessionStore.createSession(
            "session-token",
            "session-user",
            "100002",
            "admin",
            Set.of("agent:memory:read")
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/memory/summaries");
        request.addHeader("X-Session-Id", sessionId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("session-user");
            assertThat(authentication.getCredentials()).isNull();
            assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "agent:memory:read");
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isEqualTo("session-token");
            assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isEqualTo("100002");
        };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
        assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
    }

    @Test
    void shouldNotAuthenticateSessionWithoutTrustedUsername() throws Exception {
        String sessionId = sessionStore.createSession(
            "session-token",
            " ",
            "100002",
            "admin",
            Set.of("agent:memory:read")
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/memory/summaries");
        request.addHeader("X-Session-Id", sessionId);
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

    @Test
    void bearerHeaderShouldTakePrecedenceOverSessionId() throws Exception {
        userPermissionContext.onLogin("bearer-token", "bearer-user", "sys_admin", Set.of("agent:observe"), "100003");
        String sessionId = sessionStore.createSession(
            "session-token",
            "session-user",
            "100002",
            "user",
            Set.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/observability/snapshot");
        request.addHeader("Authorization", "Bearer bearer-token");
        request.addHeader("X-Session-Id", sessionId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("bearer-user");
            assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_SYS_ADMIN");
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isEqualTo("bearer-token");
            assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isEqualTo("100003");
        };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isNull();
        assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
    }

    @Test
    void invalidBearerHeaderShouldNotFallbackToSessionId() throws Exception {
        String sessionId = sessionStore.createSession(
            "session-token",
            "session-user",
            "100002",
            "admin",
            Set.of("agent:memory:read")
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/memory/summaries");
        request.addHeader("Authorization", "Bearer missing-token");
        request.addHeader("X-Session-Id", sessionId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(UserPermissionContext.CURRENT_TOKEN.get()).isEqualTo("missing-token");
            assertThat(UserPermissionContext.CURRENT_ORG_ID.get()).isNull();
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
