package com.atlas.graph.config;

import com.atlas.tool.execution.SafeToolExecutionSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Graph ReAct 节点入口安全契约测试。
 *
 * <p>中文说明：ReAct 是 kube-agent Phase 1 的核心智能诊断链路，会在多轮 Thought/Action/Observation
 * 中反复产生候选 Tool 调用。这个测试保护 Graph 入口的服务端上下文传递：traceId 必须从 Graph State
 * 进入 ReActEngine，orgId 必须是可信租户上下文，缺失时必须在调用 LLM/Tool 前 fail-closed。</p>
 *
 * <p>安全边界：本测试只读取源码并反射调用私有纯函数，不启动 Spring、不调用 LLM、不访问 kube-manager、
 * 不执行 Tool、不写审计或记忆。它验证的是入口契约，而不是 ReActEngine 的完整循环行为。</p>
 */
class GraphReActNodeSafetyGuardTest {

    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");

    @Test
    void reactNode_shouldInjectTrustedTraceIdAndFailClosedBeforeEngineWhenOrgMissing() throws IOException {
        String source = read(GRAPH_CONFIG);

        assertThat(source)
            .as("ReAct Graph 节点必须从服务端 Graph State 读取 traceId，而不是让 Action.params 或 Tool 参数伪造")
            .contains("String traceId = state.value(\"traceId\").map(Object::toString).orElse(\"\");")
            .contains("initialParams.put(\"traceId\", traceId);");

        assertThat(source)
            .as("Graph State 缺失 orgId 时可从服务端 ThreadLocal 恢复，但不能从 LLM/Plan/前端参数猜测")
            .contains("orgId = com.atlas.auth.UserPermissionContext.getCurrentOrgId();")
            .contains("GRAPH_REACT_TRUSTED_ORG_MISSING")
            .contains("failClosedGraphReActUpdates(");

        assertThat(source.indexOf("GRAPH_REACT_TRUSTED_ORG_MISSING"))
            .as("缺失可信 orgId 的阻断必须发生在 ReActEngine.runWithEvents 之前，避免先调用 LLM 或 Tool 再补救")
            .isLessThan(source.indexOf("engine.runWithEvents(input, initialParams, eventSink)"));
    }

    @Test
    void failClosedReactUpdates_shouldExposeUserVisibleAnswerWithoutPretendingToolExecuted() throws Exception {
        Map<String, Object> updates = invokeFailClosedReActUpdates(
            "⛔ ReAct 已停止：缺失可信组织上下文",
            "GRAPH_REACT_TRUSTED_ORG_MISSING",
            "trc_react_guard"
        );

        assertThat(updates)
            .containsEntry("answer", "⛔ ReAct 已停止：缺失可信组织上下文")
            .containsEntry("react_node_result", "⛔ ReAct 已停止：缺失可信组织上下文")
            .containsEntry("tool_error_code", "GRAPH_REACT_TRUSTED_ORG_MISSING")
            .containsEntry("traceId", "trc_react_guard")
            .doesNotContainKey("tool_result");

        assertThat(executeResult(updates))
            .containsEntry("executed", false)
            .containsEntry("code", "GRAPH_REACT_TRUSTED_ORG_MISSING")
            .containsEntry("source", SafeToolExecutionSource.REACT_ENGINE.name());
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeFailClosedReActUpdates(String answer,
                                                             String code,
                                                             String traceId) throws Exception {
        Method method = AtlasGraphConfig.class.getDeclaredMethod(
            "failClosedGraphReActUpdates",
            String.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, answer, code, traceId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeResult(Map<String, Object> updates) {
        return (Map<String, Object>) updates.get("execute_result");
    }
}
