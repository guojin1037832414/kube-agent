package com.atlas.http;

/**
 * Review-only kube-manager write allowlist entry.
 *
 * <p>M5.52 entries are deliberately not runtime eligible. They define the
 * future evidence shape without granting execution or retry authority.</p>
 */
public record KubeManagerWriteOperationAllowlistEntry(
    String operationId,
    String operationType,
    String httpMethod,
    String pathTemplate,
    String rbacRequirement,
    String tenantBinding,
    String readbackContractId,
    boolean retryEligible,
    boolean runtimeEligible,
    boolean phase2Excluded
) {
}
