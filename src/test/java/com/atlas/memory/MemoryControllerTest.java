package com.atlas.memory;

import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.20 Memory Controller 安全契约测试。
 */
class MemoryControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void summaries_shouldRejectMissingSessionId() {
        MemoryController controller = new MemoryController(new ConversationSummaryMemoryStore());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.summaries(null);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void append_shouldRejectMissingSessionId() {
        MemoryController controller = new MemoryController(new ConversationSummaryMemoryStore());
        MemoryController.MemorySummaryRequest request = new MemoryController.MemorySummaryRequest("conv-1", "安全摘要");

        ResponseEntity<ApiResponse<ConversationSummaryMemoryStore.MemorySummary>> response = controller.append(null, request);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void summaries_shouldUseAuthenticatedPrincipalWithoutRawSessionId() {
        ConversationSummaryMemoryStore store = new ConversationSummaryMemoryStore();
        store.append("security-user", "conv-1", "安全摘要");
        MemoryController controller = new MemoryController(store, principalResolver());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "security-user",
            null,
            "ROLE_USER",
            "agent:memory:read"
        ));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.summaries(null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<ConversationSummaryMemoryStore.MemorySummary> items =
            (List<ConversationSummaryMemoryStore.MemorySummary>) response.getBody().getData().get("items");
        assertThat(items)
            .extracting(ConversationSummaryMemoryStore.MemorySummary::summary)
            .containsExactly("安全摘要");
    }

    @Test
    void append_shouldPreferAuthenticatedPrincipalOverRawSessionId() {
        ConversationSummaryMemoryStore store = new ConversationSummaryMemoryStore();
        MemoryController controller = new MemoryController(store, principalResolver());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "security-user",
            null,
            "ROLE_USER",
            "agent:memory:write"
        ));
        MemoryController.MemorySummaryRequest request = new MemoryController.MemorySummaryRequest("conv-1", "可信身份摘要");

        ResponseEntity<ApiResponse<ConversationSummaryMemoryStore.MemorySummary>> response =
            controller.append("raw-session-id", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(store.recent("security-user"))
            .extracting(ConversationSummaryMemoryStore.MemorySummary::summary)
            .containsExactly("可信身份摘要");
        assertThat(store.recent("raw-session-id")).isEmpty();
    }

    private AgentPrincipalResolver principalResolver() {
        return new AgentPrincipalResolver(new UserPermissionContext());
    }
}
