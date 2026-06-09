package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Vue binding specification for the official watch dashboard.
 *
 * <p>中文说明：本服务只读取 M5.75 Dashboard 并生成前端绑定说明，不调用真实前端、不联网、
 * 不执行 Tool，也不产生任何可点击的运行时控制。</p>
 */
@Service
public class AgentOfficialVersionProtocolWatchVueBindingSpecService {

    private final AgentOfficialVersionProtocolWatchDashboardService dashboardService;
    private final Clock clock;

    public AgentOfficialVersionProtocolWatchVueBindingSpecService(
        AgentOfficialVersionProtocolWatchDashboardService dashboardService
    ) {
        this(dashboardService, Clock.systemUTC());
    }

    AgentOfficialVersionProtocolWatchVueBindingSpecService(
        AgentOfficialVersionProtocolWatchDashboardService dashboardService,
        Clock clock
    ) {
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

    public AgentOfficialVersionProtocolWatchVueBindingSpecResponse spec() {
        return AgentOfficialVersionProtocolWatchVueBindingSpecResponse.of(
            Instant.now(clock),
            dashboardService.dashboard()
        );
    }
}
