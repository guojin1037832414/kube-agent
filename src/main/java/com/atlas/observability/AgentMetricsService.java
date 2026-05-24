package com.atlas.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 可观测性指标服务 — M5.20 最小可用闭环。
 *
 * <p>通过 Micrometer 注册 Atlas Agent 的关键计数与耗时指标，满足 actuator/metrics 可观测性入口。
 * 同时维护内存快照，方便测试和轻量诊断页面直接查看。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@Service
public class AgentMetricsService {

    private final Counter reactRuns;
    private final Counter toolCalls;
    private final Counter hitlBlocks;
    private final Timer reactLatency;
    private final Timer toolLatency;

    private final AtomicLong reactRunCount = new AtomicLong();
    private final AtomicLong toolCallCount = new AtomicLong();
    private final AtomicLong hitlBlockCount = new AtomicLong();

    public AgentMetricsService(MeterRegistry meterRegistry) {
        this.reactRuns = Counter.builder("atlas_agent_react_runs_total")
            .description("Atlas ReAct 推理执行次数")
            .register(meterRegistry);
        this.toolCalls = Counter.builder("atlas_agent_tool_calls_total")
            .description("Atlas Tool 调用次数")
            .register(meterRegistry);
        this.hitlBlocks = Counter.builder("atlas_agent_hitl_blocks_total")
            .description("HITL 守卫阻断次数")
            .register(meterRegistry);
        this.reactLatency = Timer.builder("atlas_agent_react_latency")
            .description("Atlas ReAct 推理总耗时")
            .register(meterRegistry);
        this.toolLatency = Timer.builder("atlas_agent_tool_latency")
            .description("Atlas Tool 调用耗时")
            .register(meterRegistry);
    }

    /** 记录一次 ReAct 执行。 */
    public void recordReActRun(long costMs) {
        reactRuns.increment();
        reactRunCount.incrementAndGet();
        reactLatency.record(Duration.ofMillis(Math.max(0L, costMs)));
    }

    /** 记录一次 Tool 调用。 */
    public void recordToolCall(String toolName, boolean success, long costMs) {
        toolCalls.increment();
        toolCallCount.incrementAndGet();
        toolLatency.record(Duration.ofMillis(Math.max(0L, costMs)));
    }

    /** 记录一次 HITL fail-closed 阻断。 */
    public void recordHitlBlock(String toolName, String reason) {
        hitlBlocks.increment();
        hitlBlockCount.incrementAndGet();
    }

    /** 返回轻量诊断快照。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reactRuns", reactRunCount.get());
        data.put("toolCalls", toolCallCount.get());
        data.put("hitlBlocks", hitlBlockCount.get());
        data.put("metrics", Map.of(
            "reactRuns", "atlas_agent_react_runs_total",
            "toolCalls", "atlas_agent_tool_calls_total",
            "hitlBlocks", "atlas_agent_hitl_blocks_total",
            "reactLatency", "atlas_agent_react_latency",
            "toolLatency", "atlas_agent_tool_latency"
        ));
        return data;
    }
}
