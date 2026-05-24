package com.atlas.memory;

import com.atlas.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆摘要控制器 — M5.20 最小可用闭环。
 *
 * <p>只存储摘要，不存储完整用户原文、token、password、apiKey 等敏感信息。前端或编排层在会话结束后
 * 可以提交一条安全摘要；新会话开始前可读取最近摘要注入 Prompt。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@RestController
@RequestMapping("/api/agent/memory")
public class MemoryController {

    private final ConversationSummaryMemoryStore memoryStore;

    public MemoryController(ConversationSummaryMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 查询当前用户最近 10 条安全摘要。
     */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summaries(
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("X-Session-Id 不能为空"));
        }
        String userId = resolveUserId(sessionId);
        List<ConversationSummaryMemoryStore.MemorySummary> summaries = memoryStore.recent(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", true);
        data.put("mode", "safe-summary-memory");
        data.put("maxItems", ConversationSummaryMemoryStore.MAX_SUMMARIES_PER_USER);
        data.put("items", summaries);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * 追加当前用户的一条会话摘要。
     */
    @PostMapping("/summaries")
    public ResponseEntity<ApiResponse<ConversationSummaryMemoryStore.MemorySummary>> append(
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
        @RequestBody MemorySummaryRequest request) {
        if (request == null || request.summary() == null || request.summary().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("summary 不能为空"));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("X-Session-Id 不能为空"));
        }
        ConversationSummaryMemoryStore.MemorySummary saved = memoryStore.append(
            resolveUserId(sessionId), request.conversationId(), request.summary());
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    private String resolveUserId(String sessionId) {
        return sessionId != null && !sessionId.isBlank() ? sessionId : "anonymous";
    }

    /** 提交摘要请求体。 */
    public record MemorySummaryRequest(String conversationId, String summary) {
    }
}
