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
 * <p>中文说明：这是 kube-agent 访问 kube-manager 时的 Resilience4j 包装层。
 * 它给读写请求都加上 bulkhead/circuit breaker，防止后端抖动拖垮 Agent；
 * 但只有读请求可以自动重试，写请求默认不自动重试。</p>
 *
 * <p>安全边界：写重试需要幂等证据、durable audit、HITL 和 release evidence 共同证明。
 * 在这些证据未闭合前，POST/PATCH/PUT/DELETE 的自动重试会把一次用户动作放大成多次副作用，
 * 因此本类刻意不把 read retry 复用到写路径。</p>
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

    /**
     * 执行只读请求。
     *
     * <p>中文说明：读请求允许经过 read retry，因为重复读取不会改变 kube-manager 状态；
     * 如果未来某个 GET 实际有副作用，必须先修正 Tool 元数据和 HTTP 方法建模，不能依赖这里兜底。</p>
     */
    public <T> T executeRead(Supplier<T> operation) {
        Supplier<T> guarded = guarded(operation);
        if (!enabled) {
            return guarded.get();
        }
        return Retry.decorateSupplier(readRetry, guarded).get();
    }

    /**
     * 执行写请求。
     *
     * <p>安全边界：写请求只经过 bulkhead/circuit breaker，不做自动 retry。
     * 后续若要支持可重试写入，必须先由 idempotency key、durable receipt、post-write readback
     * 和 release gate 证明“重复发送不会产生额外副作用”。</p>
     */
    public <T> T executeWrite(Supplier<T> operation) {
        return guarded(operation).get();
    }

    /**
     * 给读写请求统一套上并发隔离和熔断保护。
     *
     * <p>中文说明：bulkhead/circuit breaker 是保护 kube-agent 和 kube-manager 的运行时稳定性机制；
     * 它们不改变权限、租户、HITL 或审计判断，也不能被当成安全授权。</p>
     */
    private <T> Supplier<T> guarded(Supplier<T> operation) {
        if (!enabled) {
            return operation;
        }
        Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead, operation);
        guarded = CircuitBreaker.decorateSupplier(circuitBreaker, guarded);
        return guarded;
    }
}
