package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 外部链接 Tool 的 HTTP 契约测试。
 *
 * <p>成熟 kube-manager 没有外链列表接口，只有多个单项 Grafana 链接和一个管理员 Dashboard 链接。
 * 这里用 mock 锁定真实路径，避免 Tool 回退到历史上不存在的 {@code /api/{orgId}/external-link}。</p>
 */
class ExternalLinkToolHttpContractTest {

    private final KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

    @Test
    void externalLinkList_shouldFetchOnlyRequestedGrafanaCategoryFromReviewedEndpoint() {
        when(httpClient.get(eq("/api/external-link/grafana/node")))
            .thenReturn(Map.of("result", "https://grafana.example/node"));

        Map<String, Object> result = new ExternalLinkListTool(httpClient)
            .execute(Map.of("category", "node", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals("https://grafana.example/node", data.get("node"));
        verify(httpClient).get(eq("/api/external-link/grafana/node"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void externalLinkList_shouldFailClosedForUnknownCategory() {
        Map<String, Object> result = new ExternalLinkListTool(httpClient)
            .execute(Map.of("category", "kubernetes-dashboard"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("UNKNOWN_EXTERNAL_LINK_CATEGORY", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void kubernetesDashboardLink_shouldUseReviewedAdminSensitiveEndpoint() {
        when(httpClient.get(eq("/api/external-link/kubernetes/dashboard")))
            .thenReturn(Map.of("result", "https://dashboard.example"));

        Map<String, Object> result = new KubernetesDashboardLinkTool(httpClient)
            .execute(Map.of("reason", "排查集群资源"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("https://dashboard.example", result.get(AtlasToolResult.KEY_DATA));
        verify(httpClient).get(eq("/api/external-link/kubernetes/dashboard"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void externalLinkTools_shouldExposeFocusedParameterSchema() {
        Map<String, ToolParameterSpec> externalSpecs = new ExternalLinkListTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(externalSpecs.containsKey("category"));
        assertEquals(false, externalSpecs.get("category").required());

        Map<String, ToolParameterSpec> dashboardSpecs = new KubernetesDashboardLinkTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(dashboardSpecs.containsKey("reason"));
        assertEquals(false, dashboardSpecs.get("reason").required());
    }
}
