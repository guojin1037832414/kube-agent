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
 * NIM 部署预检 Tool 的 HTTP 契约测试。
 *
 * <p>本测试锁定 mature 前端一键部署的前三段只读链路：目录、NIM tag、NIM 模板。
 * 预检 Tool 不允许调用 deployment POST，也不访问真实 8100。</p>
 */
class NimDeploymentPreflightToolHttpContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100002");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void preflight_shouldReadCatalogNimTagsAndTemplateWithoutCreatingDeployment() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "displayName", "llama",
            "industryCategory", "Reasoning",
            "isOneClickDeploy", true
        )))).thenReturn(Map.of("result", List.of(Map.of(
            "resourceId", "nvidia/nim/llama",
            "name", "llama"
        ))));
        when(httpClient.get(eq("/api/100002/repository/nim/tags"), eq(Map.of(
            "repository", "nvidia/nim/llama"
        )))).thenReturn(Map.of("result", List.of(Map.of(
            "repository", "nvcr.io/nim/llama",
            "tag", "1.0.0",
            "status", "Ready"
        ))));
        when(httpClient.get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/llama:1.0.0",
            "templateType", "NIM"
        )))).thenReturn(Map.of("result", List.of(Map.of(
            "id", 42,
            "templateType", "NIM",
            "cpuLimits", 4000,
            "memLimits", 16384,
            "gpuModel", "A100"
        ))));

        Map<String, Object> result = new NimDeploymentPreflightTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "displayName", " llama ",
            "industryCategory", " Reasoning ",
            "serviceName", "llama-service"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals(true, data.get("preflightOnly"));
        assertEquals("NONE", data.get("sideEffect"));
        assertEquals("nvcr.io/nim/llama:1.0.0", data.get("selectedImage"));
        assertTrue(data.containsKey("nextRequiredConfirmation"));
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentBodyPreview = (Map<String, Object>) data.get("deploymentBodyPreview");
        assertEquals(false, deploymentBodyPreview.get("safeToPost"));
        assertEquals(false, deploymentBodyPreview.get("bodyComplete"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDraft = (Map<String, Object>) deploymentBodyPreview.get("bodyDraft");
        assertEquals("llama-service", bodyDraft.get("name"));
        assertEquals("llama-service", bodyDraft.get("displayName"));
        assertEquals("nvcr.io/nim/llama:1.0.0", bodyDraft.get("image"));
        assertEquals(4000, bodyDraft.get("cpuLimits"));
        assertEquals(16384, bodyDraft.get("memLimits"));
        assertEquals("A100", bodyDraft.get("gpuSpec"));
        assertFalse(bodyDraft.containsKey("gpuModel"));

        verify(httpClient).get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "displayName", "llama",
            "industryCategory", "Reasoning",
            "isOneClickDeploy", true
        )));
        verify(httpClient).get(eq("/api/100002/repository/nim/tags"), eq(Map.of("repository", "nvidia/nim/llama")));
        verify(httpClient).get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/llama:1.0.0",
            "templateType", "NIM"
        )));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void preflight_shouldUseExplicitRepositoryAndRequestedTag() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "isOneClickDeploy", true
        )))).thenReturn(Map.of("result", List.of()));
        when(httpClient.get(eq("/api/100002/repository/nim/tags"), eq(Map.of(
            "repository", "nvidia/nim/mistral"
        )))).thenReturn(Map.of("result", List.of(
            Map.of("repository", "nvcr.io/nim/mistral", "tag", "old", "status", "Ready"),
            Map.of("repository", "nvcr.io/nim/mistral", "tag", "2.0.0", "status", "Ready")
        )));
        when(httpClient.get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/mistral:2.0.0",
            "templateType", "NIM"
        )))).thenReturn(Map.of("result", List.of(Map.of("id", 77, "templateType", "NIM"))));

        Map<String, Object> result = new NimDeploymentPreflightTool(httpClient).execute(Map.of(
            "repository", "nvidia/nim/mistral",
            "tag", "2.0.0"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals("nvcr.io/nim/mistral:2.0.0", data.get("selectedImage"));
        verify(httpClient).get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "isOneClickDeploy", true
        )));
        verify(httpClient).get(eq("/api/100002/repository/nim/tags"), eq(Map.of("repository", "nvidia/nim/mistral")));
        verify(httpClient).get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/mistral:2.0.0",
            "templateType", "NIM"
        )));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void preflight_shouldFailClosedWhenRepositoryIsUnsafeBeforeHttp() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);

        Map<String, Object> result = new NimDeploymentPreflightTool(httpClient).execute(Map.of(
            "repository", "nvidia/nim/llama?token=secret"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_REPOSITORY", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq("/api/100002/repository"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void preflight_shouldFailWhenNoTemplateMatchesImage() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "isOneClickDeploy", true
        )))).thenReturn(Map.of("result", List.of(Map.of("resourceId", "nvidia/nim/llama"))));
        when(httpClient.get(eq("/api/100002/repository/nim/tags"), eq(Map.of(
            "repository", "nvidia/nim/llama"
        )))).thenReturn(Map.of("result", List.of(Map.of(
            "repository", "nvcr.io/nim/llama",
            "tag", "1.0.0"
        ))));
        when(httpClient.get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/llama:1.0.0",
            "templateType", "NIM"
        )))).thenReturn(Map.of("result", List.of()));

        Map<String, Object> result = new NimDeploymentPreflightTool(httpClient).execute(Map.of());

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("NIM_TEMPLATE_NOT_FOUND", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient).get(eq("/api/100002/repository"), eq(Map.of(
            "page", "1",
            "limit", "20",
            "isOneClickDeploy", true
        )));
        verify(httpClient).get(eq("/api/100002/repository/nim/tags"), eq(Map.of("repository", "nvidia/nim/llama")));
        verify(httpClient).get(eq("/api/100002/template"), eq(Map.of(
            "image", "nvcr.io/nim/llama:1.0.0",
            "templateType", "NIM"
        )));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void preflight_shouldExposePlanningSchemaAndSensitiveReadMetadata() {
        Map<String, ToolParameterSpec> specs = new NimDeploymentPreflightTool(null).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.keySet().containsAll(List.of(
            "repository", "displayName", "industryCategory", "tag", "serviceName"
        )));
        assertFalse(specs.get("repository").required());
        assertTrue(specs.get("repository").aliases().contains("resourceId"));
        assertTrue(specs.get("displayName").aliases().contains("model"));

        AtlasToolMapping mapping = NimDeploymentPreflightTool.class.getAnnotation(AtlasToolMapping.class);
        ToolPermission permission = NimDeploymentPreflightTool.class.getAnnotation(ToolPermission.class);
        assertEquals(AtlasToolMapping.OperationType.SENSITIVE_READ, mapping.operationType());
        assertTrue(mapping.requiresConfirmation());
        assertEquals(ToolPermission.Policy.AUTHENTICATED, permission.value());
    }
}
