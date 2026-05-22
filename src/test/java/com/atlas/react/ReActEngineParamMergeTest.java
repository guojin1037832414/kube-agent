package com.atlas.react;

import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * ReActEngine 参数合并契约测试。
 *
 * <p>锁定会话级上下文（token/orgId/conversationId）能够被稳定透传到每轮 Action 参数，
 * 同时允许本轮 Action 参数覆盖同名字段。参数别名归一化本身由
 * {@link ToolParameterNormalizer} 负责，本测试只验证 ReActEngine 在合并后会调用统一归一化器。</p>
 */
class ReActEngineParamMergeTest {

    private ReActEngine newEngine() {
        return new ReActEngine(
            mock(ChatModel.class),
            new ObjectMapper(),
            mock(ToolRegistry.class),
            mock(ReActPromptBuilder.class),
            new ToolParameterNormalizer()
        );
    }

    @Test
    void m55_mergeInitialAndActionParams_shouldKeepTrustedInitialOrganizationId() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", String.class, Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            newEngine(),
            "diagnose_pod",
            Map.of("token", "t1", "organizationId", "100002", "conversationId", "c1"),
            Map.of("organizationId", "200001", "podName", "nginx-1")
        );

        assertEquals("t1", merged.get("token"));
        assertEquals("100002", merged.get("organizationId"));
        assertEquals("c1", merged.get("conversationId"));
        assertEquals("nginx-1", merged.get("podName"));
    }

    @Test
    void m55_mergeInitialAndActionParams_shouldDropActionOrgIdAliasWhenInitialOrganizationIdExists() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", String.class, Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            newEngine(),
            "diagnose_pod",
            Map.of("token", "t1", "organizationId", "100002", "conversationId", "c1"),
            Map.of("orgId", "200001", "podName", "nginx-1")
        );

        assertEquals("100002", merged.get("organizationId"));
        assertFalse(merged.containsKey("orgId"), "LLM Action 注入的 orgId 别名不应继续向下游传播");
        assertEquals("nginx-1", merged.get("podName"));
    }

    @Test
    void testMergeInitialAndActionParams_normalizesSnakeCaseAliases() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", String.class, Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            newEngine(),
            "diagnose_pod",
            Map.of("token", "t1", "organizationId", "100002", "conversationId", "c1"),
            Map.of("pod_name", "nginx-1", "name_space", "default")
        );

        assertEquals("nginx-1", merged.get("podName"));
        assertEquals("default", merged.get("namespace"));
        assertEquals("t1", merged.get("token"));
    }

    @Test
    void testMergeInitialAndActionParams_doesNotOverrideCanonicalValue() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", String.class, Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            newEngine(),
            "diagnose_pod",
            Map.of(),
            Map.of("podName", "canonical-pod", "pod_name", "alias-pod")
        );

        assertEquals("canonical-pod", merged.get("podName"));
    }

    @Test
    void testMergeInitialAndActionParams_doesNotMapAmbiguousNameForUnknownTool() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", String.class, Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            newEngine(),
            "unknown_tool",
            Map.of(),
            Map.of("name", "ambiguous-name")
        );

        assertEquals("ambiguous-name", merged.get("name"));
        assertFalse(merged.containsKey("podName"));
    }
}
