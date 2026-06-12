package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 构建 eval workbench 的 promotion workflow 结果模型。
 *
 * <p>中文说明：这个 service 是给未来 vue-kube-manager 工作台准备的 wrapper 层，
 * 它把 trace-set definition、gate artifact 和 promotion workflow 合成一个前端友好的只读视图，
 * 让学习者一次看懂“为什么现在只能审阅，不能直接写目录”。</p>
 *
 * <p>安全边界：这里不修改目录，不执行 eval，不调用 Tool/MCP/kube-manager，
 * 也不把 workspace 变成 catalog promotion authority。真正的目录提升仍然只允许人审和 human Git review。</p>
 */
@Service
public class AgentEvalWorkbenchPromotionWorkflowService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final AgentEvalTraceSetPromotionWorkflowService promotionWorkflowService;

    public AgentEvalWorkbenchPromotionWorkflowService(
        AgentEvalTraceSetCatalogService traceSetCatalogService,
        AgentEvalTraceSetPromotionWorkflowService promotionWorkflowService) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.promotionWorkflowService = promotionWorkflowService;
    }

    public Optional<AgentEvalWorkbenchPromotionWorkflowResponse> workflow(
        String traceSetId,
        AgentEvalTraceSetPromotionWorkflowRequest request) {
        // 中文说明：先校验 trace set definition 是否存在，再把 raw workflow 包装成 workbench 读模型。
        // 安全边界：wrapper 只组合已存在的只读证据，不引入新的 runtime 写能力。
        return traceSetCatalogService.findDefinition(traceSetId)
            .flatMap(definition -> promotionWorkflowService.workflow(traceSetId, request)
                .map(workflow -> {
                    // 中文说明：gate artifact 只用于说明当前发布前还差哪些证据，不等于目录写入。
                    // 安全边界：这里用的 gate 是 read model，不会自动触发 CI blocking 或 patch 应用。
                    AgentEvalTraceSetGateArtifact gate = traceSetCatalogService.gate(definition.id(), null)
                        .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(
                            definition,
                            null,
                            null,
                            AgentEvalTraceSetCatalogService.CATALOG_SOURCE
                        ));
                    return AgentEvalWorkbenchPromotionWorkflowResponse.from(definition, gate, workflow);
                }));
    }
}
