package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG eval gate contract without running eval or retrieval.
 *
 * <p>中文说明：本服务只生成评测门禁合同快照，不读取审计 trace、不运行 eval suite、
 * 不执行检索、不写 memory，也不调用模型、工具或 kube-manager。</p>
 */
@Service
public class AgentMemoryRagEvalGateContractService {

    private final Clock clock;

    public AgentMemoryRagEvalGateContractService() {
        this(Clock.systemUTC());
    }

    AgentMemoryRagEvalGateContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentMemoryRagEvalGateContractResponse contract() {
        return AgentMemoryRagEvalGateContractResponse.of(Instant.now(clock));
    }
}
