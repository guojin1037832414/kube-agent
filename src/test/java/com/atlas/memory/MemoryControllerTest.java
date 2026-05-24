package com.atlas.memory;

import com.atlas.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.20 Memory Controller 安全契约测试。
 */
class MemoryControllerTest {

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
}
