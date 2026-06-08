package com.atlas.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.29-2 当前安全主体解析测试。
 */
class AgentPrincipalResolverTest {

    private UserPermissionContext userPermissionContext;
    private AgentPrincipalResolver resolver;

    @BeforeEach
    void setUp() {
        userPermissionContext = new UserPermissionContext();
        resolver = new AgentPrincipalResolver(userPermissionContext);
        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    @Test
    void shouldResolveFromSecurityContextFirst() {
        userPermissionContext.onLogin("legacy-token", "legacy-user", "user", Set.of());
        userPermissionContext.bind("legacy-token", "100002");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "security-admin",
            null,
            "ROLE_SYS_ADMIN",
            "agent:observe"
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AgentPrincipal principal = resolver.current().orElseThrow();

        assertThat(principal.username()).isEqualTo("security-admin");
        assertThat(principal.role()).isEqualTo("sys_admin");
        assertThat(principal.source()).isEqualTo(AgentPrincipal.Source.SECURITY_CONTEXT);
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.authorities()).contains("ROLE_SYS_ADMIN", "agent:observe");
        assertThat(principal.permissions()).contains("agent:observe");
        assertThat(principal.organizationId()).isEqualTo("100002");
    }

    @Test
    void shouldFallbackToUserPermissionContextWhenSecurityContextIsEmpty() {
        userPermissionContext.onLogin("legacy-token", "alice", "admin", Set.of("agent:observe"));
        userPermissionContext.bind("legacy-token", "100003");

        AgentPrincipal principal = resolver.current().orElseThrow();

        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.role()).isEqualTo("admin");
        assertThat(principal.source()).isEqualTo(AgentPrincipal.Source.USER_PERMISSION_CONTEXT);
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.authorities()).contains("ROLE_ADMIN");
        assertThat(principal.permissions()).contains("agent:observe");
        assertThat(principal.organizationId()).isEqualTo("100003");
    }

    @Test
    void shouldIgnoreAnonymousAuthenticationAndFallbackToLegacyContext() {
        userPermissionContext.onLogin("legacy-token", "legacy-admin", "sys_admin", Set.of());
        userPermissionContext.bind("legacy-token", "100004");
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        ));

        AgentPrincipal principal = resolver.current().orElseThrow();

        assertThat(principal.username()).isEqualTo("legacy-admin");
        assertThat(principal.source()).isEqualTo(AgentPrincipal.Source.USER_PERMISSION_CONTEXT);
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNoAuthenticatedPrincipalExists() {
        assertThat(resolver.current()).isEmpty();
        assertThat(resolver.isCurrentAdmin()).isFalse();
    }

    private void clear() {
        SecurityContextHolder.clearContext();
        userPermissionContext.unbind();
    }
}
