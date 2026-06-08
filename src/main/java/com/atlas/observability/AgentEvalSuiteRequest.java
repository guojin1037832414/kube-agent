package com.atlas.observability;

import java.util.List;

/**
 * 管理员批量评测请求。
 *
 * <p>traceIds 是证据定位符，不是用户输入的执行参数；suite eval 不会触发 Tool。</p>
 */
public record AgentEvalSuiteRequest(
    List<String> traceIds,
    Integer limit,
    Integer minimumScore,
    Boolean failOnWarnings
) {
}
