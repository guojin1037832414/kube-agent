package com.atlas.http;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * kube-manager HTTP 出口韧性策略。
 *
 * <p>M5.28 开始把“韧性治理”从文档和依赖推进到真实执行路径。这里有一个关键安全边界：
 * 读请求可以自动重试；写请求默认不自动重试。因为 POST/PATCH/PUT/DELETE 可能已经在后端产生副作用，
 * 在没有 idempotency key、HITL、durable audit 和 release evidence 之前，自动重试会把一次用户动作放大成多次。</p>
 */
@Component
public class KubeManagerHttpResiliencePolicy {

    static final String READ_RETRY = "kubeManagerRead";
    static final String CIRCUIT_BREAKER = "kubeManager";
    static final String BULKHEAD = "kubeManager";

    private final Retry readRetry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final boolean enabled;

    @Autowired
    public KubeManagerHttpResiliencePolicy(RetryRegistry retryRegistry,
                                           CircuitBreakerRegistry circuitBreakerRegistry,
                                           BulkheadRegistry bulkheadRegistry) {
        this(
            retryRegistry.retry(READ_RETRY),
            circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER),
            bulkheadRegistry.bulkhead(BULKHEAD),
            true
        );
    }

    private KubeManagerHttpResiliencePolicy(Retry readRetry,
                                            CircuitBreaker circuitBreaker,
                                            Bulkhead bulkhead,
                                            boolean enabled) {
        this.readRetry = readRetry;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.enabled = enabled;
    }

    static KubeManagerHttpResiliencePolicy disabled() {
        return new KubeManagerHttpResiliencePolicy(null, null, null, false);
    }

    public <T> T executeRead(Supplier<T> operation) {
        Supplier<T> guarded = guarded(operation);
        if (!enabled) {
            return guarded.get();
        }
        return Retry.decorateSupplier(readRetry, guarded).get();
    }

    public <T> T executeWrite(Supplier<T> operation) {
        return guarded(operation).get();
    }

    private <T> Supplier<T> guarded(Supplier<T> operation) {
        if (!enabled) {
            return operation;
        }
        Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead, operation);
        guarded = CircuitBreaker.decorateSupplier(circuitBreaker, guarded);
        return guarded;
    }
}
