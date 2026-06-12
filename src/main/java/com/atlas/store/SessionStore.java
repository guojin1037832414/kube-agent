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
 * <p>中文说明：SessionStore 是登录后服务端可信身份上下文的短期缓存。它把 kube-manager
 * 返回的 token、可信 orgId、用户名和角色固化成 {@link SessionData}，供后续 Spring Security
 * 与 ThreadLocal 兼容桥恢复当前用户。</p>
 *
 * <p>安全边界：sessionId 只是 kube-agent 会话句柄，不是 JWT，也不是 Tool 写权限。
 * token 不会返回给前端，不应写入普通日志、Memory/RAG、prompt、eval fixture 或审计原文字段。
 * organizationId 必须由登录链路可信解析后写入，不能把前端请求体里的 orgId 原样缓存。</p>
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
     * <p>中文说明：调用者必须先完成 kube-manager 登录和可信 orgId 解析；本方法只负责生成
     * 随机 sessionId 并保存服务端会话事实，不负责再次鉴权。</p>
     *
     * <p>安全边界：日志只打印脱敏 sessionId、用户名和 orgId，不打印 token。若 orgId 无法可信解析，
     * 调用方应 fail-safe，不能传入默认值来“凑合创建会话”。</p>
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
     * <p>中文说明：返回 Optional 是为了让认证过滤器对缺失/过期会话显式 fail-closed。</p>
     *
     * @return Optional 包装，不存在或已过期返回 empty
     */
    public Optional<SessionData> findById(String sessionId) {
        return Optional.ofNullable(cache.getIfPresent(sessionId));
    }

    /**
     * 删除会话（登出时调用）。
     *
     * <p>安全边界：删除 session 只清理 kube-agent 本地会话缓存，不代表 kube-manager token
     * 已被远端吊销；真正 token 失效仍依赖 kube-manager。</p>
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
     *
     * <p>中文说明：sessionId 需要不可猜测，避免攻击者通过枚举会话句柄横向读取身份上下文。</p>
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
     *
     * <p>安全边界：sessionId 虽然不是 JWT，但仍是会话句柄，日志里必须按敏感材料处理。</p>
     */
    private String mask(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) return "***";
        return sessionId.substring(0, 8) + "...";
    }
}
