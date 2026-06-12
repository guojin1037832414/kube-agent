package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redacted trace-level candidate summary for trace-set curation review.
 *
 * <p>中文说明：这是候选 traceId 的前端展示 DTO。它只包含已脱敏的统计摘要，
 * 例如是否有 PRE_EXECUTION、FINAL、高风险事件、受保护参数摘要或 blocked outcome，
 * 用于帮助管理员判断是否送入 curation review。</p>
 *
 * <p>安全边界：本 DTO 不携带 raw audit、raw endpoint、raw principal、raw org、raw conversation、
 * raw reason 或 raw parameter values；{@code recommendedForCurationReview=true} 也只是“建议审阅”，
 * 不是目录提升、CI blocking、release authority、Tool 授权或 retrieval runtime 开关。</p>
 */
public record AgentEvalTraceSetCandidate(
    String traceId,
    String recommendation,
    boolean recommendedForCurationReview,
    List<String> recommendationReasons,
    Instant firstSeenAt,
    Instant lastSeenAt,
    int eventCount,
    int preExecutionEvents,
    int finalEvents,
    int highRiskEvents,
    int readEvents,
    int executedEvents,
    int successEvents,
    int blockedEvents,
    int errorEvents,
    int businessFailureEvents,
    boolean requiresConfirmation,
    boolean protectedParameterEvidence,
    List<String> operationTypes,
    List<String> outcomes,
    List<String> evidenceTags,
    Map<String, Object> privacy
) {

    public AgentEvalTraceSetCandidate {
        traceId = traceId != null ? traceId : "";
        recommendation = recommendation != null ? recommendation : "NEEDS_MORE_REVIEW";
        recommendationReasons = recommendationReasons != null ? List.copyOf(recommendationReasons) : List.of();
        operationTypes = operationTypes != null ? List.copyOf(operationTypes) : List.of();
        outcomes = outcomes != null ? List.copyOf(outcomes) : List.of();
        evidenceTags = evidenceTags != null ? List.copyOf(evidenceTags) : List.of();
        privacy = privacy != null ? Map.copyOf(privacy) : Map.of();
    }
}
