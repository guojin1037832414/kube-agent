package com.atlas.observability;

import java.time.Instant;
import java.util.Map;

/**
 * Read-only contract for future kube-manager write durable receipt and release evidence.
 */
public record AgentKubeManagerWriteReleaseGateContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    boolean durableReceiptContractExists,
    boolean releaseEvidenceContractExists,
    boolean boundToHttpOutlet,
    boolean releaseGateOpen,
    boolean writeRetryEnabled,
    long runtimeReleaseGateOpenCount,
    Map<String, Object> durableReceiptContract,
    Map<String, Object> releaseEvidenceContract,
    Map<String, Object> bindingStatus,
    Map<String, Object> endpointTemplates,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-write-release-gate-contract.v1";
}
