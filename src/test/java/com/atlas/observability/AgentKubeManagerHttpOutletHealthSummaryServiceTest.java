package com.atlas.observability;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager HTTP outlet health summary contract tests.
 */
class AgentKubeManagerHttpOutletHealthSummaryServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerHttpOutletHealthSummaryService.java"
    );

    @Test
    void summary_shouldExposeEffectiveReadWriteResiliencePolicyWithoutRemoteProbe() {
        AgentKubeManagerHttpOutletHealthSummaryService service = newService();

        AgentKubeManagerHttpOutletHealthSummaryResponse summary = service.summary();

        assertThat(summary.schemaVersion()).isEqualTo("agent-kube-manager-http-outlet-health-summary.v1");
        assertThat(summary.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.statusReasons()).containsExactly("local-resilience-policy-ready");
        assertThat(summary.backend())
            .containsEntry("baseUrlConfigured", true)
            .containsEntry("baseUrlRedacted", true)
            .containsEntry("baseUrlScheme", "http")
            .containsEntry("baseUrlHostConfigured", true)
            .containsEntry("baseUrlPortConfigured", true)
            .containsEntry("connectTimeoutSeconds", 7)
            .containsEntry("readTimeoutSeconds", 13)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("loginAttempted", false)
            .containsEntry("tokenInspection", false);
        assertThat(summary.readPolicy())
            .containsEntry("name", "kubeManagerRead")
            .containsEntry("registryInstancePresent", true)
            .containsEntry("automaticRetryEnabled", true)
            .containsEntry("maxAttempts", 3)
            .containsEntry("sharedCircuitBreaker", "kubeManager")
            .containsEntry("sharedBulkhead", "kubeManager");
        assertThat(summary.readPolicy().get("effectiveForHttpMethods"))
            .isEqualTo(java.util.List.of("GET"));
        assertThat(summary.writePolicy())
            .containsEntry("name", "kubeManagerWrite")
            .containsEntry("configuredRetryInstancePresent", true)
            .containsEntry("automaticRetryEnabled", false)
            .containsEntry("configuredButInactive", true)
            .containsEntry("configuredMaxAttempts", 1)
            .containsEntry("sharedCircuitBreaker", "kubeManager")
            .containsEntry("sharedBulkhead", "kubeManager");
        assertThat(summary.writePolicy().get("effectiveForHttpMethods"))
            .isEqualTo(java.util.List.of("POST", "PATCH", "PUT", "DELETE"));
        assertThat(summary.circuitBreaker())
            .containsEntry("name", "kubeManager")
            .containsEntry("registryInstancePresent", true)
            .containsEntry("state", "CLOSED")
            .containsEntry("slidingWindowSize", 50)
            .containsEntry("minimumNumberOfCalls", 10)
            .containsEntry("failureRateThreshold", 50.0f)
            .containsEntry("manualStateMutationAllowed", false);
        assertThat(summary.bulkhead())
            .containsEntry("name", "kubeManager")
            .containsEntry("registryInstancePresent", true)
            .containsEntry("availableConcurrentCalls", 32)
            .containsEntry("maxAllowedConcurrentCalls", 32)
            .containsEntry("maxConcurrentCalls", 32)
            .containsEntry("maxWaitDurationMs", 100L)
            .containsEntry("manualConfigMutationAllowed", false);
        assertThat(summary.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("summaryOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("toolExecution", false)
            .containsEntry("fallbackLogin", false)
            .containsEntry("tokenInspection", false)
            .containsEntry("circuitBreakerMutation", false);
        assertThat(summary.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsRawResponseBody", false);
        assertThat(summary.toString())
            .contains("agent-kube-manager-http-outlet-health-summary.v1", "kubeManagerRead", "kubeManager")
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
    void summary_shouldReportBlockedStatusWhenCircuitBreakerIsOpenWithoutChangingState() {
        RetryRegistry retryRegistry = retryRegistry();
        CircuitBreakerRegistry circuitBreakerRegistry = circuitBreakerRegistry();
        BulkheadRegistry bulkheadRegistry = bulkheadRegistry();
        AgentKubeManagerHttpOutletHealthSummaryService service = new AgentKubeManagerHttpOutletHealthSummaryService(
            retryRegistry,
            circuitBreakerRegistry,
            bulkheadRegistry,
            environment(),
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find("kubeManager").orElseThrow();
        circuitBreaker.transitionToOpenState();

        AgentKubeManagerHttpOutletHealthSummaryResponse summary = service.summary();

        assertThat(summary.status()).isEqualTo("OUTLET_BLOCKED");
        assertThat(summary.statusReasons()).contains("circuit-breaker-OPEN".toLowerCase(java.util.Locale.ROOT));
        assertThat(summary.circuitBreaker()).containsEntry("state", "OPEN");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenKubeManagerCalls() throws Exception {
        String source = Files.readString(SERVICE_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(");
    }

    private AgentKubeManagerHttpOutletHealthSummaryService newService() {
        return new AgentKubeManagerHttpOutletHealthSummaryService(
            retryRegistry(),
            circuitBreakerRegistry(),
            bulkheadRegistry(),
            environment(),
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );
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

    private CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(Map.of(
            "kubeManager", CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .build()
        ));
        registry.circuitBreaker("kubeManager", "kubeManager");
        return registry;
    }

    private BulkheadRegistry bulkheadRegistry() {
        BulkheadRegistry registry = BulkheadRegistry.of(Map.of(
            "kubeManager", BulkheadConfig.custom()
                .maxConcurrentCalls(32)
                .maxWaitDuration(Duration.ofMillis(100))
                .build()
        ));
        registry.bulkhead("kubeManager", "kubeManager");
        return registry;
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
            .withProperty("atlas.backend.base-url", "http://kube-manager.internal:8100")
            .withProperty("atlas.backend.connect-timeout-seconds", "7")
            .withProperty("atlas.backend.read-timeout-seconds", "13")
            .withProperty("atlas.backend.login-username", "sysadmin")
            .withProperty("atlas.backend.login-password", "secret-password");
    }
}
