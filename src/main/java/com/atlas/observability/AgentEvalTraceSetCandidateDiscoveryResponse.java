package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only redacted candidate discovery result for trace-set curation.
 *
 * <p>中文说明：这是候选发现接口的整体响应，告诉前端扫描了多少 redacted audit event、
 * 找到多少 traceId 候选、哪些候选被推荐进入人工 review，以及 discoveryPolicy/副作用边界。</p>
 *
 * <p>安全边界：响应是 admin-only read model，不接受前端 traceId 写入 catalog，不运行 eval gate，
 * 不执行 Tool/MCP/LLM/RAG/kube-manager，不写 audit/memory。candidateTraceIds 只是下一步
 * curation-review 的输入建议，不是已发布 trace set。</p>
 */
public record AgentEvalTraceSetCandidateDiscoveryResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String auditQueryBackend,
    int maxEvents,
    int inspectedEvents,
    int inspectedTraceCount,
    int candidateTraceCount,
    boolean auditQueryTruncated,
    List<String> candidateTraceIds,
    List<AgentEvalTraceSetCandidate> candidates,
    Map<String, Object> discoveryPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-trace-set-candidates.v1";

    public static AgentEvalTraceSetCandidateDiscoveryResponse of(AgentEvalTraceSetDefinition traceSet,
                                                                 String auditQueryBackend,
                                                                 int maxEvents,
                                                                 boolean auditQueryTruncated,
                                                                 List<AgentEvalTraceSetCandidate> candidates,
                                                                 Map<String, Object> discoveryPolicy,
                                                                 Map<String, Object> privacy) {
        List<AgentEvalTraceSetCandidate> safeCandidates = candidates != null
            ? List.copyOf(candidates)
            : List.of();
        List<String> candidateTraceIds = safeCandidates.stream()
            .filter(AgentEvalTraceSetCandidate::recommendedForCurationReview)
            .map(AgentEvalTraceSetCandidate::traceId)
            .toList();
        int inspectedEvents = safeCandidates.stream()
            .mapToInt(AgentEvalTraceSetCandidate::eventCount)
            .sum();
        return new AgentEvalTraceSetCandidateDiscoveryResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            traceSet != null ? traceSet.id() : "",
            traceSet != null ? traceSet.title() : "",
            traceSet != null ? traceSet.suiteId() : "",
            auditQueryBackend != null ? auditQueryBackend : "",
            Math.max(0, maxEvents),
            inspectedEvents,
            safeCandidates.size(),
            candidateTraceIds.size(),
            auditQueryTruncated,
            candidateTraceIds,
            safeCandidates,
            discoveryPolicy != null ? Map.copyOf(discoveryPolicy) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
