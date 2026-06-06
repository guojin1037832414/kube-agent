package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * MIG 配置只读 Tool 的 HTTP 契约测试。
 *
 * <p>成熟 kube-manager 只提供 {@code GET /api/mig/{gpuId}}，前端也必须先选中 GPU 规格后再查询 MIG 清单。
 * 因此本 Tool 不能继续伪装为可分页的全局列表，也不能接收 LLM 拼出的路径片段。</p>
 */
class MigConfigReadToolHttpContractTest {

    @Test
    void shouldCallReviewedMigEndpointByGpuIdOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/mig/42"), eq(Map.of())))
            .thenReturn(Map.of("result", List.of(Map.of(
                "gpuId", 42,
                "config", "1g.10gb",
                "memory", 10240
            ))));

        Map<String, Object> result = new MigConfigListTool(httpClient).execute(Map.of(
            "gpuId", "42",
            "page", "9",
            "limit", "999",
            "keyword", "probe",
            "organizationId", "999999"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/mig/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldRejectUnsafeGpuIdBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> result = new MigConfigListTool(httpClient).execute(Map.of(
            "gpuId", "../42"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_GPU_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/mig/../42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldExposeOnlyGpuIdParameterContract() {
        Map<String, ToolParameterSpec> specs = new MigConfigListTool(null).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("gpuId"));
        assertEquals("integer", specs.get("gpuId").type());
        assertTrue(specs.get("gpuId").required());
        assertTrue(specs.get("gpuId").aliases().containsAll(List.of("id", "gpu_id")));
        assertFalse(specs.containsKey("page"));
        assertFalse(specs.containsKey("limit"));
        assertFalse(specs.containsKey("keyword"));
    }

    @Test
    void shouldStayPlainReadButRequireAuthenticatedVisibility() {
        AtlasToolMapping mapping = MigConfigListTool.class.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = MigConfigListTool.class.getAnnotation(ToolPermission.class);

        assertEquals(AtlasToolMapping.OperationType.READ, mapping.operationType());
        assertFalse(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.AUTHENTICATED, permission.value());
    }
}
