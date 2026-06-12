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
 * <p>中文说明：本测试用 Mockito 模拟 {@link IntentsLoader} 和 {@link EmbeddingService}，
 * 重点学习 L1 语义召回不可用时系统怎样返回 null，把控制权交还给 L2/L3/L4 路由链路，
 * 而不是让一次 embedding 预计算失败拖垮整个 Agent 对话。</p>
 *
 * <p>安全边界：本测试不加载真实向量模型、不访问外部网络、不调用 RAG/向量库、
 * 不调用 LLM/Tool/MCP、不访问 kube-manager，也不写 audit/memory。Embedding 相似度只是意图候选证据，
 * 不是权限门禁；空缓存、空 query 或预计算异常都必须 fail-soft 返回 null，让后续安全边界继续工作。</p>
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
        // 中文说明：空意图列表表示没有可用语义候选，precompute 不应伪造 embedding。
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
        // 中文说明：当上游目录异常返回 null 时，L1 层应降级而不是中断整条意图路由链。
        // 安全边界：降级不代表授权通过，只是让 L2/L3/L4 继续给出候选证据。
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
