package com.atlas.observability;

import com.atlas.http.KubeManagerWriteIdempotencyKeyDeriver;
import com.atlas.http.KubeManagerWriteIdempotencyKeyInput;
import com.atlas.http.KubeManagerWriteIdempotencyKeyResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local read model for the generic kube-manager write idempotency contract.
 *
 * <p>This service proves that a server-side derivation contract exists, while
 * also proving it is not yet bound to outbound HTTP writes or write retries.</p>
 */
@Service
public class AgentKubeManagerWriteIdempotencyContractService {

    private final KubeManagerWriteIdempotencyKeyDeriver deriver;
    private final Clock clock;

    public AgentKubeManagerWriteIdempotencyContractService() {
        this(new KubeManagerWriteIdempotencyKeyDeriver(), Clock.systemUTC());
    }

    AgentKubeManagerWriteIdempotencyContractService(KubeManagerWriteIdempotencyKeyDeriver deriver, Clock clock) {
        this.deriver = deriver;
        this.clock = clock;
    }

    public AgentKubeManagerWriteIdempotencyContractResponse contract() {
        KubeManagerWriteIdempotencyKeyResult sample = deriver.derive(sampleInput());
        return new AgentKubeManagerWriteIdempotencyContractResponse(
            AgentKubeManagerWriteIdempotencyContractResponse.SCHEMA_VERSION,
            Instant.now(clock),
            "CONTRACT_DEFINED_NOT_BOUND",
            true,
            false,
            false,
            false,
            keyContract(sample),
            requiredEvidence(),
            sampleProof(sample),
            bindingStatus(),
            endpointTemplates(),
            safety(),
            privacy()
        );
    }

    private KubeManagerWriteIdempotencyKeyInput sampleInput() {
        return new KubeManagerWriteIdempotencyKeyInput(
            "receipt-fixture",
            "audit-receipt-digest-fixture",
            "request-spec-digest-fixture",
            "principal-fingerprint-fixture",
            "organization-fingerprint-fixture",
            "CREATE",
            "POST",
            "/api/{organizationId}/resource/{resourceId}",
            "request-body-digest-fixture",
            "release-evidence-digest-fixture"
        );
    }

    private Map<String, Object> keyContract(KubeManagerWriteIdempotencyKeyResult sample) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", sample.schemaVersion());
        contract.put("keySource", sample.keySource());
        contract.put("algorithm", sample.algorithm());
        contract.put("keyPrefix", KubeManagerWriteIdempotencyKeyDeriver.KEY_PREFIX);
        contract.put("keyLength", sample.key().length());
        contract.put("inputDigestLength", sample.inputDigest().length());
        contract.put("callerProvidedKeyAccepted", sample.callerProvidedKeyAccepted());
        contract.put("retryAllowedByThisContract", sample.retryAllowed());
        contract.put("retryAllowedOnlyWithSameEvidence", sample.retryAllowedOnlyWithSameEvidence());
        contract.put("actualKeyExposed", false);
        return Map.copyOf(contract);
    }

    private List<Map<String, Object>> requiredEvidence() {
        return List.of(
            evidence("auditReceiptId", "durable prewrite receipt id"),
            evidence("auditReceiptDigest", "digest of the durable prewrite receipt"),
            evidence("requestSpecDigest", "digest of canonical method/path/body intent"),
            evidence("principalFingerprint", "server-side principal fingerprint, never raw username/token"),
            evidence("organizationFingerprint", "server-side tenant fingerprint, never raw organization id"),
            evidence("operationType", "normalized write operation type"),
            evidence("httpMethod", "POST/PATCH/PUT/DELETE only"),
            evidence("pathTemplate", "route template, not raw backend URL or query string"),
            evidence("requestBodyDigest", "digest of canonical request body, not raw body"),
            evidence("releaseEvidenceDigest", "digest of HITL/release evidence")
        );
    }

    private Map<String, Object> evidence(String field, String description) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("field", field);
        evidence.put("required", true);
        evidence.put("description", description);
        return Map.copyOf(evidence);
    }

    private Map<String, Object> sampleProof(KubeManagerWriteIdempotencyKeyResult sample) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("deterministicFixtureUsed", true);
        proof.put("sampleKeyPrefix", KubeManagerWriteIdempotencyKeyDeriver.KEY_PREFIX);
        proof.put("sampleKeyLength", sample.key().length());
        proof.put("sampleInputDigestLength", sample.inputDigest().length());
        proof.put("actualSampleKeyExposed", false);
        proof.put("rawSampleEvidenceExposed", false);
        return Map.copyOf(proof);
    }

    private Map<String, Object> bindingStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("contractDefined", true);
        status.put("boundToKubeManagerHttpClient", false);
        status.put("httpHeaderInjectionEnabled", false);
        status.put("writeRetryEnabled", false);
        status.put("runtimeEnableSwitchPresent", false);
        status.put("callerOverrideAllowed", false);
        status.put("requiresFutureReleaseReview", true);
        return Map.copyOf(status);
    }

    private Map<String, Object> endpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("idempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract");
        endpoints.put("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness");
        endpoints.put("healthSummary", "/api/agent/observability/kube-manager/http-outlet/health-summary");
        endpoints.put("runtimeDeriveKey", "not-exposed");
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
        safety.put("callerInputAccepted", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private Map<String, Object> privacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("actualKeyExposed", false);
        privacy.put("rawEvidenceExposed", false);
        privacy.put("containsRawBaseUrl", false);
        privacy.put("containsRawBackendPath", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsLoginUsername", false);
        privacy.put("containsLoginPassword", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        return Map.copyOf(privacy);
    }
}
