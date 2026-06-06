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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 镜像注册处 Tool 的 HTTP 契约测试。
 *
 * <p>成熟 kube-manager 中 {@code /api/registry} 管理镜像注册处认证配置，
 * {@code /api/{orgId}/repository} 则是组织内产品/应用镜像目录。二者语义不同，
 * Agent Tool 必须显式拆开，避免把站点级 registry 配置误当成普通组织列表。</p>
 */
class RegistrySiteToolHttpContractTest {

    @Test
    void shouldCallReviewedSiteRegistryEndpointWithKeyWordOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/registry"), eq(Map.of("keyWord", "harbor"))))
            .thenReturn(Map.of("result", List.of(Map.of(
                "id", 7,
                "displayName", "central harbor",
                "url", "harbor.example.com",
                "username", "robot-reader"
            ))));

        Map<String, Object> result = new RegistryListTool(httpClient).execute(Map.of(
            "organizationId", "100002",
            "page", "9",
            "limit", "999",
            "keyWord", "  harbor  "
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/registry"), eq(Map.of("keyWord", "harbor")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldAcceptKeywordAliasButNeverSendPagination() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/registry"), eq(Map.of("keyWord", "central"))))
            .thenReturn(Map.of("result", List.of()));

        Map<String, Object> result = new RegistryListTool(httpClient).execute(Map.of(
            "keyword", " central ",
            "page", "3",
            "limit", "25"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/registry"), eq(Map.of("keyWord", "central")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void shouldExposeOnlyKeyWordParameterContract() {
        Map<String, ToolParameterSpec> specs = new RegistryListTool(null).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("keyWord"));
        assertEquals("string", specs.get("keyWord").type());
        assertFalse(specs.get("keyWord").required());
        assertTrue(specs.get("keyWord").aliases().containsAll(List.of("keyword", "name", "search")));
        assertFalse(specs.containsKey("page"));
        assertFalse(specs.containsKey("limit"));
    }

    @Test
    void shouldStaySensitiveReadWithHumanConfirmation() {
        AtlasToolMapping mapping = RegistryListTool.class.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = RegistryListTool.class.getAnnotation(ToolPermission.class);

        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.PUBLIC, permission.value());
    }
}
