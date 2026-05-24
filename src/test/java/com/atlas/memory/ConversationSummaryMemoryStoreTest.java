package com.atlas.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.20 长期记忆摘要存储测试。
 */
class ConversationSummaryMemoryStoreTest {

    @Test
    void append_shouldRedactSecretsAndKeepRecentTenItems() {
        ConversationSummaryMemoryStore store = new ConversationSummaryMemoryStore();
        for (int i = 0; i < 12; i++) {
            store.append("user-a", "conv-" + i,
                "第" + i + "次摘要 token=secret-" + i + " password:abc apiKey=xyz");
        }

        List<ConversationSummaryMemoryStore.MemorySummary> items = store.recent("user-a");
        assertThat(items).hasSize(ConversationSummaryMemoryStore.MAX_SUMMARIES_PER_USER);
        assertThat(items.get(0).conversationId()).isEqualTo("conv-11");
        assertThat(items.get(items.size() - 1).conversationId()).isEqualTo("conv-2");
        assertThat(items).allSatisfy(item -> assertThat(item.summary())
            .contains("[REDACTED]")
            .doesNotContain("secret-", "password:abc", "apiKey=xyz"));
    }
}
