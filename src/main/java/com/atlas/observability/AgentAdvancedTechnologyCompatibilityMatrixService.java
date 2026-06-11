package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the advanced technology compatibility matrix.
 *
 * <p>中文说明：本服务只读取官方版本/协议 Watch 并生成升级矩阵，不执行构建矩阵、
 * 不改 pom、不下载依赖、不调用模型、不触发 kube-manager 或外部 Agent 运行时。</p>
 */
@Service
public class AgentAdvancedTechnologyCompatibilityMatrixService {

    private final AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService;
    private final Clock clock;

    @Autowired
    public AgentAdvancedTechnologyCompatibilityMatrixService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService
    ) {
        this(officialVersionProtocolWatchService, Clock.systemUTC());
    }

    AgentAdvancedTechnologyCompatibilityMatrixService(
        AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
        Clock clock
    ) {
        this.officialVersionProtocolWatchService = officialVersionProtocolWatchService;
        this.clock = clock;
    }

    public AgentAdvancedTechnologyCompatibilityMatrixResponse matrix() {
        return AgentAdvancedTechnologyCompatibilityMatrixResponse.of(
            Instant.now(clock),
            officialVersionProtocolWatchService.watch()
        );
    }
}
