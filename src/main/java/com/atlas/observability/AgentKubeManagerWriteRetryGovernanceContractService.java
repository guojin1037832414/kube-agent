package com.atlas.observability;

import com.atlas.http.KubeManagerWriteCompensationPolicy;
import com.atlas.http.KubeManagerWriteRetryFailureClass;
import com.atlas.http.KubeManagerWriteRetryGovernanceCatalog;
import com.atlas.http.KubeManagerWriteRetryPredicateContract;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local read model for future kube-manager write retry governance.
 *
 * <p>This service defines the retry predicate and compensation policy evidence
 * shape without binding Resilience4j, calling kube-manager, executing readback,
 * issuing audit receipts, or enabling write retry.</p>
 */
@Service
public class AgentKubeManagerWriteRetryGovernanceContractService {

    private final Clock clock;

    public AgentKubeManagerWriteRetryGovernanceContractService() {
        this(Clock.systemUTC());
    }

    AgentKubeManagerWriteRetryGovernanceContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentKubeManagerWriteRetryGovernanceContractResponse contract() {
        return new AgentKubeManagerWriteRetryGovernanceContractResponse(
            AgentKubeManagerWriteRetryGovernanceContractResponse.SCHEMA_VERSION,
            Instant.now(clock),
            "CONTRACT_DEFINED_NOT_BOUND",
            true,
            true,
            false,
            false,
            KubeManagerWriteRetryGovernanceCatalog.runtimeRetryableFailureClassCount(),
            KubeManagerWriteRetryGovernanceCatalog.automaticCompensationPolicyCount(),
            predicateContract(),
            failureClasses(),
            compensationPolicies(),
            bindingStatus(),
            endpointTemplates(),
            safety(),
            privacy()
        );
    }

    private Map<String, Object> predicateContract() {
        KubeManagerWriteRetryPredicateContract contract =
            KubeManagerWriteRetryGovernanceCatalog.predicateContract();
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("contractId", contract.contractId());
        predicate.put("contractExists", contract.contractExists());
        predicate.put("boundToHttpOutlet", contract.boundToHttpOutlet());
        predicate.put("runtimePredicateExists", contract.runtimePredicateExists());
        predicate.put("callerOverrideAccepted", contract.callerOverrideAccepted());
        predicate.put("maxAttempts", contract.maxAttempts());
        predicate.put("backoffStrategy", contract.backoffStrategy());
        predicate.put("jitterRequired", contract.jitterRequired());
        predicate.put("sameIdempotencyKeyRequired", contract.sameIdempotencyKeyRequired());
        predicate.put("durablePrewriteReceiptRequired", contract.durablePrewriteReceiptRequired());
        predicate.put("operationAllowlistRequired", contract.operationAllowlistRequired());
        predicate.put("rbacEvidenceRequired", contract.rbacEvidenceRequired());
        predicate.put("postWriteReadbackRequiredBeforeSuccess", contract.postWriteReadbackRequiredBeforeSuccess());
        predicate.put("futureCandidateFailureClassIds", contract.futureCandidateFailureClassIds());
        predicate.put("neverRetryFailureClassIds", contract.neverRetryFailureClassIds());
        return Map.copyOf(predicate);
    }

    private List<Map<String, Object>> failureClasses() {
        return KubeManagerWriteRetryGovernanceCatalog.failureClasses().stream()
            .map(this::failureClass)
            .toList();
    }

    private Map<String, Object> failureClass(KubeManagerWriteRetryFailureClass failureClass) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("failureClassId", failureClass.failureClassId());
        item.put("category", failureClass.category());
        item.put("futureRetryCandidate", failureClass.futureRetryCandidate());
        item.put("runtimeRetryableNow", failureClass.runtimeRetryableNow());
        item.put("httpStatuses", failureClass.httpStatuses());
        item.put("exceptionSignals", failureClass.exceptionSignals());
        item.put("requiredEvidence", failureClass.requiredEvidence());
        item.put("decisionRule", failureClass.decisionRule());
        item.put("operatorGuidance", failureClass.operatorGuidance());
        return Map.copyOf(item);
    }

    private List<Map<String, Object>> compensationPolicies() {
        return KubeManagerWriteRetryGovernanceCatalog.compensationPolicies().stream()
            .map(this::compensationPolicy)
            .toList();
    }

    private Map<String, Object> compensationPolicy(KubeManagerWriteCompensationPolicy policy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("policyId", policy.policyId());
        item.put("operationType", policy.operationType());
        item.put("failureScope", policy.failureScope());
        item.put("automaticCompensationAllowed", policy.automaticCompensationAllowed());
        item.put("operatorReviewRequired", policy.operatorReviewRequired());
        item.put("runtimeBound", policy.runtimeBound());
        item.put("canOpenReleaseSwitch", policy.canOpenReleaseSwitch());
        item.put("requiredEvidence", policy.requiredEvidence());
        item.put("guidance", policy.guidance());
        return Map.copyOf(item);
    }

    private Map<String, Object> bindingStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("catalogDefined", true);
        status.put("retryPredicateBoundToResilience4j", false);
        status.put("retryPredicateBoundToHttpOutlet", false);
        status.put("failureClassifierRuntimeBound", false);
        status.put("compensationExecutorExists", false);
        status.put("automaticCompensationAllowed", false);
        status.put("writeRetryEnabled", false);
        status.put("runtimeEnableSwitchPresent", false);
        status.put("callerOverrideAllowed", false);
        status.put("requiresFutureReleaseReview", true);
        return Map.copyOf(status);
    }

    private Map<String, Object> endpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("writeRetryGovernanceContract", "/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract");
        endpoints.put("writeOperationSafetyContract", "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract");
        endpoints.put("writeIdempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract");
        endpoints.put("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness");
        endpoints.put("runtimeEnableWriteRetry", "not-exposed");
        endpoints.put("runtimeCompensationExecutor", "not-exposed");
        return Map.copyOf(endpoints);
    }

    private Map<String, Object> safety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("localProcessOnly", true);
        safety.put("summaryOnly", true);
        safety.put("kubeManagerCalls", false);
        safety.put("remoteProbeExecuted", false);
        safety.put("restClientUsed", false);
        safety.put("resiliencePredicateMutation", false);
        safety.put("retryRegistryMutation", false);
        safety.put("toolExecution", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("httpHeaderInjection", false);
        safety.put("readbackExecuted", false);
        safety.put("compensationExecuted", false);
        safety.put("writeRetryEnablement", false);
        safety.put("callerInputAccepted", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private Map<String, Object> privacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawBaseUrl", false);
        privacy.put("containsRawBackendPath", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsTokenPrefix", false);
        privacy.put("containsLoginUsername", false);
        privacy.put("containsLoginPassword", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        privacy.put("containsRawExceptionBody", false);
        return Map.copyOf(privacy);
    }
}
