package com.atlas.observability;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager governance workbench overview contract tests.
 */
class AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse.java"
    );

    @Test
    void overview_shouldBuildVueReadModelWithoutRuntimeWriteAuthority() {
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService service = newService();

        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse overview = service.overview();

        assertThat(overview.schemaVersion())
            .isEqualTo("agent-kube-manager-http-outlet-governance-workbench-overview.v1");
        assertThat(overview.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(overview.workbenchStatus()).isEqualTo("WRITE_GOVERNANCE_NOT_READY");
        assertThat(overview.frontendTarget())
            .isEqualTo("vue-kube-manager kube-manager HTTP outlet governance workbench");
        assertThat(overview.httpOutletStatus()).isEqualTo("READY");
        assertThat(overview.writeReadinessVerdict()).isEqualTo("NOT_READY");
        assertThat(overview.releaseGateOpen()).isFalse();
        assertThat(overview.writeRetryEnabled()).isFalse();
        assertThat(overview.automaticWriteRetryAllowed()).isFalse();
        assertThat(overview.governanceCardCount()).isEqualTo(6);
        assertThat(overview.blockingCardCount()).isEqualTo(5);
        assertThat(overview.boundRuntimeContractCount()).isZero();
        assertThat(overview.runtimeReleaseGateOpenCount()).isZero();
        assertThat(overview.runtimeRetryableFailureClassCount()).isZero();
        assertThat(overview.automaticCompensationPolicyCount()).isZero();
        assertThat(overview.governanceCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "http-outlet-health",
                "write-retry-readiness",
                "write-idempotency-contract",
                "write-operation-safety-contract",
                "write-retry-governance-contract",
                "write-release-gate-contract"
            );
        assertThat(overview.governanceCards()).allSatisfy(card -> {
            assertThat(card)
                .containsEntry("frontendNavigationOnly", true)
                .containsEntry("readOnly", true)
                .containsEntry("runtimeMutationAllowed", false)
                .containsEntry("kubeManagerCalls", false)
                .containsEntry("toolExecution", false)
                .containsEntry("llmUsed", false);
        });
        assertThat(overview.governanceCards().get(0))
            .containsEntry("severity", "INFO")
            .containsEntry("status", "READY");
        assertThat(overview.governanceCards().subList(1, overview.governanceCards().size()))
            .allSatisfy(card -> assertThat(card).containsEntry("severity", "BLOCKING"));
        assertThat(overview.recommendedWorkflow()).containsExactly(
            "governance-workbench-overview",
            "http-outlet-health-summary",
            "write-retry-readiness",
            "write-idempotency-contract",
            "write-operation-safety-contract",
            "write-retry-governance-contract",
            "write-release-gate-contract",
            "eval-workbench-gate-bundle-summary",
            "human-release-review-before-runtime-binding"
        );
        assertThat(overview.nextActions())
            .contains(
                "render-vue-governance-cards-with-blocking-reasons",
                "keep-kube-manager-write-retry-disabled",
                "design-runtime-binding-only-after-durable-receipt-and-release-review",
                "curate-real-redacted-eval-traces-before-any-release-gate",
                "keep-nim-hpc-slurm-bcm-paused-for-phase2"
            );
        assertThat(overview.healthSummary().schemaVersion())
            .isEqualTo("agent-kube-manager-http-outlet-health-summary.v1");
        assertThat(overview.writeRetryReadiness().readinessVerdict()).isEqualTo("NOT_READY");
        assertThat(overview.idempotencyContract().boundToHttpOutlet()).isFalse();
        assertThat(overview.operationSafetyContract().boundToHttpOutlet()).isFalse();
        assertThat(overview.retryGovernanceContract().boundToHttpOutlet()).isFalse();
        assertThat(overview.releaseGateContract().boundToHttpOutlet()).isFalse();
        assertThat(overview.workbenchPolicy())
            .containsEntry("overviewOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("runtimeWriteBindingAllowed", false)
            .containsEntry("runtimeReleaseGateSwitchPresent", false)
            .containsEntry("releaseGateOpen", false)
            .containsEntry("writeRetryEnabled", false)
            .containsEntry("writeRetryEnablementAllowed", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("durableStorageMutation", false)
            .containsEntry("resiliencePolicyMutation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsRawBackendPath", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawOrganization", false)
            .containsEntry("containsRawRequestBody", false)
            .containsEntry("containsRawResponseBody", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.toString())
            .contains("governance-workbench-overview", "write-release-gate-contract")
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "/api/login")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive");
    }

    @Test
    void source_shouldRemainReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("does not call")
            .contains("healthSummaryService.summary()")
            .contains("writeRetryReadinessService.readiness()")
            .contains("idempotencyContractService.contract()")
            .contains("operationSafetyContractService.contract()")
            .contains("retryGovernanceContractService.contract()")
            .contains("releaseGateContractService.contract()")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("executeWrite(")
            .doesNotContain("record(")
            .doesNotContain("issue")
            .doesNotContain("openRelease")
            .doesNotContain("enableWriteRetry");
        assertThat(responseSource)
            .contains("runtimeMutationAllowed")
            .contains("runtimeWriteBindingAllowed")
            .contains("runtimeReleaseGateSwitchPresent")
            .contains("writeRetryEnablementAllowed")
            .contains("phase2NimHpcSlurmBcmTouched")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("executeWrite(")
            .doesNotContain("openRelease")
            .doesNotContain("enableWriteRetry");
    }

    private AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService newService() {
        RetryRegistry retryRegistry = RetryRegistry.of(java.util.Map.of(
            "kubeManagerRead", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(500)).build(),
            "kubeManagerWrite", RetryConfig.custom().maxAttempts(1).build()
        ));
        retryRegistry.retry("kubeManagerRead", "kubeManagerRead");
        retryRegistry.retry("kubeManagerWrite", "kubeManagerWrite");
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(java.util.Map.of(
            "kubeManager", CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .build()
        ));
        circuitBreakerRegistry.circuitBreaker("kubeManager", "kubeManager");
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(java.util.Map.of(
            "kubeManager", BulkheadConfig.custom()
                .maxConcurrentCalls(32)
                .maxWaitDuration(Duration.ofMillis(100))
                .build()
        ));
        bulkheadRegistry.bulkhead("kubeManager", "kubeManager");
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);
        return new AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService(
            new AgentKubeManagerHttpOutletHealthSummaryService(
                retryRegistry,
                circuitBreakerRegistry,
                bulkheadRegistry,
                new MockEnvironment()
                    .withProperty("atlas.backend.base-url", "http://kube-manager.internal:8100")
                    .withProperty("atlas.backend.connect-timeout-seconds", "10")
                    .withProperty("atlas.backend.read-timeout-seconds", "30")
                    .withProperty("atlas.backend.login-password", "secret-password"),
                clock
            ),
            new AgentKubeManagerWriteRetryReadinessService(retryRegistry, clock),
            new AgentKubeManagerWriteIdempotencyContractService(
                new com.atlas.http.KubeManagerWriteIdempotencyKeyDeriver(),
                clock
            ),
            new AgentKubeManagerWriteOperationSafetyContractService(clock),
            new AgentKubeManagerWriteRetryGovernanceContractService(clock),
            new AgentKubeManagerWriteReleaseGateContractService(clock),
            clock
        );
    }
}
