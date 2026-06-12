package com.atlas.intent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Embedding服务配置绑定类。
 *
 * <p>从 {@code application.yml} 中读取 {@code atlas.embedding.*} 前缀配置项，
 * 供 {@link EmbeddingService} 初始化时使用。配置项包括模型路径、维度、匹配阈值等。</p>
 *
 * <p>中文说明：这是 L1 语义检索/意图匹配的配置入口，输入来自部署环境或 application 配置，
 * 输出给 EmbeddingService、EmbeddingMatcher 和意图路由链路。默认使用本地 ONNX 模型路径，
 * 便于离线运行和可恢复测试。</p>
 *
 * <p>安全边界：Embedding 只增强意图候选，不授予 Tool 执行权。模型路径、模型 ID、维度和阈值
 * 不能改变 SafeToolExecutor、HITL、audit、kube-manager 权限、Memory/RAG source custody
 * 或 release gate。自动下载模型属于外部网络/供应链行为，生产环境应通过版本锁定和证据审查治理。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Validated
@ConfigurationProperties(prefix = "atlas.embedding")
public class EmbeddingConfig {

    /** 本地模型存放基础路径；生产环境应指向受控模型目录，避免运行时下载不可审查版本。 */
    private String modelPath = System.getProperty("user.home") + "/.atlas/models/all-MiniLM";

    /** Hugging Face 模型 ID；这是供应链坐标，不应由普通用户请求动态覆盖。 */
    private String modelId = "sentence-transformers/all-MiniLM-L6-v2";

    /** 向量维度；必须与模型输出一致，否则意图匹配应失败而不是静默降级为错误结果。 */
    private int dimension = 384;

    /** 语义匹配阈值；只影响候选召回，不影响权限、审计或写操作门禁。 */
    private double matchThreshold = 0.75;

    /** 模型下载超时（单位：秒）；下载失败应降级到规则/LLM/兜底路径，不应打开越权能力。 */
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
