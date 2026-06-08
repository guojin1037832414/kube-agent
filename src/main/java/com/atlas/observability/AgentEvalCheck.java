package com.atlas.observability;

import java.util.Map;

/**
 * 确定性 Agent 评测检查项。
 *
 * <p>这里的 evidence 只能放脱敏后的计数、状态和证据锚点，不能重新暴露 raw audit
 * 中的 principal、endpoint、reason 或参数值。</p>
 */
public record AgentEvalCheck(
    String code,
    String category,
    String severity,
    String status,
    String summary,
    Map<String, Object> evidence
) {

    public static AgentEvalCheck pass(String code, String category, String summary, Map<String, Object> evidence) {
        return new AgentEvalCheck(code, category, "INFO", "PASS", summary, safeEvidence(evidence));
    }

    public static AgentEvalCheck warn(String code, String category, String summary, Map<String, Object> evidence) {
        return new AgentEvalCheck(code, category, "WARN", "WARN", summary, safeEvidence(evidence));
    }

    public static AgentEvalCheck fail(String code, String category, String summary, Map<String, Object> evidence) {
        return new AgentEvalCheck(code, category, "ERROR", "FAIL", summary, safeEvidence(evidence));
    }

    private static Map<String, Object> safeEvidence(Map<String, Object> evidence) {
        return evidence != null ? Map.copyOf(evidence) : Map.of();
    }
}
