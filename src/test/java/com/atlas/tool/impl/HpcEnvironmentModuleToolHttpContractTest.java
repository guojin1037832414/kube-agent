package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * HPC 环境与 Lmod module 只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100。创建/删除环境、安装包、安装或删除 module
 * 都会改变真实 HPC 软件栈，本批只验证 GET 读取能力。</p>
 */
class HpcEnvironmentModuleToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void hpcEnvironmentList_shouldUseTrustedOrgAndClusterPathOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/hpc-env/environments/98"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new HpcEnvironmentListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "clusterId", "98",
            "envName", "evil",
            "packages", java.util.List.of("curl")
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/hpc-env/environments/98"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcModuleList_shouldUseTrustedOrgAndWhitelistedClusterQueryOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/hpc-env/modules"), eq(Map.of("clusterId", "98"))))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new HpcModuleListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "clusterId", "98",
            "moduleName", "cuda",
            "version", "12.4",
            "packageName", "nccl"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/hpc-env/modules"), eq(Map.of("clusterId", "98")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcEnvironmentModuleTools_shouldRejectUnsafeClusterIdBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> badEnvironment = new HpcEnvironmentListTool(httpClient)
            .execute(Map.of("clusterId", "../98"));
        Map<String, Object> badModule = new HpcModuleListTool(httpClient)
            .execute(Map.of("clusterId", "98;module purge"));

        assertEquals(Boolean.FALSE, badEnvironment.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_HPC_ID", badEnvironment.get(AtlasToolResult.KEY_ERROR_CODE));
        assertEquals(Boolean.FALSE, badModule.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_HPC_ID", badModule.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq("/api/100001/hpc-env/environments/98"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcEnvironmentModuleTools_shouldBeSensitiveReadWithConfirmation() {
        assertSensitiveRead(HpcEnvironmentListTool.class);
        assertSensitiveRead(HpcModuleListTool.class);
    }

    private void assertSensitiveRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
