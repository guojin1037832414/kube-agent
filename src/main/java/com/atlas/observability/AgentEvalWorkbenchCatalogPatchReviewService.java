package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 构建 eval workbench 的 catalog patch 人审模型。
 *
 * <p>中文说明：这个服务把候选 trace-set catalog patch、trace-set gate 证据和已审阅 fixture
 * manifest 汇总成前端可渲染的 Git review 视图。它只做只读编排，不写 catalog、不执行 eval/replay、
 * 不调用 Tool/MCP/LLM/RAG/kube-manager，也不把 caller 传入的 traceId 直接升级成发布证据。</p>
 */
@Service
public class AgentEvalWorkbenchCatalogPatchReviewService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final AgentReviewedTraceFixtureManifestService reviewedTraceFixtureManifestService;

    public AgentEvalWorkbenchCatalogPatchReviewService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this(traceSetCatalogService, null);
    }

    @Autowired
    public AgentEvalWorkbenchCatalogPatchReviewService(
        AgentEvalTraceSetCatalogService traceSetCatalogService,
        AgentReviewedTraceFixtureManifestService reviewedTraceFixtureManifestService) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.reviewedTraceFixtureManifestService = reviewedTraceFixtureManifestService;
    }

    /**
     * 返回 catalog patch review 的只读快照。
     *
     * <p>中文说明：fixture manifest 只来自 classpath/repo 中已经提交的 reviewed fixture 文件；
     * 即使 request 中携带了候选 traceId，本方法也不会把它写入 catalog 或 fixture 仓库。</p>
     */
    public Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> review(
        String traceSetId,
        AgentEvalSuiteRequest request) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .flatMap(definition -> traceSetCatalogService.catalogPatchProposal(traceSetId, request)
                .map(proposal -> {
                    AgentEvalTraceSetGateArtifact gate = traceSetCatalogService.gate(definition.id(), null)
                        .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(
                            definition,
                            null,
                            null,
                            AgentEvalTraceSetCatalogService.CATALOG_SOURCE
                        ));
                    return AgentEvalWorkbenchCatalogPatchReviewResponse.from(
                        definition,
                        gate,
                        proposal,
                        reviewedFixtureManifest()
                    );
                }));
    }

    private AgentReviewedTraceFixtureManifestResponse reviewedFixtureManifest() {
        return reviewedTraceFixtureManifestService != null
            ? reviewedTraceFixtureManifestService.manifest()
            : null;
    }
}
