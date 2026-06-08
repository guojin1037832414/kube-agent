package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent observability 诊断入口安全契约测试。
 */
class ObservabilityControllerTest {

    private final UserPermissionContext userPermissionContext = new UserPermissionContext();
    private final InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
    private final ObservabilityController controller = new ObservabilityController(
        new AgentMetricsService(new SimpleMeterRegistry()),
        auditRecorder,
        auditRecorder,
        new AgentPrincipalResolver(userPermissionContext)
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userPermissionContext.unbind();
    }

    @Test
    void snapshot_shouldRejectAnonymousUser() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void snapshot_shouldRejectNonAdminUser() {
        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void snapshot_shouldAllowAdminUserFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsKeys("metrics", "audit");
    }

    @Test
    void snapshot_shouldAllowLegacyAdminFallback() {
        userPermissionContext.onLogin("admin-token", "boss", "sys_admin", Set.of());
        userPermissionContext.bind("admin-token", "100002");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsKeys("metrics", "audit");
    }

    @Test
    void auditQuery_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> anonymous =
            controller.auditByAuditId("aud_missing");

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> user =
            controller.auditByTraceId("trc_missing", 10);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditQuery_shouldReturnRedactedAdminResults() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_admin_query",
            java.time.Instant.EPOCH,
            "trc_admin_query",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.GRAPH_TOOL_CALL,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            null,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> response =
            controller.auditByTraceId("trc_admin_query", 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        String bodyText = response.getBody().getData().toString();
        assertThat(bodyText)
            .contains("aud_admin_query", "trc_admin_query", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void auditIndex_shouldReturnMetadataForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.auditIndex();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .containsEntry("backend", "in-memory-ring-buffer")
            .containsEntry("containsRawEndpoints", false);
    }
}
