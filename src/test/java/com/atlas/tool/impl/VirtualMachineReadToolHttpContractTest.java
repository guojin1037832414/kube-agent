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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 虚拟机只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，验证 Agent 到 kube-manager 的路径、可信 orgId 和 fail-closed 行为；
 * 不访问用户已经启动在 8100 的真实后端，更不会触发 VM 创建、启动、停止或删除。</p>
 */
class VirtualMachineReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void virtualMachineList_shouldUseTrustedOrgAndIgnoreForgedOrgParams() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/virtual-machine"), anyMap())).thenReturn(Map.of(
            "result", List.of(Map.of("name", "vm-training-01"))
        ));

        VirtualMachineListTool tool = new VirtualMachineListTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "token", "fake"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/virtual-machine"), eq(Map.of()));
    }

    @Test
    void virtualMachineDetail_shouldEncodeNameAndUseTrustedOrg() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/virtual-machine/vm-training-01"), anyMap())).thenReturn(Map.of(
            "result", Map.of("name", "vm-training-01", "status", "Running")
        ));

        VirtualMachineDetailTool tool = new VirtualMachineDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
            "organizationId", "999999",
            "name", "vm-training-01"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/virtual-machine/vm-training-01"), eq(Map.of()));
    }

    @Test
    void virtualMachineDetail_shouldRejectUnsafePathBeforeHttpCall() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        VirtualMachineDetailTool tool = new VirtualMachineDetailTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of("name", "../vm-training-01"));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_VM_NAME", result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq("/api/100001/virtual-machine/../vm-training-01"), anyMap());
    }

    @Test
    void virtualMachineReadTools_shouldStayPlainReadWithoutConfirmation() {
        AtlasToolMapping listMapping = VirtualMachineListTool.class.getAnnotation(AtlasToolMapping.class);
        AtlasToolMapping detailMapping = VirtualMachineDetailTool.class.getAnnotation(AtlasToolMapping.class);

        assertEquals(AtlasToolMapping.OperationType.READ, listMapping.operationType());
        assertEquals(AtlasToolMapping.OperationType.READ, detailMapping.operationType());
        assertFalse(listMapping.requiresConfirmation());
        assertFalse(detailMapping.requiresConfirmation());
    }
}
