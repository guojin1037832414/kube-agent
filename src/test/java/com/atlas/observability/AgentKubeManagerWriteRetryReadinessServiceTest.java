package com.atlas.observability;

import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager write retry readiness contract tests.
 */
class AgentKubeManagerWriteRetryReadinessServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerWriteRetryReadinessService.java"
    );

    @Test
    void readiness_shouldFailClosedAndExplainFutureWriteRetryEvidence() {
        AgentKubeManagerWriteRetryReadinessService service = new AgentKubeManagerWriteRetryReadinessService(
            retryRegistry(),
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );

        AgentKubeManagerWriteRetryReadinessResponse readiness = service.readiness();

        assertThat(readiness.schemaVersion()).isEqualTo("agent-kube-manager-write-retry-readiness.v1");
        assertThat(readiness.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(readiness.readinessVerdict()).isEqualTo("NOT_READY");
        assertThat(readiness.readyForControlledWriteRetry()).isFalse();
        assertThat(readiness.writeRetryEnabled()).isFalse();
        assertThat(readiness.automaticWriteRetryAllowed()).isFalse();
        assertThat(readiness.effectivePolicy())
            .containsEntry("writeRetryName", "kubeManagerWrite")
            .containsEntry("configuredRetryInstancePresent", true)
            .containsEntry("configuredButInactive", true)
            .containsEntry("automaticRetryEnabled", false)
            .containsEntry("configuredMaxAttempts", 1)
            .containsEntry("runtimeEnableEndpointPresent", false)
            .containsEntry("configurationMutationAllowed", false);
        assertThat(readiness.effectivePolicy().get("effectiveForHttpMethods"))
            .isEqualTo(java.util.List.of("POST", "PATCH", "PUT", "DELETE"));
        assertThat(readiness.effectivePolicy().get("currentGuards"))
            .isEqualTo(java.util.List.of("CircuitBreaker:kubeManager", "Bulkhead:kubeManager"));
        assertThat(readiness.requirements())
            .extracting(requirement -> requirement.get("id"))
            .containsExactly(
                "server-derived-idempotency-key",
                "durable-prewrite-receipt",
                "human-release-and-hitl-evidence",
                "read-after-write-verification",
                "bounded-retry-predicate",
                "operation-allowlist-and-rbac",
                "compensation-and-replay-evidence",
                "ci-gate-and-operator-observability"
            );
        assertThat(readiness.requirements())
            .allSatisfy(requirement -> assertThat(requirement).containsEntry("satisfied", false));
        assertThat(readiness.currentEvidence())
            .containsEntry("readRetryRegistryInstancePresent", true)
            .containsEntry("writeRetryRegistryInstancePresent", true)
            .containsEntry("writeRetryConfiguredButInactive", true)
            .containsEntry("writeRetryBoundIntoExecutionPath", false)
            .containsEntry("writePathCircuitBreakerAndBulkheadOnly", true)
            .containsEntry("highRiskDurablePrewriteGateExists", true)
            .containsEntry("adminAuditQueryExists", true)
            .containsEntry("replayTimelineExists", true)
            .containsEntry("evalGateBundleExists", true)
            .containsEntry("genericKubeManagerIdempotencyBoundaryExists", true)
            .containsEntry("genericKubeManagerIdempotencyBoundaryBoundToHttpOutlet", false)
            .containsEntry("serverDerivedIdempotencyKeyDeriverExists", true)
            .containsEntry("callerProvidedIdempotencyKeyAccepted", false)
            .containsEntry("genericWriteOperationAllowlistExists", false)
            .containsEntry("retryPredicateBoundToWriteFailureClasses", false)
            .containsEntry("postWriteReadbackContractExists", false)
            .containsEntry("runtimeWriteRetryEnablementSwitchExists", false)
            .containsEntry("nimHpcSlurmBcmPhase2Paused", true);
        assertThat(readiness.blockedReasons()).contains(
            "generic-kube-manager-idempotency-boundary-not-bound-to-http-outlet",
            "write-operation-allowlist-missing",
            "post-write-readback-contract-missing",
            "runtime-enable-switch-intentionally-absent"
        );
        assertThat(readiness.futureEnablementProtocol())
            .containsEntry("enablementMode", "future-code-release-only")
            .containsEntry("runtimeToggleAllowed", false)
            .containsEntry("callerCanRequestRetry", false)
            .containsEntry("defaultIfAnyCheckMissing", "fail-closed-no-auto-retry");
        assertThat(readiness.endpointTemplates())
            .containsEntry("healthSummary", "/api/agent/observability/kube-manager/http-outlet/health-summary")
            .containsEntry("writeIdempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
            .containsEntry("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
            .containsEntry("runtimeEnableWriteRetry", "not-exposed");
        assertThat(readiness.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("summaryOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableStorageMutation", false)
            .containsEntry("resiliencePolicyMutation", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("callerInputAccepted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(readiness.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsRawResponseBody", false);
        assertThat(readiness.toString())
            .contains("agent-kube-manager-write-retry-readiness.v1", "NOT_READY", "kubeManagerWrite")
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
    void readiness_shouldStillFailClosedWhenNoWriteRetryInstanceExists() {
        RetryRegistry registry = RetryRegistry.of(Map.of(
            "kubeManagerRead", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build()
        ));
        registry.retry("kubeManagerRead", "kubeManagerRead");
        AgentKubeManagerWriteRetryReadinessService service = new AgentKubeManagerWriteRetryReadinessService(
            registry,
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );

        AgentKubeManagerWriteRetryReadinessResponse readiness = service.readiness();

        assertThat(readiness.readinessVerdict()).isEqualTo("NOT_READY");
        assertThat(readiness.writeRetryEnabled()).isFalse();
        assertThat(readiness.automaticWriteRetryAllowed()).isFalse();
        assertThat(readiness.effectivePolicy())
            .containsEntry("configuredRetryInstancePresent", false)
            .containsEntry("configuredButInactive", false)
            .containsEntry("automaticRetryEnabled", false);
        assertThat(readiness.effectivePolicy()).doesNotContainKey("configuredMaxAttempts");
        assertThat(readiness.currentEvidence())
            .containsEntry("writeRetryRegistryInstancePresent", false)
            .containsEntry("writeRetryConfiguredButInactive", false)
            .containsEntry("writeRetryBoundIntoExecutionPath", false)
            .containsEntry("genericKubeManagerIdempotencyBoundaryExists", true)
            .containsEntry("genericKubeManagerIdempotencyBoundaryBoundToHttpOutlet", false);
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenWriteRetryEnablement() throws Exception {
        String source = Files.readString(SERVICE_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("transitionTo")
            .doesNotContain(".changeConfig")
            .doesNotContain(".reset()");
    }

    private RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.of(Map.of(
            "kubeManagerRead", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build(),
            "kubeManagerWrite", RetryConfig.custom()
                .maxAttempts(1)
                .build()
        ));
        registry.retry("kubeManagerRead", "kubeManagerRead");
        registry.retry("kubeManagerWrite", "kubeManagerWrite");
        return registry;
    }
}
