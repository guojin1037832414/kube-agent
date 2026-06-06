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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 资源余量与公开监控指标 Tool 的 HTTP 合约测试。
 *
 * <p>全部使用 mock HTTP 客户端，不访问真实 8100，避免对线上监控和资源系统造成扰动。</p>
 */
class MetricAndResourceAnalysisToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void nodeRemainingResource_shouldUseTrustedOrgAndNoCallerQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/node/remaining"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("cpu", "8", "memory", "64Gi")));

        NodeRemainingResourceTool tool = new NodeRemainingResourceTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "page", "9",
            "limit", "999",
            "token", "fake-token"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/node/remaining"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void publicMetricTools_shouldCallReviewedEndpointsWithoutCallerQuery() {
        assertMetricCall(MetricGpuServerInstantTool::new, "/api/public/metric/prometheus/instant/server/gpu");
        assertMetricCall(MetricCpuServerInstantTool::new, "/api/public/metric/prometheus/instant/server/cpu");
        assertMetricCall(MetricStorageServerInstantTool::new, "/api/public/metric/prometheus/instant/server/storage");
        assertMetricCall(MetricPodInstantTool::new, "/api/public/metric/prometheus/instant/pod");
    }

    @Test
    void metricAndResourceTools_shouldBePlainReadWithoutConfirmation() {
        assertPlainRead(NodeRemainingResourceTool.class);
        assertPlainRead(MetricGpuServerInstantTool.class);
        assertPlainRead(MetricCpuServerInstantTool.class);
        assertPlainRead(MetricStorageServerInstantTool.class);
        assertPlainRead(MetricPodInstantTool.class);
    }

    private void assertMetricCall(Function<KubeManagerHttpClient, BaseTool> factory, String expectedPath) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("status", "ok")));

        BaseTool tool = factory.apply(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "metricType", "unsafe",
            "query", "up",
            "page", "9",
            "limit", "999"
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
