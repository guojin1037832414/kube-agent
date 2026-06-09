package com.atlas.observability;

import com.atlas.http.KubeManagerPostWriteReadbackContract;
import com.atlas.http.KubeManagerWriteOperationAllowlistEntry;
import com.atlas.http.KubeManagerWriteSafetyContractCatalog;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local read model for the generic kube-manager write operation safety contract.
 *
 * <p>This service intentionally does not call kube-manager, bind to the HTTP
 * client, issue audit receipts, or enable retries. It only defines the
 * allowlist/RBAC/readback evidence shape that future releases must satisfy.</p>
 */
@Service
public class AgentKubeManagerWriteOperationSafetyContractService {

    private final Clock clock;

    public AgentKubeManagerWriteOperationSafetyContractService() {
        this(Clock.systemUTC());
    }

    AgentKubeManagerWriteOperationSafetyContractService(Clock clock) {
        this.clock = clock;
    }

    public AgentKubeManagerWriteOperationSafetyContractResponse contract() {
        return new AgentKubeManagerWriteOperationSafetyContractResponse(
            AgentKubeManagerWriteOperationSafetyContractResponse.SCHEMA_VERSION,
            Instant.now(clock),
            "CONTRACT_DEFINED_NOT_BOUND",
            true,
            true,
            false,
            false,
            allowedOperationClasses(),
            requiredRbacEvidence(),
            readbackContract(),
            retryEligibilityGates(),
            blockedRuntimeBindings(),
            endpointTemplates(),
            safety(),
            privacy()
        );
    }

    private List<Map<String, Object>> allowedOperationClasses() {
        return KubeManagerWriteSafetyContractCatalog.reviewOnlyAllowlistEntries().stream()
            .map(this::allowedOperationClass)
            .toList();
    }

    private Map<String, Object> allowedOperationClass(KubeManagerWriteOperationAllowlistEntry entry) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("id", entry.operationId());
        operation.put("operationType", entry.operationType());
        operation.put("httpMethod", entry.httpMethod());
        operation.put("pathTemplate", entry.pathTemplate());
        operation.put("rbacRequirement", entry.rbacRequirement());
        operation.put("tenantBinding", entry.tenantBinding());
        operation.put("readbackContractId", entry.readbackContractId());
        operation.put("tenantScoped", true);
        operation.put("highRisk", true);
        operation.put("requiresDurablePrewriteReceipt", true);
        operation.put("requiresServerDerivedIdempotencyKey", true);
        operation.put("requiresRbacEvidence", true);
        operation.put("requiresHumanReleaseEvidence", true);
        operation.put("requiresPostWriteReadback", true);
        operation.put("eligibleForRuntimeExecutionNow", entry.runtimeEligible());
        operation.put("eligibleForAutomaticRetryNow", entry.retryEligible());
        operation.put("boundToHttpOutlet", false);
        operation.put("phase2ExcludedDomain", entry.phase2Excluded());
        return Map.copyOf(operation);
    }

    private List<Map<String, Object>> requiredRbacEvidence() {
        return List.of(
            rbacEvidence("principalFingerprint", "server-side principal fingerprint, never raw username or token"),
            rbacEvidence("organizationFingerprint", "server-side tenant fingerprint, never raw organization id"),
            rbacEvidence("rolePermissionDigest", "digest of roles and permissions proven by the server-side principal"),
            rbacEvidence("tenantOwnershipEvidenceDigest", "digest proving the target resource belongs to the active tenant"),
            rbacEvidence("toolRiskMetadataDigest", "digest of the Tool risk metadata that classified the operation as high risk"),
            rbacEvidence("hitlConfirmationDigest", "digest of the explicit human confirmation evidence"),
            rbacEvidence("releaseEvidenceDigest", "digest of release review evidence for the operation class"),
            rbacEvidence("requestSpecDigest", "digest of canonical method, route template, operation type, and request body")
        );
    }

    private Map<String, Object> rbacEvidence(String field, String description) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("field", field);
        evidence.put("required", true);
        evidence.put("description", description);
        evidence.put("rawValueAllowed", false);
        return Map.copyOf(evidence);
    }

    private Map<String, Object> readbackContract() {
        KubeManagerPostWriteReadbackContract catalogContract =
            KubeManagerWriteSafetyContractCatalog.genericReadbackContract();
        Map<String, Object> readback = new LinkedHashMap<>();
        readback.put("contractId", catalogContract.contractId());
        readback.put("contractExists", true);
        readback.put("boundToHttpOutlet", false);
        readback.put("readEndpointTemplate", catalogContract.readEndpointTemplate());
        readback.put("readMethod", catalogContract.readMethod());
        readback.put("samePrincipalRequired", catalogContract.samePrincipalRequired());
        readback.put("sameOrganizationFingerprintRequired", catalogContract.sameOrganizationFingerprintRequired());
        readback.put("requestSpecDigestRequired", catalogContract.requestSpecDigestRequired());
        readback.put("idempotencyDigestRequired", catalogContract.idempotencyDigestRequired());
        readback.put("readbackIsWriteFree", true);
        readback.put("successClaimRequiresReadback", catalogContract.successClaimRequiresReadback());
        readback.put("acceptsCallerSuccessClaim", catalogContract.acceptsCallerSuccessClaim());
        readback.put("executorExists", catalogContract.executorExists());
        readback.put("executedByReadinessEndpoint", catalogContract.executedByReadinessEndpoint());
        readback.put("canOpenReleaseSwitch", catalogContract.canOpenReleaseSwitch());
        readback.put("statusFieldNames", catalogContract.statusFieldNames());
        readback.put("expectedSuccessTerminalStates", catalogContract.expectedSuccessTerminalStates());
        readback.put("expectedFailureTerminalStates", catalogContract.expectedFailureTerminalStates());
        readback.put("timeoutPolicy", Map.of(
            "maxElapsedSeconds", 120,
            "maxAttempts", 8,
            "backoff", "bounded-jittered-exponential",
            "defaultIfUnverified", "UNKNOWN_NEEDS_OPERATOR_REVIEW"
        ));
        readback.put("failureHandling", List.of(
            "do-not-claim-success-without-readback",
            "record-final-audit-outcome-before-returning",
            "surface-compensation-guidance-to-operator",
            "never-enable-automatic-retry-from-readback-endpoint"
        ));
        return Map.copyOf(readback);
    }

    private Map<String, Object> retryEligibilityGates() {
        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("serverDerivedIdempotencyKeyRequired", true);
        gates.put("durablePrewriteReceiptRequired", true);
        gates.put("operationAllowlistRequired", true);
        gates.put("rbacEvidenceRequired", true);
        gates.put("humanReleaseEvidenceRequired", true);
        gates.put("boundedRetryPredicateRequired", true);
        gates.put("postWriteReadbackRequired", true);
        gates.put("compensationPolicyRequired", true);
        gates.put("redactedReplayEvalGateRequired", true);
        gates.put("runtimeRetryEligibleWriteOperationCount", KubeManagerWriteSafetyContractCatalog.runtimeRetryEligibleOperationCount());
        gates.put("phase2ExcludedDomains", KubeManagerWriteSafetyContractCatalog.phase2ExcludedDomains());
        gates.put("runtimeBindingAllowedNow", false);
        gates.put("callerOverrideAllowed", false);
        gates.put("defaultIfAnyGateMissing", "fail-closed-no-write-retry");
        return Map.copyOf(gates);
    }

    private Map<String, Object> blockedRuntimeBindings() {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("kubeManagerHttpClientBinding", "blocked-until-release-review");
        bindings.put("executeWriteBinding", "blocked-until-release-review");
        bindings.put("httpHeaderInjection", "blocked-until-idempotency-bound");
        bindings.put("writeRetryPredicateBinding", "blocked-until-failure-classes-modeled");
        bindings.put("realWriteExecution", "blocked-by-contract");
        bindings.put("runtimeEnableSwitch", "not-exposed");
        bindings.put("resiliencePolicyMutation", "blocked-by-contract");
        bindings.put("auditMutation", "not-performed-by-this-read-model");
        bindings.put("toolExecution", "not-performed-by-this-read-model");
        bindings.put("llmExecution", "not-performed-by-this-read-model");
        return Map.copyOf(bindings);
    }

    private Map<String, Object> endpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("operationSafetyContract", "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract");
        endpoints.put("writeIdempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract");
        endpoints.put("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness");
        endpoints.put("healthSummary", "/api/agent/observability/kube-manager/http-outlet/health-summary");
        endpoints.put("runtimeAllowlistMutation", "not-exposed");
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
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("httpHeaderInjection", false);
        safety.put("writeRetryEnablement", false);
        safety.put("allowlistMutation", false);
        safety.put("readbackExecuted", false);
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
