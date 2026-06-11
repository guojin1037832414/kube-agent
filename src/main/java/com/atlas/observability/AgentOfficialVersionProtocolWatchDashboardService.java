package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Vue dashboard read model for the official version/protocol watch.
 *
 * <p>中文说明：本服务只组合 M5.74 官方 Watch 契约，不联网、不升级依赖、不调用模型、
 * 不执行 Tool，也不给前端生成任何运行时启用按钮。</p>
 */
@Service
public class AgentOfficialVersionProtocolWatchDashboardService {

    private final AgentOfficialVersionProtocolWatchService sourceWatchService;
    private final Clock clock;

    @Autowired
    public AgentOfficialVersionProtocolWatchDashboardService(
        AgentOfficialVersionProtocolWatchService sourceWatchService
    ) {
        this(sourceWatchService, Clock.systemUTC());
    }

    AgentOfficialVersionProtocolWatchDashboardService(
        AgentOfficialVersionProtocolWatchService sourceWatchService,
        Clock clock
    ) {
        this.sourceWatchService = sourceWatchService;
        this.clock = clock;
    }

    public AgentOfficialVersionProtocolWatchDashboardResponse dashboard() {
        return AgentOfficialVersionProtocolWatchDashboardResponse.of(
            Instant.now(clock),
            sourceWatchService.watch()
        );
    }
}
