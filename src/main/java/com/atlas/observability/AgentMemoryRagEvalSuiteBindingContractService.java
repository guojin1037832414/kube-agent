package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG eval-suite binding contract without running evals.
 */
@Service
public class AgentMemoryRagEvalSuiteBindingContractService {

    private final AgentMemoryRagEvalGateContractService evalGateContractService;
    private final AgentEvalSuiteCatalogService evalSuiteCatalogService;
    private final AgentEvalTraceSetCatalogService evalTraceSetCatalogService;
    private final Clock clock;

    public AgentMemoryRagEvalSuiteBindingContractService(
        AgentMemoryRagEvalGateContractService evalGateContractService,
        AgentEvalSuiteCatalogService evalSuiteCatalogService,
        AgentEvalTraceSetCatalogService evalTraceSetCatalogService
    ) {
        this(evalGateContractService, evalSuiteCatalogService, evalTraceSetCatalogService, Clock.systemUTC());
    }

    AgentMemoryRagEvalSuiteBindingContractService(
        AgentMemoryRagEvalGateContractService evalGateContractService,
        AgentEvalSuiteCatalogService evalSuiteCatalogService,
        AgentEvalTraceSetCatalogService evalTraceSetCatalogService,
        Clock clock
    ) {
        this.evalGateContractService = evalGateContractService;
        this.evalSuiteCatalogService = evalSuiteCatalogService;
        this.evalTraceSetCatalogService = evalTraceSetCatalogService;
        this.clock = clock;
    }

    public AgentMemoryRagEvalSuiteBindingContractResponse contract() {
        return AgentMemoryRagEvalSuiteBindingContractResponse.of(
            Instant.now(clock),
            evalGateContractService.contract(),
            evalSuiteCatalogService.catalog(),
            evalTraceSetCatalogService.catalog()
        );
    }
}
