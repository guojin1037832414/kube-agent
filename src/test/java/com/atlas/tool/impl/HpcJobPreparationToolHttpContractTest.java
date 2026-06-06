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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * HPC 作业准备数据 Tool 的 HTTP 契约测试。
 *
 * <p>本测试全部使用 mock HTTP client，不访问真实 8100。HPC 作业提交、删除、重提会改变调度系统，
 * 不在本批只读准备数据范围内。</p>
 */
class HpcJobPreparationToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void hpcPartitionList_shouldUseTrustedOrgAndClusterIdOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/hpc-job/partition/98"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new HpcPartitionListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "clusterId", "98",
            "jobCommand", "sbatch evil.sh"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/hpc-job/partition/98"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcSbatchParameterList_shouldEncodeSafeCategoryAndIgnoreCommandFields() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/hpc-job/sbatch_parameter/Basic%20Job%20Information"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new HpcSbatchParameterListTool(httpClient).execute(Map.of(
            "category", " Basic Job Information ",
            "jobScript", "#!/bin/bash",
            "startCommand", "sbatch train.sh",
            "token", "forged"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/hpc-job/sbatch_parameter/Basic%20Job%20Information"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcPreparationTools_shouldFailClosedBeforeHttpForUnsafePathSegments() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> badCluster = new HpcPartitionListTool(httpClient)
            .execute(Map.of("clusterId", "../98"));
        Map<String, Object> badCategory = new HpcSbatchParameterListTool(httpClient)
            .execute(Map.of("category", "../Resource Allocation"));

        assertEquals(Boolean.FALSE, badCluster.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_HPC_ID", badCluster.get(AtlasToolResult.KEY_ERROR_CODE));
        assertEquals(Boolean.FALSE, badCategory.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_SBATCH_CATEGORY", badCategory.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq("/api/100001/hpc-job/partition/98"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void hpcPreparationTools_shouldBePlainReadWithoutConfirmation() {
        assertPlainRead(HpcPartitionListTool.class);
        assertPlainRead(HpcSbatchParameterListTool.class);
    }

    private void assertPlainRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.READ, mapping.operationType());
        assertFalse(mapping.requiresConfirmation());
    }
}
