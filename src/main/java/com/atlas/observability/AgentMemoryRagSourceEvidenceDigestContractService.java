package com.atlas.observability;

import com.atlas.memoryrag.MemoryRagSourceEvidenceDigestDeriver;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Memory/RAG source evidence digest contract without touching runtime retrieval.
 *
 * <p>中文说明：这里唯一的计算是对内置 synthetic sample 做本地 SHA-256 摘要，
 * 用来证明合同形状稳定。真实文档摄取、向量库检索、prompt 拼接和工具调用都不在本服务中发生。</p>
 *
 * <p>安全边界：该服务不读取用户文档、不写 memory、不执行检索、不调用向量库/LLM/Tool/MCP/kube-manager。
 * 输出的 digest 是证据指纹，不是内容真实性证明，也不是 prompt 引用授权。</p>
 */
@Service
public class AgentMemoryRagSourceEvidenceDigestContractService {

    private final Clock clock;
    private final MemoryRagSourceEvidenceDigestDeriver deriver;

    public AgentMemoryRagSourceEvidenceDigestContractService() {
        this(Clock.systemUTC(), new MemoryRagSourceEvidenceDigestDeriver());
    }

    AgentMemoryRagSourceEvidenceDigestContractService(Clock clock,
                                                      MemoryRagSourceEvidenceDigestDeriver deriver) {
        this.clock = clock;
        this.deriver = deriver;
    }

    public AgentMemoryRagSourceEvidenceDigestContractResponse contract() {
        return AgentMemoryRagSourceEvidenceDigestContractResponse.of(
            Instant.now(clock),
            deriver.derive(AgentMemoryRagSourceEvidenceDigestContractResponse.syntheticSampleInput())
        );
    }
}
