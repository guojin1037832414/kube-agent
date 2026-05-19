package com.atlas.orchestrator.polish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 润色阶段性能指标收集 — v3.1 B方案。
 *
 * <p>当前使用 SLF4J 日志输出指标，后续可无缝替换为 Micrometer/Prometheus。</p>
 *
 * <p>收集维度：</p>
 * <ul>
 *   <li>同步/流式调用次数与平均延迟</li>
 *   <li>Token 输出量统计</li>
 *   <li>失败率与降级次数</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P3
 */
@Component
public class PolishMetrics {

    private static final Logger log = LoggerFactory.getLogger(PolishMetrics.class);

    private final AtomicLong syncCount = new AtomicLong(0);
    private final AtomicLong syncTotalMs = new AtomicLong(0);
    private final AtomicLong streamCount = new AtomicLong(0);
    private final AtomicLong streamTotalMs = new AtomicLong(0);
    private final AtomicLong chunkCount = new AtomicLong(0);
    private final AtomicLong chunkTotalChars = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);

    public void recordSync(long elapsedMs, int inputChars) {
        syncCount.incrementAndGet();
        syncTotalMs.addAndGet(elapsedMs);
        log.debug("[PolishMetrics] sync | latency={}ms | inputChars={}", elapsedMs, inputChars);
    }

    public void recordStreamComplete(long elapsedMs) {
        streamCount.incrementAndGet();
        streamTotalMs.addAndGet(elapsedMs);
        log.debug("[PolishMetrics] stream_complete | latency={}ms", elapsedMs);
    }

    public void recordChunk(int chunkChars) {
        chunkCount.incrementAndGet();
        chunkTotalChars.addAndGet(chunkChars);
    }

    public void recordFailure(String phase, Throwable err) {
        failureCount.incrementAndGet();
        log.warn("[PolishMetrics] failure | phase={} | error={}", phase, err.getMessage());
    }

    public void recordFallback() {
        fallbackCount.incrementAndGet();
        log.info("[PolishMetrics] fallback_triggered");
    }

    /**
     * 输出统计摘要（建议每分钟/每百次调用时输出）。
     */
    public void printSummary() {
        long sync = syncCount.get();
        long stream = streamCount.get();
        long fail = failureCount.get();
        long fall = fallbackCount.get();
        long total = sync + stream;

        log.info("[PolishMetrics] 汇总: totalCalls={}, syncAvgLatency={}ms, streamAvgLatency={}ms, " +
                "chunks={}, chunkAvgChars={}, failures={}, fallbacks={}, failureRate={}%",
            total,
            sync > 0 ? syncTotalMs.get() / sync : 0,
            stream > 0 ? streamTotalMs.get() / stream : 0,
            chunkCount.get(),
            chunkCount.get() > 0 ? chunkTotalChars.get() / chunkCount.get() : 0,
            fail, fall,
            total > 0 ? (fail * 100.0 / total) : 0);
    }
}
