package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程学习状态 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100，
 * 不触发课程实例创建、暂停、恢复、重置、删除或批量重置。</p>
 */
class CoursewareLearningStatusToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void learningStatus_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/learn/deployment/status/42"), anyMap())).thenReturn(Map.of(
            "result", Map.of("coursewareId", 42, "status", "Running")
        ));

        CoursewareLearningStatusTool tool = new CoursewareLearningStatusTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "coursewareId", "42"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/learn/deployment/status/42"), eq(Map.of()));
    }

    @Test
    void learningStatus_shouldRejectUnsafeIdBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        CoursewareLearningStatusTool tool = new CoursewareLearningStatusTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("coursewareId", "42/extra"));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_COURSEWARE_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100001/learn/deployment/status/42/extra"), anyMap());
    }

    @Test
    void learningStatus_shouldBeSensitiveReadWithConfirmation() {
        AtlasToolMapping mapping = CoursewareLearningStatusTool.class.getAnnotation(AtlasToolMapping.class);

        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
