package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager write retry governance contract read-model tests.
 */
class AgentKubeManagerWriteRetryGovernanceContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerWriteRetryGovernanceContractService.java"
    );

    @Test
    void contract_shouldDescribeRetryGovernanceWithoutRuntimeBinding() {
        AgentKubeManagerWriteRetryGovernanceContractService service =
            new AgentKubeManagerWriteRetryGovernanceContractService(
                Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
            );

        AgentKubeManagerWriteRetryGovernanceContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-retry-governance-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.retryPredicateContractExists()).isTrue();
        assertThat(contract.compensationPolicyContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.runtimeRetryableFailureClassCount()).isZero();
        assertThat(contract.automaticCompensationPolicyCount()).isZero();
        assertThat(contract.predicateContract())
            .containsEntry("contractId", "generic-write-retry-predicate.v1")
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("runtimePredicateExists", false)
            .containsEntry("callerOverrideAccepted", false)
            .containsEntry("maxAttempts", 2)
            .containsEntry("backoffStrategy", "bounded-jittered-exponential")
            .containsEntry("sameIdempotencyKeyRequired", true)
            .containsEntry("durablePrewriteReceiptRequired", true)
            .containsEntry("postWriteReadbackRequiredBeforeSuccess", true);
        assertThat(contract.failureClasses())
            .extracting(item -> item.get("failureClassId"))
            .contains("transport-timeout-before-acceptance", "caller-validation-error", "unknown-acceptance-without-readback");
        assertThat(contract.failureClasses())
            .allSatisfy(item -> assertThat(item).containsEntry("runtimeRetryableNow", false));
        assertThat(contract.compensationPolicies())
            .extracting(item -> item.get("policyId"))
            .containsExactly(
                "create-unknown-acceptance-review",
                "update-partial-state-review",
                "delete-unknown-state-review",
                "action-unknown-effect-review"
            );
        assertThat(contract.compensationPolicies())
            .allSatisfy(item -> assertThat(item)
                .containsEntry("automaticCompensationAllowed", false)
                .containsEntry("operatorReviewRequired", true)
                .containsEntry("runtimeBound", false)
                .containsEntry("canOpenReleaseSwitch", false));
        assertThat(contract.bindingStatus())
            .containsEntry("catalogDefined", true)
            .containsEntry("retryPredicateBoundToResilience4j", false)
            .containsEntry("retryPredicateBoundToHttpOutlet", false)
            .containsEntry("failureClassifierRuntimeBound", false)
            .containsEntry("compensationExecutorExists", false)
            .containsEntry("automaticCompensationAllowed", false)
            .containsEntry("writeRetryEnabled", false)
            .containsEntry("runtimeEnableSwitchPresent", false)
            .containsEntry("callerOverrideAllowed", false);
        assertThat(contract.endpointTemplates())
            .containsEntry("writeRetryGovernanceContract", "/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract")
            .containsEntry("writeOperationSafetyContract", "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract")
            .containsEntry("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
            .containsEntry("runtimeEnableWriteRetry", "not-exposed")
            .containsEntry("runtimeCompensationExecutor", "not-exposed");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("summaryOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("resiliencePredicateMutation", false)
            .containsEntry("retryRegistryMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("readbackExecuted", false)
            .containsEntry("compensationExecuted", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("callerInputAccepted", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawResponseBody", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-retry-governance-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String source = Files.readString(SERVICE_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("RetryRegistry")
            .doesNotContain("Retry.decorate")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain(".changeConfig")
            .doesNotContain(".reset()");
    }
}
