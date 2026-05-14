package com.atlas.intent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * L3 LLM 分类结果结构化输出。
 *
 * <p>配合 Spring AI {@link org.springframework.ai.converter.BeanOutputConverter} 使用，
 * LLM 将直接输出此结构的 JSON。Jackson 负责反序列化。</p>
 *
 * <p><b>设计说明：</b></p>
 * <ul>
 *   <li>使用 Java record，天然不可变，与 Jackson 2.x 兼容（需显式 {@code @JsonProperty}）。</li>
 *   <li>confidence 范围 0.0~1.0，由 LLM 自评，下游 {@link com.atlas.intent.IntentRouter} 用阈值过滤。</li>
 *   <li>reasoning 用于日志审计和调试，不影响路由逻辑。</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P1
 */
public record L3ClassificationResult(

    @JsonProperty(value = "intentId", required = true)
    String intentId,

    @JsonProperty(value = "confidence", required = true)
    double confidence,

    @JsonProperty(value = "reasoning", required = true)
    String reasoning
) {

    /**
     * 判断当前结果是否达到指定置信度阈值。
     *
     * @param threshold 阈值，如 0.70
     * @return true 当且仅当 confidence ≥ threshold 且 intentId 非空
     */
    public boolean isConfident(double threshold) {
        return confidence >= threshold && intentId != null && !intentId.isBlank();
    }

    /**
     * 判断是否为兜底 unknown 或低置信度无效结果。
     *
     * @return true 当 intentId 为 "unknown" 或 confidence < 0.5
     */
    public boolean isUnknown() {
        return "unknown".equalsIgnoreCase(intentId) || confidence < 0.5;
    }
}
