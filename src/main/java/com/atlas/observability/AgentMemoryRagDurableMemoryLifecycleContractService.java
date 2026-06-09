package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the durable Memory/RAG lifecycle contract without binding persistent runtime.
 *
 * <p>中文说明：本服务只生成合同快照，不连接数据库、不写 memory、不运行删除/导出/恢复任务，
 * 也不调用向量检索接口、embedding、reranker、LLM 或 kube-manager。</p>
 */
@Service
public class AgentMemoryRagDurableMemoryLifecycleContractService {

    private final Clock clock;

    public AgentMemoryRagDurableMemoryLifecycleContractService() {
        this(Clock.systemUTC());
    }

    AgentMemoryRagDurableMemoryLifecycleContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentMemoryRagDurableMemoryLifecycleContractResponse contract() {
        return AgentMemoryRagDurableMemoryLifecycleContractResponse.of(Instant.now(clock));
    }
}
