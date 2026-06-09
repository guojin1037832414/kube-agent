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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5.29-1 真实 MVC 过滤链测试。
 *
 * <p>教学重点：源代码契约测试能防止配置被误删，但不能证明 Spring Security 真的拦截请求。
 * 这里用最小 Controller 切片验证四类入口：诊断面 admin-only、Actuator 管理面 admin-only、
 * 运行时 Agent API authenticated，以及未来未知 /api/agent/** 的默认认证兜底。</p>
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
    void observabilityAuditQueryShouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/agent/observability/audit/index"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/audit/id/aud_123"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/audit/trace/trc_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/health-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/top-tier/readiness-overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/memory-rag/readiness")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/memory-rag/citation-source-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/memory-rag/source-evidence-digest-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/replay/trace/trc_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/trace/trc_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/suite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/suites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/capabilities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/gate-bundle-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateLimit\":50,\"maxRecommendedCandidates\":5}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/trace-sets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/eval/trace-sets/phase1-core-golden/candidates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/curation-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/catalog-patch-proposal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateLimit\":50,\"maxRecommendedCandidates\":5}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/gate-bundle")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/observability/audit/index")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/audit/id/aud_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/health-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/top-tier/readiness-overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/memory-rag/readiness")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/memory-rag/citation-source-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/memory-rag/source-evidence-digest-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/replay/trace/trc_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/trace/trc_123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/suite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/suites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/suites/core-safety-smoke/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/capabilities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/gate-bundle-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateLimit\":50,\"maxRecommendedCandidates\":5}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/trace-sets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/observability/eval/trace-sets/phase1-core-golden/candidates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/gate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/curation-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/curation-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/catalog-patch-proposal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_11111111111111111111111111111111\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/catalog-patch-proposal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateLimit\":50,\"maxRecommendedCandidates\":5}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/gate-bundle")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceIds\":[\"trc_123\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/observability/eval/trace-sets/gate-bundle")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON))
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
    void unknownAgentApiShouldRequireAuthenticatedPrincipalByDefault() throws Exception {
        mockMvc.perform(get("/api/agent/chat"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isOk());
    }

    @Test
    void memoryAndMcpEndpointsRequireAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/api/agent/memory/summaries"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/mcp/manifest"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/mcp/governance/overview"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/memory/summaries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isOk());

        String sessionId = sessionStore.createSession("session-token", "session-user", "100002", "user", Set.of());
        mockMvc.perform(get("/api/agent/mcp/manifest")
                .header("X-Session-Id", sessionId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/mcp/governance/overview")
                .header("X-Session-Id", sessionId))
            .andExpect(status().isOk());
    }

    @Test
    void conversationEndpointsRequireAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/api/agent/conversations"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/conversations/conv-1"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
            .andExpect(status().isOk());

        String sessionId = sessionStore.createSession("session-token", "session-user", "100002", "user", Set.of());
        mockMvc.perform(get("/api/agent/conversations/conv-1")
                .header("X-Session-Id", sessionId))
            .andExpect(status().isOk());
    }

    @Test
    void chatStreamGraphAndHitlEndpointsRequireAuthenticatedPrincipal() throws Exception {
        String body = "{\"message\":\"查看节点状态\"}";

        mockMvc.perform(post("/api/agent/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/chat/graph")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/hitl/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"threadId\":\"run-1\",\"confirmToken\":\"token\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        String sessionId = sessionStore.createSession("session-token", "session-user", "100002", "user", Set.of());
        mockMvc.perform(post("/api/agent/chat/graph")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/agent/observability/snapshot")
        String observabilitySnapshot() {
            return "diagnostic";
        }

        @GetMapping("/api/agent/observability/audit/index")
        String observabilityAuditIndex() {
            return "audit-index";
        }

        @GetMapping("/api/agent/observability/audit/id/{id}")
        String observabilityAuditId(@PathVariable String id) {
            return id;
        }

        @GetMapping("/api/agent/observability/audit/trace/{id}")
        String observabilityAuditTrace(@PathVariable String id) {
            return id;
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/health-summary")
        String observabilityKubeManagerHttpOutletHealthSummary() {
            return "kube-manager-http-outlet-health-summary";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
        String observabilityKubeManagerWriteRetryReadiness() {
            return "kube-manager-write-retry-readiness";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
        String observabilityKubeManagerWriteIdempotencyContract() {
            return "kube-manager-write-idempotency-contract";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract")
        String observabilityKubeManagerWriteOperationSafetyContract() {
            return "kube-manager-write-operation-safety-contract";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract")
        String observabilityKubeManagerWriteRetryGovernanceContract() {
            return "kube-manager-write-retry-governance-contract";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract")
        String observabilityKubeManagerWriteReleaseGateContract() {
            return "kube-manager-write-release-gate-contract";
        }

        @GetMapping("/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview")
        String observabilityKubeManagerHttpOutletGovernanceWorkbenchOverview() {
            return "kube-manager-http-outlet-governance-workbench-overview";
        }

        @GetMapping("/api/agent/observability/top-tier/readiness-overview")
        String observabilityTopTierReadinessOverview() {
            return "top-tier-readiness-overview";
        }

        @GetMapping("/api/agent/observability/memory-rag/readiness")
        String observabilityMemoryRagReadiness() {
            return "memory-rag-readiness";
        }

        @GetMapping("/api/agent/observability/memory-rag/citation-source-contract")
        String observabilityMemoryRagCitationSourceContract() {
            return "memory-rag-citation-source-contract";
        }

        @GetMapping("/api/agent/observability/memory-rag/source-evidence-digest-contract")
        String observabilityMemoryRagSourceEvidenceDigestContract() {
            return "memory-rag-source-evidence-digest-contract";
        }

        @GetMapping("/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
        String observabilityMemoryRagDurableMemoryLifecycleContract() {
            return "memory-rag-durable-memory-lifecycle-contract";
        }

        @GetMapping("/api/agent/observability/replay/trace/{id}")
        String observabilityReplayTrace(@PathVariable String id) {
            return id;
        }

        @GetMapping("/api/agent/observability/eval/trace/{id}")
        String observabilityEvalTrace(@PathVariable String id) {
            return id;
        }

        @PostMapping("/api/agent/observability/eval/suite")
        String observabilityEvalSuite(@RequestBody(required = false) String body) {
            return body;
        }

        @GetMapping("/api/agent/observability/eval/suites")
        String observabilityEvalSuites() {
            return "eval-suites";
        }

        @PostMapping("/api/agent/observability/eval/suites/{id}/run")
        String observabilityEvalSuiteRun(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/suites/{id}/gate")
        String observabilityEvalSuiteGate(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @GetMapping("/api/agent/observability/eval/workbench/capabilities")
        String observabilityEvalWorkbenchCapabilities() {
            return "eval-workbench-capabilities";
        }

        @GetMapping("/api/agent/observability/eval/workbench/overview")
        String observabilityEvalWorkbenchOverview() {
            return "eval-workbench-overview";
        }

        @GetMapping("/api/agent/observability/eval/workbench/gate-bundle-summary")
        String observabilityEvalWorkbenchGateBundleSummary() {
            return "eval-workbench-gate-bundle-summary";
        }

        @GetMapping("/api/agent/observability/eval/workbench/trace-sets/{id}")
        String observabilityEvalWorkbenchTraceSetDetail(@PathVariable String id) {
            return id;
        }

        @PostMapping("/api/agent/observability/eval/workbench/trace-sets/{id}/promotion-workflow")
        String observabilityEvalWorkbenchPromotionWorkflow(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/workbench/trace-sets/{id}/catalog-patch-review")
        String observabilityEvalWorkbenchCatalogPatchReview(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @GetMapping("/api/agent/observability/eval/trace-sets")
        String observabilityEvalTraceSets() {
            return "eval-trace-sets";
        }

        @GetMapping("/api/agent/observability/eval/trace-sets/{id}/candidates")
        String observabilityEvalTraceSetCandidates(@PathVariable String id) {
            return id;
        }

        @PostMapping("/api/agent/observability/eval/trace-sets/{id}/gate")
        String observabilityEvalTraceSetGate(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/trace-sets/{id}/curation-review")
        String observabilityEvalTraceSetCurationReview(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/trace-sets/{id}/catalog-patch-proposal")
        String observabilityEvalTraceSetCatalogPatchProposal(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/trace-sets/{id}/promotion-workflow")
        String observabilityEvalTraceSetPromotionWorkflow(@PathVariable String id, @RequestBody(required = false) String body) {
            return id + body;
        }

        @PostMapping("/api/agent/observability/eval/trace-sets/gate-bundle")
        String observabilityEvalTraceSetGateBundle(@RequestBody(required = false) String body) {
            return body;
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

        @GetMapping("/api/agent/mcp/governance/overview")
        String mcpGovernanceOverview() {
            return "mcp-governance";
        }

        @GetMapping("/api/agent/conversations")
        String conversations() {
            return "conversations";
        }

        @GetMapping("/api/agent/conversations/{id}")
        String conversationDetail(@PathVariable String id) {
            return id;
        }

        @PostMapping("/api/agent/chat/stream")
        String chatStream() {
            return "stream";
        }

        @PostMapping("/api/agent/chat/graph")
        String chatGraph() {
            return "graph";
        }

        @PostMapping("/api/agent/hitl/confirm")
        String hitlConfirm() {
            return "hitl";
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
