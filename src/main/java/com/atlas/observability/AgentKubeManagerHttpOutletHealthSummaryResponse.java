package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only operator summary for the kube-manager HTTP outlet.
 *
 * <p>This DTO is intentionally local-process only. It describes configured and
 * effective Resilience4j policy state without probing kube-manager, reading
 * tokens, exposing backend URLs, or mutating circuit breaker state.</p>
 */
public record AgentKubeManagerHttpOutletHealthSummaryResponse(
    String schemaVersion,
    Instant generatedAt,
    String status,
    List<String> statusReasons,
    Map<String, Object> backend,
    Map<String, Object> readPolicy,
    Map<String, Object> writePolicy,
    Map<String, Object> circuitBreaker,
    Map<String, Object> bulkhead,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-http-outlet-health-summary.v1";
}
