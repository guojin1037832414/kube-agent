package com.atlas.observability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Versioned golden/red-team trace set metadata for deterministic eval gates.
 *
 * <p>The trace IDs are replay evidence anchors only. They are never Tool
 * arguments, kube-manager endpoints, or authorization material.</p>
 */
public record AgentEvalTraceSetDefinition(
    String id,
    String title,
    String purpose,
    String phase,
    String suiteId,
    List<String> traceIds,
    List<String> evidenceRequirements,
    List<String> tags,
    Map<String, Object> curationPolicy,
    Map<String, Object> guarantees
) {

    public AgentEvalTraceSetDefinition {
        id = safeText(id);
        title = safeText(title);
        purpose = safeText(purpose);
        phase = safeText(phase);
        suiteId = safeText(suiteId);
        traceIds = normalizeTraceIds(traceIds);
        evidenceRequirements = evidenceRequirements != null ? List.copyOf(evidenceRequirements) : List.of();
        tags = tags != null ? List.copyOf(tags) : List.of();
        curationPolicy = curationPolicy != null ? Map.copyOf(curationPolicy) : Map.of();
        guarantees = guarantees != null ? Map.copyOf(guarantees) : Map.of();
    }

    public static AgentEvalTraceSetDefinition of(String id,
                                                 String title,
                                                 String purpose,
                                                 String phase,
                                                 String suiteId,
                                                 List<String> traceIds,
                                                 List<String> evidenceRequirements,
                                                 List<String> tags,
                                                 Map<String, Object> curationPolicy,
                                                 Map<String, Object> guarantees) {
        return new AgentEvalTraceSetDefinition(
            id,
            title,
            purpose,
            phase,
            suiteId,
            traceIds,
            evidenceRequirements,
            tags,
            curationPolicy,
            guarantees
        );
    }

    private static List<String> normalizeTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String traceId : traceIds) {
            String value = safeText(traceId).trim();
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
