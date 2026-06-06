package com.atlas.store;

import com.atlas.dto.Conversation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话存储权限收敛测试。
 *
 * <p>conversationId 只能定位资源，不能代表授权；详情、改名、删除都必须再校验所属 userId。</p>
 */
class ConversationStoreTest {

    @Test
    void userScopedOperations_shouldRejectCrossUserConversationId() {
        ConversationStore store = new ConversationStore();
        Conversation ownerConversation = store.create("user-a", "owner title");

        assertThat(store.findByUserAndId("user-b", ownerConversation.id())).isEmpty();
        assertThat(store.updateTitleForUser("user-b", ownerConversation.id(), "hijacked")).isFalse();
        assertThat(store.removeForUser("user-b", ownerConversation.id())).isFalse();

        Conversation unchanged = store.findByUserAndId("user-a", ownerConversation.id()).orElseThrow();
        assertThat(unchanged.title()).isEqualTo("owner title");
    }

    @Test
    void userScopedOperations_shouldAllowOwner() {
        ConversationStore store = new ConversationStore();
        Conversation ownerConversation = store.create("user-a", "owner title");

        assertThat(store.updateTitleForUser("user-a", ownerConversation.id(), "new title")).isTrue();
        assertThat(store.findByUserAndId("user-a", ownerConversation.id()).orElseThrow().title())
            .isEqualTo("new title");
        assertThat(store.removeForUser("user-a", ownerConversation.id())).isTrue();
        assertThat(store.findByUserAndId("user-a", ownerConversation.id())).isEmpty();
    }
}
