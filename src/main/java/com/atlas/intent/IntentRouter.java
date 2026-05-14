package com.atlas.intent;

import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentArbiter;
import com.atlas.intent.core.IntentResult;
import com.atlas.intent.core.ScoreNormalizer;
import com.atlas.intent.embedding.EmbeddingConfig;
import com.atlas.intent.llm.L3IntentClassifier;
import com.atlas.intent.rule.RuleMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 意图路由器 — v3.1 统一评分版。
 *
 * <p>核心理念：<b>收集 → 归一化 → 仲裁</b></p>
 * <ol>
 *   <li><b>收集</b>：L1(L2/L3/L4) 多层分别执行，收集所有命中结果</li>
 *   <li><b>归一化</b>：{@link ScoreNormalizer} 将各层原始分数映射到统一 [0,1] 空间</li>
 *   <li><b>仲裁</b>：{@link IntentArbiter} 处理多层冲突，输出唯一最佳结果</li>
 * </ol>
 *
 * <p>L1 极高置信度（≥ 0.90）可跳过 L2/L3/L4 短路返回，优化 99% 高频场景。</p>
 *
 * <p>降级策略：任何一层 Matcher 为 null（如 Embedding 不可用、LLM 故障），
 * 直接跳过该层，不影响其他层结果收集。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    // ── 各层 Matcher，允许 null（表示该层不可用） ──────────────────
    private final EmbeddingMatcher embeddingMatcher;    // L1
    private final RuleMatcher ruleMatcher;              // L2 + L4
    private final Optional<L3IntentClassifier> l3Classifier; // L3（可能为 empty）
    private final EmbeddingConfig config;

    public IntentRouter(EmbeddingMatcher embeddingMatcher,
                        RuleMatcher ruleMatcher,
                        L3IntentClassifier l3Classifier,
                        EmbeddingConfig config) {
        this.embeddingMatcher = embeddingMatcher;
        this.ruleMatcher = ruleMatcher;
        this.l3Classifier = Optional.ofNullable(l3Classifier);
        this.config = config;
    }

    /**
     * 主路由入口：按收集→归一化→仲裁模式执行。
     *
     * @param query 用户原始 query
     * @return 最佳意图结果（不会返回 null，至少返回 unknown）
     */
    public IntentResult route(String query) {
        if (query == null || query.isBlank()) {
            log.warn("[IntentRouter] 收到空 query");
            return unknown(query);
        }

        log.debug("[IntentRouter] 路由输入: {}", query);
        List<IntentResult> candidates = new ArrayList<>();

        // ═══════════════════════════════════════════
        // L1: Embedding 语义预筛
        // ═══════════════════════════════════════════
        IntentResult l1 = safeMatch(() -> {
            if (embeddingMatcher == null) return null;
            IntentResult r = embeddingMatcher.match(query);
            if (r == null) return null;
            // L1 归一化：原始 cosine sim → sigmoid
            return r.withNormalizedScore(ScoreNormalizer.normalizeL1(r.confidence()));
        }, "L1");

        if (l1 != null) {
            // L1 极高置信度 → 短路（99% 高频优化）
            if (l1.confidence() >= 0.90) {
                log.info("[IntentRouter] L1 高置信度直接命中: {} (norm={:.3f})",
                         l1.intentId(), l1.confidence());
                return l1;
            }
            candidates.add(l1);
            log.debug("[IntentRouter] L1 中低置信度 (norm={:.3f})，进入仲裁候选池", l1.confidence());
        }

        // ═══════════════════════════════════════════
        // L2: 规则精确匹配
        // ═══════════════════════════════════════════
        IntentResult l2 = safeMatch(() -> {
            if (ruleMatcher == null) return null;
            IntentResult r = ruleMatcher.exactMatch(query);
            if (r == null) return null;
            // L2 Exact 归一化：固定 0.98
            return r.withNormalizedScore(ScoreNormalizer.normalizeL2Exact());
        }, "L2");

        if (l2 != null) {
            // L2 精确匹配直接短路 — 零 token、零 LLM 延迟
            log.info("[IntentRouter] L2 精确命中短路: {} (confidence={:.3f})",
                     l2.intentId(), l2.confidence());
            return l2;
        }

        // ═══════════════════════════════════════════
        // L3: LLM 语义分类
        // ═══════════════════════════════════════════
        IntentResult l3 = safeMatch(() -> {
            L3IntentClassifier classifier = l3Classifier.orElse(null);
            if (classifier == null) return null;
            IntentResult r = classifier.classify(query);
            if (r == null) return null;
            // L3 归一化：LLM confidence 保守校准
            return r.withNormalizedScore(ScoreNormalizer.normalizeL3(r.confidence()));
        }, "L3");

        if (l3 != null) {
            candidates.add(l3);
            log.debug("[IntentRouter] L3 LLM 命中: {} (norm={:.3f})", l3.intentId(), l3.confidence());
        }

        // ═══════════════════════════════════════════
        // L4: 模糊兜底
        // ═══════════════════════════════════════════
        IntentResult l4 = safeMatch(() -> {
            if (ruleMatcher == null) return null;
            IntentResult r = ruleMatcher.fuzzyMatch(query);
            if (r == null) return null;
            // L4 归一化：模糊分 → 封顶
            return r.withNormalizedScore(ScoreNormalizer.normalizeL4(r.confidence()));
        }, "L4");

        if (l4 != null) {
            candidates.add(l4);
            log.debug("[IntentRouter] L4 兜底命中: {} (norm={:.3f})", l4.intentId(), l4.confidence());
        }

        // ═══════════════════════════════════════════
        // 仲裁 / 降级
        // ═══════════════════════════════════════════
        if (!candidates.isEmpty()) {
            // 仅 1 个候选直接返回
            if (candidates.size() == 1) {
                IntentResult sole = candidates.get(0);
                log.info("[IntentRouter] 单层命中: {} ({} norm={:.3f} report={:.3f})",
                         sole.intentId(), sole.matchedLevel(), sole.confidence(), sole.reportScore());
                return sole;
            }

            // 多候选 → 仲裁器裁决
            IntentResult winner = IntentArbiter.arbitrate(candidates);
            if (winner != null) {
                log.info("[IntentRouter] 仲裁胜出: {} ({} norm={:.3f} report={:.3f}) 候选数={}",
                         winner.intentId(), winner.matchedLevel(), winner.confidence(),
                         winner.reportScore(), candidates.size());
                return winner;
            }
        }

        // ═══════════════════════════════════════════
        // 全层未命中
        // ═══════════════════════════════════════════
        log.warn("[IntentRouter] 全层未命中，返回 unknown: {}", query);
        return unknown(query);
    }

    // ── 辅助方法 ────────────────────────────────────────────────

    /**
     * 安全执行某层匹配，捕获所有异常防止一层故障拖垮整个路由链。
     */
    private IntentResult safeMatch(java.util.function.Supplier<IntentResult> matcher, String layer) {
        try {
            return matcher.get();
        } catch (Exception e) {
            log.error("[IntentRouter] {} 匹配异常，跳过: {}", layer, e.getMessage());
            return null;
        }
    }

    /**
     * Unknown 兜底结果。
     */
    private IntentResult unknown(String query) {
        return new IntentResult("unknown", "未知意图", 0.0, "L4",
            "query", "p3", query);
    }
}
