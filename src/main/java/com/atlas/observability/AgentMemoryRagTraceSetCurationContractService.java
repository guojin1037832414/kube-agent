package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG trace-set curation contract without running evals.
 *
 * <p>中文说明：这里只读取 suite catalog 与 trace-set catalog，目的是证明“目录已定义但运行时仍关闭”。
 * 不调用 gate、curation review、candidate discovery，也不读取原始审计或触发检索。</p>
 */
@Service
public class AgentMemoryRagTraceSetCurationContractService {

    private final AgentEvalTraceSetCatalogService evalTraceSetCatalogService;
    private final AgentEvalSuiteCatalogService evalSuiteCatalogService;
    private final Clock clock;

    @Autowired
    public AgentMemoryRagTraceSetCurationContractService(
        AgentEvalTraceSetCatalogService evalTraceSetCatalogService,
        AgentEvalSuiteCatalogService evalSuiteCatalogService
    ) {
        this(evalTraceSetCatalogService, evalSuiteCatalogService, Clock.systemUTC());
    }

    AgentMemoryRagTraceSetCurationContractService(
        AgentEvalTraceSetCatalogService evalTraceSetCatalogService,
        AgentEvalSuiteCatalogService evalSuiteCatalogService,
        Clock clock
    ) {
        this.evalTraceSetCatalogService = evalTraceSetCatalogService;
        this.evalSuiteCatalogService = evalSuiteCatalogService;
        this.clock = clock;
    }

    public AgentMemoryRagTraceSetCurationContractResponse contract() {
        return AgentMemoryRagTraceSetCurationContractResponse.of(
            Instant.now(clock),
            evalTraceSetCatalogService.catalog(),
            evalSuiteCatalogService.catalog()
        );
    }
}
