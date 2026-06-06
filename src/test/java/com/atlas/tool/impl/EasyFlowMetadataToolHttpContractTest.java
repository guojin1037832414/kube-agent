package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * EasyFlow 流程/阶段元数据 Tool 的 HTTP 契约测试。
 *
 * <p>只使用 mock 客户端，不触碰用户线上 kube-manager；这些元数据用于增强 AI 日志分析上下文。</p>
 */
class EasyFlowMetadataToolHttpContractTest {

    private KubeManagerHttpClient httpClient;

    @BeforeEach
    void setUpTrustedContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        httpClient = mock(KubeManagerHttpClient.class);
    }

    @AfterEach
    void tearDownTrustedContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
        UserPermissionContext.CURRENT_TOKEN.remove();
    }

    @Test
    void flowList_shouldPassReviewedFlowQueryOnly() {
        Map<String, Object> expectedQuery = new LinkedHashMap<>();
        expectedQuery.put("page", "2");
        expectedQuery.put("limit", "20");
        expectedQuery.put("flowId", "7");
        expectedQuery.put("type", "train");
        expectedQuery.put("description", "gpu flow");
        when(httpClient.get(eq("/api/100001/easy-flow/flow"), eq(expectedQuery)))
            .thenReturn(Map.of("result", List.of(Map.of("id", 7))));

        Map<String, Object> result = new EasyFlowFlowListTool(httpClient).execute(Map.of(
            "page", "2",
            "limit", "20",
            "flowId", " 7 ",
            "type", " train ",
            "description", " gpu flow ",
            "organizationId", "100002",
            "token", "should-not-pass-through"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/flow"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void flowDetail_shouldUseTrustedOrgAndSafeFlowId() {
        when(httpClient.get(eq("/api/100001/easy-flow/flow/7")))
            .thenReturn(Map.of("result", Map.of("id", 7)));

        Map<String, Object> result = new EasyFlowFlowDetailTool(httpClient)
            .execute(Map.of("flowId", "7", "orgId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/flow/7"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void flowList_shouldRejectNonNumericFlowIdFilter() {
        Map<String, Object> result = new EasyFlowFlowListTool(httpClient)
            .execute(Map.of("flowId", "abc"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void flowDetail_shouldRejectUnsafeFlowId() {
        Map<String, Object> result = new EasyFlowFlowDetailTool(httpClient)
            .execute(Map.of("flowId", "../7"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void stageList_shouldUseReviewedEndpoint() {
        when(httpClient.get(eq("/api/100001/easy-flow/flow/7/stage")))
            .thenReturn(Map.of("result", List.of(Map.of("id", 11, "code", "train"))));

        Map<String, Object> result = new EasyFlowStageListTool(httpClient)
            .execute(Map.of("flowId", "7"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/flow/7/stage"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void stageDetail_shouldUseReviewedEndpointAndRejectInjectedOrg() {
        when(httpClient.get(eq("/api/100001/easy-flow/flow/7/stage/11")))
            .thenReturn(Map.of("result", Map.of("id", 11, "code", "train")));

        Map<String, Object> result = new EasyFlowStageDetailTool(httpClient)
            .execute(Map.of("flowId", "7", "stageId", "11", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/flow/7/stage/11"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void stageDetail_shouldRejectUnsafeStageId() {
        Map<String, Object> result = new EasyFlowStageDetailTool(httpClient)
            .execute(Map.of("flowId", "7", "stageId", "11/extra"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void metadataTools_shouldExposeFocusedParameterSchema() {
        Map<String, ToolParameterSpec> flowListSpecs = new EasyFlowFlowListTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(flowListSpecs.containsKey("page"));
        assertTrue(flowListSpecs.containsKey("limit"));
        assertTrue(flowListSpecs.containsKey("flowId"));
        assertTrue(flowListSpecs.containsKey("type"));
        assertTrue(flowListSpecs.containsKey("description"));

        Map<String, ToolParameterSpec> stageDetailSpecs = new EasyFlowStageDetailTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(stageDetailSpecs.get("flowId").required());
        assertTrue(stageDetailSpecs.get("stageId").required());
    }
}
