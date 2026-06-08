package com.atlas.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security 配置的源代码契约测试。
 *
 * <p>这里先不用完整 Spring MVC 上下文，是为了避免把 M5.29-1 的小步迁移变成大规模
 * Controller wiring 测试。配置进入主线后，后续再逐步补 MockMvc / 方法级授权测试。</p>
 */
class AgentSecurityConfigContractTest {

    private static final Path SOURCE = Path.of(
        "src/main/java/com/atlas/auth/AgentSecurityConfig.java"
    );

    @Test
    void shouldDeclareStandardSecurityFilterChainAndStatelessSessionPolicy() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("SecurityFilterChain agentSecurityFilterChain");
        assertThat(source).contains("SessionCreationPolicy.STATELESS");
        assertThat(source).contains("UserDetailsService agentUserDetailsService");
        assertThat(source).contains("kube-agent only accepts kube-manager Bearer sessions");
        assertThat(source).contains("csrf(AbstractHttpConfigurer::disable)");
        assertThat(source).contains("httpBasic(AbstractHttpConfigurer::disable)");
        assertThat(source).contains("formLogin(AbstractHttpConfigurer::disable)");
        assertThat(source).contains("logout(AbstractHttpConfigurer::disable)");
        assertThat(source).contains("new AuthTokenFilter(userPermissionContext, sessionStore)");
    }

    @Test
    void shouldProtectAdminDiagnosticsRuntimeSseAndDefaultAgentApiSurface() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("@EnableMethodSecurity");
        assertThat(source).contains(".requestMatchers(\"/api/agent/observability/**\").hasAnyRole(\"ADMIN\", \"SYS_ADMIN\")");
        assertThat(source)
            .contains("\"/api/agent/chat/stream\"")
            .contains("\"/api/agent/chat/graph\"")
            .contains("\"/api/agent/hitl/**\"")
            .contains("\"/api/agent/memory/**\"")
            .contains("\"/api/agent/mcp/**\"")
            .contains("\"/api/agent/conversations\"")
            .contains("\"/api/agent/conversations/**\"")
            .contains(").authenticated()");
        assertThat(source).contains(".requestMatchers(\"/api/agent/**\").authenticated()");
        assertThat(source).contains(".requestMatchers(\"/actuator/health\", \"/actuator/info\").permitAll()");
        assertThat(source).contains(".requestMatchers(\"/actuator/**\").hasAnyRole(\"ADMIN\", \"SYS_ADMIN\")");
        assertThat(source).contains(".anyRequest().permitAll()");
    }

    @Test
    void shouldKeepLoginSessionBootstrapEndpointsOpenDuringIncrementalMigration() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source)
            .contains("/api/agent/login")
            .contains("/api/agent/logout")
            .contains("/api/agent/me")
            .contains("/api/agent/health");
    }
}
