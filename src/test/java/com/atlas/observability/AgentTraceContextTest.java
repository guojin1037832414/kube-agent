package com.atlas.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent trace 上下文契约测试。
 *
 * <p>traceId 是后续 OpenTelemetry Span、审计流水、SSE 回放和多 Agent 调度的共同关联键。
 * 这里先锁住最小内核：生成格式稳定、ThreadLocal 与 MDC 同步、嵌套作用域可恢复。</p>
 */
class AgentTraceContextTest {

    @BeforeEach
    @AfterEach
    void clearTraceContext() {
        AgentTraceContext.clear();
    }

    @Test
    void newTraceId_shouldUseStablePrefixAnd128BitHexPayload() {
        String first = AgentTraceContext.newTraceId();
        String second = AgentTraceContext.newTraceId();

        assertTrue(first.matches("trc_[0-9a-f]{32}"), "traceId 应使用 trc_ + 128bit hex，便于日志检索和跨系统传递");
        assertTrue(second.matches("trc_[0-9a-f]{32}"), "每次生成的新 traceId 都应保持同一格式");
        assertNotEquals(first, second, "连续生成的 traceId 不应复用同一个值");
    }

    @Test
    void currentOrNew_shouldRejectUnsafeExternalTraceCandidates() {
        assertEquals("", AgentTraceContext.safeCandidateOrBlank(" \r\ntrace-injection"));
        assertEquals("", AgentTraceContext.safeCandidateOrBlank("trc_" + "a".repeat(200)));
        assertEquals("", AgentTraceContext.safeCandidateOrBlank("trace id with spaces"));
        assertEquals("trc_0123456789abcdef0123456789abcdef",
            AgentTraceContext.safeCandidateOrBlank("trc_0123456789abcdef0123456789abcdef"));
        assertEquals("0123456789abcdef0123456789abcdef",
            AgentTraceContext.safeCandidateOrBlank("0123456789abcdef0123456789abcdef"));

        String generated = AgentTraceContext.currentOrNew(" \r\nmalicious");
        assertTrue(generated.matches("trc_[0-9a-f]{32}"), "非法外部 trace 候选值必须被丢弃并由服务端重新生成");
    }

    @Test
    void bind_shouldExposeTraceIdInThreadLocalAndMdcAndRestoreNestedScope() {
        try (AgentTraceContext.Scope outer = AgentTraceContext.bind("trc_outer")) {
            assertEquals("trc_outer", AgentTraceContext.currentTraceId());
            assertEquals("trc_outer", MDC.get(AgentTraceContext.MDC_TRACE_ID));
            assertEquals("trc_outer", AgentTraceContext.currentOrNew(""));

            try (AgentTraceContext.Scope inner = AgentTraceContext.bind("trc_inner")) {
                assertEquals("trc_inner", AgentTraceContext.currentTraceId());
                assertEquals("trc_inner", MDC.get(AgentTraceContext.MDC_TRACE_ID));
            }

            assertEquals("trc_outer", AgentTraceContext.currentTraceId(), "内层 scope 关闭后必须恢复外层 traceId");
            assertEquals("trc_outer", MDC.get(AgentTraceContext.MDC_TRACE_ID), "MDC 也必须恢复外层 traceId");
        }

        assertNull(AgentTraceContext.currentTraceId(), "最外层 scope 关闭后必须清理 ThreadLocal，避免线程池污染");
        assertNull(MDC.get(AgentTraceContext.MDC_TRACE_ID), "最外层 scope 关闭后必须清理 MDC，避免日志串线");
    }

    @Test
    void bind_shouldRestorePreexistingMdcWhenNoPreviousThreadLocalTraceExists() {
        MDC.put(AgentTraceContext.MDC_TRACE_ID, "mdc-before");

        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind("trc_request")) {
            assertEquals("trc_request", AgentTraceContext.currentTraceId());
            assertEquals("trc_request", MDC.get(AgentTraceContext.MDC_TRACE_ID));
        }

        assertNull(AgentTraceContext.currentTraceId(), "没有旧 ThreadLocal 时应恢复为空");
        assertEquals("mdc-before", MDC.get(AgentTraceContext.MDC_TRACE_ID), "已有日志 MDC 值应被原样恢复");
    }
}
