package com.atlas.orchestrator;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.hitl.HitlGuard;
import com.atlas.intent.IntentRouter;
import com.atlas.orchestrator.polish.ToolResultPolishingService;
import com.atlas.react.ReActEventSinkRegistry;
import com.atlas.store.SessionStore;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

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
    void testToJson_escapesControlCharactersForSseSingleLinePayload() throws Exception {
        AtlasOrchestrator orchestrator = new AtlasOrchestrator(
            mock(IntentRouter.class),
            mock(StreamingEmitter.class),
            mock(ToolRegistry.class),
            mock(UserPermissionContext.class),
            mock(KubeManagerHttpClient.class),
            mock(HitlGuard.class),
            mock(ReActEventSinkRegistry.class),
            mock(TimedDecisionCache.class),
            mock(ToolResultPolishingService.class),
            Runnable::run,
            mock(SessionStore.class),
            null,
            null
        );

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
        AtlasOrchestrator orchestrator = new AtlasOrchestrator(
            mock(IntentRouter.class),
            mock(StreamingEmitter.class),
            mock(ToolRegistry.class),
            mock(UserPermissionContext.class),
            mock(KubeManagerHttpClient.class),
            mock(HitlGuard.class),
            mock(ReActEventSinkRegistry.class),
            mock(TimedDecisionCache.class),
            mock(ToolResultPolishingService.class),
            Runnable::run,
            mock(SessionStore.class),
            null,
            null
        );

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
}
