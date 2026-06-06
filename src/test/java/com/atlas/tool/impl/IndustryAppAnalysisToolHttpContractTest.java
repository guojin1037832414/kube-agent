package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 行业应用分析 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP 客户端，不访问真实 8100，避免对线上行业应用实例、模板和调用历史造成影响。</p>
 */
class IndustryAppAnalysisToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void templateList_shouldCallReviewedEndpointWithWhitelistedQueryOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "2",
            "limit", "50",
            "category", "traffic",
            "keyword", "video",
            "tags", "gpu,edge",
            "includeDetail", true
        );
        when(httpClient.get(eq("/api/100001/industry-app/template"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        IndustryAppTemplateListTool tool = new IndustryAppTemplateListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.ofEntries(
            entry("organizationId", "999999"),
            entry("orgId", "888888"),
            entry("token", "fake-token"),
            entry("page", "2"),
            entry("limit", "50"),
            entry("category", " traffic "),
            entry("keyword", " video "),
            entry("tags", " gpu,edge "),
            entry("includeDetail", "true"),
            entry("creatorId", "777")
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/industry-app/template"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceList_shouldCallReviewedEndpointWithWhitelistedQueryOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "1",
            "limit", "10",
            "name", "vision",
            "status", "Running",
            "mineOnly", false,
            "includeDetail", true
        );
        when(httpClient.get(eq("/api/100001/industry-app/instance"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        IndustryAppInstanceListTool tool = new IndustryAppInstanceListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "name", " vision ",
            "status", "Running",
            "mineOnly", "false",
            "includeDetail", "yes",
            "namespace", "should-not-pass"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/industry-app/instance"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void detailAndMetadataTools_shouldUsePositiveIdPathAndNoCallerQuery() {
        assertIdOnlyGet(IndustryAppTemplateDetailTool::new, "/api/100001/industry-app/template/42", "appId");
        assertIdOnlyGet(IndustryAppTemplateApiDocTool::new, "/api/100001/industry-app/template/42/api-doc", "appId");
        assertIdOnlyGet(IndustryAppResourcePresetListTool::new, "/api/100001/industry-app/template/42/resource-preset", "appId");
        assertIdOnlyGet(IndustryAppParamListTool::new, "/api/100001/industry-app/template/42/app-param", "appId");
    }

    @Test
    void apiHistory_shouldCallReviewedEndpointWithHistoryFiltersOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "3",
            "limit", "20",
            "httpMethod", "GET",
            "url", "/v1/chat",
            "sinceSeconds", "3600"
        );
        when(httpClient.get(eq("/api/100001/industry-app/instance/77/api-history"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        IndustryAppInstanceApiHistoryTool tool = new IndustryAppInstanceApiHistoryTool(httpClient);
        Map<String, Object> result = tool.execute(Map.ofEntries(
            entry("organizationId", "999999"),
            entry("instanceId", "77"),
            entry("page", "3"),
            entry("limit", "20"),
            entry("httpMethod", " GET "),
            entry("url", " /v1/chat "),
            entry("sinceSeconds", "3600"),
            entry("requestBody", "should-not-pass")
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/industry-app/instance/77/api-history"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void invalidPathSegmentAndPagination_shouldFailBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> badIdResult = new IndustryAppTemplateDetailTool(httpClient)
            .execute(Map.of("appId", "../42"));
        Map<String, Object> badLimitResult = new IndustryAppTemplateListTool(httpClient)
            .execute(Map.of("limit", "5000"));
        Map<String, Object> badSinceResult = new IndustryAppInstanceApiHistoryTool(httpClient)
            .execute(Map.of("instanceId", "7", "sinceSeconds", "999999"));

        assertEquals(Boolean.FALSE, badIdResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.FALSE, badLimitResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.FALSE, badSinceResult.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient, never()).get(eq("/api/100001/industry-app/template/../42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void industryAppTools_shouldBePlainReadWithoutConfirmation() {
        assertPlainRead(IndustryAppTemplateListTool.class);
        assertPlainRead(IndustryAppTemplateDetailTool.class);
        assertPlainRead(IndustryAppTemplateApiDocTool.class);
        assertPlainRead(IndustryAppInstanceListTool.class);
        assertPlainRead(IndustryAppInstanceApiHistoryTool.class);
        assertPlainRead(IndustryAppResourcePresetListTool.class);
        assertPlainRead(IndustryAppParamListTool.class);
    }

    private void assertIdOnlyGet(Function<KubeManagerHttpClient, BaseTool> factory, String expectedPath, String idKey) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("id", "42")));

        BaseTool tool = factory.apply(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            idKey, "42",
            "page", "9",
            "limit", "999",
            "token", "fake-token"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    private void assertPlainRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.READ, mapping.operationType());
        assertFalse(mapping.requiresConfirmation());
    }
}
