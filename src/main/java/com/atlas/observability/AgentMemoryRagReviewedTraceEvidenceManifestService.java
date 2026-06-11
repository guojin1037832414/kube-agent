package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG reviewed trace-evidence manifest without accepting trace IDs.
 *
 * <p>中文说明：这里是“证据准入清单”装配层。它只组合已经存在的 Memory/RAG 契约，
 * 帮 Vue 和人工 Git review 看清楚下一批 redacted trace fixtures 必须满足哪些条件。
 * 它不会发现候选 trace、不会执行 eval、不会改 trace-set catalog，也不会调用检索、向量库、模型或 kube-manager。</p>
 */
@Service
public class AgentMemoryRagReviewedTraceEvidenceManifestService {

    private final AgentMemoryRagTraceSetCurationContractService curationContractService;
    private final AgentMemoryRagSourceEvidenceDigestContractService sourceEvidenceDigestContractService;
    private final AgentMemoryRagDurableMemoryLifecycleContractService durableMemoryLifecycleContractService;
    private final AgentMemoryRagEvalGateContractService evalGateContractService;
    private final AgentMemoryRagEvalSuiteBindingContractService evalSuiteBindingContractService;
    private final AgentMemoryRagReadinessService memoryRagReadinessService;
    private final Clock clock;

    @Autowired
    public AgentMemoryRagReviewedTraceEvidenceManifestService(
        AgentMemoryRagTraceSetCurationContractService curationContractService,
        AgentMemoryRagSourceEvidenceDigestContractService sourceEvidenceDigestContractService,
        AgentMemoryRagDurableMemoryLifecycleContractService durableMemoryLifecycleContractService,
        AgentMemoryRagEvalGateContractService evalGateContractService,
        AgentMemoryRagEvalSuiteBindingContractService evalSuiteBindingContractService,
        AgentMemoryRagReadinessService memoryRagReadinessService
    ) {
        this(
            curationContractService,
            sourceEvidenceDigestContractService,
            durableMemoryLifecycleContractService,
            evalGateContractService,
            evalSuiteBindingContractService,
            memoryRagReadinessService,
            Clock.systemUTC()
        );
    }

    AgentMemoryRagReviewedTraceEvidenceManifestService(
        AgentMemoryRagTraceSetCurationContractService curationContractService,
        AgentMemoryRagSourceEvidenceDigestContractService sourceEvidenceDigestContractService,
        AgentMemoryRagDurableMemoryLifecycleContractService durableMemoryLifecycleContractService,
        AgentMemoryRagEvalGateContractService evalGateContractService,
        AgentMemoryRagEvalSuiteBindingContractService evalSuiteBindingContractService,
        AgentMemoryRagReadinessService memoryRagReadinessService,
        Clock clock
    ) {
        this.curationContractService = curationContractService;
        this.sourceEvidenceDigestContractService = sourceEvidenceDigestContractService;
        this.durableMemoryLifecycleContractService = durableMemoryLifecycleContractService;
        this.evalGateContractService = evalGateContractService;
        this.evalSuiteBindingContractService = evalSuiteBindingContractService;
        this.memoryRagReadinessService = memoryRagReadinessService;
        this.clock = clock;
    }

    public AgentMemoryRagReviewedTraceEvidenceManifestResponse manifest() {
        return AgentMemoryRagReviewedTraceEvidenceManifestResponse.of(
            Instant.now(clock),
            curationContractService.contract(),
            sourceEvidenceDigestContractService.contract(),
            durableMemoryLifecycleContractService.contract(),
            evalGateContractService.contract(),
            evalSuiteBindingContractService.contract(),
            memoryRagReadinessService.readiness()
        );
    }
}
