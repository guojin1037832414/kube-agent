package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 组织内产品配置只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100，也不保存、删除或修改折扣配置。</p>
 */
class ProductConfigReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void productTypeList_shouldUseTrustedOrgAndIgnoreCallerQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/product/type"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        ProductTypeListTool tool = new ProductTypeListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "page", "9",
            "keyword", "gpu"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/product/type"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void postPayProductList_shouldUseTrustedOrgAndWhitelistedQueryOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
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
        when(httpClient.get(eq("/api/100001/product/post-pay"), eq(expectedQuery)))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        PostPayProductListTool tool = new PostPayProductListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.ofEntries(
            entry("organizationId", "999999"),
            entry("orgId", "888888"),
            entry("page", "2"),
            entry("limit", "50"),
            entry("productTypeCode", " gpu "),
            entry("resourceCode", " A800-80G "),
            entry("software", " NVAIE "),
            entry("startTime", "2026-06-06 00:00:00"),
            entry("endTime", "2026-06-07 00:00:00"),
            entry("gpuModel", " A800,3090 "),
            entry("gpuPercentLimits", "100"),
            entry("discountPolicy", "delete-all"),
            entry("amount", "1")
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/product/post-pay"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void postPayProductList_shouldRejectInvalidQueryBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        PostPayProductListTool tool = new PostPayProductListTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("gpuPercentLimits", "-1"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient, never()).get(eq("/api/100001/product/post-pay"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void productConfigReadTools_shouldBeSensitiveReadWithConfirmation() {
        assertSensitive(ProductTypeListTool.class.getAnnotation(AtlasToolMapping.class));
        assertSensitive(PostPayProductListTool.class.getAnnotation(AtlasToolMapping.class));
    }

    private static void assertSensitive(AtlasToolMapping mapping) {
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
