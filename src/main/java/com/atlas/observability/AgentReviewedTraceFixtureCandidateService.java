package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * reviewed redacted trace fixture 的候选证据预检服务。
 *
 * <p>中文说明：template 只告诉人审“fixture 应该长什么样”，manifest 只告诉人审“仓库里有没有文件”。
 * 本服务补中间一步：给定 trace set 和候选 traceId 后，从现有 redacted replay / deterministic eval
 * 读模型生成一个可人工审查的 fixture candidate 包，帮助后续真实 fixture 入仓。</p>
 *
 * <p>安全边界：这是 preview-only / read-only / review-only 服务。候选 traceId 只是定位脱敏证据的锚点，
 * 不会被当成已经 reviewed 的 fixture；本服务不创建文件、不上传 fixture、不写 catalog、不运行 Tool/MCP/LLM/RAG、
 * 不访问 kube-manager、不写 audit/memory，也不授予 CI blocking 或 release authority。</p>
 */
@Service
public class AgentReviewedTraceFixtureCandidateService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final AgentEvalReportService evalReportService;

    public AgentReviewedTraceFixtureCandidateService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                                     AgentEvalReportService evalReportService) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.evalReportService = evalReportService;
    }

    /**
     * 生成某个 trace set 的 fixture candidate 预检包。
     *
     * <p>中文说明：请求里的 traceIds 只用于选择第一个 W3C-compatible redacted replay anchor；
     * 非法值只计数不回显，避免把 token、路径或任意用户输入写进治理响应。</p>
     */
    public Optional<AgentReviewedTraceFixtureCandidateResponse> candidate(String traceSetId,
                                                                          AgentEvalSuiteRequest request) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .map(definition -> {
                CandidateTraceIds candidateTraceIds = normalizeTraceIds(request != null ? request.traceIds() : null);
                String selectedTraceId = candidateTraceIds.acceptedTraceIds().isEmpty()
                    ? ""
                    : candidateTraceIds.acceptedTraceIds().get(0);
                AgentEvalReportResponse report = selectedTraceId.isBlank()
                    ? null
                    : evalReportService.evaluateTrace(selectedTraceId, boundedLimit(request != null ? request.limit() : null));
                return AgentReviewedTraceFixtureCandidateResponse.from(
                    definition,
                    selectedTraceId,
                    candidateTraceIds.acceptedTraceIds().size(),
                    candidateTraceIds.rejectedTraceIdCount(),
                    report
                );
            });
    }

    private int boundedLimit(Integer limit) {
        if (limit == null) {
            return AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS;
        }
        return Math.max(1, Math.min(limit, AgentEvalReportService.MAX_TRACE_MAX_RESULTS));
    }

    private CandidateTraceIds normalizeTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return new CandidateTraceIds(List.of(), 0);
        }
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        int rejected = 0;
        for (String traceId : traceIds) {
            String candidate = AgentTraceContext.safeCandidateOrBlank(traceId);
            if (!candidate.isBlank() && !AgentTraceContext.w3cTraceIdOrBlank(candidate).isBlank()) {
                accepted.add(candidate);
            } else if (traceId != null && !traceId.isBlank()) {
                rejected++;
            }
        }
        return new CandidateTraceIds(List.copyOf(accepted), rejected);
    }

    private record CandidateTraceIds(List<String> acceptedTraceIds, int rejectedTraceIdCount) {
    }
}
