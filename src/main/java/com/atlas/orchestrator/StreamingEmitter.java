package com.atlas.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSE 流式输出发射器。
 *
 * <p>管理 SseEmitter 生命周期，包括创建、事件发送、心跳保活和异常处理。</p>
 *
 * <p>连接管理策略：</p>
 * <ul>
 *   <li>永不超时（{@code timeout = 0L}），由应用层控制关闭</li>
 *   <li>15 秒心跳包，防止中间件断开长连接</li>
 *   <li>全局连接数限制（默认 500）</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class StreamingEmitter {

    private static final Logger log = LoggerFactory.getLogger(StreamingEmitter.class);
    private static final long HEARTBEAT_INTERVAL_MS = 15000L; // 15秒心跳
    private static final int MAX_CONNECTIONS = 500;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });

    /**
     * 创建新的 SSE 连接。
     *
     * @param sessionId 会话标识（通常为用户ID+时间戳）
     * @return 初始化后的 SseEmitter
     */
    public SseEmitter createEmitter(String sessionId) {
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("SSE连接数已达上限: " + MAX_CONNECTIONS);
        }

        SseEmitter emitter = new SseEmitter(0L); // 永不超时
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.debug("[StreamingEmitter] 连接完成: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("[StreamingEmitter] 连接超时: {}", sessionId);
        });
        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.error("[StreamingEmitter] 连接异常: {}", sessionId, e);
        });

        // 启动心跳
        scheduleHeartbeat(sessionId, emitter);

        log.info("[StreamingEmitter] 新连接创建: {} (当前总数: {})", sessionId, emitters.size());
        return emitter;
    }

    /**
     * 发送单个事件。
     */
    public void send(SseEmitter emitter, SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .name(event.type())
                .data(event.content()));
        } catch (IOException e) {
            log.error("[StreamingEmitter] 发送事件失败: {}", event.type(), e);
        }
    }

    /**
     * 发送事件并标记完成。
     */
    public void sendAndComplete(SseEmitter emitter, SseEvent event) {
        send(emitter, event);
        complete(emitter);
    }

    /**
     * 发送 done 事件并关闭连接。
     */
    public void complete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                .name("done")
                .data("{\"type\":\"done\"}"));
        } catch (IOException ignored) {
        } finally {
            emitter.complete();
        }
    }

    /**
     * 发送 error 事件并关闭连接。
     */
    public void error(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                .name("error")
                .data("{\"type\":\"error\",\"message\":\"" + message + "\"}"));
        } catch (IOException ignored) {
        } finally {
            emitter.complete();
        }
    }

    /**
     * 获取当前活跃连接数。
     */
    public int activeCount() {
        return emitters.size();
    }

    // ==================== 私有方法 ====================

    private void scheduleHeartbeat(String sessionId, SseEmitter emitter) {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!emitters.containsKey(sessionId)) return;
            try {
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("{\"type\":\"heartbeat\"}"));
            } catch (IOException e) {
                emitters.remove(sessionId);
                log.debug("[StreamingEmitter] 心跳失败，移除连接: {}", sessionId);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
}
