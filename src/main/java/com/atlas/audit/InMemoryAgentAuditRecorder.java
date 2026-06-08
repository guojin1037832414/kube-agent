package com.atlas.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Agent audit recorder with an optional durable sink.
 *
 * <p>The in-memory ring buffer is still the fast diagnostic view used by tests
 * and admin snapshots. The durable sink is an append-only evidence channel that
 * can be swapped later without changing SafeToolExecutor audit semantics.</p>
 */
@Service
public class InMemoryAgentAuditRecorder implements AgentAuditRecorder, AgentAuditSnapshotProvider {

    private static final String SNAPSHOT_SCHEMA_VERSION = "agent-audit-snapshot.v1";
    private static final int MAX_RECENT_EVENTS = 200;

    private final Object monitor = new Object();
    private final ArrayDeque<AgentAuditEvent> recentEvents = new ArrayDeque<>();
    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong blockedEvents = new AtomicLong();
    private final AtomicLong errorEvents = new AtomicLong();
    private final AgentAuditTelemetryPublisher telemetryPublisher;
    private final AgentAuditDurableSink durableSink;

    public InMemoryAgentAuditRecorder() {
        this(null, AgentAuditDurableSink.noop());
    }

    @Autowired
    public InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher,
                                      AgentAuditDurableSink durableSink) {
        this.telemetryPublisher = telemetryPublisher;
        this.durableSink = durableSink != null ? durableSink : AgentAuditDurableSink.noop();
    }

    public InMemoryAgentAuditRecorder(AgentAuditTelemetryPublisher telemetryPublisher) {
        this(telemetryPublisher, AgentAuditDurableSink.noop());
    }

    @Override
    public void record(AgentAuditEvent event) {
        if (event == null) {
            return;
        }
        totalEvents.incrementAndGet();
        if (event.outcome() == AgentAuditOutcome.BLOCKED) {
            blockedEvents.incrementAndGet();
        }
        if (event.outcome() == AgentAuditOutcome.ERROR) {
            errorEvents.incrementAndGet();
        }
        synchronized (monitor) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeLast();
            }
        }
        appendDurable(event);
        publishTelemetry(event);
    }

    public List<AgentAuditEvent> recentEvents() {
        synchronized (monitor) {
            return List.copyOf(recentEvents);
        }
    }

    @Override
    public Map<String, Object> snapshot() {
        List<AgentAuditEvent> events;
        synchronized (monitor) {
            events = new ArrayList<>(recentEvents);
        }
        return Map.of(
            "schemaVersion", SNAPSHOT_SCHEMA_VERSION,
            "generatedAt", Instant.now(Clock.systemUTC()),
            "replayCapabilities", replayCapabilities(),
            "durability", durabilityStatus().toDiagnosticMap(),
            "totalEvents", totalEvents.get(),
            "blockedEvents", blockedEvents.get(),
            "errorEvents", errorEvents.get(),
            "recentEvents", events.stream()
                .map(this::diagnosticSummary)
                .toList()
        );
    }

    public AgentAuditDurabilityStatus durabilityStatus() {
        return durableSink.status();
    }

    private Map<String, Object> replayCapabilities() {
        return Map.of(
            "maxRecentEvents", MAX_RECENT_EVENTS,
            "supportsTraceLookup", true,
            "supportsRecentEventTimeline", true,
            "containsRawPrincipal", false,
            "containsRawReason", false,
            "containsRawParameterValues", false,
            "durableRetention", durabilityStatus().durableRetention()
        );
    }

    private Map<String, Object> diagnosticSummary(AgentAuditEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("auditId", event.auditId());
        summary.put("occurredAt", event.occurredAt());
        summary.put("traceId", event.traceId());
        summary.put("intentId", event.intentId());
        summary.put("toolName", event.toolName());
        summary.put("source", event.source());
        summary.put("httpMethod", event.httpMethod());
        summary.put("operationType", event.operationType());
        summary.put("requiresConfirmation", event.requiresConfirmation());
        summary.put("outcome", event.outcome());
        summary.put("executed", event.executed());
        summary.put("success", event.success());
        summary.put("parameterSummary", diagnosticParameterSummary(event.parameterSummary()));
        summary.put("reasonSummary", reasonSummary(event.reason()));
        summary.put("telemetry", AgentAuditTelemetryProjector.project(event).toDiagnosticMap());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> diagnosticParameterSummary(Map<String, Object> parameterSummary) {
        if (parameterSummary == null || parameterSummary.isEmpty()) {
            return Map.of("count", 0, "keys", List.of());
        }
        Object rawKeys = parameterSummary.get("keys");
        List<Map<String, Object>> keys = rawKeys instanceof List<?>
            ? ((List<?>) rawKeys).stream()
                .filter(Map.class::isInstance)
                .map(item -> diagnosticParameterKey((Map<String, Object>) item))
                .toList()
            : List.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", parameterSummary.getOrDefault("count", keys.size()));
        summary.put("truncated", parameterSummary.getOrDefault("truncated", false));
        summary.put("keys", keys);
        return summary;
    }

    private Map<String, Object> diagnosticParameterKey(Map<String, Object> item) {
        Map<String, Object> key = new LinkedHashMap<>();
        boolean protectedField = Boolean.TRUE.equals(item.get("protected"));
        String rawName = item.get("name") != null ? item.get("name").toString() : "";
        key.put("name", safeDiagnosticName(rawName, protectedField));
        key.put("protected", protectedField);
        key.put("type", item.getOrDefault("type", ""));
        key.put("present", item.getOrDefault("present", false));
        return key;
    }

    private String safeDiagnosticName(String name, boolean protectedField) {
        if (protectedField) {
            return "<protected>";
        }
        if (name == null || name.isBlank()) {
            return "";
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("secret")
            || lowerName.contains("password")
            || lowerName.contains("credential")
            || lowerName.contains("authorization")
            || lowerName.contains("apikey")
            || lowerName.contains("api_key")
            || lowerName.contains("token")) {
            return "<redacted>";
        }
        return name.length() <= 80 ? name : name.substring(0, 80) + "...";
    }

    private Map<String, Object> reasonSummary(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of("present", false, "length", 0);
        }
        return Map.of(
            "present", true,
            "length", reason.length()
        );
    }

    private void appendDurable(AgentAuditEvent event) {
        try {
            durableSink.append(event);
        } catch (RuntimeException ignored) {
            // Durable failures are exposed through durabilityStatus().
        }
    }

    private void publishTelemetry(AgentAuditEvent event) {
        if (telemetryPublisher == null) {
            return;
        }
        try {
            telemetryPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Telemetry publication is diagnostic and must not rewrite the Tool result.
        }
    }
}
