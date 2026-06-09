package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Phase 1 advanced-technology adoption contract.
 *
 * <p>中文说明：本服务只生成“技术采用闸门”快照。它不升级依赖、不连接外部 Agent
 * runtime、不调用模型、不写审计，也不访问 kube-manager。</p>
 */
@Service
public class AgentAdvancedTechnologyAdoptionContractService {

    private final Clock clock;

    public AgentAdvancedTechnologyAdoptionContractService() {
        this(Clock.systemUTC());
    }

    AgentAdvancedTechnologyAdoptionContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentAdvancedTechnologyAdoptionContractResponse contract() {
        return AgentAdvancedTechnologyAdoptionContractResponse.of(Instant.now(clock));
    }
}
