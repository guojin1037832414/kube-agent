package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only readiness contract for future kube-manager write retry enablement.
 *
 * <p>This DTO deliberately reports the current state as not ready. It teaches
 * operators and future maintainers which evidence must exist before POST,
 * PATCH, PUT, or DELETE can ever receive automatic retry protection.</p>
 */
public record AgentKubeManagerWriteRetryReadinessResponse(
    String schemaVersion,
    Instant generatedAt,
    String readinessVerdict,
    boolean readyForControlledWriteRetry,
    boolean writeRetryEnabled,
    boolean automaticWriteRetryAllowed,
    Map<String, Object> effectivePolicy,
    List<Map<String, Object>> requirements,
    Map<String, Object> currentEvidence,
    List<String> blockedReasons,
    Map<String, Object> futureEnablementProtocol,
    Map<String, Object> endpointTemplates,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-write-retry-readiness.v1";
}
