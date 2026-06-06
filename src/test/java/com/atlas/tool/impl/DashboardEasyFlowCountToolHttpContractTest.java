package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Dashboard EasyFlow 统计 Tool 的 HTTP 合约测试。
 *
 * <p>测试只使用 mock HTTP 客户端，不访问真实 8100，避免影响线上系统数据。</p>
 */
class DashboardEasyFlowCountToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void shouldCallReviewedReadOnlyDashboardCountEndpointWithFixedQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/dashboard/easy-flow/count"), eq(Map.of("page", "1", "limit", "100"))))
            .thenReturn(Map.of("result", Map.of("total", 12)));

        DashboardEasyFlowCountTool tool = new DashboardEasyFlowCountTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "page", "9",
            "limit", "999",
            "keyword", "probe"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Map.of("total", 12), result.get(AtlasToolResult.KEY_DATA));
        verify(httpClient).get(eq("/api/100001/dashboard/easy-flow/count"), eq(Map.of("page", "1", "limit", "100")));
        verifyNoMoreInteractions(httpClient);
    }
}
