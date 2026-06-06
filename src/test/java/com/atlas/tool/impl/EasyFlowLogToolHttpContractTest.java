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
 * EasyFlow 日志分析 Tool 的 HTTP 契约测试。
 *
 * <p>这些接口只用 mock 锁定 kube-manager 真实路径，不触碰用户已经启动的 8100 线上后端。</p>
 */
class EasyFlowLogToolHttpContractTest {

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
    void instanceList_shouldPassPaginationAndKeywordToReviewedEndpoint() {
        when(httpClient.get(eq("/api/100001/easy-flow/instance"), eq(Map.of(
            "page", "2",
            "limit", "20",
            "keyword", "train flow"
        )))).thenReturn(Map.of("result", List.of(Map.of("id", 7))));

        Map<String, Object> result = new EasyFlowInstanceListTool(httpClient)
            .execute(Map.of("page", "2", "limit", "20", "keyword", " train flow ", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/instance"), eq(Map.of(
            "page", "2",
            "limit", "20",
            "keyword", "train flow"
        )));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceDetail_shouldUseTrustedOrgAndPathVariable() {
        when(httpClient.get(eq("/api/100001/easy-flow/instance/7")))
            .thenReturn(Map.of("result", Map.of("id", 7)));

        Map<String, Object> result = new EasyFlowInstanceDetailTool(httpClient)
            .execute(Map.of("instanceId", "7", "orgId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/instance/7"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceDetail_shouldRejectUnsafePathSegment() {
        Map<String, Object> result = new EasyFlowInstanceDetailTool(httpClient)
            .execute(Map.of("instanceId", "../7"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void analyzerList_shouldUseReviewedEndpoint() {
        when(httpClient.get(eq("/api/100001/easy-flow/analyzer")))
            .thenReturn(Map.of("result", Map.of("TF_TRAIN", "TensorFlow 训练")));

        Map<String, Object> result = new EasyFlowAnalyzerListTool(httpClient).execute(Map.of());

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/analyzer"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceLog_shouldPassOnlySupportedLogQueryParams() {
        Map<String, Object> expectedQuery = new LinkedHashMap<>();
        expectedQuery.put("limitBytes", 20000);
        expectedQuery.put("tailLines", 100);
        expectedQuery.put("timestamps", true);
        when(httpClient.get(eq("/api/100001/easy-flow/instance/7/log/train"), eq(expectedQuery)))
            .thenReturn(Map.of("result", "epoch=1 loss=0.2"));

        Map<String, Object> result = new EasyFlowInstanceLogTool(httpClient).execute(Map.of(
            "instanceId", "7",
            "stageCode", "train",
            "limitBytes", "20000",
            "tailLines", "100",
            "timestamps", "true",
            "ignored", "should-not-pass-through"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/instance/7/log/train"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceLog_shouldRejectUnsafeStageCodePathSegment() {
        Map<String, Object> result = new EasyFlowInstanceLogTool(httpClient).execute(Map.of(
            "instanceId", "7",
            "stageCode", "../train"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceLogList_shouldCallListEndpointWithSupportedQueryParams() {
        Map<String, Object> expectedQuery = new LinkedHashMap<>();
        expectedQuery.put("sinceSeconds", 3600);
        expectedQuery.put("tailLines", 50);
        when(httpClient.get(eq("/api/100001/easy-flow/instance/7/log/train/list"), eq(expectedQuery)))
            .thenReturn(Map.of("result", List.of()));

        Map<String, Object> result = new EasyFlowInstanceLogListTool(httpClient).execute(Map.of(
            "instanceId", "7",
            "stageCode", "train",
            "sinceSeconds", "3600",
            "tailLines", "50"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/instance/7/log/train/list"), eq(expectedQuery));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void instanceLogAbstract_shouldTrimAnalyzerCodeAndCallAbstractEndpoint() {
        when(httpClient.get(eq("/api/100001/easy-flow/instance/7/log/train/abstract"), eq(Map.of("analyzerCode", "TF_TRAIN"))))
            .thenReturn(Map.of("result", Map.of("analyzerCode", "TF_TRAIN")));

        Map<String, Object> result = new EasyFlowInstanceLogAbstractTool(httpClient).execute(Map.of(
            "instanceId", "7",
            "stageCode", "train",
            "analyzerCode", " TF_TRAIN "
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/easy-flow/instance/7/log/train/abstract"), eq(Map.of("analyzerCode", "TF_TRAIN")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void easyFlowLogTools_shouldExposeFocusedParameterSchema() {
        Map<String, ToolParameterSpec> logSpecs = new EasyFlowInstanceLogTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(logSpecs.containsKey("instanceId"));
        assertTrue(logSpecs.containsKey("stageCode"));
        assertTrue(logSpecs.containsKey("tailLines"));
        assertTrue(logSpecs.get("instanceId").required());
        assertTrue(logSpecs.get("stageCode").required());

        Map<String, ToolParameterSpec> abstractSpecs = new EasyFlowInstanceLogAbstractTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
        assertTrue(abstractSpecs.containsKey("analyzerCode"));
        assertEquals(false, abstractSpecs.get("analyzerCode").required());
    }
}
