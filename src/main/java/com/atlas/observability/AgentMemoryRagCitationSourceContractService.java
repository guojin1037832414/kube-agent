package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG citation-source contract without touching retrieval runtime.
 *
 * <p>中文说明：引用契约是未来 RAG 的准入门，不是检索器。这里不查文档、不写 memory、
 * 不调用向量库、embedding、reranker 或 LLM。</p>
 */
@Service
public class AgentMemoryRagCitationSourceContractService {

    private final Clock clock;

    public AgentMemoryRagCitationSourceContractService() {
        this(Clock.systemUTC());
    }

    AgentMemoryRagCitationSourceContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentMemoryRagCitationSourceContractResponse contract() {
        return AgentMemoryRagCitationSourceContractResponse.of(Instant.now(clock));
    }
}
