package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * M5.5 代表性 Tool 的 organizationId 治理测试。
 *
 * <p>本测试覆盖三类典型入口：Dashboard 固定读、Deployment 标准读、Storage 写操作。
 * 共同目标是证明最终 HTTP path 的 orgId 必须来自会话 ThreadLocal，而不是来自
 * LLM Action 或用户 params 中注入的 {@code organizationId/orgId}。</p>
 */
class OrganizationIdGovernanceRepresentativeToolTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void m55_dashboardImageCount_shouldUseThreadLocalOrgIdInsteadOfInjectedOrganizationId() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/dashboard/image/count"), anyMap()))
            .thenReturn(Map.of("result", Map.of("count", 1)));

        DashboardImageCountTool tool = new DashboardImageCountTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of("organizationId", "100002"));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/dashboard/image/count"), eq(Map.of("page", "1", "limit", "100")));
        verify(httpClient, never()).get(eq("/api/100002/dashboard/image/count"), anyMap());
    }

    @Test
    void m55_deploymentQuery_shouldUseThreadLocalOrgIdInsteadOfInjectedOrgIdAlias() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/deployment"), anyMap()))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        DeploymentQueryTool tool = new DeploymentQueryTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of("orgId", "100002", "name", "demo"));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/deployment"), eq(Map.of(
            "page", "1",
            "limit", "100",
            "name", "demo"
        )));
        verify(httpClient, never()).get(eq("/api/100002/deployment"), anyMap());
    }

    @Test
    void m55_storageCreate_shouldUseThreadLocalOrgIdForPostPath() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.post(eq("/api/100001/file/storage"), anyMap()))
            .thenReturn(Map.of("result", Map.of("id", "pvc-1")));

        StorageCreateTool tool = new StorageCreateTool(httpClient);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("organizationId", "100002");
        params.put("name", "train-data");
        params.put("size", 10);
        params.put("type", "fileset");
        params.put("scope", "user");
        params.put("location", "shanghai");
        params.put("approved", true);
        Map<String, Object> result = tool.execute(params);

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        ArgumentCaptor<Map<String, Object>> bodyCaptor = forClass(Map.class);
        verify(httpClient).post(eq("/api/100001/file/storage"), bodyCaptor.capture());
        assertEquals(Map.of(
            "areaCode", "shanghai",
            "displayName", "train-data",
            "size", 10,
            "type", "fileset",
            "scope", "user"
        ), bodyCaptor.getValue());
        verify(httpClient, never()).post(eq("/api/100002/file/storage"), anyMap());
    }

    @Test
    void m55_legacyQueryTools_shouldUseThreadLocalOrgIdInsteadOfInjectedOrganizationId() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/node/all/gpu-map")))
            .thenReturn(Map.of("result", Map.of("gpu", 1)));
        when(httpClient.get(eq("/api/100001/dashboard/resources")))
            .thenReturn(Map.of("result", Map.of("nodes", 1)));
        when(httpClient.get(eq("/api/100001/image"), anyMap()))
            .thenReturn(Map.of("result", Map.of("records", java.util.List.of())));

        Map<String, Object> injectedParams = Map.of("organizationId", "100002");
        assertEquals(true, new GpuQueryTool(httpClient).execute(injectedParams).get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(true, new ClusterOverviewTool(httpClient).execute(injectedParams).get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(true, new ImageQueryTool(httpClient).execute(injectedParams).get(AtlasToolResult.KEY_SUCCESS));

        verify(httpClient).get(eq("/api/100001/node/all/gpu-map"));
        verify(httpClient).get(eq("/api/100001/dashboard/resources"));
        verify(httpClient).get(eq("/api/100001/image"), eq(Map.of("page", "1", "limit", "100")));
        verify(httpClient, never()).get(eq("/api/100002/node/all/gpu-map"));
        verify(httpClient, never()).get(eq("/api/100002/dashboard/resources"));
        verify(httpClient, never()).get(eq("/api/100002/image"), anyMap());
    }
}
