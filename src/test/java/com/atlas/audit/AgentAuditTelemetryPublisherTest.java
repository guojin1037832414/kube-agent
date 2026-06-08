package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.27 审计遥测 Observation 发布契约测试。
 */
class AgentAuditTelemetryPublisherTest {

    @Test
    void publish_shouldCreateObservationEventWithCardinalityAwareRedactedAttributes() {
        ObservationRegistry registry = ObservationRegistry.create();
        CapturingObservationHandler handler = new CapturingObservationHandler();
        registry.observationConfig().observationHandler(handler);

        new AgentAuditTelemetryPublisher(registry).publish(sensitiveEvent());

        assertThat(handler.startedContexts).hasSize(1);
        Observation.Context context = handler.startedContexts.get(0);
        String lowCardinalityText = context.getLowCardinalityKeyValues().toString();
        String highCardinalityText = context.getHighCardinalityKeyValues().toString();

        assertThat(context.getName()).isEqualTo("atlas.agent.audit");
        assertThat(context.getContextualName()).isEqualTo("agent.tool pod_query_tool");
        assertThat(handler.events).containsExactly("atlas.agent.audit.recorded");
        assertThat(handler.stoppedContexts).containsExactly(context);
        assertThat(context.getLowCardinalityKeyValue("atlas.agent.tool.name").getValue()).isEqualTo("pod_query_tool");
        assertThat(context.getLowCardinalityKeyValue("atlas.agent.audit.outcome").getValue()).isEqualTo("BUSINESS_FAILURE");
        assertThat(context.getLowCardinalityKeyValue("atlas.agent.tool.success").getValue()).isEqualTo("false");
        assertThat(context.getLowCardinalityKeyValue("gen_ai.operation.name").getValue()).isEqualTo("tool_call");
        assertThat(context.getLowCardinalityKeyValue("error.type").getValue()).isEqualTo("tool_business_failure");
        assertThat(context.getHighCardinalityKeyValue("atlas.agent.audit.id").getValue())
            .isEqualTo("aud_0123456789abcdef0123456789abcdef");
        assertThat(context.getHighCardinalityKeyValue("atlas.agent.trace.id").getValue())
            .isEqualTo("trc_0123456789abcdef0123456789abcdef");
        assertThat(lowCardinalityText)
            .doesNotContain("aud_0123456789abcdef0123456789abcdef", "trc_0123456789abcdef0123456789abcdef");
        assertThat(lowCardinalityText + highCardinalityText)
            .doesNotContain("conv-secret", "user-secret", "org-secret", "secret-token", "/api/org-secret");
    }

    @Test
    void record_shouldKeepAuditSnapshotWhenTelemetryPublisherFails() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder(new ThrowingTelemetryPublisher());

        recorder.record(sensitiveEvent());

        assertThat(recorder.recentEvents()).extracting(AgentAuditEvent::auditId)
            .containsExactly("aud_0123456789abcdef0123456789abcdef");
        assertThat(recorder.snapshot()).containsEntry("totalEvents", 1L);
    }

    @Test
    void recorder_shouldPreferTelemetryAwareConstructorInSpringContext() throws NoSuchMethodException {
        Constructor<InMemoryAgentAuditRecorder> constructor =
            InMemoryAgentAuditRecorder.class.getConstructor(AgentAuditTelemetryPublisher.class);

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    private AgentAuditEvent sensitiveEvent() {
        return new AgentAuditEvent(
            "aud_0123456789abcdef0123456789abcdef",
            Instant.parse("2026-06-09T00:00:00Z"),
            "trc_0123456789abcdef0123456789abcdef",
            "conv-secret",
            "user-secret",
            "org-secret",
            "pod_query",
            "pod_query_tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-secret/pod?token=secret-token"),
            AtlasToolMapping.OperationType.SENSITIVE_READ,
            true,
            AgentAuditOutcome.BUSINESS_FAILURE,
            true,
            false,
            "business failed because token=secret-token",
            Map.of(
                "count", 2,
                "truncated", false,
                "keys", List.of(Map.of(
                    "name", "token",
                    "protected", true,
                    "type", "string",
                    "present", true
                ))
            )
        );
    }

    private static final class CapturingObservationHandler implements ObservationHandler<Observation.Context> {
        private final List<Observation.Context> startedContexts = new ArrayList<>();
        private final List<Observation.Context> stoppedContexts = new ArrayList<>();
        private final List<String> events = new ArrayList<>();

        @Override
        public void onStart(Observation.Context context) {
            startedContexts.add(context);
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            events.add(event.getName());
        }

        @Override
        public void onStop(Observation.Context context) {
            stoppedContexts.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    private static final class ThrowingTelemetryPublisher extends AgentAuditTelemetryPublisher {
        private ThrowingTelemetryPublisher() {
            super(ObservationRegistry.NOOP);
        }

        @Override
        public void publish(AgentAuditEvent event) {
            throw new IllegalStateException("telemetry backend unavailable");
        }
    }
}
