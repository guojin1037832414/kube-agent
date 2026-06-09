package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the Vue binding specification for the advanced technology compatibility matrix.
 *
 * <p>中文说明：本服务只读取 M5.77 兼容矩阵并生成前端绑定说明，不调用真实前端、不联网、
 * 不执行 Tool、不升级依赖，也不产生任何可点击的运行时控制。</p>
 */
@Service
public class AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService {

    private final AgentAdvancedTechnologyCompatibilityMatrixService matrixService;
    private final Clock clock;

    public AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService(
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService
    ) {
        this(matrixService, Clock.systemUTC());
    }

    AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService(
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService,
        Clock clock
    ) {
        this.matrixService = matrixService;
        this.clock = clock;
    }

    public AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse spec() {
        return AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.of(
            Instant.now(clock),
            matrixService.matrix()
        );
    }
}
