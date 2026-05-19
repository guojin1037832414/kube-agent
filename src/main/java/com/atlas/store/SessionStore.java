package com.atlas.store;

import com.atlas.dto.SessionData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * 认证会话存储 — 基于 Caffeine 的内存缓存。
 *
 * <p>管理用户登录后的会话状态：</p>
 * <ul>
 *   <li>Key = sessionId（{@code ses_<22chars>}）</li>
 *   <li>Value = {@link SessionData}（含 JWT token、用户名、组织 ID 等）</li>
 *   <li>TTL = 30min，无访问自动过期（防止内存泄漏 + 安全清理）</li>
 *   <li>最大条目 = 5000（满足内部平台量级）</li>
 * </ul>
 *
 * <p>Session ID 生成策略：{@link SecureRandom} 128-bit + Base64 URL-safe 编码，
 * 格式 {@code ses_<22 characters>}，如 {@code ses_Aq9xLp3vMnK8WzQrT2YjF}。</p>
 *
 * <p><b>职责分离：</b></p>
 * <ul>
 *   <li>{@code Authorization: Bearer <JWT>} — 身份凭证，透传给 kube-manager</li>
 *   <li>{@code X-Session-Id: <sessionId>} — 业务会话标识，kube-agent 内部使用</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    /** 会话缓存 TTL：30 分钟 */
    public static final Duration SESSION_TTL = Duration.ofMinutes(30);

    /** 最大缓存条目数 */
    public static final int MAX_SIZE = 5000;

    /** Session ID 前缀 */
    private static final String SESSION_PREFIX = "ses_";

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Base64 URL-safe 编码器（无填充） */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Cache<String, SessionData> cache;

    public SessionStore() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(SESSION_TTL)
                .evictionListener((String key, SessionData value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause == com.github.benmanes.caffeine.cache.RemovalCause.EXPIRED) {
                        log.debug("[SessionStore] Session 已过期: sessionId={}", key);
                    }
                })
                .recordStats()
                .build();
        log.info("[SessionStore] 初始化完成: TTL={}, maxSize={}", SESSION_TTL, MAX_SIZE);
    }

    // ═══════════════════════════════════════════════════════════
    //  会话生命周期管理
    // ═══════════════════════════════════════════════════════════

    /**
     * 创建新会话并缓存。
     *
     * @param token        kube-manager 返回的 JWT Token
     * @param username     用户名
     * @param organizationId 组织 ID
     * @param role         角色标识
     * @param permissions  权限集合
     * @return 生成的 sessionId（格式 ses_xxxxx）
     */
    public String createSession(String token, String username, String organizationId, String role, Set<String> permissions) {
        String sessionId = generateSessionId();
        SessionData data = new SessionData(token, username, organizationId, role, permissions, System.currentTimeMillis());
        cache.put(sessionId, data);
        log.info("[SessionStore] 会话已创建: sessionId={}, user={}, org={}",
                mask(sessionId), username, organizationId);
        return sessionId;
    }

    /**
     * 根据 sessionId 查询会话数据。
     *
     * @return Optional 包装，不存在或已过期返回 empty
     */
    public Optional<SessionData> findById(String sessionId) {
        return Optional.ofNullable(cache.getIfPresent(sessionId));
    }

    /**
     * 删除会话（登出时调用）。
     */
    public void remove(String sessionId) {
        cache.invalidate(sessionId);
        log.debug("[SessionStore] 会话已删除: sessionId={}", mask(sessionId));
    }

    /**
     * 获取缓存统计信息。
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    /**
     * 当前活跃会话数（估算值）。
     */
    public long size() {
        return cache.estimatedSize();
    }

    // ═══════════════════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 生成安全的 Session ID。
     *
     * <p>SecureRandom 128-bit → 16 bytes → Base64 URL-safe 编码 → 22 chars</p>
     */
    private String generateSessionId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        String encoded = BASE64_ENCODER.encodeToString(bytes);
        // Base64(16 bytes) = 22 chars (without padding)
        return SESSION_PREFIX + encoded;
    }

    /**
     * Session ID 脱敏 — 日志中只显示前缀 + 前 4 位 + ...
     */
    private String mask(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) return "***";
        return sessionId.substring(0, 8) + "...";
    }
}
