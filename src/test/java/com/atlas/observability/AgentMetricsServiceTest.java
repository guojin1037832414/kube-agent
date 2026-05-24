package com.atlas.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.20 Agent 可观测性指标测试。
 */
class AgentMetricsServiceTest {

    @Test
    void metricsService_shouldRegisterAndIncrementCoreAgentMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetricsService service = new AgentMetricsService(registry);

        service.recordReActRun(123);
        service.recordToolCall("node_query", true, 10);
        service.recordHitlBlock("deploy_delete", "HITL_CONFIRMATION_REQUIRED");

        assertThat(registry.counter("atlas_agent_react_runs_total").count()).isEqualTo(1.0);
        assertThat(registry.counter("atlas_agent_tool_calls_total").count()).isEqualTo(1.0);
        assertThat(registry.counter("atlas_agent_hitl_blocks_total").count()).isEqualTo(1.0);
        assertThat(registry.find("atlas_agent_react_latency").timer()).isNotNull();
        assertThat(registry.find("atlas_agent_tool_latency").timer()).isNotNull();

        Map<String, Object> snapshot = service.snapshot();
        assertThat(snapshot).containsEntry("reactRuns", 1L)
            .containsEntry("toolCalls", 1L)
            .containsEntry("hitlBlocks", 1L);
    }
}
