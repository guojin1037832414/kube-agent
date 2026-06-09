package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only contract summary for future kube-manager write idempotency.
 */
public record AgentKubeManagerWriteIdempotencyContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    boolean serverDerivedKeyContractExists,
    boolean boundToHttpOutlet,
    boolean callerProvidedIdempotencyKeyAccepted,
    boolean writeRetryEnabled,
    Map<String, Object> keyContract,
    List<Map<String, Object>> requiredEvidence,
    Map<String, Object> sampleProof,
    Map<String, Object> bindingStatus,
    Map<String, Object> endpointTemplates,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-write-idempotency-contract.v1";
}
