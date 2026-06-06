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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 资源预设详情 Tool 的 HTTP 合约测试。
 *
 * <p>重点锁定组织隔离和路径参数校验，防止 LLM 输入被拼成越权路径。</p>
 */
class ResourcePresetDetailToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void shouldCallReviewedReadOnlyDetailEndpointWithTrustedOrgContext() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/resource-preset/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("id", 42, "name", "A100-80G")));

        ResourcePresetDetailTool tool = new ResourcePresetDetailTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "resourcePresetId", "42"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Map.of("id", 42, "name", "A100-80G"), result.get(AtlasToolResult.KEY_DATA));
        verify(httpClient).get(eq("/api/100001/resource-preset/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldRejectPathInjectionBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        ResourcePresetDetailTool tool = new ResourcePresetDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
            "resourcePresetId", "../42"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient, never()).get(eq("/api/100001/resource-preset/../42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }
}
