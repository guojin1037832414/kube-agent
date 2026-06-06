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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板详情只读 Tool 的 HTTP 契约测试。
 *
 * <p>本测试只验证 Tool 到 HTTP client 的边界，不访问真实 8100，
 * 也不触发模板创建、更新或删除。</p>
 */
class TemplateDetailToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void templateDetail_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/template/42"), anyMap())).thenReturn(Map.of(
            "result", Map.of("id", 42, "name", "cuda-template")
        ));

        TemplateDetailTool tool = new TemplateDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "templateId", "42"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/template/42"), eq(Map.of()));
    }

    @Test
    void jobTemplateDetail_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/train-job-template/77"), anyMap())).thenReturn(Map.of(
            "result", Map.of("id", 77, "name", "pytorch-template")
        ));

        JobTemplateDetailTool tool = new JobTemplateDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "templateId", 77
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/train-job-template/77"), eq(Map.of()));
    }

    @Test
    void templateDetail_shouldRejectUnsafeIdBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        TemplateDetailTool tool = new TemplateDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("templateId", "../42"));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_TEMPLATE_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100001/template/../42"), anyMap());
    }

    @Test
    void templateDetailTools_shouldStayPlainReadWithoutConfirmation() {
        AtlasToolMapping templateMapping = TemplateDetailTool.class.getAnnotation(AtlasToolMapping.class);
        AtlasToolMapping jobTemplateMapping = JobTemplateDetailTool.class.getAnnotation(AtlasToolMapping.class);

        assertEquals(AtlasToolMapping.OperationType.READ, templateMapping.operationType());
        assertEquals(AtlasToolMapping.OperationType.READ, jobTemplateMapping.operationType());
        assertFalse(templateMapping.requiresConfirmation());
        assertFalse(jobTemplateMapping.requiresConfirmation());
    }
}
