package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课件只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问用户已经启动的 8100，
 * 不触发课件保存、上传、删除、分发或课程环境操作。</p>
 */
class CoursewareReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void coursewareDetail_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/courseware/info/42"), anyMap())).thenReturn(Map.of(
            "result", Map.of("id", 42, "name", "AI 入门")
        ));

        CoursewareDetailTool tool = new CoursewareDetailTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "coursewareId", "42"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/courseware/info/42"), eq(Map.of()));
    }

    @Test
    void coursewareGradeList_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/courseware/grade/42"), anyMap())).thenReturn(Map.of(
            "result", List.of(Map.of("gradeName", "class-a"))
        ));

        CoursewareGradeListTool tool = new CoursewareGradeListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "orgId", "888888",
            "coursewareId", 42
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/courseware/grade/42"), eq(Map.of()));
    }

    @Test
    void coursewareDetail_shouldRejectUnsafeIdBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        CoursewareDetailTool tool = new CoursewareDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("coursewareId", "../42"));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_COURSEWARE_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100001/courseware/info/../42"), anyMap());
    }

    @Test
    void coursewareReadTools_shouldExposeExpectedRiskMetadata() {
        AtlasToolMapping detailMapping = CoursewareDetailTool.class.getAnnotation(AtlasToolMapping.class);
        AtlasToolMapping gradeMapping = CoursewareGradeListTool.class.getAnnotation(AtlasToolMapping.class);

        assertEquals(AtlasToolMapping.OperationType.READ, detailMapping.operationType());
        assertFalse(detailMapping.requiresConfirmation());
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, gradeMapping.operationType());
        assertTrue(gradeMapping.requiresConfirmation());
    }
}
