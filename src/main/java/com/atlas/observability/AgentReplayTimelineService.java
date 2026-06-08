package com.atlas.observability;

import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.AgentAuditQueryEvent;
import com.atlas.audit.AgentAuditQueryResponse;
import com.atlas.audit.AgentAuditQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a frontend-friendly replay timeline from the redacted audit query API.
 */
@Service
public class AgentReplayTimelineService {

    private final AgentAuditQueryService auditQueryService;

    public AgentReplayTimelineService(AgentAuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    public AgentReplayTimelineResponse traceTimeline(String traceId, int maxResults) {
        AgentAuditQueryResponse auditResponse = auditQueryService.findByTraceId(traceId, maxResults);
        List<AgentAuditQueryEvent> chronologicalEvents = new ArrayList<>(auditResponse.events());
        java.util.Collections.reverse(chronologicalEvents);

        List<AgentReplayTimelineStep> steps = new ArrayList<>();
        for (int i = 0; i < chronologicalEvents.size(); i++) {
            steps.add(toStep(chronologicalEvents.get(i), i + 1));
        }
        return AgentReplayTimelineResponse.of(
            auditResponse.query(),
            auditResponse.maxResults(),
            auditResponse.truncated(),
            auditResponse.index(),
            steps
        );
    }

    private AgentReplayTimelineStep toStep(AgentAuditQueryEvent event, int position) {
        String outcome = safeText(event.outcome()).toUpperCase(Locale.ROOT);
        String operationType = safeText(event.operationType());
        return new AgentReplayTimelineStep(
            stepId(event, position),
            position,
            event.occurredAt(),
            phase(outcome),
            kind(outcome),
            status(outcome),
            safeText(event.auditId()),
            safeText(event.traceId()),
            safeText(event.intentId()),
            safeText(event.toolName()),
            safeText(event.source()),
            operationType,
            safeText(event.httpMethod()),
            event.requiresConfirmation(),
            event.executed(),
            event.success(),
            Math.max(0, event.apiEndpointCount()),
            safeMap(event.reasonSummary()),
            safeMap(event.parameterSummary()),
            safeMap(event.telemetry()),
            labels(event, outcome, operationType)
        );
    }

    private String stepId(AgentAuditQueryEvent event, int position) {
        String auditId = safeText(event.auditId());
        return !auditId.isBlank() ? auditId + ":" + position : "step-" + position;
    }

    private String phase(String outcome) {
        return AgentAuditOutcome.PREPARED.name().equals(outcome) ? "PRE_EXECUTION" : "FINAL";
    }

    private String kind(String outcome) {
        if (AgentAuditOutcome.PREPARED.name().equals(outcome)) {
            return "TOOL_PREPARED";
        }
        if (AgentAuditOutcome.BLOCKED.name().equals(outcome)) {
            return "TOOL_BLOCKED";
        }
        if (AgentAuditOutcome.ERROR.name().equals(outcome)) {
            return "TOOL_ERROR";
        }
        if (AgentAuditOutcome.BUSINESS_FAILURE.name().equals(outcome)) {
            return "TOOL_BUSINESS_FAILURE";
        }
        return "TOOL_RESULT";
    }

    private String status(String outcome) {
        if (AgentAuditOutcome.PREPARED.name().equals(outcome)) {
            return "prepared";
        }
        if (AgentAuditOutcome.SUCCESS.name().equals(outcome)) {
            return "success";
        }
        if (AgentAuditOutcome.BLOCKED.name().equals(outcome)) {
            return "blocked";
        }
        if (AgentAuditOutcome.ERROR.name().equals(outcome)) {
            return "error";
        }
        if (AgentAuditOutcome.BUSINESS_FAILURE.name().equals(outcome)) {
            return "business_failure";
        }
        return "unknown";
    }

    private List<String> labels(AgentAuditQueryEvent event, String outcome, String operationType) {
        List<String> labels = new ArrayList<>();
        if (!outcome.isBlank()) {
            labels.add("outcome:" + outcome);
        }
        if (operationType != null && !operationType.isBlank()) {
            labels.add("operation:" + operationType);
        }
        labels.add(event.requiresConfirmation() ? "confirmation:required" : "confirmation:not_required");
        labels.add(event.executed() ? "execution:executed" : "execution:not_executed");
        labels.add(event.success() ? "result:success" : "result:not_success");
        return List.copyOf(labels);
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value != null ? Map.copyOf(value) : Map.of();
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }
}
