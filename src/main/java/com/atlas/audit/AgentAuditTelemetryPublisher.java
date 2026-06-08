package com.atlas.audit;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 将 Agent 审计投影发布到 Micrometer Observation。
 *
 * <p>这里不是另起一套“自定义追踪系统”，而是把 M5.26 已经脱敏的
 * {@link AgentAuditTelemetryProjection} 接入 Spring Boot / Micrometer / OpenTelemetry 的标准链路。
 * 生产环境后续只需要配置 OTLP exporter，就可以把这些事件送到 Tempo、Jaeger、Grafana 或云厂商 APM。</p>
 *
 * <p>标签分层很重要：低基数字段可以安全进入指标维度；auditId、traceId、eventTime 等每次都变化的字段只能作为
 * 高基数字段进入 trace/event 侧，避免把 Prometheus 这类指标后端打成“无限标签爆炸”。</p>
 */
@Service
public class AgentAuditTelemetryPublisher {

    static final String OBSERVATION_NAME = "atlas.agent.audit";
    static final String OBSERVATION_EVENT_NAME = "atlas.agent.audit.recorded";

    private static final Set<String> LOW_CARDINALITY_KEYS = Set.of(
        "atlas.agent.intent.id",
        "atlas.agent.tool.name",
        "atlas.agent.execution.source",
        "atlas.agent.http.method",
        "atlas.agent.operation.type",
        "atlas.agent.requires_confirmation",
        "atlas.agent.audit.outcome",
        "atlas.agent.tool.executed",
        "atlas.agent.tool.success",
        "atlas.agent.reason.present",
        "atlas.agent.parameters.truncated",
        "atlas.agent.privacy.raw_principal_included",
        "atlas.agent.privacy.raw_reason_included",
        "atlas.agent.privacy.raw_parameter_values_included",
        "gen_ai.operation.name",
        "gen_ai.tool.name",
        "otel.status_code",
        "http.request.method",
        "error.type"
    );

    private final ObservationRegistry observationRegistry;

    public AgentAuditTelemetryPublisher(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
    }

    public void publish(AgentAuditEvent event) {
        if (event == null || observationRegistry.isNoop()) {
            return;
        }
        AgentAuditTelemetryProjection projection = AgentAuditTelemetryProjector.project(event);
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
            .contextualName(projection.spanName());

        addAttributes(observation, projection.stableAttributes());
        addAttributes(observation, projection.experimentalOtelAttributes());
        observation.start();
        try {
            observation.event(Observation.Event.of(OBSERVATION_EVENT_NAME));
        } finally {
            observation.stop();
        }
    }

    private void addAttributes(Observation observation, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        attributes.forEach((key, value) -> {
            String safeValue = value != null ? String.valueOf(value) : "";
            if (LOW_CARDINALITY_KEYS.contains(key)) {
                observation.lowCardinalityKeyValue(key, safeValue);
            } else {
                observation.highCardinalityKeyValue(key, safeValue);
            }
        });
    }
}
