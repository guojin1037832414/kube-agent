package com.atlas.intent.embedding;

import ai.onnxruntime.OrtEnvironment;

/**
 * ONNX Runtime 全局环境持有器。
 *
 * <p>Atlas 的本地 Embedding 服务会在 JVM 内多次执行 ONNX 推理。根据 ONNX Runtime
 * 的官方建议，{@link OrtEnvironment} 应在进程级别复用，避免每次创建会话或推理时反复初始化
 * native 运行时资源。本类通过静态常量保存 {@code OrtEnvironment.getEnvironment()} 返回的全局单例，
 * 为所有 ONNX Session 提供统一入口。</p>
 *
 * <p>该类不保存业务状态，也不允许实例化；调用方只需要通过 {@link #get()} 获取环境对象。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public final class OnnxEnvironmentHolder {

    /**
     * ONNX Runtime 进程级环境单例。
     *
     * <p>{@code OrtEnvironment.getEnvironment()} 本身已由 ONNX Runtime 保证单例语义，
     * 这里再用静态 final 字段固定引用，便于项目内统一管理和注释说明。</p>
     */
    private static final OrtEnvironment ENVIRONMENT = OrtEnvironment.getEnvironment();

    /**
     * 私有构造方法，防止工具类被误实例化。
     */
    private OnnxEnvironmentHolder() {
        throw new UnsupportedOperationException("OnnxEnvironmentHolder 是工具类，禁止实例化");
    }

    /**
     * 获取 ONNX Runtime 全局环境。
     *
     * @return JVM 进程级共享的 {@link OrtEnvironment} 实例
     */
    public static OrtEnvironment get() {
        return ENVIRONMENT;
    }
}
