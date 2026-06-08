package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.23 traceId 传播契约测试。
 *
 * <p>本测试只读取源码，不启动 Spring，不调用 LLM，也不访问 kube-manager。
 * 它用于固定“统一安全执行边界 + 统一 trace 内核”的结构，防止后续新增入口绕过
 * {@code AgentTraceContext} 或在 Tool 执行前丢失 traceId。</p>
 */
class M523TracePropagationContractTest {

    private static final Path TRACE_CONTEXT = Path.of("src/main/java/com/atlas/observability/AgentTraceContext.java");
    private static final Path SAFE_REQUEST = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionRequest.java");
    private static final Path SAFE_RESULT = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionResult.java");
    private static final Path SAFE_EXECUTOR = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutor.java");
    private static final Path REACT_ENGINE = Path.of("src/main/java/com/atlas/react/ReActEngine.java");
    private static final Path REACT_EVENT = Path.of("src/main/java/com/atlas/react/ReActEvent.java");
    private static final Path ORCHESTRATOR = Path.of("src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java");
    private static final Path HITL_CONTROLLER = Path.of("src/main/java/com/atlas/controller/HITLController.java");
    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");
    private static final Path BRIDGE_CALLBACK = Path.of("src/main/java/com/atlas/graph/bridge/AtlasToolCallback.java");
    private static final Path CORE_CALLBACK = Path.of("src/main/java/com/atlas/tool/core/AtlasToolCallback.java");
    private static final Path PROTECTED_FILTER = Path.of("src/main/java/com/atlas/tool/core/ProtectedToolParameterFilter.java");

    @Test
    void traceContext_shouldBindThreadLocalAndMdcWithStableGeneratedId() throws IOException {
        String source = read(TRACE_CONTEXT);

        assertThat(source)
            .contains("ThreadLocal<String> CURRENT_TRACE_ID")
            .contains("public static final String MDC_TRACE_ID = \"traceId\"")
            .contains("safeCandidateOrBlank(String candidate)")
            .contains("ACCEPTED_TRACE_ID_PATTERN")
            .contains("MDC.put(MDC_TRACE_ID, resolved)")
            .contains("MDC.remove(MDC_TRACE_ID)")
            .contains("return \"trc_\" + HexFormat.of().formatHex(bytes)");
    }

    @Test
    void safeExecutionBoundary_shouldCarryTraceIdInRequestResultToolResultAndGraphUpdates() throws IOException {
        assertThat(read(SAFE_REQUEST))
            .contains("String traceId")
            .contains("this(intentId, parameters, userId, token, orgId, conversationId, \"\", confirmation, source)");

        assertThat(read(SAFE_RESULT))
            .contains("String traceId")
            .contains("notExecuted(String answer, String traceId)")
            .contains("executed(boolean success,")
            .contains("updates.put(\"traceId\", traceId)");

        assertThat(read(SAFE_EXECUTOR))
            .contains("AgentTraceContext.currentOrNew(request.traceId())")
            .contains("AgentTraceContext.bind(traceId)")
            .contains("structured.put(\"traceId\", traceId)")
            .contains("SafeToolExecutionResult.executed(success, summary, structured, traceId)");
    }

    @Test
    void allPrimaryEntrypoints_shouldPassTraceIdIntoSafeToolExecutionRequest() throws IOException {
        assertThat(read(REACT_ENGINE))
            .contains("AgentTraceContext.currentOrNew(trustedString(initialParams, \"traceId\", \"\"))")
            .contains("traceId,")
            .contains("SafeToolExecutionSource.REACT_ENGINE")
            .contains("ReActEvent.content(steps, finalAnswer, traceMetadata)")
            .contains("ReActEvent.observation(steps, toolName, observation, observationTruncated, riskMetadata)");

        assertThat(read(ORCHESTRATOR))
            .contains("AgentTraceContext.currentOrNew(httpReq.getHeader(\"X-Trace-Id\"))")
            .contains("emit(emitter, \"trace\", Map.of(\"traceId\", finalTraceId))")
            .contains("try (AgentTraceContext.Scope ignored = AgentTraceContext.bind(traceId))")
            .contains("inputs.put(\"traceId\", traceId)")
            .contains("finalTraceId,")
            .contains("inputs.put(\"traceId\", AgentTraceContext.currentOrNew(traceId))");

        assertThat(read(GRAPH_CONFIG))
            .contains("state.value(\"traceId\").map(Object::toString).orElse(\"\")")
            .contains("SafeToolExecutionSource.PLAN_EXECUTE_NODE")
            .contains("SafeToolExecutionSource.GRAPH_TOOL_CALL");

        assertThat(read(BRIDGE_CALLBACK))
            .contains("AgentTraceContext.currentOrNew(\"\")")
            .contains("SafeToolExecutionSource.TOOL_CALLBACK");

        assertThat(read(CORE_CALLBACK))
            .contains("AgentTraceContext.currentOrNew(\"\")")
            .contains("SafeToolExecutionSource.TOOL_CALLBACK");

        assertThat(read(HITL_CONTROLLER))
            .contains("oldState.value(\"traceId\")")
            .contains("AgentTraceContext.safeCandidateOrBlank")
            .contains("AgentTraceContext.bind(context.traceId())")
            .contains("resumeGraph(threadId, decision, confirmation, emitter, context.traceId())")
            .contains("inputs.put(\"traceId\", AgentTraceContext.currentOrNew(traceId))")
            .contains("emitSse(emitter, \"trace\", Map.of(\"traceId\", inputs.get(\"traceId\")))");
    }

    @Test
    void traceId_shouldBeProtectedControlPlaneFieldNotToolBusinessParam() throws IOException {
        assertThat(read(PROTECTED_FILTER))
            .contains("\"traceId\"")
            .contains("\"trace_id\"")
            .contains("\"traceparent\"")
            .contains("\"tracestate\"")
            .contains("\"traceid\"");

        assertThat(read(REACT_EVENT))
            .contains("observation(int step,")
            .contains("content(int step, String content, Map<String, Object> metadata)")
            .contains("error(int step, String content, Map<String, Object> metadata)");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
