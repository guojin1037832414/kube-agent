package com.atlas.intent;

import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentResult;
import com.atlas.intent.embedding.EmbeddingConfig;
import com.atlas.intent.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * L1 层 — Embedding 语义预筛。
 *
 * <p>启动时预计算所有意图 examples 的 Embedding 向量并缓存。
 * 运行时将用户 query 与各意图的 examples 平均向量做余弦相似度比较。</p>
 *
 * <p>零 token 消耗，延迟 &lt; 10ms。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
/**
 * L1 语义预筛器 — Embedding 匹配。
 *
 * <p><b>注意</b>：无 {@code @Component}，由 {@link com.atlas.config.AtlasConfiguration} 条件创建。</p>
 */
public class EmbeddingMatcher {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingMatcher.class);

    private final EmbeddingService embeddingService;
    private final IntentsLoader intentsLoader;
    private final EmbeddingConfig config;

    /** 意图ID → examples平均Embedding向量 */
    private final Map<String, float[]> intentEmbeddings = new LinkedHashMap<>();

    public EmbeddingMatcher(EmbeddingService embeddingService,
                            IntentsLoader intentsLoader,
                            EmbeddingConfig config) {
        this.embeddingService = embeddingService;
        this.intentsLoader = intentsLoader;
        this.config = config;
    }

    /**
     * 预计算所有意图 examples 的 Embedding。
     */
    @PostConstruct
    public void precompute() {
        var allIntents = intentsLoader.getAllIntents();
        if (allIntents == null) {
            log.warn("[EmbeddingMatcher] getAllIntents() 返回 null，跳过预计算");
            return;
        }
        for (var def : allIntents) {
            if (def.examples() == null || def.examples().isEmpty()) continue;
            float[][] vecs = embeddingService.batchEncode(def.examples());
            float[] avg = averageVectors(vecs);
            intentEmbeddings.put(def.intentId(), avg);
        }
        log.info("[EmbeddingMatcher] 预计算完成: {} 个意图", intentEmbeddings.size());
    }

    /**
     * L1 匹配：返回最佳匹配的意图，无命中返回 null。
     */
    public IntentResult match(String query) {
        if (intentEmbeddings.isEmpty()) return null;

        float[] qVec = embeddingService.encode(query);
        String bestId = null;
        double bestSim = -1;

        for (Map.Entry<String, float[]> entry : intentEmbeddings.entrySet()) {
            double sim = embeddingService.cosineSimilarity(qVec, entry.getValue());
            if (sim > bestSim) {
                bestSim = sim;
                bestId = entry.getKey();
            }
        }

        if (bestId == null || bestSim < config.getMatchThreshold()) {
            return null;
        }

        var def = intentsLoader.getIntent(bestId);
        return new IntentResult(bestId, def.description(), bestSim, "L1",
            def.agent(), def.level(), query);
    }

    /** 对多个向量取平均 */
    private float[] averageVectors(float[][] vectors) {
        int dim = vectors[0].length;
        float[] avg = new float[dim];
        for (float[] v : vectors) {
            for (int i = 0; i < dim; i++) avg[i] += v[i];
        }
        for (int i = 0; i < dim; i++) avg[i] /= vectors.length;
        // L2归一化
        float norm = 0;
        for (float v : avg) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-6) for (int i = 0; i < dim; i++) avg[i] /= norm;
        return avg;
    }
}
