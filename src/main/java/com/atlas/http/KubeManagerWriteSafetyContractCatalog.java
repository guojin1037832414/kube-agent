package com.atlas.http;

import java.util.List;

/**
 * Source-owned catalog for generic kube-manager write safety contracts.
 *
 * <p>This catalog is static and review-only. It must not scan ToolRegistry or
 * infer runtime eligibility from existing Tools because that would silently
 * promote high-risk operations into a write allowlist.</p>
 */
public final class KubeManagerWriteSafetyContractCatalog {

    public static final String SCHEMA_VERSION = "kube-manager-write-safety-catalog.v1";
    public static final String READBACK_CONTRACT_ID = "generic-tenant-resource-readback.v1";

    private KubeManagerWriteSafetyContractCatalog() {
    }

    public static List<KubeManagerWriteOperationAllowlistEntry> reviewOnlyAllowlistEntries() {
        return List.of(
            entry(
                "generic-tenant-create",
                "CREATE",
                "POST",
                "/api/{organizationId}/{resourceType}",
                "principal+tenant+role+hitl+release-digest",
                "same-organization-fingerprint"
            ),
            entry(
                "generic-tenant-update-patch",
                "UPDATE",
                "PATCH",
                "/api/{organizationId}/{resourceType}/{resourceId}",
                "principal+tenant+role+hitl+release-digest",
                "same-organization-fingerprint"
            ),
            entry(
                "generic-tenant-update-put",
                "UPDATE",
                "PUT",
                "/api/{organizationId}/{resourceType}/{resourceId}",
                "principal+tenant+role+hitl+release-digest",
                "same-organization-fingerprint"
            ),
            entry(
                "generic-tenant-delete",
                "DELETE",
                "DELETE",
                "/api/{organizationId}/{resourceType}/{resourceId}",
                "principal+tenant+role+hitl+release-digest+compensation-digest",
                "same-organization-fingerprint"
            ),
            entry(
                "generic-tenant-action",
                "ACTION",
                "POST",
                "/api/{organizationId}/{resourceType}/{resourceId}/actions/{actionName}",
                "principal+tenant+role+hitl+release-digest+action-risk-digest",
                "same-organization-fingerprint"
            )
        );
    }

    public static KubeManagerPostWriteReadbackContract genericReadbackContract() {
        return new KubeManagerPostWriteReadbackContract(
            READBACK_CONTRACT_ID,
            "GET",
            "/api/{organizationId}/{resourceType}/{resourceId}",
            true,
            true,
            true,
            true,
            true,
            false,
            false,
            false,
            false,
            List.of("status", "phase", "state", "conditions[].type"),
            List.of("READY", "ACTIVE", "SUCCEEDED", "DELETED_CONFIRMED"),
            List.of("FAILED", "ERROR", "FORBIDDEN", "NOT_FOUND_UNEXPECTED", "TIMEOUT")
        );
    }

    public static List<String> phase2ExcludedDomains() {
        return List.of("NIM", "HPC", "Slurm", "BCM");
    }

    public static long runtimeRetryEligibleOperationCount() {
        return reviewOnlyAllowlistEntries().stream()
            .filter(KubeManagerWriteOperationAllowlistEntry::runtimeEligible)
            .filter(KubeManagerWriteOperationAllowlistEntry::retryEligible)
            .count();
    }

    private static KubeManagerWriteOperationAllowlistEntry entry(String operationId,
                                                                String operationType,
                                                                String httpMethod,
                                                                String pathTemplate,
                                                                String rbacRequirement,
                                                                String tenantBinding) {
        return new KubeManagerWriteOperationAllowlistEntry(
            operationId,
            operationType,
            httpMethod,
            pathTemplate,
            rbacRequirement,
            tenantBinding,
            READBACK_CONTRACT_ID,
            false,
            false,
            false
        );
    }
}
