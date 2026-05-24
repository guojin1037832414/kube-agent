package com.atlas.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 长期记忆摘要存储 — M5.20 最小可用版。
 *
 * <p>当前版本采用 Caffeine 内存缓存，保存“安全摘要”而非完整对话原文。它的目标不是替代
 * Redis/VectorDB，而是在 M5 阶段先形成可验证闭环：</p>
 * <ul>
 *   <li>按用户保存最近 10 条摘要；</li>
 *   <li>自动清洗 token、password、apiKey 等敏感片段；</li>
 *   <li>后续可平滑替换为 Redis/Chroma 持久化实现。</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@Component
public class ConversationSummaryMemoryStore {

    /** 每个用户最多保存的摘要条数。 */
    public static final int MAX_SUMMARIES_PER_USER = 10;

    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)(api[-_ ]?key|token|password|secret|authorization)\s*[:=]\s*[^\s,;]+"
    );

    private final Cache<String, Deque<MemorySummary>> cache;

    public ConversationSummaryMemoryStore() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofDays(30))
            .recordStats()
            .build();
    }

    /**
     * 追加一条安全摘要。
     */
    public MemorySummary append(String userId, String conversationId, String summary) {
        String safeUserId = normalizeUserId(userId);
        String safeConversationId = safeText(conversationId, 120);
        String safeSummary = safeText(summary, 2000);
        MemorySummary item = new MemorySummary(safeConversationId, safeSummary, System.currentTimeMillis());
        Deque<MemorySummary> deque = cache.get(safeUserId, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addFirst(item);
            while (deque.size() > MAX_SUMMARIES_PER_USER) {
                deque.removeLast();
            }
        }
        return item;
    }

    /**
     * 查询某用户最近摘要，最新在前。
     */
    public List<MemorySummary> recent(String userId) {
        Deque<MemorySummary> deque = cache.getIfPresent(normalizeUserId(userId));
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    /**
     * 当前缓存用户数。
     */
    public long userCount() {
        return cache.estimatedSize();
    }

    private String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : safeText(userId, 120).toLowerCase(Locale.ROOT);
    }

    private String safeText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = SECRET_PATTERN.matcher(text).replaceAll("$1=[REDACTED]").strip();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength) + "... [TRUNCATED]";
    }

    /**
     * 安全记忆摘要记录。
     */
    public record MemorySummary(String conversationId, String summary, long createdAt) {
    }
}
