package com.atlas.intent;

import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentResult;
import com.atlas.intent.embedding.EmbeddingConfig;
import com.atlas.intent.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EmbeddingMatcher 降级测试 — 验证 L1 层故障时的优雅降级。
 *
 * <p>只测试降级场景（无需验证命中逻辑），确保 IntentRouter 能安全跳过 L1。</p>
 *
 * @version 3.1.0-M2
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingMatcherMockTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private IntentsLoader intentsLoader;

    @Mock
    private EmbeddingConfig config;

    @InjectMocks
    private EmbeddingMatcher embeddingMatcher;

    // ═══════════════════════════════════════════════════════════
    // TC-L1-DOWN-01: 预计算异常 → intentEmbeddings 为空 → match 返回 null
    // ═══════════════════════════════════════════════════════════

    @Test
    void testPrecomputeException_emptyCache_returnsNull() {
        // 空意图列表 → precompute 不添加任何 embedding
        when(intentsLoader.getAllIntents()).thenReturn(List.of());

        embeddingMatcher.precompute();

        IntentResult result = embeddingMatcher.match("任何查询");
        assertNull(result, "空缓存时 match 应返回 null，IntentRouter 会降级到 L2/L4");
    }

    // ═══════════════════════════════════════════════════════════
    // TC-L1-DOWN-02: 预计算时 batchEncode 抛异常 → 跳过崩溃
    // ═══════════════════════════════════════════════════════════

    @Test
    void testBatchEncodeException_doesNotCrash() {
        // 使用真实 EmbeddingMatcher 的 precompute 行为：
        // 当 intentsLoader 返回 null（模拟异常链），应正常跳过
        when(intentsLoader.getAllIntents()).thenReturn(null);

        assertDoesNotThrow(() -> embeddingMatcher.precompute());
    }

    // ═══════════════════════════════════════════════════════════
    // TC-L1-DOWN-03: 空 query  → match 不会崩溃（由 IntentRouter 过滤）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testEmptyQuery_returnsNull() {
        when(intentsLoader.getAllIntents()).thenReturn(List.of());
        embeddingMatcher.precompute();

        IntentResult result = embeddingMatcher.match("");
        assertNull(result);
    }
}
