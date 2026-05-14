/* ============================================================
 * Atlas v3.1 — 意图评分统一体系 代码落地包
 * 包含：ScoreNormalizer / IntentArbiter / 修改后的 IntentResult / 修改后的 IntentRouter
 * ============================================================ */

/* ── 1. ScoreNormalizer.java ────────────────────────────────── */
// src/main/java/com/atlas/intent/core/ScoreNormalizer.java
package com.atlas.intent.core;

/**
 * 统一归一化器：将 L1~L4 各层原始分数映射到 [0, 1] 校准空间。
 *
 * <p>设计原则：
 * <ul>
 *   <li>L1（Embedding）：Sigmoid 拉伸，放大中段区分度</li>
 *   <li>L2 Exact：固定 0.98，保留规则权威性但非绝对 1.0</li>
 *   <li>L3（LLM）：Temperature Scaling 占位，百分比转小数</li>
 *   <li>L4（Fuzzy）：乘以 0.75 封顶，弱化兜底印象</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-unified
 */
public final class ScoreNormalizer {

    private static final double L1_K = 10.0;   // Sigmoid 斜率
    private static final double L1_MU = 0.82;  // Sigmoid 中点
    private static final double L2_EXACT = 0.98;
    private static final double L4_CAP = 0.75;
    private static final double LLM_PCT_ALPHA = 0.95;

    private ScoreNormalizer() {}

    /**
     * 主归一化入口。
     *
     * @param rawScore 原始分数（L1=cosineSim, L2=1.0/关键词覆盖, L3=logit或pct, L4=fuzzyScore）
     * @param level    "L1" / "L2" / "L3" / "L4"
     * @param exact    仅对 L2/L4 有意义：是否为 exact 匹配
     */
    public static double normalize(double rawScore, String level, boolean exact) {
        return switch (level) {
            case "L1" -> normalizeL1(rawScore);
            case "L2" -> exact ? L2_EXACT : normalizeL4(rawScore);
            case "L3" -> normalizeL3(rawScore);
            case "L4" -> normalizeL4(rawScore);
            default -> Math.max(0.0, Math.min(1.0, rawScore));
        };
    }

    /**
     * L1：Sigmoid 拉伸
     * s = 1 / (1 + exp(-10 * (sim - 0.82)))
     */
    public static double normalizeL1(double sim) {
        double s = 1.0 / (1.0 + Math.exp(-L1_K * (sim - L1_MU)));
        return Math.min(1.0, Math.max(0.0, s));
    }

    /**
     * L2 Exact：固定规则分
     */
    public static double normalizeL2Exact() {
        return L2_EXACT;
    }

    /**
     * L3：LLM 输出校准
     * <ul>
     *   <li>若 rawScore > 1.0（百分比），视为 pct/100 * 0.95</li>
     *   <li>若 rawScore ∈ [0,1]，直接乘 0.95 做保守校准</li>
     * </ul>
     */
    public static double normalizeL3(double rawScore) {
        if (rawScore > 1.0) {
            // 假设传入的是百分比，如 95.0
            return Math.min(1.0, (rawScore / 100.0) * LLM_PCT_ALPHA);
        }
        return Math.min(1.0, rawScore * LLM_PCT_ALPHA);
    }

    /**
     * L4：模糊兜底封顶
     * s = raw * 0.75，最高 0.75
     */
    public static double normalizeL4(double rawScore) {
        return Math.min(L4_CAP, Math.max(0.0, rawScore * L4_CAP));
    }

    /**
     * 计算报告分（仅用于日志 / 监控，不参与路由决策）
     */
    public static double reportScore(double normalizedScore, String matchedLevel) {
        return switch (matchedLevel) {
            case "L1" -> normalizedScore * 0.95;
            case "L2" -> normalizedScore * 1.00; // 0.98 * 1.00 = 0.98
            case "L3" -> normalizedScore * 0.97;
            case "L4" -> normalizedScore * 0.75;
            default -> normalizedScore;
        };
    }

    /**
     * 快速对照：将 L1 raw sim 转换为一目了然的 normalized score
     *（供文档/日志表格用，非运行时必需）
     */
    public static void printL1Table() {
        System.out.println("raw_sim -> norm_score");
        for (double r = 0.50; r <= 1.00; r += 0.05) {
            System.out.printf("%.2f -> %.2f%n", r, normalizeL1(r));
        }
    }
}


/* ── 2. IntentArbiter.java ──────────────────────────────────── */
// src/main/java/com/atlas/intent/core/IntentArbiter.java
package com.atlas.intent.core;

import java.util.*;

/**
 * 意图冲突仲裁器：当多层返回不同 intentId 且分数均高时，按规则链裁决。
 *
 * <p>仲裁规则链（优先级由高到低）：</p>
 * <ol>
 *   <li>同 intentId 合并，取最大 score + 3% crossBoost</li>
 *   <li>同层决胜：同 matchedLevel 取分数高者</li>
 *   <li>L2 Exact 护城河：L2 exact ≥ 0.95 时，除非对方 ≥ 0.93 且 L2，否则 L2 胜</li>
 *   <li>极高语义压倒：对方 L2 ≥ 0.95，但己方 ≥ 0.96 且 p0/p1 高优，己方胜</li>
 *   <li>意图优先级兜底：p0/p1 允许比对方低最多 0.05</li>
 *   <li>显著差距：Δ ≥ 0.15，高分胜</li>
 *   <li>模糊区 fallback：层级优先级 [L2, L3, L1, L4]</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-unified
 */
public final class IntentArbiter {

    public static final double CROSS_BOOST = 0.03;
    public static final double CROSS_BOOST_CAP = 1.0;
    public static final double CANDIDATE_MIN_SCORE = 0.70;
    public static final double L2_MOAT_THRESHOLD = 0.95;
    public static final double L2_MOAT_OPPONENT_MIN = 0.93;
    public static final double SEMANTIC_OVERRULE_MIN = 0.96;
    public static final double LEVEL_WIN_MARGIN = -0.05;
    public static final double SIGNIFICANT_GAP = 0.15;

    private static final List<String> LAYER_PRIORITY =
        List.of("L2", "L3", "L1", "L4");

    private IntentArbiter() {}

    /**
     * 仲裁入口。
     *
     * @param results 多层匹配结果（含 normalizedScore）
     * @return 唯一最佳结果；若全部低于阈值则返回 null（由外部 fallback 到 unknown）
     */
    public static IntentResult arbitrate(List<IntentResult> results) {
        if (results == null || results.isEmpty()) return null;

        // Step 1: 合并同 intentId
        Map<String, IntentResult> merged = new LinkedHashMap<>();
        for (IntentResult r : results) {
            merged.merge(r.intentId(), r, (a, b) -> {
                double best = Math.max(a.confidence(), b.confidence());
                double boosted = Math.min(CROSS_BOOST_CAP, best + CROSS_BOOST);
                return new IntentResult(
                    a.intentId(), a.description(), boosted,
                    pickHigherLayer(a.matchedLevel(), b.matchedLevel()),
                    a.agent(), a.level(), a.rawQuery()
                );
            });
        }

        // Step 2: 筛选候选（≥ 0.70）
        List<IntentResult> candidates = merged.values().stream()
            .filter(r -> r.confidence() >= CANDIDATE_MIN_SCORE)
            .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
            .toList();

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // Step 3: 取 Top2
        IntentResult A = candidates.get(0);
        IntentResult B = candidates.get(1);
        double sA = A.confidence();
        double sB = B.confidence();

        // Rule A: 同层决胜
        if (Objects.equals(A.matchedLevel(), B.matchedLevel())) {
            return A;
        }

        // Rule B: L2 Exact 护城河
        if ("L2".equals(A.matchedLevel()) && sA >= L2_MOAT_THRESHOLD) {
            if (sB < L2_MOAT_OPPONENT_MIN || !"L2".equals(B.matchedLevel())) {
                return A;
            }
        }

        // Rule C: 极高语义压倒（反过来 B 是 L2 时）
        if ("L2".equals(B.matchedLevel()) && sB >= L2_MOAT_THRESHOLD) {
            if (sA >= SEMANTIC_OVERRULE_MIN && isHighPriority(A.level())) {
                return A;
            }
        }

        // Rule D: 意图优先级兜底
        int prioA = priorityOf(A.level());
        int prioB = priorityOf(B.level());
        double margin = sA - sB;
        if (prioA < prioB && margin >= LEVEL_WIN_MARGIN) {
            return A; // A 优先级更高（数值更小=越高），且分数未落后太多
        }

        // Rule E: 显著差距
        if (margin >= SIGNIFICANT_GAP) {
            return A;
        }

        // Rule F: 模糊区 fallback — 按层级优先级
        return layerPriorityWinner(A, B);
    }

    // ── 私有辅助 ──

    private static boolean isHighPriority(String level) {
        return "p0".equals(level) || "p1".equals(level);
    }

    private static int priorityOf(String level) {
        return switch (level == null ? "" : level) {
            case "p0" -> 0;
            case "p1" -> 1;
            case "p2" -> 2;
            case "p3" -> 3;
            default -> 99;
        };
    }

    private static String pickHigherLayer(String a, String b) {
        int ia = LAYER_PRIORITY.indexOf(a);
        int ib = LAYER_PRIORITY.indexOf(b);
        return ia <= ib ? a : b;
    }

    private static IntentResult layerPriorityWinner(IntentResult a, IntentResult b) {
        int ia = LAYER_PRIORITY.indexOf(a.matchedLevel());
        int ib = LAYER_PRIORITY.indexOf(b.matchedLevel());
        return ia <= ib ? a : b;
    }
}


/* ── 3. 修改后的 IntentResult.java ──────────────────────────── */
// src/main/java/com/atlas/intent/core/IntentResult.java
package com.atlas.intent.core;

/**
 * 意图分类统一结果（增强版）。
 *
 * <p>v3.1.0-unified 改动：
 * <ul>
 *   <li>confidence 字段语义升级为 "normalizedScore"，不再存储原始分数</li>
 *   <li>rawScore 仍保留用于调试，但当前 record 中不额外存储，由 Matcher 内部打印到日志</li>
 *   <li>matchedLevel 和 agent/level 继续保留</li>
 * </ul>
 *
 * <p>向后兼容：现有调用 `new IntentResult(id, desc, sim, "L1", agent, level, query)`
 * 仍然有效，只需将传入的 sim 改为已归一化后的值即可。</p>
 *
 * @param intentId     命中意图ID
 * @param description  意图描述
 * @param confidence   统一置信度 0.0~1.0（已归一化 / normalizedScore）
 * @param matchedLevel L1/L2/L3/L4（命中所在层）
 * @param agent        目标Agent
 * @param level        p0/p1/p2/p3
 * @param rawQuery     原始query
 */
public record IntentResult(
    String intentId, String description, double confidence,
    String matchedLevel, String agent, String level, String rawQuery
) {

    /**
     * 便捷工厂方法：由 Matcher 在内部调用，自动走 {@link ScoreNormalizer} 归一化。
     *
     * @param rawScore 该层原始分数
     * @param exact    仅 L2/L4 有效，是否 exact 匹配
     */
    public static IntentResult of(
        String intentId, String description,
        double rawScore, String matchedLevel,
        String agent, String level, String rawQuery,
        boolean exact
    ) {
        double norm = ScoreNormalizer.normalize(rawScore, matchedLevel, exact);
        return new IntentResult(intentId, description, norm, matchedLevel, agent, level, rawQuery);
    }

    /**
     * 同 intent 多层级交叉确认后的加分构造（由 IntentArbiter 调用）。
     */
    public IntentResult withBoostedConfidence(double newConfidence) {
        double safe = Math.min(1.0, Math.max(0.0, newConfidence));
        return new IntentResult(this.intentId, this.description, safe,
            this.matchedLevel, this.agent, this.level, this.rawQuery);
    }

    /**
     * 获取报告分（仅日志/监控用，不参与路由）。
     */
    public double reportScore() {
        return ScoreNormalizer.reportScore(this.confidence, this.matchedLevel);
    }
}


/* ── 4. 修改后的 IntentRouter.java ──────────────────────────── */
// src/main/java/com/atlas/intent/IntentRouter.java
package com.atlas.intent;

import com.atlas.intent.core.IntentArbiter;
import com.atlas.intent.core.IntentResult;
import com.atlas.intent.core.ScoreNormalizer;
import com.atlas.intent.embedding.EmbeddingConfig;
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
 * <p>与旧版最大差异：</p>
 * <ol>
 *   <li>每层结果经 {@link ScoreNormalizer} 归一化后再参与决策</li>
 *   <li>L1 高置信度（≥ 0.90）可短路；中低置信度收集后进入仲裁，不再无脑回退</li>
 *   <li>引入 {@link IntentArbiter} 处理多层级冲突</li>
 *   <li>L2 Exact 优先执行，但不再直接 return，而是进入仲裁候选池</li>
 *   <li>L4 结果也参与仲裁，确保 L1 极高分不会被 L4 错误覆盖</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-unified
 */
@Component
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private final EmbeddingMatcher embeddingMatcher;
    private final RuleMatcher ruleMatcher;
    private final EmbeddingConfig config;

    // L3 占位，未来注入 LLM 分类器
    // private final Optional<LlmMatcher> llmMatcher;

    public IntentRouter(EmbeddingMatcher embeddingMatcher,
                        RuleMatcher ruleMatcher,
                        EmbeddingConfig config) {
        this.embeddingMatcher = embeddingMatcher;
        this.ruleMatcher = ruleMatcher;
        this.config = config;
    }

    /**
     * 主路由入口。
     *
     * @param query 用户原始query
     * @return 最佳意图结果（不会返回 null，至少返回 unknown）
     */
    public IntentResult route(String query) {
        log.debug("[IntentRouter] 路由输入: {}", query);

        List<IntentResult> candidates = new ArrayList<>();

        // ── L1: Embedding 语义预筛 ──
        IntentResult l1 = embeddingMatcher.match(query);
        if (l1 != null) {
            double s1 = l1.confidence();
            log.debug("[IntentRouter] L1 raw={} norm={} intent={}",
                config.getMatchThreshold(), l1.confidence(), l1.intentId());

            if (s1 >= 0.90) {
                // L1 极高置信 → 短路（99% 场景命中）
                log.info("[IntentRouter] L1 高置信度直接命中: {} (norm={:.3f})",
                    l1.intentId(), s1);
                return l1;
            }
            candidates.add(l1);
        }

        // ── L2: 规则精确匹配 ──
        IntentResult l2 = ruleMatcher.exactMatch(query);
        if (l2 != null) {
            log.debug("[IntentRouter] L2 Exact 命中 intent={} (norm={:.3f})",
                l2.intentId(), l2.confidence());
            candidates.add(l2);
        }

        // ── L3: LLM 语义分类（预留） ──
        // IntentResult l3 = llmMatcher.map(m -> m.classify(query)).orElse(null);
        // if (l3 != null) candidates.add(l3);
        log.debug("[IntentRouter] L3(LLM) 预留中...");

        // ── L4: 模糊兜底 ──
        IntentResult l4 = ruleMatcher.fuzzyMatch(query);
        if (l4 != null) {
            log.debug("[IntentRouter] L4 兜底命中 intent={} (norm={:.3f})",
                l4.intentId(), l4.confidence());
            candidates.add(l4);
        }

        // ── 仲裁/降级 ──
        if (!candidates.isEmpty()) {
            // 若只有 1 个候选直接返回
            if (candidates.size() == 1) {
                IntentResult sole = candidates.get(0);
                log.info("[IntentRouter] 单层命中: {} ({} norm={:.3f})",
                    sole.intentId(), sole.matchedLevel(), sole.confidence());
                return sole;
            }

            // 多候选 → 仲裁器裁决
            IntentResult winner = IntentArbiter.arbitrate(candidates);
            if (winner != null) {
                log.info("[IntentRouter] 多候选仲裁胜出: {} ({} norm={:.3f} report={:.3f})",
                    winner.intentId(), winner.matchedLevel(), winner.confidence(), winner.reportScore());
                return winner;
            }
        }

        // ── 全层未命中 ──
        log.warn("[IntentRouter] 全层未命中，返回 unknown: {}", query);
        return unknown(query);
    }

    private IntentResult unknown(String query) {
        return new IntentResult("unknown", "未知意图", 0.0, "L4",
            "query", "p3", query);
    }
}
