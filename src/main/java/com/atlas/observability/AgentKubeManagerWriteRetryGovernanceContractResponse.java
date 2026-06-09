package com.atlas.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only contract for future kube-manager write retry predicate governance.
 */
public record AgentKubeManagerWriteRetryGovernanceContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    boolean retryPredicateContractExists,
    boolean compensationPolicyContractExists,
    boolean boundToHttpOutlet,
    boolean writeRetryEnabled,
    long runtimeRetryableFailureClassCount,
    long automaticCompensationPolicyCount,
    Map<String, Object> predicateContract,
    List<Map<String, Object>> failureClasses,
    List<Map<String, Object>> compensationPolicies,
    Map<String, Object> bindingStatus,
    Map<String, Object> endpointTemplates,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-kube-manager-write-retry-governance-contract.v1";
}
