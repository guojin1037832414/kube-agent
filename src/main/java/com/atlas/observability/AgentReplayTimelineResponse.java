package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理员专用的脱敏 replay timeline 响应。
 *
 * <p>中文说明：这是前端回放和确定性 eval 的共享证据容器，描述某个 trace 的步骤顺序、
 * 隐私证明和审计索引信息。</p>
 *
 * <p>安全边界：response 永远声明 redactedOnly，不携带 raw principal、raw organization、
 * raw conversation、raw endpoint、raw reason 或 raw parameter values。它只用于 admin-only
 * 诊断，不重新执行 Tool，也不是 prompt 权威。</p>
 */
public record AgentReplayTimelineResponse(
    String schemaVersion,
    Instant generatedAt,
    String traceId,
    int resultCount,
    int maxResults,
    boolean truncated,
    String order,
    Map<String, Object> privacy,
    Map<String, Object> index,
    List<AgentReplayTimelineStep> steps
) {

    public static final String SCHEMA_VERSION = "agent-replay-timeline.v1";

    /**
     * 创建 timeline 响应并补齐隐私证明。
     *
     * <p>中文说明：privacyMetadata 是前端和 eval 的显式契约，让调用方不能把 replay 误解为原始日志。</p>
     */
    public static AgentReplayTimelineResponse of(String traceId,
                                                 int maxResults,
                                                 boolean truncated,
                                                 Map<String, Object> index,
                                                 List<AgentReplayTimelineStep> steps) {
        List<AgentReplayTimelineStep> safeSteps = steps != null ? List.copyOf(steps) : List.of();
        return new AgentReplayTimelineResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            traceId != null ? traceId : "",
            safeSteps.size(),
            Math.max(0, maxResults),
            truncated,
            "oldest-first",
            privacyMetadata(),
            index != null ? Map.copyOf(index) : Map.of(),
            safeSteps
        );
    }

    private static Map<String, Object> privacyMetadata() {
        return Map.of(
            "redactedOnly", true,
            "containsRawPrincipal", false,
            "containsRawOrganization", false,
            "containsRawConversation", false,
            "containsRawEndpoints", false,
            "containsRawReason", false,
            "containsRawParameterValues", false
        );
    }
}
