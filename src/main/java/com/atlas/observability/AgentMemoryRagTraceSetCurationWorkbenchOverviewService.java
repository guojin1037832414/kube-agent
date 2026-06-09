package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds a Vue-ready Memory/RAG curation workbench without invoking runtime gates.
 *
 * <p>中文说明：这里是“页面读模型装配层”，只组合已经通过安全审计的只读契约。
 * 它不会发现候选 trace、不会执行 curation review、不会生成 gate bundle，也不会调用
 * kube-manager、MCP tools/call、向量库、embedding model、reranker 或 LLM。</p>
 */
@Service
public class AgentMemoryRagTraceSetCurationWorkbenchOverviewService {

    private final AgentMemoryRagTraceSetCurationContractService curationContractService;
    private final AgentMemoryRagEvalSuiteBindingContractService suiteBindingContractService;
    private final AgentMemoryRagReadinessService memoryRagReadinessService;
    private final Clock clock;

    public AgentMemoryRagTraceSetCurationWorkbenchOverviewService(
        AgentMemoryRagTraceSetCurationContractService curationContractService,
        AgentMemoryRagEvalSuiteBindingContractService suiteBindingContractService,
        AgentMemoryRagReadinessService memoryRagReadinessService
    ) {
        this(curationContractService, suiteBindingContractService, memoryRagReadinessService, Clock.systemUTC());
    }

    AgentMemoryRagTraceSetCurationWorkbenchOverviewService(
        AgentMemoryRagTraceSetCurationContractService curationContractService,
        AgentMemoryRagEvalSuiteBindingContractService suiteBindingContractService,
        AgentMemoryRagReadinessService memoryRagReadinessService,
        Clock clock
    ) {
        this.curationContractService = curationContractService;
        this.suiteBindingContractService = suiteBindingContractService;
        this.memoryRagReadinessService = memoryRagReadinessService;
        this.clock = clock;
    }

    public AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse overview() {
        return AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.of(
            Instant.now(clock),
            curationContractService.contract(),
            suiteBindingContractService.contract(),
            memoryRagReadinessService.readiness()
        );
    }
}
