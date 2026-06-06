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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static java.util.Map.entry;

/**
 * 成本与使用分析 Tool 的 HTTP 契约测试。
 *
 * <p>测试只使用 mock HTTP 客户端，不访问真实 8100，避免影响线上账单/配置数据。</p>
 */
class FinancialAnalysisToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void podUseRecordList_shouldCallReviewedEndpointWithWhitelistedQueryOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "2",
            "limit", "50",
            "userName", "张三",
            "containerName", "train-pod",
            "startTime", "2026-06-01 00:00:00",
            "endTime", "2026-06-05 23:59:59"
        );
        when(httpClient.get(eq("/api/100001/pod-use/record"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        PodUseRecordListTool tool = new PodUseRecordListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.ofEntries(
            entry("organizationId", "999999"),
            entry("orgId", "888888"),
            entry("token", "fake-token"),
            entry("userId", "777"),
            entry("page", "2"),
            entry("limit", "50"),
            entry("userName", " 张三 "),
            entry("containerName", " train-pod "),
            entry("startTime", "2026-06-01 00:00:00"),
            entry("endTime", "2026-06-05 23:59:59")
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/pod-use/record"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void podUseBillList_shouldCallReviewedEndpointWithBillFilters() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "1",
            "limit", "100",
            "applicationName", "gpu-train",
            "startTime", "2026-06-01 00:00:00",
            "endTime", "2026-06-05 23:59:59",
            "podStatus", "finish"
        );
        when(httpClient.get(eq("/api/100001/pod-use/bill"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        PodUseBillListTool tool = new PodUseBillListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "page", "1",
            "limit", "100",
            "applicationName", "gpu-train",
            "startTime", "2026-06-01 00:00:00",
            "endTime", "2026-06-05 23:59:59",
            "podStatus", "finish",
            "approved", true
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/pod-use/bill"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void costConfigList_shouldCallReviewedEndpointWithTimeFiltersOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "page", "1",
            "limit", "20",
            "startTime", "2026-06-01 00:00:00",
            "endTime", "2026-06-05 23:59:59"
        );
        when(httpClient.get(eq("/api/100001/cost"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        CostConfigListTool tool = new CostConfigListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "page", "1",
            "limit", "20",
            "startTime", "2026-06-01 00:00:00",
            "endTime", "2026-06-05 23:59:59",
            "deviceAmount", "1",
            "userId", "777"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/cost"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void financialTools_shouldBeSensitiveReadAndRequireConfirmation() {
        assertSensitiveRead(PodUseRecordListTool.class);
        assertSensitiveRead(PodUseBillListTool.class);
        assertSensitiveRead(CostConfigListTool.class);
    }

    @Test
    void invalidPagination_shouldFailBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        PodUseBillListTool tool = new PodUseBillListTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("limit", "5000"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient, never()).get(eq("/api/100001/pod-use/bill"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    private void assertSensitiveRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
