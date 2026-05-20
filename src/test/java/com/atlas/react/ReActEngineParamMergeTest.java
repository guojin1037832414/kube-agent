package com.atlas.react;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReActEngine 参数合并契约测试。
 *
 * <p>锁定会话级上下文（token/orgId/conversationId）能够被稳定透传到每轮 Action 参数，
 * 同时允许本轮 Action 参数覆盖同名字段。</p>
 */
class ReActEngineParamMergeTest {

    @Test
    void testMergeInitialAndActionParams_prefersActionParams() throws Exception {
        Method method = ReActEngine.class.getDeclaredMethod(
            "mergeInitialAndActionParams", Map.class, Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) method.invoke(
            null,
            Map.of("token", "t1", "organizationId", "100002", "conversationId", "c1"),
            Map.of("organizationId", "200001", "podName", "nginx-1")
        );

        assertEquals("t1", merged.get("token"));
        assertEquals("200001", merged.get("organizationId"));
        assertEquals("c1", merged.get("conversationId"));
        assertEquals("nginx-1", merged.get("podName"));
    }
}
