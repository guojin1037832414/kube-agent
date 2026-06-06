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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * BCM 用户与节点分配只读 Tool 的 HTTP 契约测试。
 *
 * <p>全部使用 mock HTTP client，不访问真实 8100。创建 Slurm/BareMetal、切换 SSH/Sudo、
 * 站点管理员跨组织查询等操作均不在本批接入范围。</p>
 */
class BcmAllocationReadToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void bcmUserList_shouldUseTrustedOrgAndFixedEmptyQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/bcm/users"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new BcmUserListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "keyword", "admin",
            "assignedUserIds", java.util.List.of(1, 2)
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/bcm/users"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void bcmSlurmNodeAllocationList_shouldUseTrustedOrgAndFixedEmptyQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/bcm/all-slurm-nodes"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new BcmSlurmNodeAllocationListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "nodeName", "gpu-node-01",
            "loginNode", Map.of("name", "forged"),
            "workNode", java.util.List.of()
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/bcm/all-slurm-nodes"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void bcmBareMetalNodeAllocationList_shouldUseTrustedOrgAndFixedEmptyQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100001/bcm/all-bare-metal-nodes"), eq(Map.of())))
            .thenReturn(Map.of("result", java.util.List.of()));

        Map<String, Object> result = new BcmBareMetalNodeAllocationListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "keyword", "baremetal",
            "nodeType", "login",
            "sudo", true
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/bcm/all-bare-metal-nodes"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void bcmAllocationReadTools_shouldBeSensitiveReadWithConfirmation() {
        assertSensitiveRead(BcmUserListTool.class);
        assertSensitiveRead(BcmSlurmNodeAllocationListTool.class);
        assertSensitiveRead(BcmBareMetalNodeAllocationListTool.class);
    }

    private void assertSensitiveRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
    }
}
