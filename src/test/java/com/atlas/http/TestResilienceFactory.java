package com.atlas.http;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;

final class TestResilienceFactory {

    private TestResilienceFactory() {
    }

    static KubeManagerHttpResiliencePolicy policy(int readRetryAttempts) {
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(readRetryAttempts)
            .waitDuration(Duration.ZERO)
            .retryExceptions(RuntimeException.class)
            .build());
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(100)
            .slidingWindowSize(100)
            .build());
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(BulkheadConfig.custom()
            .maxConcurrentCalls(100)
            .maxWaitDuration(Duration.ZERO)
            .build());
        bulkheadRegistry.bulkhead(KubeManagerHttpResiliencePolicy.BULKHEAD, BulkheadConfig.custom()
            .maxConcurrentCalls(100)
            .maxWaitDuration(Duration.ZERO)
            .build());
        return new KubeManagerHttpResiliencePolicy(retryRegistry, circuitBreakerRegistry, bulkheadRegistry);
    }
}
