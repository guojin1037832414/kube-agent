package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 构建 reviewed trace-evidence 控制面视图。
 *
 * <p>中文说明：该服务只读取 trace set catalog，并把已经定义好的 golden/red-team trace evidence
 * 以只读方式呈现给前端和学习文档。它用于回答“哪些 trace 已经被纳入评测证据”，
 * 而不是执行评测或修改目录。</p>
 *
 * <p>安全边界：本服务不修改 catalog、不运行 eval、不提升 trace set、不写审计、不调用 LLM/Tool/MCP
 * 或 kube-manager，也不授予 release authority。</p>
 */
@Service
public class AgentReviewedEvalTraceEvidenceService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final Clock clock;

    @Autowired
    public AgentReviewedEvalTraceEvidenceService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this(traceSetCatalogService, Clock.systemUTC());
    }

    AgentReviewedEvalTraceEvidenceService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                          Clock clock) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.clock = clock;
    }

    /**
     * 返回 reviewed trace evidence 快照。
     *
     * <p>中文说明：generatedAt 只表示视图生成时间，不能被当作 trace 已通过新评测的证明。</p>
     */
    public AgentReviewedEvalTraceEvidenceResponse evidence() {
        return AgentReviewedEvalTraceEvidenceResponse.of(
            Instant.now(clock),
            traceSetCatalogService.catalog()
        );
    }
}
