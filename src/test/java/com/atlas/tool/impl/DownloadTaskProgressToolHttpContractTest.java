package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 下载任务进度 Tool 的 HTTP 契约测试。
 *
 * <p>成熟 kube-manager 的进度读取返回实时百分比、状态、已下载大小和总大小。
 * 这些信息虽然只读，但仍可能暴露文件任务上下文，因此保持敏感读取与人工确认。</p>
 */
class DownloadTaskProgressToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100002");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void shouldCallReviewedDownloadProgressEndpointByTaskIdOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/download/progress/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of(
                "taskId", 42,
                "progress", 37.5D,
                "status", "running",
                "downloaded", 384L,
                "totalSize", 1024L
            )));

        Map<String, Object> result = new DownloadTaskProgressTool(httpClient).execute(Map.of(
            "id", "42",
            "page", "9",
            "limit", "999",
            "keyword", "probe"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/download/progress/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldRejectUnsafeTaskIdBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> result = new DownloadTaskProgressTool(httpClient).execute(Map.of(
            "id", "42/extra"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_DOWNLOAD_TASK_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100002/download/progress/42/extra"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldExposeOnlyTaskIdParameterContract() {
        Map<String, ToolParameterSpec> specs = new DownloadTaskProgressTool(null).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("id"));
        assertEquals("integer", specs.get("id").type());
        assertTrue(specs.get("id").required());
        assertTrue(specs.get("id").aliases().containsAll(List.of("taskId", "task_id")));
        assertFalse(specs.containsKey("page"));
        assertFalse(specs.containsKey("limit"));
        assertFalse(specs.containsKey("keyword"));
    }

    @Test
    void shouldStaySensitiveReadWithHumanConfirmation() {
        AtlasToolMapping mapping = DownloadTaskProgressTool.class.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = DownloadTaskProgressTool.class.getAnnotation(ToolPermission.class);

        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.PUBLIC, permission.value());
    }
}
