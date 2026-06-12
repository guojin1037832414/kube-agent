package com.atlas.observability;

/**
 * Trace-set 证据晋升工作流的请求选项。
 *
 * <p>中文说明：这个 record 只让调用方调整候选扫描数量、评测限额、最低分和推荐候选上限，
 * 方便前端在“候选发现 -> curation review -> patch proposal”链路里做教学式展示。</p>
 *
 * <p>安全边界：所有字段都只影响 review artifact，不授予 catalog write authority，
 * 不授权 Tool 或 kube-manager 执行，也不接收任何能绕过 review workflow 的控制字段。</p>
 */
public record AgentEvalTraceSetPromotionWorkflowRequest(
    Integer candidateLimit,
    Integer evaluationLimit,
    Integer minimumScore,
    Boolean failOnWarnings,
    Integer maxRecommendedCandidates
) {
}
