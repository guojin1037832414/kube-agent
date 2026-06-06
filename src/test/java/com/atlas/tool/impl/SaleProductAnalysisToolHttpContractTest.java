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
 * 产品目录与租赁报价分析 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP 客户端，不访问真实 8100，不创建订单、不变更订单状态、不触发支付。</p>
 */
class SaleProductAnalysisToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void productType_shouldCallPublicEndpointWithoutCallerQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/public/product/type"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new PublicProductTypeListTool(httpClient).execute(Map.of(
            "page", "9",
            "limit", "999",
            "orderId", "777"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/public/product/type"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void publicProductLists_shouldUseReviewedEndpointsAndWhitelistedQueryOnly() {
        Map<String, Object> expectedQuery = Map.of(
            "page", "2",
            "limit", "50",
            "productTypeCode", "gpu",
            "resourceCode", "A800-80G",
            "software", "NVAIE",
            "startTime", "2026-06-06 00:00:00",
            "endTime", "2026-06-07 00:00:00",
            "gpuModel", "A800,3090",
            "gpuPercentLimits", "100"
        );

        Map<String, Object> params = Map.ofEntries(
            entry("organizationId", "999999"),
            entry("page", "2"),
            entry("limit", "50"),
            entry("productTypeCode", " gpu "),
            entry("resourceCode", " A800-80G "),
            entry("software", " NVAIE "),
            entry("startTime", "2026-06-06 00:00:00"),
            entry("endTime", "2026-06-07 00:00:00"),
            entry("gpuModel", " A800,3090 "),
            entry("gpuPercentLimits", "100"),
            entry("orderStatus", "approve"),
            entry("amount", "1")
        );

        assertProductListCall(PublicPostPayProductListTool::new, "/api/public/product/post-pay", expectedQuery, params);
        assertProductListCall(PublicPrePayProductListTool::new, "/api/public/product/pre-pay", expectedQuery, params);
        assertProductListCall(PublicServerProductListTool::new, "/api/public/product/server", expectedQuery, params);
    }

    @Test
    void leaseOrderAmountEstimate_shouldUseTrustedOrgAndCountWhitelistOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        Map<String, Object> expectedQuery = Map.of(
            "id", "42",
            "startTime", "2026-06-06 00:00:00",
            "endTime", "2026-06-07 00:00:00"
        );
        when(httpClient.get(eq("/api/100001/lease/order/count"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("amount", 10000)));

        LeaseOrderAmountEstimateTool tool = new LeaseOrderAmountEstimateTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "id", "42",
            "startTime", " 2026-06-06 00:00:00 ",
            "endTime", " 2026-06-07 00:00:00 ",
            "status", "approve",
            "username", "admin"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/lease/order/count"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void invalidProductQueryAndOrderCount_shouldFailBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> badLimitResult = new PublicPostPayProductListTool(httpClient)
            .execute(Map.of("limit", "5000"));
        Map<String, Object> badPercentResult = new PublicServerProductListTool(httpClient)
            .execute(Map.of("gpuPercentLimits", "-1"));
        Map<String, Object> badOrderIdResult = new LeaseOrderAmountEstimateTool(httpClient)
            .execute(Map.of("id", "../42", "startTime", "2026-06-06", "endTime", "2026-06-07"));

        assertEquals(Boolean.FALSE, badLimitResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.FALSE, badPercentResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.FALSE, badOrderIdResult.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient, never()).get(eq("/api/public/product/post-pay"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void saleProductTools_shouldBePlainReadWithoutConfirmation() {
        assertPlainRead(PublicProductTypeListTool.class);
        assertPlainRead(PublicPostPayProductListTool.class);
        assertPlainRead(PublicPrePayProductListTool.class);
        assertPlainRead(PublicServerProductListTool.class);
        assertPlainRead(LeaseOrderAmountEstimateTool.class);
    }

    private void assertProductListCall(Function<KubeManagerHttpClient, BaseTool> factory,
                                       String expectedPath,
                                       Map<String, Object> expectedQuery,
                                       Map<String, Object> params) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        BaseTool tool = factory.apply(httpClient);
        Map<String, Object> result = tool.execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    private void assertPlainRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.READ, mapping.operationType());
        assertFalse(mapping.requiresConfirmation());
    }
}
