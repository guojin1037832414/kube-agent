package com.atlas.intent.embedding;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * ONNX Session 单例持有器。
 *
 * <p>封装一个 {@link OrtSession} 实例，负责加载本地 ONNX 模型并执行推理。
 * 根据 ONNX Runtime 官方实践，{@code OrtSession} 本身是线程安全的，
 * 可在多线程环境中复用同一个实例，无需额外池化。</p>
 *
 * <p>通过 {@link PreDestroy} 生命周期方法确保应用关闭时释放 native 资源。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
/**
 * ONNX Session 单例持有器。
 *
 * <p><b>注意</b>：无 {@code @Component}，由 {@link com.atlas.config.AtlasConfiguration} 条件创建。</p>
 */
public class OnnxSessionHolder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxSessionHolder.class);

    /** ONNX 推理会话实例。 */
    private final OrtSession session;

    /**
     * 构造方法：根据框架给定的模型路径初始化 ONNX Session。
     *
     * @param config Embedding 配置（通过 Spring 自动注入）
     * @throws OrtException 模型加载失败时抛出
     */
    public OnnxSessionHolder(EmbeddingConfig config) throws OrtException {
        Path modelPath = ModelDownloader.resolveModelPath(config);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        // 多线程加速：启动与 CPU 核心相同数量的线程
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        // 开启全部图优化
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = OnnxEnvironmentHolder.get().createSession(modelPath.toString(), options);
        log.info("[OnnxSessionHolder] ONNX 模型加载成功: {}", modelPath);
    }

    /**
     * 返回底层的 ONNX Session 实例。
     *
     * @return 已初始化的 {@link OrtSession}
     */
    public OrtSession getSession() {
        return session;
    }

    /**
     * 应用关闭时显式释放 ONNX Session，避免 native 内存泄漏。
     */
    @Override
    @PreDestroy
    public void close() {
        if (session != null) {
            try {
                session.close();
                log.info("[OnnxSessionHolder] ONNX Session 已安全释放");
            } catch (OrtException e) {
                log.error("[OnnxSessionHolder] 释放 Session 异常", e);
            }
        }
    }
}
