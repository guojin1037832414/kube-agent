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
 * L1 语义预筛器 — Embedding 匹配。
 *
 * <p>中文说明：EmbeddingMatcher 在启动时把 intents.yml 中的 examples 编成向量，
 * 运行时把用户 query 与各意图的平均向量做余弦相似度比较，给 IntentRouter 提供 L1 候选。
 * 它的作用是低成本发现语义相近意图，尤其是用户没有完全说出关键词时。</p>
 *
 * <p>安全边界：Embedding 相似度不是安全门禁。模型文件、向量距离和阈值只能影响候选排序，
 * 不能授予 ToolPermission、token/orgId/userId、HITL、audit、release 或 kube-manager 写权限。
 * 预计算失败或缓存为空时应降级到其他层，而不是默认命中某个高风险 intent。</p>
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
     *
     * <p>中文说明：预计算只建立本地只读向量索引，不访问 kube-manager、不执行 Tool、
     * 不写 audit/memory，也不把用户数据写入持久向量库。</p>
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
     *
     * <p>安全边界：返回 null 是正常降级信号；返回 IntentResult 也只是候选，不能直接执行。</p>
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
