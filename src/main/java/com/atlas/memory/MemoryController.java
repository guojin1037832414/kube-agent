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
 * <p>中文说明：当前实现只接受调用方提交的 summary 字段，并由 {@link ConversationSummaryMemoryStore}
 * 做基础脱敏、截断和按用户隔离。它不保存服务端完整对话原文，也不会主动调用 LLM/RAG/vector store
 * 去生成或检索记忆。</p>
 *
 * <p>安全边界：这里的摘要是 caller-submitted bounded summary，只能作为轻量会话提示材料或学习样例；
 * 在完成 source custody、租户/隐私、删除/导出、reviewed trace、eval gate 之前，不能把它当成可信
 * RAG 证据或自动注入 prompt 的权威来源。</p>
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
     *
     * <p>中文说明：读取 owner 来自 {@link AgentPrincipalResolver}，不来自 {@code X-Session-Id}
     * 或请求体。返回的是当前用户自己的摘要缓存，不会跨用户聚合，也不触发外部网络/LLM/RAG。</p>
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
     *
     * <p>中文说明：request.summary 是调用方提供的文本，不是服务端验证过的事实。
     * 本接口只做长度控制、敏感词正则清洗和用户隔离；后续如果要进入 prompt，必须经过更严格的
     * Memory/RAG source evidence 和 eval gate。</p>
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
