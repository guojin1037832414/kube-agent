package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the official technology/protocol watch without external calls.
 *
 * <p>中文说明：官方文档由开发者在 Git review 中核验，本服务只发布快照契约，
 * 避免把网络状态、版本追逐或第三方运行时变成请求路径的一部分。</p>
 */
@Service
public class AgentOfficialVersionProtocolWatchService {

    private final Clock clock;

    public AgentOfficialVersionProtocolWatchService() {
        this(Clock.systemUTC());
    }

    AgentOfficialVersionProtocolWatchService(Clock clock) {
        this.clock = clock;
    }

    public AgentOfficialVersionProtocolWatchResponse watch() {
        return AgentOfficialVersionProtocolWatchResponse.of(Instant.now(clock));
    }
}
