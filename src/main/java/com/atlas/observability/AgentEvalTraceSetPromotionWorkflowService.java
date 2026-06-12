package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 编排只读的 Eval trace-set promotion workflow。
 *
 * <p>中文说明：这里把“候选发现 -> 人工审阅 -> 补丁建议”串成一个可读的教学工作流，
 * 方便前端和学习者一次看见 promotion path 的完整证据链。它只组合已经存在的安全步骤：
 * 先发现 redacted 候选，再挑选推荐 trace anchor 进入 curation review，最后产出 catalog patch proposal。</p>
 *
 * <p>安全边界：本 service 是 read-only / workflow-only / proposal-only 层，不修改
 * {@code eval-trace-sets.json}，不执行 eval runtime，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，也不授予 catalog write authority。即使返回了 {@code readyForGitReview=true}，
 * 也只是说明“可以进入人工 Git review”，不等于 release authority。</p>
 */
@Service
public class AgentEvalTraceSetPromotionWorkflowService {

    public static final int DEFAULT_MAX_RECOMMENDED_CANDIDATES = 10;
    public static final int MAX_RECOMMENDED_CANDIDATES = 25;

    private final AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService;
    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalTraceSetPromotionWorkflowService(
        AgentEvalTraceSetCandidateDiscoveryService candidateDiscoveryService,
        AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public Optional<AgentEvalTraceSetPromotionWorkflowArtifact> workflow(
        String traceSetId,
        AgentEvalTraceSetPromotionWorkflowRequest request) {
        // 中文说明：先把请求标准化，再用有界候选数走 discovery -> review -> patch proposal。
        // 安全边界：这里不接受前端直接传来的 catalog mutation 指令，任何输出都只能是审阅证据。
        AgentEvalTraceSetPromotionWorkflowRequest safeRequest = request != null
            ? request
            : new AgentEvalTraceSetPromotionWorkflowRequest(null, null, null, null, null);
        int maxSelectedCandidates = boundMaxRecommendedCandidates(safeRequest.maxRecommendedCandidates());
        return candidateDiscoveryService.discover(traceSetId, safeRequest.candidateLimit())
            .flatMap(discovery -> {
                List<String> selected = selectedRecommendedTraceIds(discovery, maxSelectedCandidates);
                AgentEvalSuiteRequest reviewRequest = new AgentEvalSuiteRequest(
                    selected,
                    safeRequest.evaluationLimit(),
                    safeRequest.minimumScore(),
                    safeRequest.failOnWarnings()
                );
                return traceSetCatalogService.catalogPatchProposal(traceSetId, reviewRequest)
                    .map(proposal -> AgentEvalTraceSetPromotionWorkflowArtifact.from(
                        discovery,
                        selected,
                        proposal,
                        maxSelectedCandidates
                    ));
            });
    }

    private int boundMaxRecommendedCandidates(Integer maxRecommendedCandidates) {
        // 中文说明：推荐候选上限只影响前端/审阅工作量，不影响目录权力。
        // 安全边界：这个上限只是 read-model 体积控制，不能把更多候选变成更多写权限。
        if (maxRecommendedCandidates == null) {
            return DEFAULT_MAX_RECOMMENDED_CANDIDATES;
        }
        return Math.max(1, Math.min(maxRecommendedCandidates, MAX_RECOMMENDED_CANDIDATES));
    }

    private List<String> selectedRecommendedTraceIds(AgentEvalTraceSetCandidateDiscoveryResponse discovery,
                                                     int maxSelectedCandidates) {
        // 中文说明：这里把推荐候选做稳定去重，保留先到先得顺序，便于后续审阅和 Git diff。
        // 安全边界：selected 只是“建议审阅”的 traceId 列表，不是目录晋升，也不是 runtime catalog write。
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String traceId : discovery.candidateTraceIds()) {
            if (selected.size() >= maxSelectedCandidates) {
                break;
            }
            selected.add(traceId);
        }
        return List.copyOf(selected);
    }
}
