package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * reviewed fixture candidate 工作台组合服务。
 *
 * <p>中文说明：这个服务把“从 redacted audit 中发现候选 traceId”和“对首个推荐候选生成 fixture candidate
 * 预检草稿”串成一个只读页面包。它的存在是为了让人审者少手动拼接接口，而不是为了自动提交 fixture。</p>
 *
 * <p>安全边界：本服务不接受 caller traceId，不写 catalog，不创建 fixture 文件，不执行 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 HITL/audit/memory。自动选择的 traceId 仍只是候选锚点，必须经过人工 Git review 才可能成为 reviewed fixture。</p>
 */
@Service
public class AgentReviewedTraceFixtureCandidateWorkbenchService {

    private final AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService;
    private final AgentReviewedTraceFixtureCandidateService candidateService;

    public AgentReviewedTraceFixtureCandidateWorkbenchService(
        AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService,
        AgentReviewedTraceFixtureCandidateService candidateService) {
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.candidateService = candidateService;
    }

    /**
     * 生成某个 trace set 的自动候选预检工作台。
     *
     * <p>中文说明：limit 只控制 redacted recent audit 的扫描上限；请求不会携带 traceIds，因此前端无法把任意
     * traceId 注入成 fixture 证据。若没有推荐候选，也会返回 fail-closed 的 candidatePreview，方便页面展示缺口。</p>
     */
    public Optional<AgentReviewedTraceFixtureCandidateWorkbenchResponse> workbench(String traceSetId,
                                                                                  Integer limit) {
        return candidateDiscoveryService.discover(traceSetId, limit)
            .flatMap(discovery -> {
                List<String> selectedTraceIds = discovery.candidateTraceIds().isEmpty()
                    ? List.of()
                    : List.of(discovery.candidateTraceIds().get(0));
                AgentEvalSuiteRequest previewRequest = new AgentEvalSuiteRequest(
                    selectedTraceIds,
                    null,
                    null,
                    null
                );
                return candidateService.candidate(traceSetId, previewRequest)
                    .map(candidatePreview -> AgentReviewedTraceFixtureCandidateWorkbenchResponse.from(
                        discovery,
                        candidatePreview
                    ));
            });
    }
}
