package com.atlas.observability;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Agent trace 上下文。
 *
 * <p>M5.23 引入的最小 trace 内核：先用 ThreadLocal + MDC 打通请求、Tool 执行、
 * ReAct 和日志链路。后续接入 OpenTelemetry Span、审计持久化和前端回放时，都应复用
 * 这里的 traceId，而不是每层各自生成一套 ID。</p>
 */
public final class AgentTraceContext {

    public static final String MDC_TRACE_ID = "traceId";
    private static final int MAX_ACCEPTED_TRACE_ID_LENGTH = 96;
    private static final Pattern ACCEPTED_TRACE_ID_PATTERN = Pattern.compile(
        "(?:trc_[0-9a-f]{32}|[0-9a-f]{32}|[A-Za-z0-9][A-Za-z0-9._:-]{0,95})");
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentTraceContext() {
    }

    public static String currentTraceId() {
        return CURRENT_TRACE_ID.get();
    }

    public static String currentOrNew(String candidate) {
        String accepted = safeCandidateOrBlank(candidate);
        if (!accepted.isBlank()) {
            return accepted;
        }
        String current = currentTraceId();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return newTraceId();
    }

    public static Scope bind(String traceId) {
        String previous = CURRENT_TRACE_ID.get();
        String previousMdc = MDC.get(MDC_TRACE_ID);
        String resolved = currentOrNew(traceId);
        CURRENT_TRACE_ID.set(resolved);
        MDC.put(MDC_TRACE_ID, resolved);
        return new Scope(previous, previousMdc);
    }

    public static void clear() {
        CURRENT_TRACE_ID.remove();
        MDC.remove(MDC_TRACE_ID);
    }

    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "trc_" + HexFormat.of().formatHex(bytes);
    }

    /**
     * 接收外部 trace 候选值前先做最小安全规范化。
     *
     * <p>traceId 会进入 MDC、SSE、审计和未来 HTTP header，因此不能把任意用户输入直接写入日志上下文。
     * 当前只接受长度受限、无空白控制字符的 ASCII 标识：内部 {@code trc_ + 32hex}、W3C trace-id
     * 形态的 32 位 hex，或由网关生成的短 ASCII trace 标识。非法候选值会被丢弃并触发服务端生成。</p>
     */
    public static String safeCandidateOrBlank(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "";
        }
        if (!candidate.equals(candidate.trim()) || candidate.chars().anyMatch(Character::isWhitespace)) {
            return "";
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > MAX_ACCEPTED_TRACE_ID_LENGTH) {
            return "";
        }
        if (!ACCEPTED_TRACE_ID_PATTERN.matcher(trimmed).matches()) {
            return "";
        }
        return trimmed;
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;
        private final String previousMdc;
        private boolean closed;

        private Scope(String previous, String previousMdc) {
            this.previous = previous;
            this.previousMdc = previousMdc;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous != null && !previous.isBlank()) {
                CURRENT_TRACE_ID.set(previous);
            } else {
                CURRENT_TRACE_ID.remove();
            }
            if (previousMdc != null && !previousMdc.isBlank()) {
                MDC.put(MDC_TRACE_ID, previousMdc);
            } else {
                MDC.remove(MDC_TRACE_ID);
            }
            closed = true;
        }
    }
}
