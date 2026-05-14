package com.atlas.intent.core;

/**
 * 意图分类统一结果。
 *
 * <p>v3.1.0 增强：
 * <ul>
 *   <li>confidence 语义升级为统一归一化分数（normalizedScore），范围 [0, 1]</li>
 *   <li>新增 {@link #of} 工厂方法，Matcher 调用时自动归一化</li>
 *   <li>新增 {@link #withNormalizedScore} 方法，Router 可重新校准分数</li>
 *   <li>新增 {@link #reportScore()} 报告分（仅日志/监控用）</li>
 * </ul>
 *
 * <p>向后兼容：现有直接构造调用仍然有效。</p>
 *
 * @param intentId     命中意图ID
 * @param description  意图描述
 * @param confidence   统一置信度 0.0~1.0（已归一化 / normalizedScore）
 * @param matchedLevel 命中所在层：L1 / L2 / L3 / L4
 * @param agent        目标Agent
 * @param level        意图优先级：p0 / p1 / p2 / p3
 * @param rawQuery     原始query
 */
public record IntentResult(
    String intentId, String description, double confidence,
    String matchedLevel, String agent, String level, String rawQuery
) {

    /**
     * 工厂方法：Matcher 创建结果时调用，自动归一化 rawScore。
     *
     * <p>示例用法：
     * <pre>{@code
     * return IntentResult.of(def, bestSim, "L1", false, query);
     * }</pre></p>
     *
     * @param def       意图定义
     * @param rawScore  该层原始分数
     * @param level     命中层 "L1"/"L2"/"L3"/"L4"
     * @param exact     是否精确匹配（仅 L2/L4 有意义）
     * @param rawQuery  原始 query
     */
    public static IntentResult of(
        com.atlas.intent.config.IntentDefinition def,
        double rawScore, String level, boolean exact, String rawQuery
    ) {
        double norm = ScoreNormalizer.normalize(rawScore, level, exact);
        return new IntentResult(
            def.intentId(), def.description(), norm,
            level, def.agent(), def.level(), rawQuery
        );
    }

    /**
     * 重新校准分数（Router 归一化用）。
     *
     * @param newScore 新的归一化分数
     */
    public IntentResult withNormalizedScore(double newScore) {
        double safe = Math.min(1.0, Math.max(0.0, newScore));
        return new IntentResult(
            this.intentId, this.description, safe,
            this.matchedLevel, this.agent, this.level, this.rawQuery
        );
    }

    /**
     * 同意图多层级交叉确认后的加分构造（由 {@link IntentArbiter} 调用）。
     */
    public IntentResult withBoostedConfidence(double newConfidence) {
        return withNormalizedScore(newConfidence);
    }

    /**
     * 获取报告分（仅日志/监控面板用，不参与路由决策）。
     */
    public double reportScore() {
        return ScoreNormalizer.reportScore(this.confidence, this.matchedLevel);
    }
}
