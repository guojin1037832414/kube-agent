package com.atlas.intent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Embedding服务配置绑定类。
 *
 * <p>从 {@code application.yml} 中读取 {@code atlas.embedding.*} 前缀配置项，
 * 供 {@link EmbeddingService} 初始化时使用。配置项包括模型路径、维度、匹配阈值等。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Validated
@ConfigurationProperties(prefix = "atlas.embedding")
public class EmbeddingConfig {

    /**
     * 本地模型存放基础路径，默认 {@code ~/.atlas/models/all-MiniLM}。
     */
    private String modelPath = System.getProperty("user.home") + "/.atlas/models/all-MiniLM";

    /**
     * Hugging Face 模型 ID，用于首次启动自动下载。
     */
    private String modelId = "sentence-transformers/all-MiniLM-L6-v2";

    /**
     * 向量维度（all-MiniLM-L6-v2 固定为 384）。
     */
    private int dimension = 384;

    /**
     * 语义匹配阈值，超过该值视为命中意图（默认 0.75）。
     */
    private double matchThreshold = 0.75;

    /**
     * 模型下载超时（单位：秒）。
     */
    private int downloadTimeoutSeconds = 120;

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public double getMatchThreshold() { return matchThreshold; }
    public void setMatchThreshold(double matchThreshold) { this.matchThreshold = matchThreshold; }
    public int getDownloadTimeoutSeconds() { return downloadTimeoutSeconds; }
    public void setDownloadTimeoutSeconds(int downloadTimeoutSeconds) { this.downloadTimeoutSeconds = downloadTimeoutSeconds; }
}
