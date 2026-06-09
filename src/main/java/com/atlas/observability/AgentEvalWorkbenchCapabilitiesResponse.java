package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-describing backend contract for the future Vue eval workbench.
 */
public record AgentEvalWorkbenchCapabilitiesResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    int capabilityCount,
    List<AgentEvalWorkbenchCapability> capabilities,
    List<String> recommendedWorkflow,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-capabilities.v1";

    public static AgentEvalWorkbenchCapabilitiesResponse of(List<AgentEvalWorkbenchCapability> capabilities,
                                                            List<String> recommendedWorkflow,
                                                            Map<String, Object> workbenchPolicy,
                                                            Map<String, Object> privacy) {
        List<AgentEvalWorkbenchCapability> safeCapabilities = capabilities != null
            ? List.copyOf(capabilities)
            : List.of();
        return new AgentEvalWorkbenchCapabilitiesResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            AgentEvalReportResponse.EVALUATION_VERSION,
            safeCapabilities.size(),
            safeCapabilities,
            recommendedWorkflow != null ? List.copyOf(recommendedWorkflow) : List.of(),
            workbenchPolicy != null ? Map.copyOf(workbenchPolicy) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
