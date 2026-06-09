package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKubeManagerWriteReleaseGateContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerWriteReleaseGateContractService.java"
    );

    @Test
    void contract_shouldDescribeReleaseGateWithoutIssuingReceiptsOrOpeningRuntimeSwitches() {
        AgentKubeManagerWriteReleaseGateContractService service =
            new AgentKubeManagerWriteReleaseGateContractService(
                Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
            );

        AgentKubeManagerWriteReleaseGateContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-release-gate-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.durableReceiptContractExists()).isTrue();
        assertThat(contract.releaseEvidenceContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.releaseGateOpen()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.runtimeReleaseGateOpenCount()).isZero();
        assertThat(contract.durableReceiptContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("issuerExists", false)
            .containsEntry("issuedByReadinessEndpoint", false)
            .containsEntry("durableStorageMutationAllowed", false)
            .containsEntry("receiptPhase", "PRE_EXECUTION")
            .containsEntry("digestAlgorithm", "SHA-256");
        assertThat(contract.releaseEvidenceContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("hitlEvidenceRequired", true)
            .containsEntry("releaseReviewRequired", true)
            .containsEntry("callerProvidedReleaseEvidenceAccepted", false)
            .containsEntry("canOpenReleaseSwitch", false);
        assertThat(contract.bindingStatus())
            .containsEntry("durableReceiptBoundToHttpOutlet", false)
            .containsEntry("durableReceiptIssuerExists", false)
            .containsEntry("releaseEvidenceBoundToHttpOutlet", false)
            .containsEntry("serverHitlConfirmationBound", false)
            .containsEntry("runtimeReleaseSwitchPresent", false)
            .containsEntry("releaseGateOpen", false)
            .containsEntry("writeRetryEnabled", false);
        assertThat(contract.endpointTemplates())
            .containsEntry("writeReleaseGateContract", "/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract")
            .containsEntry("runtimeIssueDurableReceipt", "not-exposed")
            .containsEntry("runtimeOpenReleaseGate", "not-exposed")
            .containsEntry("runtimeEnableWriteRetry", "not-exposed");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("releaseDecisionSigned", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("durableStorageMutation", false)
            .containsEntry("httpHeaderInjection", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("releaseGateOpen", false)
            .containsEntry("callerInputAccepted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawReleaseEvidence", false)
            .containsEntry("containsRawReceipt", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-release-gate-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain(
                "kube-manager.internal",
                "secret-password",
                "sysadmin",
                "Bearer",
                "user-token",
                "/api/login",
                "/api/100002"
            );
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String source = Files.readString(SERVICE_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("RetryRegistry")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("HitlController")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("transitionTo")
            .doesNotContain(".changeConfig")
            .doesNotContain(".reset()");
    }
}
