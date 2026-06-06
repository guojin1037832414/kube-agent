package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TensorBoard 只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100，也不创建、更新或删除 TensorBoard。</p>
 */
class TensorBoardReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void tensorBoardList_shouldUseTrustedOrgAndPassAllowedListQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/tensorboard"), anyMap())).thenReturn(Map.of(
            "result", Map.of("items", List.of())
        ));

        TensorBoardListTool tool = new TensorBoardListTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "page", 2,
            "limit", 20,
            "keyword", "resnet"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/tensorboard"), eq(Map.of(
            "page", "2",
            "limit", "20",
            "keyword", "resnet"
        )));
    }

    @Test
    void tensorBoardEnvironment_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/tensorboard/data/environment"), anyMap())).thenReturn(Map.of(
            "result", Map.of("status", "READY")
        ));

        TensorBoardEnvironmentTool tool = new TensorBoardEnvironmentTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of("organizationId", "999999"));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/tensorboard/data/environment"), eq(Map.of()));
    }

    @Test
    void tensorBoardRuns_shouldUseTrustedOrgAndFixedPath() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/tensorboard/data/runs"), anyMap())).thenReturn(Map.of(
            "result", List.of("train-1")
        ));

        TensorBoardRunsTool tool = new TensorBoardRunsTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of("orgId", "888888"));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/tensorboard/data/runs"), eq(Map.of()));
    }

    @Test
    void trainJobTensorBoardRuns_shouldUseTrustedOrgAndValidatedPathId() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/tensorboard/trainjob-runs/42"), anyMap())).thenReturn(Map.of(
            "result", List.of("epoch-1")
        ));

        TrainJobTensorBoardRunsTool tool = new TrainJobTensorBoardRunsTool(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "tensorBoardDeploymentId", "42"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/tensorboard/trainjob-runs/42"), eq(Map.of()));
    }

    @Test
    void trainJobTensorBoardRuns_shouldRejectUnsafePathIdBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        TrainJobTensorBoardRunsTool tool = new TrainJobTensorBoardRunsTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("tensorBoardDeploymentId", "42/extra"));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_TENSORBOARD_DEPLOYMENT_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100001/tensorboard/trainjob-runs/42/extra"), anyMap());
    }

    @Test
    void tensorBoardReadTools_shouldBeSensitiveReadWithConfirmation() {
        assertSensitive(TensorBoardListTool.class.getAnnotation(AtlasToolMapping.class));
        assertSensitive(TensorBoardEnvironmentTool.class.getAnnotation(AtlasToolMapping.class));
        assertSensitive(TensorBoardRunsTool.class.getAnnotation(AtlasToolMapping.class));
        assertSensitive(TrainJobTensorBoardRunsTool.class.getAnnotation(AtlasToolMapping.class));
    }

    private static void assertSensitive(AtlasToolMapping mapping) {
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
