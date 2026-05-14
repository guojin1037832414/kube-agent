package com.atlas.intent.embedding;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地 Embedding 编码核心服务。
 *
 * <p>基于 DJL HuggingFace Tokenizer + ONNX Runtime 推理，将文本编码为 384 维归一化向量。
 * 单查询延迟 &lt; 10ms，零 token 消耗。</p>
 *
 * <p><b>编码流程：</b></p>
 * <ol>
 *   <li>Tokenizer 将文本转为 {@code input_ids + attention_mask + token_type_ids}</li>
 *   <li>ONNX 模型推理，输出 {@code last_hidden_state}（维度 [batch, seqLen, 384]）</li>
 *   <li>Mean Pooling：用 attention mask 加权平均各 token embedding</li>
 *   <li>L2 归一化：确保向量模长为 1.0</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
/**
 * 本地 Embedding 编码核心服务。
 *
 * <p><b>注意</b>：无 {@code @Service}，由 {@link com.atlas.config.AtlasConfiguration} 条件创建。</p>
 */
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /**
     * Tokenizer 名称常量——ONNX MiniLM 模型固定输入名
     */
    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";
    private static final String OUTPUT_NAME = "last_hidden_state";

    private final OnnxSessionHolder sessionHolder;
    private final HuggingFaceTokenizer tokenizer;
    private final ReentrantLock tokenizerLock = new ReentrantLock();
    private final int dimension;

    /**
     * 构造方法：初始化 Tokenizer 和 Session。
     *
     * @param sessionHolder ONNX Session 单例
     * @param config        Embedding 配置
     */
    public EmbeddingService(OnnxSessionHolder sessionHolder, EmbeddingConfig config) throws Exception {
        this.sessionHolder = sessionHolder;
        this.dimension = config.getDimension();
        Path tokenizerPath = ModelDownloader.resolveTokenizerPath(config);
        log.info("[EmbeddingService] 加载 Tokenizer: {}", tokenizerPath);
        this.tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerPath)
                .optPadToMaxLength()
                .build();
    }

    /**
     * 单文本编码。
     *
     * @param text 输入文本
     * @return 384 维归一化向量
     */
    public float[] encode(String text) {
        float[][] batch = batchEncode(List.of(text));
        return batch[0];
    }

    /**
     * 批量编码。
     *
     * @param texts 文本列表
     * @return 每个文本对应的 384 维向量数组
     */
    public float[][] batchEncode(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }

        tokenizerLock.lock();
        try {
            ai.djl.huggingface.tokenizers.Encoding[] encodings = new ai.djl.huggingface.tokenizers.Encoding[texts.size()];
            for (int i = 0; i < texts.size(); i++) {
                encodings[i] = tokenizer.encode(texts.get(i));
            }

            // 获取 padding 后的统一长度
            int maxLen = encodings[0].getIds().length;
            int batchSize = texts.size();

            // input_ids, attention_mask, token_type_ids
            long[][] inputIds = new long[batchSize][maxLen];
            long[][] attentionMask = new long[batchSize][maxLen];
            long[][] tokenTypeIds = new long[batchSize][maxLen];

            for (int i = 0; i < batchSize; i++) {
                inputIds[i] = encodings[i].getIds();
                attentionMask[i] = encodings[i].getAttentionMask();
                tokenTypeIds[i] = encodings[i].getTypeIds();
            }

            return runInference(inputIds, attentionMask, tokenTypeIds);
        } finally {
            tokenizerLock.unlock();
        }
    }

    private float[][] runInference(long[][] inputIds, long[][] attentionMask, long[][] tokenTypeIds) {
        OrtEnvironment env = OnnxEnvironmentHolder.get();
        int batchSize = inputIds.length;
        int seqLen = inputIds[0].length;

        try (
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, inputIds);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, attentionMask);
            OnnxTensor typeTensor = OnnxTensor.createTensor(env, tokenTypeIds);
            OrtSession.Result result = sessionHolder.getSession().run(Map.of(
                INPUT_IDS, inputIdsTensor,
                ATTENTION_MASK, maskTensor,
                TOKEN_TYPE_IDS, typeTensor
            ))
        ) {
            // output: float[][][] [batch, seqLen, 384]
            OnnxValue onnxValue = result.get(OUTPUT_NAME)
                .orElseThrow(() -> new RuntimeException("ONNX输出缺失: " + OUTPUT_NAME));
            Object raw = onnxValue.getValue();
            float[][][] tokenEmbeddings = (float[][][]) raw;
            float[][] embeddings = new float[batchSize][dimension];

            for (int b = 0; b < batchSize; b++) {
                embeddings[b] = meanPooling(tokenEmbeddings[b], attentionMask[b]);
            }
            return embeddings;
        } catch (OrtException e) {
            throw new RuntimeException("ONNX 推理失败", e);
        }
    }

    /**
     * Mean Pooling + L2 归一化。
     *
     * @param tokenEmbedding 单个样本的 [seqLen, 384] 向量
     * @param attentionMask  对应的 attention mask
     * @return 384 维句向量
     */
    private float[] meanPooling(float[][] tokenEmbedding, long[] attentionMask) {
        float[] pooled = new float[dimension];
        float sumMask = 0;
        for (int i = 0; i < attentionMask.length; i++) {
            float mask = (float) attentionMask[i];
            sumMask += mask;
            for (int j = 0; j < dimension; j++) {
                pooled[j] += tokenEmbedding[i][j] * mask;
            }
        }
        for (int j = 0; j < dimension; j++) {
            pooled[j] /= sumMask;
        }
        // L2 归一化
        float norm = 0;
        for (float v : pooled) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int j = 0; j < dimension; j++) {
                pooled[j] /= norm;
            }
        }
        return pooled;
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * <p>因为输入向量已经经过 L2 归一化，余弦相似度等于点积。</p>
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度 ∈ [-1, 1]，归一化后 ∈ [0, 1]
     */
    public double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot; // L2归一化后就是cosine
    }

    /**
     * 在候选文本中找出与查询最相似的一条。
     *
     * @param query     用户查询文本
     * @param candidates 候选文本列表
     * @return 最佳匹配的索引；无候选返回 -1
     */
    public int findBestMatch(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return -1;
        }
        float[] qVec = encode(query);
        float[][] cVecs = batchEncode(candidates);

        int bestIdx = 0;
        double bestScore = -1;
        for (int i = 0; i < cVecs.length; i++) {
            double sim = cosineSimilarity(qVec, cVecs[i]);
            if (sim > bestScore) {
                bestScore = sim;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
