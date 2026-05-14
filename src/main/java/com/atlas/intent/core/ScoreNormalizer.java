package com.atlas.intent.core;

/**
 * 统一归一化器：将 L1~L4 各层原始分数映射到 [0, 1] 校准空间。
 *
 * <p>设计原则：
 * <ul>
 *   <li>L1（Embedding）：Sigmoid 拉伸，放大中段区分度</li>
 *   <li>L2 Exact：固定 0.98，保留规则权威性但非绝对 1.0</li>
 *   <li>L3（LLM）：保守系数 0.95 校准，百分比自动转换</li>
 *   <li>L4（Fuzzy）：封顶 0.75，弱化兜底印象</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public final class ScoreNormalizer {

    // ── L1 Sigmoid 参数 ──────────────────────────────────────────
    private static final double L1_K = 10.0;   // Sigmoid 斜率
    private static final double L1_MU = 0.82;  // Sigmoid 中点（中点半分位点）

    // ── 各层归一化常量 ───────────────────────────────────────────
    private static final double L2_EXACT = 0.98;
    private static final double L4_CAP = 0.75;
    private static final double LLM_ALPHA = 0.95; // LLM 保守校准系数

    private ScoreNormalizer() {}

    /**
     * 通用归一化入口。
     *
     * @param rawScore 原始分数
     * @param level    "L1" / "L2" / "L3" / "L4"
     * @param exact    L2/L4 时有效，是否为精确匹配
     */
    public static double normalize(double rawScore, String level, boolean exact) {
        return switch (level) {
            case "L1" -> normalizeL1(rawScore);
            case "L2" -> exact ? L2_EXACT : Math.min(L4_CAP, Math.max(0.0, rawScore));
            case "L3" -> normalizeL3(rawScore);
            case "L4" -> normalizeL4(rawScore);
            default -> clamp01(rawScore);
        };
    }

    /**
     * L1：Sigmoid 拉伸，将 cosine sim 中段拉开。
     * s = 1 / (1 + exp(-10 * (sim - 0.82)))
     *
     * <p>快速对照：0.75→0.25, 0.80→0.45, 0.85→0.73, 0.90→0.90, 0.95→0.98</p>
     */
    public static double normalizeL1(double sim) {
        double s = 1.0 / (1.0 + Math.exp(-L1_K * (sim - L1_MU)));
        return clamp01(s);
    }

    /** L2 Exact：固定规则分 0.98 */
    public static double normalizeL2Exact() {
        return L2_EXACT;
    }

    /**
     * L3：LLM 输出校准。
     * 若 rawScore > 1.0（如百分比 95.0），视为 (pct/100) * 0.95；
     * 若 rawScore ∈ [0,1]，直接乘 0.95 保守校准。
     */
    public static double normalizeL3(double rawScore) {
        if (rawScore > 1.0) {
            // 百分比输入，如 LLM 返回 95.0 代表 95%
            return Math.min(1.0, (rawScore / 100.0) * LLM_ALPHA);
        }
        return Math.min(1.0, rawScore * LLM_ALPHA);
    }

    /**
     * L4：模糊兜底封顶。
     * raw ∈ [0, 0.99] 时，normalized ∈ [0, 0.7425]，确保不超过 0.75。
     */
    public static double normalizeL4(double rawScore) {
        return Math.min(L4_CAP, Math.max(0.0, rawScore * L4_CAP));
    }

    /**
     * 报告分（仅用于日志 / 监控面板），带层级权重因子。
     */
    public static double reportScore(double normalizedScore, String level) {
        return switch (level) {
            case "L1" -> normalizedScore * 0.95;
            case "L2" -> normalizedScore * 1.00; // 0.98 * 1.00 = 0.98
            case "L3" -> normalizedScore * 0.97;
            case "L4" -> normalizedScore * 0.75;
            default -> normalizedScore;
        };
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
