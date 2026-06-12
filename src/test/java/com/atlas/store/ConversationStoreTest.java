package com.atlas.store;

import com.atlas.dto.Conversation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话存储权限收敛测试。
 *
 * <p>conversationId 只能定位资源，不能代表授权；详情、改名、删除都必须再校验所属 userId。</p>
 *
 * <p>中文说明：本测试保护 {@link ConversationStore} 的用户隔离契约。输入是测试中构造的
 * userId 与 conversationId，输出是 store 对详情读取、标题修改和删除操作的允许/拒绝结果。
 * 这能帮助学习者理解：资源 ID 只是定位会话，真正的访问判断必须绑定当前可信 Principal 的 owner。</p>
 *
 * <p>安全边界：本测试只访问内存 store，不创建真实登录态、不调用 LLM/Tool/MCP/RAG、
 * 不访问 kube-manager、不写 audit/memory，也不产生长期记忆。conversationId 不能作为授权凭证，
 * 也不能被当作 prompt、traceId、HITL token 或跨用户恢复聊天历史的依据。</p>
 */
class ConversationStoreTest {

    /**
     * 中文说明：恶意用户即使猜到或拿到别人的 conversationId，也不能读取、改名或删除该会话。
     * 安全边界：这里校验的是 store 层 owner 过滤，不代表 Controller 可以跳过 Principal 校验。
     */
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

    /**
     * 中文说明：会话拥有者可以正常维护自己的会话元数据，验证安全收敛不会误伤合法路径。
     * 安全边界：允许 owner 操作不等于允许 Agent 恢复历史 prompt 或把会话当作长期记忆。
     */
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
