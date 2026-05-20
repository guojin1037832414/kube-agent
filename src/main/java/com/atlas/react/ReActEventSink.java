package com.atlas.react;

/**
 * ReAct 事件接收器。
 *
 * <p>用于把 ReActEngine 与具体传输层解耦：引擎只负责发出领域事件，
 * AtlasOrchestrator 再将事件翻译成 SSE。这样既能保持同步 run() 兼容，
 * 又能为后续 WebSocket、审计日志、调试面板复用同一事件模型。</p>
 */
@FunctionalInterface
public interface ReActEventSink {

    /** 无操作接收器，供同步非流式场景使用。 */
    ReActEventSink NOOP = event -> { };

    /** 接收一个 ReAct 过程事件。 */
    void accept(ReActEvent event);
}
