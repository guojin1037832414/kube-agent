package com.atlas.react;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ReAct 事件接收器运行期注册表。
 *
 * <p>Graph State 只能保存可序列化业务数据，不能保存 Lambda、SseEmitter、Spring Bean 等运行期对象。
 * 因此 SSE 事件回调通过本注册表以 sessionId 间接查找：State 中只保存
 * {@code react_event_session_id} 字符串，真实 {@link ReActEventSink} 保存在当前 JVM 内存中。</p>
 *
 * <p>这是一个 best-effort 实时事件通道：如果连接已断开或 sink 不存在，发布事件会静默降级，
 * 不影响 ReAct 主流程生成最终答案。</p>
 */
@Component
public class ReActEventSinkRegistry {

    private static final Logger log = LoggerFactory.getLogger(ReActEventSinkRegistry.class);

    /** sessionId -> 当前 SSE 连接对应的事件接收器 */
    private final ConcurrentMap<String, ReActEventSink> sinks = new ConcurrentHashMap<>();

    /**
     * 注册当前 ReAct 会话的事件接收器。
     *
     * @param sessionId 会话/运行 ID
     * @param sink      运行期事件接收器
     */
    public void register(String sessionId, ReActEventSink sink) {
        if (sessionId == null || sessionId.isBlank() || sink == null) {
            return;
        }
        sinks.put(sessionId, sink);
        log.debug("[ReActEventSinkRegistry] 注册事件接收器: sessionId={}", sessionId);
    }

    /**
     * 查找事件接收器。
     *
     * @param sessionId 会话/运行 ID
     * @return 事件接收器；不存在时为空
     */
    public Optional<ReActEventSink> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sinks.get(sessionId));
    }

    /**
     * 发布事件。sink 不存在或发送失败时不抛出，避免 SSE 连接问题影响 ReAct 主流程。
     *
     * @param sessionId 会话/运行 ID
     * @param event     ReAct 过程事件
     */
    public void publish(String sessionId, ReActEvent event) {
        find(sessionId).ifPresent(sink -> {
            try {
                sink.accept(event);
            } catch (Exception e) {
                log.warn("[ReActEventSinkRegistry] 事件发送失败: sessionId={}, type={}, error={}",
                    sessionId, event != null ? event.type() : "null", e.getMessage());
            }
        });
    }

    /**
     * 注销会话事件接收器，防止长连接断开后内存泄漏。
     *
     * @param sessionId 会话/运行 ID
     */
    public void unregister(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sinks.remove(sessionId);
        log.debug("[ReActEventSinkRegistry] 注销事件接收器: sessionId={}", sessionId);
    }

    /** 当前注册数量，供测试和健康检查使用。 */
    public int size() {
        return sinks.size();
    }
}
