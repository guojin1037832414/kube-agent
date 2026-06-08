package com.atlas.controller;

import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import com.atlas.dto.Conversation;
import com.atlas.store.ConversationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.29-5 Conversation Controller 可信主体归属测试。
 *
 * <p>教学重点：Conversation ID 和 X-Session-Id 都不是授权事实；会话 owner 必须来自
 * Spring Security / AgentPrincipalResolver 解析出的服务端可信主体。</p>
 */
class ConversationControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void create_shouldRejectWhenTrustedPrincipalIsMissing() {
        ConversationController controller = new ConversationController(
            new ConversationStore(),
            principalResolver()
        );

        ResponseEntity<?> response = controller.create("raw-session-id", Map.of("title", "demo"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) response.getBody()).isSuccess()).isFalse();
    }

    @Test
    void create_shouldUseAuthenticatedPrincipalInsteadOfRawSessionId() {
        ConversationStore store = new ConversationStore();
        ConversationController controller = new ConversationController(store, principalResolver());
        authenticate("security-user");

        ResponseEntity<?> response = controller.create("raw-session-id", Map.of("title", "可信会话"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(store.findByUser("security-user")).hasSize(1);
        assertThat(store.findByUser("security-user").get(0).title()).isEqualTo("可信会话");
        assertThat(store.findByUser("raw-session-id")).isEmpty();
    }

    @Test
    void detailAndUpdateAndDelete_shouldStayScopedToAuthenticatedPrincipal() {
        ConversationStore store = new ConversationStore();
        Conversation ownerConversation = store.create("owner-user", "owner title");
        ConversationController controller = new ConversationController(store, principalResolver());

        authenticate("other-user");
        assertThat(controller.detail("ignored-session", ownerConversation.id()).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.updateTitle("ignored-session", ownerConversation.id(), Map.of("title", "hijacked"))
            .getStatusCode().value()).isEqualTo(404);
        assertThat(controller.delete("ignored-session", ownerConversation.id()).getStatusCode().value()).isEqualTo(404);

        authenticate("owner-user");
        assertThat(controller.detail("ignored-session", ownerConversation.id()).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller.updateTitle("ignored-session", ownerConversation.id(), Map.of("title", "new title"))
            .getStatusCode().is2xxSuccessful()).isTrue();
        Conversation updated = store.findByUserAndId("owner-user", ownerConversation.id()).orElseThrow();
        assertThat(updated.title()).isEqualTo("new title");
        assertThat(controller.delete("ignored-session", ownerConversation.id()).getStatusCode().is2xxSuccessful()).isTrue();
    }

    private AgentPrincipalResolver principalResolver() {
        return new AgentPrincipalResolver(new UserPermissionContext());
    }

    private void authenticate(String username) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            username,
            null,
            "ROLE_USER"
        ));
    }
}
