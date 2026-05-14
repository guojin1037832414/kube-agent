package com.atlas.intent.core;

import java.util.*;

/**
 * 意图冲突仲裁器：当多层返回不同 intentId 且分数均高时，按规则链裁决。
 *
 * <p>仲裁规则链（优先级由高到低）：</p>
 * <ol>
 *   <li>同 intentId 合并，取 max score + 3% crossBoost（多层交叉确认）</li>
 *   <li>同层决胜：同 matchedLevel 取高者</li>
 *   <li>L2 Exact 护城河：L2 exact ≥ 0.95 时，除非对方也是 L2 且 ≥ 0.93，否则 L2 胜</li>
 *   <li>极高语义压倒：对方 L2 ≥ 0.95，但己方 L1/L3 ≥ 0.96 且 p0/p1 高优意图，己方胜</li>
 *   <li>意图优先级兜底：高优意图（数值小）允许落后 ≤ 0.05</li>
 *   <li>显著差距：Δ ≥ 0.15，高分直接胜出</li>
 *   <li>模糊区 fallback：层级优先级 [L2, L3, L1, L4]</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public final class IntentArbiter {

    // ── 仲裁常量 ────────────────────────────────────────────────
    public static final double CROSS_BOOST = 0.03;
    public static final double CROSS_BOOST_CAP = 1.0;
    public static final double CANDIDATE_MIN = 0.70;       // 候选最低分
    public static final double L2_MOAT_THRESHOLD = 0.95;   // L2 护城河触发线
    public static final double L2_MOAT_OPPONENT_MIN = 0.93;
    public static final double SEMANTIC_OVERRULE_MIN = 0.96;  // 极高语义压倒线
    public static final double LEVEL_WIN_MARGIN = -0.05;   // 高优意图允许落后幅度
    public static final double SIGNIFICANT_GAP = 0.15;     // 显著差距阈值

    // 层级优先级：L2 > L3 > L1 > L4（前驱权威，兜底最低）
    private static final List<String> LAYER_PRIORITY =
        List.of("L2", "L3", "L1", "L4");

    private IntentArbiter() {}

    /**
     * 仲裁入口。
     *
     * @param results 多层匹配结果
     * @return 唯一最佳结果；全部低于阈值则返回 null（由外部 fallback 到 unknown）
     */
    public static IntentResult arbitrate(List<IntentResult> results) {
        if (results == null || results.isEmpty()) return null;

        // Step 1: 合并同 intentId
        Map<String, IntentResult> merged = mergeByIntentId(results);

        // Step 2: 筛选候选（≥ 0.70）
        List<IntentResult> candidates = merged.values().stream()
            .filter(r -> r.confidence() >= CANDIDATE_MIN)
            .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
            .toList();

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // Step 3: 取 Top2
        IntentResult A = candidates.get(0);
        IntentResult B = candidates.get(1);
        double sA = A.confidence();
        double sB = B.confidence();

        // ── 规则链 ────────────────────────────────────────────

        // Rule A: 同层决胜
        if (Objects.equals(A.matchedLevel(), B.matchedLevel())) {
            return A; // A 分数更高
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
                return A; // A 语义极高，且是 p0/p1 高优意图
            }
        }

        // Rule D: 意图优先级兜底
        int prioA = priorityOf(A.level());
        int prioB = priorityOf(B.level());
        double margin = sA - sB;
        if (prioA < prioB && margin >= LEVEL_WIN_MARGIN) {
            return A; // A 优先级更高，且未落后超过 0.05
        }

        // Rule E: 显著差距
        if (margin >= SIGNIFICANT_GAP) {
            return A;
        }

        // Rule F: 模糊区 fallback — 层级优先级决胜
        return layerPriorityWinner(A, B);
    }

    // ── 私有方法 ────────────────────────────────────────────────

    /**
     * 合并同 intentId，取最高分 + crossBoost。
     */
    private static Map<String, IntentResult> mergeByIntentId(List<IntentResult> results) {
        Map<String, IntentResult> merged = new LinkedHashMap<>();
        for (IntentResult r : results) {
            merged.merge(r.intentId(), r, (old, nw) -> {
                double best = Math.max(old.confidence(), nw.confidence());
                double boosted = Math.min(CROSS_BOOST_CAP, best + CROSS_BOOST);
                String layer = pickHigherLayer(old.matchedLevel(), nw.matchedLevel());
                return new IntentResult(
                    old.intentId(), old.description(), boosted,
                    layer, old.agent(), old.level(), old.rawQuery()
                );
            });
        }
        return merged;
    }

    private static boolean isHighPriority(String level) {
        return "p0".equals(level) || "p1".equals(level);
    }

    /** 优先级数值：p0=0, p1=1, p2=2, p3=3, 其他=99 */
    private static int priorityOf(String level) {
        return switch (level == null ? "" : level) {
            case "p0" -> 0;
            case "p1" -> 1;
            case "p2" -> 2;
            case "p3" -> 3;
            default -> 99;
        };
    }

    /** L2 > L3 > L1 > L4，数值小=优先级高 */
    private static String pickHigherLayer(String a, String b) {
        int ia = LAYER_PRIORITY.indexOf(a);
        int ib = LAYER_PRIORITY.indexOf(b);
        return ia <= ib ? a : b;
    }

    private static IntentResult layerPriorityWinner(IntentResult a, IntentResult b) {
        return LAYER_PRIORITY.indexOf(a.matchedLevel()) <=
               LAYER_PRIORITY.indexOf(b.matchedLevel()) ? a : b;
    }
}
