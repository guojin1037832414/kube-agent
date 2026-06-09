package com.atlas.observability;

import com.atlas.http.KubeManagerWriteDurableReceiptContract;
import com.atlas.http.KubeManagerWriteReleaseEvidenceContract;
import com.atlas.http.KubeManagerWriteReleaseGateCatalog;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local read model for future kube-manager write release gates.
 *
 * <p>This service defines durable prewrite receipt and HITL/release evidence
 * contracts without issuing receipts, calling HITL services, mutating audit
 * storage, opening release switches, or enabling write retry.</p>
 */
@Service
public class AgentKubeManagerWriteReleaseGateContractService {

    private final Clock clock;

    public AgentKubeManagerWriteReleaseGateContractService() {
        this(Clock.systemUTC());
    }

    AgentKubeManagerWriteReleaseGateContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentKubeManagerWriteReleaseGateContractResponse contract() {
        return new AgentKubeManagerWriteReleaseGateContractResponse(
            AgentKubeManagerWriteReleaseGateContractResponse.SCHEMA_VERSION,
            Instant.now(clock),
            "CONTRACT_DEFINED_NOT_BOUND",
            true,
            true,
            false,
            false,
            false,
            KubeManagerWriteReleaseGateCatalog.runtimeReleaseGateOpenCount(),
            durableReceiptContract(),
            releaseEvidenceContract(),
            bindingStatus(),
            endpointTemplates(),
            safety(),
            privacy()
        );
    }

    private Map<String, Object> durableReceiptContract() {
        KubeManagerWriteDurableReceiptContract contract =
            KubeManagerWriteReleaseGateCatalog.durableReceiptContract();
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contractId", contract.contractId());
        receipt.put("contractExists", contract.contractExists());
        receipt.put("boundToHttpOutlet", contract.boundToHttpOutlet());
        receipt.put("issuerExists", contract.issuerExists());
        receipt.put("issuedByReadinessEndpoint", contract.issuedByReadinessEndpoint());
        receipt.put("durableStorageMutationAllowed", contract.durableStorageMutationAllowed());
        receipt.put("receiptPhase", contract.receiptPhase());
        receipt.put("digestAlgorithm", contract.digestAlgorithm());
        receipt.put("requiredFields", contract.requiredFields());
        receipt.put("rejectedCallerFields", contract.rejectedCallerFields());
        return Map.copyOf(receipt);
    }

    private Map<String, Object> releaseEvidenceContract() {
        KubeManagerWriteReleaseEvidenceContract contract =
            KubeManagerWriteReleaseGateCatalog.releaseEvidenceContract();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("contractId", contract.contractId());
        evidence.put("contractExists", contract.contractExists());
        evidence.put("boundToHttpOutlet", contract.boundToHttpOutlet());
        evidence.put("hitlEvidenceRequired", contract.hitlEvidenceRequired());
        evidence.put("releaseReviewRequired", contract.releaseReviewRequired());
        evidence.put("callerProvidedReleaseEvidenceAccepted", contract.callerProvidedReleaseEvidenceAccepted());
        evidence.put("canOpenReleaseSwitch", contract.canOpenReleaseSwitch());
        evidence.put("requiredEvidence", contract.requiredEvidence());
        evidence.put("rejectedEvidenceSources", contract.rejectedEvidenceSources());
        evidence.put("releaseBlockers", contract.releaseBlockers());
        return Map.copyOf(evidence);
    }

    private Map<String, Object> bindingStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("catalogDefined", true);
        status.put("durableReceiptBoundToHttpOutlet", false);
        status.put("durableReceiptIssuerExists", false);
        status.put("releaseEvidenceBoundToHttpOutlet", false);
        status.put("serverHitlConfirmationBound", false);
        status.put("runtimeReleaseSwitchPresent", false);
        status.put("releaseGateOpen", false);
        status.put("writeRetryEnabled", false);
        status.put("callerOverrideAllowed", false);
        status.put("requiresFutureReleaseReview", true);
        return Map.copyOf(status);
    }

    private Map<String, Object> endpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("writeReleaseGateContract", "/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract");
        endpoints.put("writeIdempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract");
        endpoints.put("writeOperationSafetyContract", "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract");
        endpoints.put("writeRetryGovernanceContract", "/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract");
        endpoints.put("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness");
        endpoints.put("runtimeIssueDurableReceipt", "not-exposed");
        endpoints.put("runtimeOpenReleaseGate", "not-exposed");
        endpoints.put("runtimeEnableWriteRetry", "not-exposed");
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
        safety.put("toolExecution", false);
        safety.put("hitlInvocation", false);
        safety.put("releaseDecisionSigned", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("durableStorageMutation", false);
        safety.put("httpHeaderInjection", false);
        safety.put("readbackExecuted", false);
        safety.put("resiliencePolicyMutation", false);
        safety.put("writeRetryEnablement", false);
        safety.put("releaseGateOpen", false);
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
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        privacy.put("containsRawExceptionBody", false);
        privacy.put("containsRawReleaseEvidence", false);
        privacy.put("containsRawReceipt", false);
        return Map.copyOf(privacy);
    }
}
