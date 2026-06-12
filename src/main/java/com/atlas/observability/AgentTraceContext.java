package com.atlas.observability;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Agent trace 上下文。
 *
 * <p>中文说明：这是 M5.23 引入的最小 trace 内核，先用 ThreadLocal + MDC 打通请求、Tool 执行、
 * ReAct、审计和日志链路。后续接入 OpenTelemetry Span、审计持久化、Eval trace set 和前端回放时，
 * 都应复用这里的 traceId，而不是每层各自生成一套无法关联的 ID。</p>
 *
 * <p>安全边界：traceId 只能用于关联观测证据，不是用户身份、租户、Session、HITL、audit prewrite、
 * release gate 或 Tool 执行授权。外部传入候选值必须先经过 {@link #safeCandidateOrBlank(String)}
 * 过滤，避免日志注入、header 注入或把任意用户输入写入 MDC。</p>
 */
public final class AgentTraceContext {

    public static final String MDC_TRACE_ID = "traceId";
    private static final int MAX_ACCEPTED_TRACE_ID_LENGTH = 96;
    private static final int SPAN_ID_BYTES = 8;
    private static final Pattern ACCEPTED_TRACE_ID_PATTERN = Pattern.compile(
        "(?:trc_[0-9a-f]{32}|[0-9a-f]{32}|[A-Za-z0-9][A-Za-z0-9._:-]{0,95})");
    private static final Pattern W3C_TRACE_ID_PATTERN = Pattern.compile("(?:trc_)?([0-9a-f]{32})");
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentTraceContext() {
    }

    /**
     * 读取当前线程绑定的 traceId。
     *
     * <p>中文说明：该值通常由 Controller、Graph 或 SafeToolExecutor 在进入执行链路时绑定，
     * 输出给日志、审计和 SSE 关联展示。没有绑定时返回 null，调用方不能把 null 当作授权缺省通过。</p>
     */
    public static String currentTraceId() {
        return CURRENT_TRACE_ID.get();
    }

    /**
     * 获取安全候选 traceId、当前线程 traceId 或新生成 traceId。
     *
     * <p>中文说明：外部 gateway/header/SSE 请求带来的 trace 候选会先做最小规范化；
     * 候选不可信时退回当前线程值或服务端新值，保证观测链路连续但不接受任意字符串进入日志。</p>
     */
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

    /**
     * 将 traceId 绑定到当前线程和日志 MDC，并在 Scope 关闭时恢复旧值。
     *
     * <p>安全边界：线程池会复用线程，必须使用 try-with-resources 关闭 Scope，否则后续用户请求可能继承
     * 上一个请求的 traceId，造成审计和日志串线。traceId 绑定不绑定 token/orgId，也不授予任何 Tool 权限。</p>
     */
    public static Scope bind(String traceId) {
        String previous = CURRENT_TRACE_ID.get();
        String previousMdc = MDC.get(MDC_TRACE_ID);
        String resolved = currentOrNew(traceId);
        CURRENT_TRACE_ID.set(resolved);
        MDC.put(MDC_TRACE_ID, resolved);
        return new Scope(previous, previousMdc);
    }

    /**
     * 清理当前线程 trace 上下文。
     *
     * <p>中文说明：用于明确结束请求或测试隔离，避免 ThreadLocal/MDC 污染后续链路。</p>
     */
    public static void clear() {
        CURRENT_TRACE_ID.remove();
        MDC.remove(MDC_TRACE_ID);
    }

    /**
     * 生成 kube-agent 内部 traceId。
     *
     * <p>中文说明：使用 128-bit 随机值并带 {@code trc_} 前缀，便于人类在日志里搜索；
     * 它的随机性用于降低碰撞概率，不用于认证或防重放。</p>
     */
    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "trc_" + HexFormat.of().formatHex(bytes);
    }

    /**
     * 将当前 Agent traceId 转成 W3C Trace Context 的 {@code traceparent} header。
     *
     * <p>内部 traceId 使用 {@code trc_ + 32hex}，便于人读和日志搜索；HTTP 出口需要兼容
     * OpenTelemetry / 网关 / Collector 的标准传播格式，所以这里在能提取 32 位十六进制 trace-id
     * 时生成 {@code 00-traceid-spanid-01}。如果 traceId 来自外部网关且不是 32hex 形态，则返回空串，
     * 只传播 {@code X-Trace-Id}。</p>
     */
    public static String traceparentOrBlank(String traceId) {
        String w3cTraceId = w3cTraceIdOrBlank(traceId);
        if (w3cTraceId.isBlank()) {
            return "";
        }
        return "00-" + w3cTraceId + "-" + newSpanId() + "-01";
    }

    /**
     * 提取可用于 W3C Trace Context 的 32 位 hex trace-id。
     *
     * <p>中文说明：只有内部 {@code trc_ + 32hex} 或标准 32hex 形态会被传播为 traceparent；
     * 网关自定义短 ID 仍可作为 X-Trace-Id 展示，但不会伪装成标准 OpenTelemetry trace-id。</p>
     */
    public static String w3cTraceIdOrBlank(String traceId) {
        String accepted = safeCandidateOrBlank(traceId);
        if (accepted.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = W3C_TRACE_ID_PATTERN.matcher(accepted);
        if (!matcher.matches()) {
            return "";
        }
        String w3cTraceId = matcher.group(1);
        return "00000000000000000000000000000000".equals(w3cTraceId) ? "" : w3cTraceId;
    }

    private static String newSpanId() {
        byte[] bytes = new byte[SPAN_ID_BYTES];
        String spanId;
        do {
            RANDOM.nextBytes(bytes);
            spanId = HexFormat.of().formatHex(bytes);
        } while ("0000000000000000".equals(spanId));
        return spanId;
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

    /**
     * trace 绑定作用域。
     *
     * <p>中文说明：Scope 保存进入当前链路前的 ThreadLocal/MDC 值，关闭时原样恢复。
     * 这对异步和线程池尤其重要：Agent trace 是观测关联，不应该泄漏到下一次请求。</p>
     */
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
