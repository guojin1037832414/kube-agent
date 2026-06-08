package com.atlas.observability;

import java.util.List;
import java.util.Map;

/**
 * 命名 Agent Eval Suite 的稳定目录定义。
 *
 * <p>它只描述“应该用哪些脱敏 replay evidence 来评测”，不携带真实 trace 内容，
 * 也不是 Tool 执行授权来源。</p>
 */
public record AgentEvalSuiteDefinition(
    String id,
    String title,
    String purpose,
    String phase,
    int defaultMinimumScore,
    boolean defaultFailOnWarnings,
    int defaultLimit,
    int maxCases,
    List<String> checkCodes,
    List<String> evidenceRequirements,
    List<String> tags,
    Map<String, Object> guarantees
) {

    public static AgentEvalSuiteDefinition of(String id,
                                              String title,
                                              String purpose,
                                              String phase,
                                              int defaultMinimumScore,
                                              boolean defaultFailOnWarnings,
                                              int defaultLimit,
                                              int maxCases,
                                              List<String> checkCodes,
                                              List<String> evidenceRequirements,
                                              List<String> tags,
                                              Map<String, Object> guarantees) {
        return new AgentEvalSuiteDefinition(
            id != null ? id : "",
            title != null ? title : "",
            purpose != null ? purpose : "",
            phase != null ? phase : "",
            Math.max(0, Math.min(100, defaultMinimumScore)),
            defaultFailOnWarnings,
            Math.max(1, Math.min(defaultLimit, AgentEvalReportService.MAX_TRACE_MAX_RESULTS)),
            Math.max(1, Math.min(maxCases, AgentEvalReportService.MAX_SUITE_CASES)),
            checkCodes != null ? List.copyOf(checkCodes) : List.of(),
            evidenceRequirements != null ? List.copyOf(evidenceRequirements) : List.of(),
            tags != null ? List.copyOf(tags) : List.of(),
            guarantees != null ? Map.copyOf(guarantees) : Map.of()
        );
    }
}
