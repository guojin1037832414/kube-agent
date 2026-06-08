package com.atlas.auth;

import com.atlas.store.SessionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5.29-1 真实 MVC 过滤链测试。
 *
 * <p>教学重点：源代码契约测试能防止配置被误删，但不能证明 Spring Security 真的拦截请求。
 * 这里用最小 Controller 切片验证三类入口：诊断面 admin-only、Actuator 管理面 admin-only、
 * 普通 Agent API 暂时保持兼容放行。</p>
 */
@WebMvcTest(controllers = AgentSecurityConfigWebMvcTest.TestController.class)
@Import({
    AgentSecurityConfig.class,
    AgentSecurityConfigWebMvcTest.TestController.class,
    AgentSecurityConfigWebMvcTest.TestSecurityBeans.class
})
class AgentSecurityConfigWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserPermissionContext userPermissionContext;

    @Autowired
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        userPermissionContext.onLogin("admin-token", "root", "sys_admin", Set.of("agent:observe"));
        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
    }

    @AfterEach
    void tearDown() {
        userPermissionContext.unbind();
    }

    @Test
    void observabilitySnapshotShouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/agent/observability/snapshot"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/snapshot")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/snapshot")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsOpenButOtherActuatorEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/env")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());
    }

    @Test
    void ordinaryAgentApiRemainsOpenDuringIncrementalMigration() throws Exception {
        mockMvc.perform(get("/api/agent/chat"))
            .andExpect(status().isOk());
    }

    @Test
    void memoryAndMcpEndpointsRequireAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/api/agent/memory/summaries"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/mcp/manifest"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/memory/summaries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isOk());

        String sessionId = sessionStore.createSession("session-token", "session-user", "100002", "user", Set.of());
        mockMvc.perform(get("/api/agent/mcp/manifest")
                .header("X-Session-Id", sessionId))
            .andExpect(status().isOk());
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/agent/observability/snapshot")
        String observabilitySnapshot() {
            return "diagnostic";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @GetMapping("/actuator/env")
        String actuatorEnv() {
            return "env";
        }

        @GetMapping("/api/agent/chat")
        String chat() {
            return "chat";
        }

        @GetMapping("/api/agent/memory/summaries")
        String memorySummaries() {
            return "memory";
        }

        @GetMapping("/api/agent/mcp/manifest")
        String mcpManifest() {
            return "mcp";
        }
    }

    @Configuration
    public static class TestSecurityBeans {

        @Bean
        UserPermissionContext userPermissionContext() {
            return new UserPermissionContext();
        }

        @Bean
        SessionStore sessionStore() {
            return new SessionStore();
        }
    }
}
