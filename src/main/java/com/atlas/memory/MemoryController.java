package com.atlas.memory;

import com.atlas.auth.AgentPrincipal;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Optional;

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
    private final AgentPrincipalResolver principalResolver;

    public MemoryController(ConversationSummaryMemoryStore memoryStore) {
        this(memoryStore, null);
    }

    @Autowired
    public MemoryController(ConversationSummaryMemoryStore memoryStore,
                            AgentPrincipalResolver principalResolver) {
        this.memoryStore = memoryStore;
        this.principalResolver = principalResolver;
    }

    /**
     * 查询当前用户最近 10 条安全摘要。
     */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summaries(
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        List<ConversationSummaryMemoryStore.MemorySummary> summaries = memoryStore.recent(userId.get());
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
        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        ConversationSummaryMemoryStore.MemorySummary saved = memoryStore.append(
            userId.get(), request.conversationId(), request.summary());
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    private Optional<String> resolveUserId() {
        return currentPrincipal()
            .filter(AgentPrincipal::isAuthenticated)
            .map(AgentPrincipal::username)
            .filter(username -> username != null && !username.isBlank());
    }

    private Optional<AgentPrincipal> currentPrincipal() {
        if (principalResolver == null) {
            return Optional.empty();
        }
        return principalResolver.current();
    }

    /** 提交摘要请求体。 */
    public record MemorySummaryRequest(String conversationId, String summary) {
    }
}
