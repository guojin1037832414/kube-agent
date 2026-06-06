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
 * 下载任务状态 Tool 的 HTTP 契约测试。
 *
 * <p>成熟 kube-manager 将列表、状态、进度分成三个接口：
 * {@code GET /download}、{@code GET /download/status/{id}}、{@code GET /download/progress/{id}}。
 * 本测试锁定 {@code upload_status_list} 的历史 Tool 名只负责“按任务 ID 查状态”，
 * 防止它继续误导 LLM 构造不存在的分页状态列表。</p>
 */
class DownloadTaskStatusToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100002");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void shouldCallReviewedDownloadStatusEndpointByTaskIdOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/download/status/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of(
                "id", 42,
                "status", "running",
                "downloadedSize", 128L
            )));

        Map<String, Object> result = new UploadStatusListTool(httpClient).execute(Map.of(
            "id", "42",
            "page", "9",
            "limit", "999",
            "keyword", "probe"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/download/status/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldRejectUnsafeTaskIdBeforeCallingHttpClient() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> result = new UploadStatusListTool(httpClient).execute(Map.of(
            "id", "../42"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_DOWNLOAD_TASK_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100002/download/status/../42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldExposeOnlyTaskIdParameterContract() {
        Map<String, ToolParameterSpec> specs = new UploadStatusListTool(null).getParameterSpecs().stream()
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
        AtlasToolMapping mapping = UploadStatusListTool.class.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = UploadStatusListTool.class.getAnnotation(ToolPermission.class);

        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.PUBLIC, permission.value());
    }
}
