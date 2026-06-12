package com.atlas.orchestrator;

import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.hitl.HitlGuard;
import com.atlas.intent.IntentRouter;
import com.atlas.orchestrator.polish.ToolResultPolishingService;
import com.atlas.react.ReActEventSinkRegistry;
import com.atlas.store.ConversationStore;
import com.atlas.store.SessionStore;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AtlasOrchestrator JSON 序列化回归测试。
 *
 * <p>重点锁定 SSE data payload 的 JSON 字符串转义契约：content 中的真实换行、回车、Tab
 * 和其他控制字符必须被转义成合法 JSON，避免 EventSource 将单条 data 拆裂成多行。</p>
 */
class AtlasOrchestratorJsonTest {

    @Test
    void delegateDisplayContent_shouldPreferProfessionalAgentResultAndFallbackToAnswer() throws Exception {
        AtlasOrchestrator orchestrator = newOrchestrator();
        Method method = AtlasOrchestrator.class.getDeclaredMethod("delegateDisplayContent", java.util.function.Function.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<String> queryResult = (Optional<String>) method.invoke(
            orchestrator,
            (java.util.function.Function<String, Optional<Object>>) key ->
                Optional.ofNullable(Map.<String, Object>of(
                    "query_result", "节点查询完成",
                    "answer", "delegate fallback answer"
                ).get(key))
        );

        assertEquals(Optional.of("节点查询完成"), queryResult,
            "delegate SSE 应优先展示专业 Agent 输出，而不是 fallback answer");

        @SuppressWarnings("unchecked")
        Optional<String> fallbackAnswer = (Optional<String>) method.invoke(
            orchestrator,
            (java.util.function.Function<String, Optional<Object>>) key ->
                Optional.ofNullable(Map.<String, Object>of(
                    "answer", "❌ 安全上下文缺失：无法确定当前用户所属组织"
                ).get(key))
        );

        assertEquals(Optional.of("❌ 安全上下文缺失：无法确定当前用户所属组织"), fallbackAnswer,
            "delegate fail-closed 只写 answer 时也必须能推送给前端");
    }

    @Test
    void delegateDisplayContent_shouldIgnoreEmptyPlaceholderMaps() throws Exception {
        AtlasOrchestrator orchestrator = newOrchestrator();
        Method method = AtlasOrchestrator.class.getDeclaredMethod("delegateDisplayContent", java.util.function.Function.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<String> content = (Optional<String>) method.invoke(
            orchestrator,
            (java.util.function.Function<String, Optional<Object>>) key ->
                Optional.ofNullable(Map.<String, Object>of(
                    "query_result", Map.of()
                ).get(key))
        );

        assertTrue(content.isEmpty(), "delegate 占位空 Map 不应被当成用户可读回答推送");
    }

    @Test
    void registerSupervisorDisplayContent_shouldDedupeFinalAnswersAndRejectPlaceholders() throws Exception {
        AtlasOrchestrator orchestrator = newOrchestrator();
        Method method = AtlasOrchestrator.class.getDeclaredMethod(
            "registerSupervisorDisplayContent",
            String.class,
            Set.class
        );
        method.setAccessible(true);

        // 中文说明：Supervisor Graph 的同一份最终答案可能同时存在于 ReAct event、answer、result key 中。
        // 本测试保护“前端只收到一次最终内容”的 SSE 契约；这里不启动 Graph、不执行 Tool、也不访问 kube-manager。
        Set<String> emittedContents = new HashSet<>();

        assertTrue((Boolean) method.invoke(orchestrator, "最终回答", emittedContents),
            "第一次出现的最终回答应该允许推送");
        assertFalse((Boolean) method.invoke(orchestrator, " 最终回答 ", emittedContents),
            "仅空白差异不应导致重复 SSE content");
        assertTrue((Boolean) method.invoke(orchestrator, "后续不同错误说明", emittedContents),
            "不同内容仍应允许推送，避免吞掉新的澄清或错误原因");
        assertFalse((Boolean) method.invoke(orchestrator, "", emittedContents),
            "空内容不应生成前端气泡");
        assertFalse((Boolean) method.invoke(orchestrator, "{}", emittedContents),
            "Graph 占位空 Map 字符串不应被当成用户可读回答");
    }

    @Test
    void emitSupervisorDisplayContent_shouldOnlySendFirstUniqueContent() throws Exception {
        CapturingStreamingEmitter streamingEmitter = new CapturingStreamingEmitter();
        AtlasOrchestrator orchestrator = newOrchestrator(streamingEmitter);
        Method method = AtlasOrchestrator.class.getDeclaredMethod(
            "emitSupervisorDisplayContent",
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class,
            String.class,
            String.class,
            Set.class
        );
        method.setAccessible(true);

        // 中文说明：这个测试比纯集合测试更贴近 SSE 运行时，直接确认重复最终内容不会再次调用 StreamingEmitter。
        // 安全边界：仍然只是展示层测试，不创建 Graph run、不执行 Tool、不调用 LLM、不访问 kube-manager。
        Set<String> emittedContents = new HashSet<>();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);

        assertTrue((Boolean) method.invoke(orchestrator, emitter, "direct_answer", "最终回答", emittedContents));
        assertFalse((Boolean) method.invoke(orchestrator, emitter, "react_node", "最终回答", emittedContents));
        assertTrue((Boolean) method.invoke(orchestrator, emitter, "delegate", "不同 delegate 回答", emittedContents));

        assertEquals(2, streamingEmitter.events.size(), "同一份最终内容只应发送一次，不同内容仍应发送");
        assertEquals("content", streamingEmitter.events.get(0).type());
        assertTrue(streamingEmitter.events.get(0).content().contains("最终回答"));
        assertTrue(streamingEmitter.events.get(1).content().contains("不同 delegate 回答"));
    }

    @Test
    void testToJson_escapesControlCharactersForSseSingleLinePayload() throws Exception {
        AtlasOrchestrator orchestrator = newOrchestrator();

        Method method = AtlasOrchestrator.class.getDeclaredMethod("toJson", Map.class);
        method.setAccessible(true);

        String original = "第一行\n第二行\r\n第三行\t\u0001 \"quote\" \\ slash";
        String json = (String) method.invoke(orchestrator, Map.of(
            "type", "content",
            "content", original
        ));

        assertFalse(json.contains("第一行\n第二行"), "JSON payload 内部不能包含真实 LF 换行");
        assertFalse(json.contains("第二行\r\n第三行"), "JSON payload 内部不能包含真实 CRLF");
        assertTrue(json.contains("\\n"), "真实换行应转义为 \\n");
        assertTrue(json.contains("\\r"), "真实回车应转义为 \\r");
        assertTrue(json.contains("\\t"), "真实 Tab 应转义为 \\t");
        assertTrue(json.contains("\\u0001"), "其他控制字符应转义为 unicode escape");

        JsonNode parsed = new ObjectMapper().readTree(json);
        assertEquals("content", parsed.get("type").asText());
        assertEquals(original, parsed.get("content").asText());
    }

    @Test
    void testToJson_serializesListsAsJsonArraysForStructuredClarification() throws Exception {
        AtlasOrchestrator orchestrator = newOrchestrator();

        Method method = AtlasOrchestrator.class.getDeclaredMethod("toJson", Map.class);
        method.setAccessible(true);

        String json = (String) method.invoke(orchestrator, Map.of(
            "type", "clarify",
            "suggestions", List.of("先调用 gpu_query", "选择明确 gpuSpec"),
            "metadata", Map.of("codes", List.of("MISSING_GPU_SPEC"))
        ));

        JsonNode parsed = new ObjectMapper().readTree(json);
        assertTrue(parsed.get("suggestions").isArray(), "suggestions 必须是 JSON 数组，前端才能直接渲染选项");
        assertEquals("先调用 gpu_query", parsed.get("suggestions").get(0).asText());
        assertTrue(parsed.get("metadata").get("codes").isArray(), "嵌套 List 也必须保持数组结构");
        assertEquals("MISSING_GPU_SPEC", parsed.get("metadata").get("codes").get(0).asText());
    }

    private AtlasOrchestrator newOrchestrator() {
        return newOrchestrator(mock(StreamingEmitter.class));
    }

    private AtlasOrchestrator newOrchestrator(StreamingEmitter streamingEmitter) {
        return new AtlasOrchestrator(
            mock(IntentRouter.class),
            streamingEmitter,
            mock(ToolRegistry.class),
            mock(UserPermissionContext.class),
            mock(AgentPrincipalResolver.class),
            mock(KubeManagerHttpClient.class),
            mock(HitlGuard.class),
            mock(SafeToolExecutor.class),
            mock(ReActEventSinkRegistry.class),
            mock(TimedDecisionCache.class),
            mock(ToolResultPolishingService.class),
            Runnable::run,
            mock(SessionStore.class),
            mock(ConversationStore.class),
            null,
            null
        );
    }

    private static final class CapturingStreamingEmitter extends StreamingEmitter {
        private final List<SseEvent> events = new ArrayList<>();

        @Override
        public void send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, SseEvent event) {
            events.add(event);
        }
    }
}
