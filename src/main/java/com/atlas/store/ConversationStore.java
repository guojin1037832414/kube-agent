package com.atlas.store;

import com.atlas.dto.Conversation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务会话（Conversation）存储 — 基于 Caffeine 的内存缓存。
 *
 * <p>管理前端聊天会话的元数据（不存储消息内容，消息由前端 Pinia 管理）：</p>
 * <ul>
 *   <li>Key = conversationId（UUID）</li>
 *   <li>Value = {@link Conversation}（id, userId, title, messageCount, createdAt, updatedAt）</li>
 *   <li>TTL = 24h，无访问自动过期</li>
 *   <li>最大条目 = 5000</li>
 * </ul>
 *
 * <p>会话列表按 {@code updatedAt} 倒序排列（最新会话在前），满足前端侧边栏展示需求。</p>
 *
 * <p>中文说明：ConversationStore 只保存当前用户聊天会话的轻量元数据，帮助前端组织侧边栏。
 * 它不是长期记忆、不是 RAG 文档库、不是审计日志，也不是 Agent trace store。</p>
 *
 * <p>安全边界：conversationId 只用于定位资源，不能当授权凭证。凡是详情、改名、删除这类
 * 用户可见操作，都必须走带 userId 的收敛方法或由 Controller 先用当前 Principal 过滤。
 * 标题来自用户/前端输入，不能作为 prompt 权威、eval 证据或 release 事实。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
@Component
public class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);

    /** 会话元数据缓存 TTL：24 小时 */
    public static final Duration CONVERSATION_TTL = Duration.ofHours(24);

    /** 最大缓存条目数 */
    public static final int MAX_SIZE = 5000;

    private final Cache<String, Conversation> cache;

    public ConversationStore() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(CONVERSATION_TTL)
                .evictionListener((String key, Conversation value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause == com.github.benmanes.caffeine.cache.RemovalCause.EXPIRED) {
                        log.debug("[ConversationStore] 会话已过期: convId={}, title={}", key,
                                value != null ? value.title() : "unknown");
                    }
                })
                .recordStats()
                .build();
        log.info("[ConversationStore] 初始化完成: TTL={}, maxSize={}", CONVERSATION_TTL, MAX_SIZE);
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD 操作
    // ═══════════════════════════════════════════════════════════

    /**
     * 创建新会话。
     *
     * <p>中文说明：userId 必须来自服务端可信 Principal 或登录会话，不应直接信任 X-Session-Id、
     * 请求体 userId 或 LLM 生成字段。</p>
     *
     * <p>安全边界：title 只是展示字段；这里不写 prompt、不写消息正文、不写 Memory/RAG。</p>
     *
     * @param userId  所属用户标识（sessionId 或 username）
     * @param title   会话标题，空时默认 "新会话"
     * @return 新创建的 Conversation
     */
    public Conversation create(String userId, String title) {
        String id = "conv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String actualTitle = (title == null || title.isBlank()) ? "新会话" : title;
        long now = System.currentTimeMillis();
        Conversation conv = new Conversation(id, userId, actualTitle, 0, now, now);
        cache.put(id, conv);
        log.debug("[ConversationStore] 会话已创建: convId={}, user={}, title={}", id, userId, actualTitle);
        return conv;
    }

    /**
     * 根据 ID 查询。
     *
     * <p>安全边界：该方法只做资源定位，不做授权收敛；Controller 对外暴露时应优先使用
     * {@link #findByUserAndId(String, String)}。</p>
     */
    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }

    /**
     * 按“用户 + 会话 ID”查询。
     *
     * <p>会话 ID 本身不是权限凭证，所有详情、改名、删除入口都必须用当前用户再收敛一次，
     * 防止别人猜到/拿到 conversationId 后横向读取或修改会话元数据。</p>
     */
    public Optional<Conversation> findByUserAndId(String userId, String id) {
        return findById(id)
                .filter(c -> c.userId().equals(userId));
    }

    /**
     * 列出某用户的全部会话，按 updatedAt 倒序排列（最新在前）。
     *
     * <p>中文说明：列表必须按当前可信用户过滤，避免侧边栏泄露其他用户 conversation 元数据。</p>
     */
    public List<Conversation> findByUser(String userId) {
        return cache.asMap().values().stream()
                .filter(c -> c.userId().equals(userId))
                .sorted(Comparator.comparingLong(Conversation::updatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 更新会话标题。
     *
     * <p>安全边界：该方法不校验 owner，只适合内部已经完成归属判断的路径；
     * 对 Controller 暴露请使用 {@link #updateTitleForUser(String, String, String)}。</p>
     */
    public boolean updateTitle(String id, String newTitle) {
        Conversation existing = cache.getIfPresent(id);
        if (existing == null) return false;
        Conversation updated = new Conversation(
                existing.id(),
                existing.userId(),
                newTitle,
                existing.messageCount(),
                existing.createdAt(),
                System.currentTimeMillis()
        );
        cache.put(id, updated);
        log.debug("[ConversationStore] 标题已更新: convId={}, title={}", id, newTitle);
        return true;
    }

    /**
     * 仅允许会话所属用户更新标题。
     *
     * <p>中文说明：这是对外写元数据时的安全入口，conversationId 命中后还必须匹配 owner。</p>
     */
    public boolean updateTitleForUser(String userId, String id, String newTitle) {
        Conversation existing = cache.getIfPresent(id);
        if (existing == null || !existing.userId().equals(userId)) return false;
        return updateTitle(id, newTitle);
    }

    /**
     * 更新消息计数。
     *
     * <p>中文说明：messageCount 是前端展示计数，不代表服务端保存了消息，也不能证明 Agent
     * 成功执行了某个任务。</p>
     */
    public boolean updateMessageCount(String id, int count) {
        Conversation existing = cache.getIfPresent(id);
        if (existing == null) return false;
        Conversation updated = new Conversation(
                existing.id(),
                existing.userId(),
                existing.title(),
                count,
                existing.createdAt(),
                System.currentTimeMillis()
        );
        cache.put(id, updated);
        return true;
    }

    /**
     * 删除会话。
     *
     * <p>安全边界：该方法不校验 owner，只适合内部已确认归属的路径；用户入口必须使用
     * {@link #removeForUser(String, String)}。</p>
     */
    public boolean remove(String id) {
        boolean existed = cache.getIfPresent(id) != null;
        cache.invalidate(id);
        if (existed) {
            log.debug("[ConversationStore] 会话已删除: convId={}", id);
        }
        return existed;
    }

    /**
     * 仅允许会话所属用户删除会话。
     *
     * <p>中文说明：删除只清理会话元数据，不删除 Memory/RAG、audit 或 eval evidence；
     * 这些证据链需要各自的生命周期策略。</p>
     */
    public boolean removeForUser(String userId, String id) {
        Conversation existing = cache.getIfPresent(id);
        if (existing == null || !existing.userId().equals(userId)) return false;
        return remove(id);
    }

    /**
     * 当前缓存中的会话数（估算值）。
     */
    public long size() {
        return cache.estimatedSize();
    }
}
