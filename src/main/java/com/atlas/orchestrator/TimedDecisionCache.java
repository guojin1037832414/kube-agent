package com.atlas.orchestrator;

import com.atlas.brain.BrainDecision;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HITL 待确认决策缓存 — 带 TTL 自动过期 + 幂等性保护 + 安全审计。
 *
 * <p>替代裸 {@link ConcurrentHashMap}，解决以下问题：</p>
 * <ul>
 *   <li><b>TTL 过期</b>：5 分钟未确认，自动清理，避免内存泄漏</li>
 *   <li><b>最大容量</b>：最多缓存 1000 条，防内存撑爆</li>
 *   <li><b>幂等性</b>：同一会话重复 confirm 只处理一次</li>
 *   <li><b>审计日志</b>：每次 put/get/remove 记录完整操作轨迹</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <pre>
 * AtlasOrchestrator 触发 HITL → put(sessionId, decision) → 生成 confirmToken
 *      ↓
 * 前端显示确认弹窗（含 confirmToken + threadId）
 *      ↓
 * 用户确认 → POST /api/v1/hitl/confirm {threadId, token} → remove(sessionId, token) → resumeGraph
 *      ↓
 * 超时未确认 → Caffeine TTL 自动驱逐（调用 evictionListener 记录日志）
 * </pre>
 *
 * @author Atlas Team
 * @since 3.1.0-M1.5
 */
@Component
public class TimedDecisionCache {
    private static final Logger log = LoggerFactory.getLogger(TimedDecisionCache.class);

    /** 默认 TTL：5 分钟 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** 最大缓存条目数 */
    public static final int MAX_SIZE = 1000;

    /** Caffeine 缓存：key=sessionId, value=Entry */
    private final Cache<String, Entry> cache;

    /** 已处理的会话 ID 集合（去重用，不限制容量，定期清理） */
    private final Cache<String, Boolean> processedTokens;

    /**
     * 缓存条目结构：决策 + 确认 Token + 创建时间 + 过期时间
     */
    public record Entry(BrainDecision decision, String confirmToken, long createdAt, long expireAt) {}

    /**
     * 构造方法：初始化 Caffeine 缓存。
     */
    public TimedDecisionCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(DEFAULT_TTL)
                .evictionListener((String key, Entry value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause == com.github.benmanes.caffeine.cache.RemovalCause.EXPIRED) {
                        log.warn("[HITL] 会话 {} 决策已过期（{} 分钟未确认）, target={}",
                                key, DEFAULT_TTL.toMinutes(),
                                value != null ? value.decision().target() : "unknown");
                    }
                })
                .recordStats()
                .build();

        // 已处理 Token 集合，TTL 更长（10分钟），防重放攻击
        this.processedTokens = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE * 2L)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();

        log.info("[HITL] TimedDecisionCache 初始化完成: TTL={}, maxSize={}", DEFAULT_TTL, MAX_SIZE);
    }

    /**
     * 存入待确认决策，生成唯一确认 Token。
     *
     * @param sessionId 会话 ID
     * @param decision  BrainDecision（HITL_CONFIRM 或 ASK_CLARIFY）
     * @return 确认 Token（前端需要在确认时原样带回）
     */
    public String put(String sessionId, BrainDecision decision) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(decision, "decision 不能为空");

        // 生成防篡改 Token
        String confirmToken = generateToken(sessionId, decision);
        long now = System.currentTimeMillis();
        Entry entry = new Entry(decision, confirmToken, now, now + DEFAULT_TTL.toMillis());

        cache.put(sessionId, entry);
        log.info("[HITL] 决策已缓存: sessionId={}, actionType={}, target={}, token={}",
                sessionId, decision.actionType(), decision.target(), confirmToken.substring(0, 8) + "...");

        return confirmToken;
    }

    /**
     * 获取待确认决策（不删除，仅查看）。
     *
     * @param sessionId 会话 ID
     * @return 缓存条目，不存在或已过期返回 null
     */
    public Entry get(String sessionId) {
        Entry entry = cache.getIfPresent(sessionId);
        if (entry == null) {
            log.debug("[HITL] 未找到待确认决策: sessionId={}", sessionId);
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt()) {
            cache.invalidate(sessionId);
            log.warn("[HITL] 决策已过期: sessionId={}, target={}", sessionId, entry.decision().target());
            return null;
        }
        return entry;
    }

    /**
     * 安全取出并删除待确认决策（带 Token 校验 + 幂等性保护）。
     *
     * <p>这是 confirm 操作的核心方法：</p>
     * <ol>
     *   <li>校验 confirmToken 匹配 → 防用户伪造</li>
     *   <li>校验该会话未处理过 → 幂等性保护</li>
     *   <li>返回决策并从缓存删除</li>
     * </ol>
     *
     * @param sessionId    会话 ID
     * @param confirmToken 确认 Token
     * @return 决策结果，校验失败返回 null
     */
    public BrainDecision remove(String sessionId, String confirmToken) {
        // 幂等性检查：该会话是否已处理过
        if (processedTokens.getIfPresent(sessionId) != null) {
            log.warn("[HITL] 重复确认请求: sessionId={}", sessionId);
            return null;
        }

        Entry entry = cache.getIfPresent(sessionId);
        if (entry == null) {
            log.warn("[HITL] 待确认决策不存在或已过期: sessionId={}", sessionId);
            return null;
        }

        // Token 校验
        if (!entry.confirmToken().equals(confirmToken)) {
            log.error("[HITL] Token 不匹配! sessionId={}, 期望={}, 实际={}",
                    sessionId, entry.confirmToken().substring(0, 8), confirmToken.substring(0, 8));
            return null;
        }

        // 标记已处理（幂等性保护）
        processedTokens.put(sessionId, true);
        // 从主缓存删除
        cache.invalidate(sessionId);

        log.info("[HITL] 确认完成: sessionId={}, actionType={}, target={}",
                sessionId, entry.decision().actionType(), entry.decision().target());
        return entry.decision();
    }

    /**
     * 仅删除（用于 clarify 场景，无需 Token 校验）。
     */
    public BrainDecision removeForClarify(String sessionId) {
        Entry entry = cache.getIfPresent(sessionId);
        if (entry != null) {
            cache.invalidate(sessionId);
            log.info("[HITL] 澄清清理: sessionId={}", sessionId);
        }
        return entry != null ? entry.decision() : null;
    }

    /**
     * 获取缓存统计信息。
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    /**
     * 当前缓存中的决策数量。
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 生成防篡改确认 Token。
     *
     * <p>采用 sessionId + timestamp + UUID 前缀拼接后 SHA-256 截取，
     * 保证不可预测且单次有效。</p>
     */
    private String generateToken(String sessionId, BrainDecision decision) {
        String raw = sessionId + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID().toString().substring(0, 8);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 截取前 16 个 hex 字符作为 Token
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 不可能不存在，降级为简单字符串
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
