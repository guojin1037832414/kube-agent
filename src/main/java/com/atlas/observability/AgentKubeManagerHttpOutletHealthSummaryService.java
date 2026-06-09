package com.atlas.observability;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a safe local summary of the kube-manager HTTP outlet.
 *
 * <p>Important boundary: this service never calls {@code KubeManagerHttpClient}
 * or {@code RestClient}. It only reads Spring configuration and Resilience4j
 * registry state, so opening the page cannot become a remote health probe or a
 * hidden kube-manager request.</p>
 */
@Service
public class AgentKubeManagerHttpOutletHealthSummaryService {

    private static final String READ_RETRY = "kubeManagerRead";
    private static final String WRITE_RETRY = "kubeManagerWrite";
    private static final String KUBE_MANAGER = "kubeManager";

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final Environment environment;
    private final Clock clock;

    public AgentKubeManagerHttpOutletHealthSummaryService(RetryRegistry retryRegistry,
                                                          CircuitBreakerRegistry circuitBreakerRegistry,
                                                          BulkheadRegistry bulkheadRegistry,
                                                          Environment environment) {
        this(retryRegistry, circuitBreakerRegistry, bulkheadRegistry, environment, Clock.systemUTC());
    }

    AgentKubeManagerHttpOutletHealthSummaryService(RetryRegistry retryRegistry,
                                                   CircuitBreakerRegistry circuitBreakerRegistry,
                                                   BulkheadRegistry bulkheadRegistry,
                                                   Environment environment,
                                                   Clock clock) {
        this.retryRegistry = retryRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.environment = environment;
        this.clock = clock;
    }

    public AgentKubeManagerHttpOutletHealthSummaryResponse summary() {
        Retry readRetry = retryRegistry.find(READ_RETRY).orElse(null);
        Retry configuredWriteRetry = retryRegistry.find(WRITE_RETRY).orElse(null);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(KUBE_MANAGER).orElse(null);
        Bulkhead bulkhead = bulkheadRegistry.find(KUBE_MANAGER).orElse(null);

        Map<String, Object> readPolicy = readPolicy(readRetry);
        Map<String, Object> writePolicy = writePolicy(configuredWriteRetry);
        Map<String, Object> circuitBreakerSummary = circuitBreakerSummary(circuitBreaker);
        Map<String, Object> bulkheadSummary = bulkheadSummary(bulkhead);
        List<String> statusReasons = statusReasons(readRetry, circuitBreaker, bulkhead);

        return new AgentKubeManagerHttpOutletHealthSummaryResponse(
            AgentKubeManagerHttpOutletHealthSummaryResponse.SCHEMA_VERSION,
            Instant.now(clock),
            status(circuitBreaker, bulkhead, statusReasons),
            statusReasons,
            backend(),
            readPolicy,
            writePolicy,
            circuitBreakerSummary,
            bulkheadSummary,
            safety(),
            privacy()
        );
    }

    private Map<String, Object> backend() {
        String baseUrl = environment.getProperty("atlas.backend.base-url", "http://localhost:8100");
        URI uri = safeUri(baseUrl);
        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("baseUrlConfigured", baseUrl != null && !baseUrl.isBlank());
        backend.put("baseUrlRedacted", true);
        backend.put("baseUrlScheme", uri != null ? safeText(uri.getScheme()) : "unknown");
        backend.put("baseUrlHostConfigured", uri != null && uri.getHost() != null && !uri.getHost().isBlank());
        backend.put("baseUrlPortConfigured", uri != null && uri.getPort() > 0);
        backend.put("connectTimeoutSeconds", intProperty("atlas.backend.connect-timeout-seconds", 10));
        backend.put("readTimeoutSeconds", intProperty("atlas.backend.read-timeout-seconds", 30));
        backend.put("remoteProbeExecuted", false);
        backend.put("loginAttempted", false);
        backend.put("tokenInspection", false);
        return Map.copyOf(backend);
    }

    private Map<String, Object> readPolicy(Retry retry) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", READ_RETRY);
        policy.put("registryInstancePresent", retry != null);
        policy.put("automaticRetryEnabled", retry != null);
        policy.put("effectiveForHttpMethods", List.of("GET"));
        policy.put("sharedCircuitBreaker", KUBE_MANAGER);
        policy.put("sharedBulkhead", KUBE_MANAGER);
        if (retry != null) {
            RetryConfig config = retry.getRetryConfig();
            Retry.Metrics metrics = retry.getMetrics();
            policy.put("maxAttempts", config.getMaxAttempts());
            policy.put("waitStrategy", "intervalFunction");
            policy.put("totalCalls", metrics.getNumberOfTotalCalls());
            policy.put("successfulCallsWithoutRetryAttempt", metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
            policy.put("successfulCallsWithRetryAttempt", metrics.getNumberOfSuccessfulCallsWithRetryAttempt());
            policy.put("failedCallsWithoutRetryAttempt", metrics.getNumberOfFailedCallsWithoutRetryAttempt());
            policy.put("failedCallsWithRetryAttempt", metrics.getNumberOfFailedCallsWithRetryAttempt());
        }
        return Map.copyOf(policy);
    }

    private Map<String, Object> writePolicy(Retry configuredWriteRetry) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", WRITE_RETRY);
        policy.put("configuredRetryInstancePresent", configuredWriteRetry != null);
        policy.put("automaticRetryEnabled", false);
        policy.put("effectiveForHttpMethods", List.of("POST", "PATCH", "PUT", "DELETE"));
        policy.put("configuredButInactive", configuredWriteRetry != null);
        policy.put("reason", "Writes use circuit breaker and bulkhead only until idempotency evidence is implemented.");
        policy.put("sharedCircuitBreaker", KUBE_MANAGER);
        policy.put("sharedBulkhead", KUBE_MANAGER);
        if (configuredWriteRetry != null) {
            policy.put("configuredMaxAttempts", configuredWriteRetry.getRetryConfig().getMaxAttempts());
        }
        return Map.copyOf(policy);
    }

    private Map<String, Object> circuitBreakerSummary(CircuitBreaker circuitBreaker) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", KUBE_MANAGER);
        summary.put("registryInstancePresent", circuitBreaker != null);
        if (circuitBreaker == null) {
            return Map.copyOf(summary);
        }
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
        summary.put("state", circuitBreaker.getState().name());
        summary.put("failureRate", metrics.getFailureRate());
        summary.put("slowCallRate", metrics.getSlowCallRate());
        summary.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        summary.put("failedCalls", metrics.getNumberOfFailedCalls());
        summary.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());
        summary.put("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
        summary.put("slidingWindowSize", config.getSlidingWindowSize());
        summary.put("minimumNumberOfCalls", config.getMinimumNumberOfCalls());
        summary.put("failureRateThreshold", config.getFailureRateThreshold());
        summary.put("slowCallRateThreshold", config.getSlowCallRateThreshold());
        summary.put("slowCallDurationThresholdMs", config.getSlowCallDurationThreshold().toMillis());
        summary.put("manualStateMutationAllowed", false);
        return Map.copyOf(summary);
    }

    private Map<String, Object> bulkheadSummary(Bulkhead bulkhead) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", KUBE_MANAGER);
        summary.put("registryInstancePresent", bulkhead != null);
        if (bulkhead == null) {
            return Map.copyOf(summary);
        }
        Bulkhead.Metrics metrics = bulkhead.getMetrics();
        BulkheadConfig config = bulkhead.getBulkheadConfig();
        summary.put("availableConcurrentCalls", metrics.getAvailableConcurrentCalls());
        summary.put("maxAllowedConcurrentCalls", metrics.getMaxAllowedConcurrentCalls());
        summary.put("maxConcurrentCalls", config.getMaxConcurrentCalls());
        summary.put("maxWaitDurationMs", config.getMaxWaitDuration().toMillis());
        summary.put("manualConfigMutationAllowed", false);
        return Map.copyOf(summary);
    }

    private List<String> statusReasons(Retry readRetry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        List<String> reasons = new ArrayList<>();
        if (readRetry == null) {
            reasons.add("read-retry-registry-instance-missing");
        }
        if (circuitBreaker == null) {
            reasons.add("circuit-breaker-registry-instance-missing");
        }
        if (bulkhead == null) {
            reasons.add("bulkhead-registry-instance-missing");
        }
        if (circuitBreaker != null && circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            reasons.add("circuit-breaker-" + circuitBreaker.getState().name().toLowerCase(Locale.ROOT));
        }
        if (bulkhead != null && bulkhead.getMetrics().getAvailableConcurrentCalls() <= 0) {
            reasons.add("bulkhead-saturated");
        }
        if (reasons.isEmpty()) {
            reasons.add("local-resilience-policy-ready");
        }
        return List.copyOf(reasons);
    }

    private String status(CircuitBreaker circuitBreaker, Bulkhead bulkhead, List<String> statusReasons) {
        if (statusReasons.stream().anyMatch(reason -> reason.endsWith("-missing"))) {
            return "DEGRADED";
        }
        if (circuitBreaker != null
            && (circuitBreaker.getState() == CircuitBreaker.State.OPEN
                || circuitBreaker.getState() == CircuitBreaker.State.FORCED_OPEN)) {
            return "OUTLET_BLOCKED";
        }
        if (bulkhead != null && bulkhead.getMetrics().getAvailableConcurrentCalls() <= 0) {
            return "SATURATED";
        }
        return "READY";
    }

    private Map<String, Object> safety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("localProcessOnly", true);
        safety.put("summaryOnly", true);
        safety.put("kubeManagerCalls", false);
        safety.put("remoteProbeExecuted", false);
        safety.put("toolExecution", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("fallbackLogin", false);
        safety.put("tokenInspection", false);
        safety.put("circuitBreakerMutation", false);
        safety.put("bulkheadMutation", false);
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
        return Map.copyOf(privacy);
    }

    private int intProperty(String name, int defaultValue) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private URI safeUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String safeText(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }
}
