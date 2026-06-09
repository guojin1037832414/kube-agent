package com.atlas.observability;

import com.atlas.http.KubeManagerWriteIdempotencyKeyDeriver;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager write idempotency contract read-model tests.
 */
class AgentKubeManagerWriteIdempotencyContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerWriteIdempotencyContractService.java"
    );
    private static final Path DERIVER_SOURCE = Path.of(
        "src/main/java/com/atlas/http/KubeManagerWriteIdempotencyKeyDeriver.java"
    );

    @Test
    void contract_shouldDescribeServerDerivedKeyWithoutBindingRuntimeWrites() {
        AgentKubeManagerWriteIdempotencyContractService service =
            new AgentKubeManagerWriteIdempotencyContractService(
                new KubeManagerWriteIdempotencyKeyDeriver(),
                Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
            );

        AgentKubeManagerWriteIdempotencyContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-idempotency-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.serverDerivedKeyContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.callerProvidedIdempotencyKeyAccepted()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.keyContract())
            .containsEntry("schemaVersion", "kube-manager-write-idempotency-key.v1")
            .containsEntry("keySource", "server-derived-sha256-bound-evidence.v1")
            .containsEntry("algorithm", "SHA-256")
            .containsEntry("keyPrefix", "km-write-v1-")
            .containsEntry("keyLength", 76)
            .containsEntry("inputDigestLength", 64)
            .containsEntry("callerProvidedKeyAccepted", false)
            .containsEntry("retryAllowedByThisContract", false)
            .containsEntry("retryAllowedOnlyWithSameEvidence", true)
            .containsEntry("actualKeyExposed", false);
        assertThat(contract.requiredEvidence())
            .extracting(evidence -> evidence.get("field"))
            .containsExactly(
                "auditReceiptId",
                "auditReceiptDigest",
                "requestSpecDigest",
                "principalFingerprint",
                "organizationFingerprint",
                "operationType",
                "httpMethod",
                "pathTemplate",
                "requestBodyDigest",
                "releaseEvidenceDigest"
            );
        assertThat(contract.sampleProof())
            .containsEntry("deterministicFixtureUsed", true)
            .containsEntry("sampleKeyPrefix", "km-write-v1-")
            .containsEntry("sampleKeyLength", 76)
            .containsEntry("sampleInputDigestLength", 64)
            .containsEntry("actualSampleKeyExposed", false)
            .containsEntry("rawSampleEvidenceExposed", false);
        assertThat(contract.bindingStatus())
            .containsEntry("contractDefined", true)
            .containsEntry("boundToKubeManagerHttpClient", false)
            .containsEntry("httpHeaderInjectionEnabled", false)
            .containsEntry("writeRetryEnabled", false)
            .containsEntry("runtimeEnableSwitchPresent", false)
            .containsEntry("callerOverrideAllowed", false)
            .containsEntry("requiresFutureReleaseReview", true);
        assertThat(contract.endpointTemplates())
            .containsEntry("idempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
            .containsEntry("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
            .containsEntry("runtimeDeriveKey", "not-exposed")
            .containsEntry("runtimeEnableWriteRetry", "not-exposed");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("summaryOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("httpHeaderInjection", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("callerInputAccepted", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("actualKeyExposed", false)
            .containsEntry("rawEvidenceExposed", false)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawRequestBody", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-idempotency-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain(
                "receipt-fixture",
                "audit-receipt-digest-fixture",
                "principal-fingerprint-fixture",
                "organization-fingerprint-fixture",
                "request-body-digest-fixture",
                "km-write-v1-0",
                "Bearer",
                "secret-password",
                "/api/100002",
                "http://kube-manager.internal"
            );
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String deriverSource = Files.readString(DERIVER_SOURCE);

        assertThat(serviceSource)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("transitionTo")
            .doesNotContain(".reset()");
        assertThat(deriverSource)
            .doesNotContain("RestClient")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("executeWrite(")
            .doesNotContain("Authorization")
            .doesNotContain("X-Token");
    }
}
