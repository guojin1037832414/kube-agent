package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only contract for future kube-manager write operation eligibility.
 *
 * <p>The contract describes the allowlist, RBAC evidence, and post-write
 * readback requirements that must exist before any generic kube-manager write
 * can be bound to automatic retry or runtime execution.</p>
 */
public record AgentKubeManagerWriteOperationSafetyContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    boolean operationAllowlistContractExists,
    boolean postWriteReadbackContractExists,
    boolean boundToHttpOutlet,
    boolean writeRetryEnabled,
    List<Map<String, Object>> allowedOperationClasses,
    List<Map<String, Object>> requiredRbacEvidence,
    Map<String, Object> readbackContract,
    Map<String, Object> retryEligibilityGates,
    Map<String, Object> blockedRuntimeBindings,
    Map<String, Object> endpointTemplates,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-write-operation-safety-contract.v1";
}
