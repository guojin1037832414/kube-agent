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
 * 组织内产品/应用镜像目录 Tool 的 HTTP 契约测试。
 *
 * <p>本批区分三个容易混淆的概念：站点级 registry 配置、组织普通镜像仓库、产品/应用镜像目录。
 * 当前测试只覆盖 mature kube-manager 的 {@code /api/{orgId}/repository} 系列敏感只读能力，
 * 不访问真实 8100，也不接入镜像拉取、重试或部署创建。</p>
 */
class RepositoryCatalogToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100002");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void catalogList_shouldUseMatureRepositoryEndpointAndReviewedQueryFields() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository"), eq(Map.of(
            "page", "2",
            "limit", "12",
            "displayName", "llama",
            "status", "Ready",
            "industryCategory", "Reasoning",
            "aieSupported", true,
            "aieEssential", false,
            "isOneClickDeploy", true
        )))).thenReturn(Map.of("result", List.of(Map.of(
            "resourceId", "nvidia/nim/llama",
            "displayName", "Llama NIM",
            "latestTagStatus", "Ready"
        ))));

        Map<String, Object> result = new RepositoryCatalogListTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "page", "2",
            "limit", "12",
            "displayName", " llama ",
            "status", " Ready ",
            "industryCategory", " Reasoning ",
            "aieSupported", "true",
            "aieEssential", "0",
            "isOneClickDeploy", 1,
            "keyword", "ignored-alias-without-normalizer"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/repository"), eq(Map.of(
            "page", "2",
            "limit", "12",
            "displayName", "llama",
            "status", "Ready",
            "industryCategory", "Reasoning",
            "aieSupported", true,
            "aieEssential", false,
            "isOneClickDeploy", true
        )));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void catalogCategory_shouldCallMatureCategoryEndpointWithoutQuery() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository/category"), eq(Map.of())))
            .thenReturn(Map.of("result", List.of(Map.of("category", "Reasoning"))));

        Map<String, Object> result = new RepositoryCatalogCategoryListTool(httpClient).execute(Map.of(
            "page", "9",
            "limit", "100"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/repository/category"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void normalTagAndNimTag_shouldRequireRepositoryAndCallSeparateReviewedEndpoints() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository/tags"), eq(Map.of("repository", "nvidia/cuda"))))
            .thenReturn(Map.of("result", List.of(Map.of("tag", "12.4", "status", "Ready"))));
        when(httpClient.get(eq("/api/100002/repository/nim/tags"), eq(Map.of("repository", "nvidia/nim/llama"))))
            .thenReturn(Map.of("result", List.of(Map.of("tag", "latest", "status", "Ready"))));

        Map<String, Object> normalResult = new RepositoryCatalogTagListTool(httpClient).execute(Map.of(
            "repository", " nvidia/cuda ",
            "page", "99",
            "limit", "999"
        ));
        Map<String, Object> nimResult = new RepositoryCatalogNimTagListTool(httpClient).execute(Map.of(
            "repository", "nvidia/nim/llama"
        ));

        assertEquals(Boolean.TRUE, normalResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.TRUE, nimResult.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/repository/tags"), eq(Map.of("repository", "nvidia/cuda")));
        verify(httpClient).get(eq("/api/100002/repository/nim/tags"), eq(Map.of("repository", "nvidia/nim/llama")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void tagTools_shouldFailClosedForMissingOrUnsafeRepositoryBeforeHttp() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> missing = new RepositoryCatalogTagListTool(httpClient).execute(Map.of());
        Map<String, Object> unsafe = new RepositoryCatalogNimTagListTool(httpClient).execute(Map.of(
            "repository", "nvidia/cuda?token=secret"
        ));

        assertEquals(Boolean.FALSE, missing.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("MISSING_REQUIRED_PARAMS", missing.get(AtlasToolResult.KEY_ERROR_CODE));
        assertEquals(Boolean.FALSE, unsafe.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_REPOSITORY", unsafe.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq("/api/100002/repository/tags"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void catalogList_shouldRejectAmbiguousBooleanValuesBeforeHttp() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> result = new RepositoryCatalogListTool(httpClient).execute(Map.of(
            "aieSupported", "1.5"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("TYPE_MISMATCH", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq("/api/100002/repository"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void catalogList_shouldExposeReviewedParameterContract() {
        Map<String, ToolParameterSpec> specs = new RepositoryCatalogListTool(null).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.keySet().containsAll(List.of(
            "page", "limit", "displayName", "status", "industryCategory",
            "aieSupported", "aieEssential", "isOneClickDeploy"
        )));
        assertEquals("boolean", specs.get("isOneClickDeploy").type());
        assertFalse(specs.get("displayName").required());
        assertTrue(specs.get("displayName").aliases().contains("keyword"));
        assertFalse(specs.containsKey("keyWord"));
    }

    @Test
    void tagTools_shouldExposeOnlyRepositoryParameterContract() {
        assertRepositoryOnly(new RepositoryCatalogTagListTool(null));
        assertRepositoryOnly(new RepositoryCatalogNimTagListTool(null));
    }

    @Test
    void repositoryCatalogTools_shouldStaySensitiveReadWithHumanConfirmation() {
        assertSensitiveRead(RepositoryCatalogListTool.class);
        assertSensitiveRead(RepositoryCatalogCategoryListTool.class);
        assertSensitiveRead(RepositoryCatalogTagListTool.class);
        assertSensitiveRead(RepositoryCatalogNimTagListTool.class);
    }

    private void assertRepositoryOnly(com.atlas.tool.core.BaseTool tool) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("repository"));
        assertEquals("string", specs.get("repository").type());
        assertTrue(specs.get("repository").required());
        assertTrue(specs.get("repository").aliases().containsAll(List.of("resourceId", "repo")));
        assertFalse(specs.containsKey("page"));
        assertFalse(specs.containsKey("limit"));
        assertFalse(specs.containsKey("keyword"));
    }

    private void assertSensitiveRead(Class<?> toolClass) {
        AtlasToolMapping mapping = toolClass.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = toolClass.getAnnotation(ToolPermission.class);

        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.AUTHENTICATED, permission.value());
    }
}
