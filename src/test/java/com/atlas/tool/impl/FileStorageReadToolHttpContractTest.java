package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 文件/存储准备上下文只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100。文件预览、下载、上传、编辑、复制移动、压缩解压、删除、
 * 存储申请/扩容/删除等会读取内容或改变文件系统/存储状态的接口均不在本批接入范围。</p>
 */
class FileStorageReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void noParamFileStorageReadTools_shouldUseTrustedOrgAndFixedEmptyQuery() {
        KubeManagerHttpClient volumeClient = mockGet("/api/100001/file/volume-path");
        assertFixedEmptyQuery(new FileVolumePathTool(volumeClient), volumeClient, "/api/100001/file/volume-path");

        KubeManagerHttpClient userVolumeClient = mockGet("/api/100001/file/volume-path/user");
        assertFixedEmptyQuery(new FileUserVolumePathTool(userVolumeClient), userVolumeClient,
            "/api/100001/file/volume-path/user");

        KubeManagerHttpClient userExtraClient = mockGet("/api/100001/file/volume-path/user-extra");
        assertFixedEmptyQuery(new FileUserExtraVolumePathTool(userExtraClient), userExtraClient,
            "/api/100001/file/volume-path/user-extra");

        KubeManagerHttpClient claimedOptionClient = mockGet("/api/100001/file/claimed-volume-option");
        assertFixedEmptyQuery(new FileClaimedVolumeOptionListTool(claimedOptionClient), claimedOptionClient,
            "/api/100001/file/claimed-volume-option");

        KubeManagerHttpClient storageOptionClient = mockGet("/api/100001/file/storage/option");
        assertFixedEmptyQuery(new FileStorageOptionTool(storageOptionClient), storageOptionClient,
            "/api/100001/file/storage/option");

        KubeManagerHttpClient trainStorageClient = mockGet("/api/100001/file/train-storage");
        assertFixedEmptyQuery(new FileTrainStorageTool(trainStorageClient), trainStorageClient,
            "/api/100001/file/train-storage");
    }

    @Test
    void fileStorageSelection_shouldUseTrustedOrgAndOnlyPassName() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/file/selectStorage"), eq(Map.of("name", "pvc-train"))))
            .thenReturn(Map.of("result", Map.of("name", "pvc-train")));

        Map<String, Object> result = new FileSelectStorageTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "name", " pvc-train ",
            "scope", "org",
            "userId", "42",
            "keyword", "secret"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/file/selectStorage"), eq(Map.of("name", "pvc-train")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void fileStorageReadTools_shouldBeSensitiveReadWithConfirmation() {
        assertSensitiveRead(FileVolumePathTool.class);
        assertSensitiveRead(FileUserVolumePathTool.class);
        assertSensitiveRead(FileUserExtraVolumePathTool.class);
        assertSensitiveRead(FileClaimedVolumeOptionListTool.class);
        assertSensitiveRead(FileStorageOptionTool.class);
        assertSensitiveRead(FileSelectStorageTool.class);
        assertSensitiveRead(FileTrainStorageTool.class);
    }

    private KubeManagerHttpClient mockGet(String path) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(path), eq(Map.of()))).thenReturn(Map.of("result", List.of()));
        return httpClient;
    }

    private void assertFixedEmptyQuery(BaseTool tool, KubeManagerHttpClient httpClient, String path) {
        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "path", "/forged",
            "namespace", "kube-system",
            "keyword", "secret"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(path), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    private void assertSensitiveRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
