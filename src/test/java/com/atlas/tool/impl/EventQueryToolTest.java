package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EventQueryTool 单元测试。
 *
 * <p>本测试锁定 event_query 的小样本边界：只基于 kube-manager Pod 列表 warning 字段
 * 生成 Pod Warning 摘要，不暴露完整 Kubernetes EventList 语义。</p>
 */
class EventQueryToolTest {

    private final UserPermissionContext userPermissionContext = new UserPermissionContext();

    @AfterEach
    void clearContext() {
        userPermissionContext.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_shouldQueryPodWarningsAndApplyLocalFilters() {
        userPermissionContext.bind("test-token", "100002");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        EventQueryTool tool = new EventQueryTool(httpClient);

        when(httpClient.get(eq("/api/100002/pod"), anyMap())).thenReturn(Map.of(
            "result", Map.of(
                "records", List.of(
                    Map.of(
                        "name", "nginx-pod-1",
                        "namespace", "ns100002",
                        "status", "Pending",
                        "username", "zhaotiandi",
                        "warning", "FailedScheduling: insufficient cpu"
                    ),
                    Map.of(
                        "name", "normal-pod",
                        "namespace", "ns100002",
                        "status", "Running",
                        "username", "zhaotiandi",
                        "warning", ""
                    ),
                    Map.of(
                        "name", "redis-pod",
                        "namespace", "ns100002",
                        "status", "Pending",
                        "username", "zhaotiandi",
                        "warning", "BackOff pulling image"
                    )
                )
            )
        ));

        Map<String, Object> result = tool.execute(Map.of(
            "namespace", "ns100002",
            "status", "Pending",
            "reason", "FailedScheduling"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals("podWarningSummaries", data.get("dataKind"));
        assertEquals(1, data.get("count"));
        assertTrue(data.get("limitations").toString().contains("不是完整 Kubernetes EventList"));

        List<Map<String, Object>> summaries = (List<Map<String, Object>>) data.get("podWarningSummaries");
        assertEquals("nginx-pod-1", summaries.get(0).get("podName"));
        assertEquals("FailedScheduling: insufficient cpu", summaries.get(0).get("warning"));

        verify(httpClient).get(eq("/api/100002/pod"), eq(Map.of(
            "page", "1",
            "limit", "100",
            "namespace", "ns100002",
            "status", "Pending"
        )));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_shouldReturnEmptySummariesForNonListResponseAndBlankWarnings() {
        userPermissionContext.bind("test-token", "100002");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        EventQueryTool tool = new EventQueryTool(httpClient);

        when(httpClient.get(eq("/api/100002/pod"), anyMap())).thenReturn(Map.of(
            "result", Map.of("total", 0)
        ));

        Map<String, Object> result = tool.execute(Map.of(
            "namespace", "ns100002",
            "podName", "   ",
            "keyword", "   "
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals(0, data.get("count"));
        assertTrue(((List<?>) data.get("podWarningSummaries")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_shouldApplyCaseInsensitivePodNameAndKeywordFilters() {
        userPermissionContext.bind("test-token", "100002");
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        EventQueryTool tool = new EventQueryTool(httpClient);

        when(httpClient.get(eq("/api/100002/pod"), anyMap())).thenReturn(Map.of(
            "result", List.of(
                Map.of(
                    "podName", "Nginx-Pod-1",
                    "nameSpace", "ns100002",
                    "phase", "Pending",
                    "userName", "zhaotiandi",
                    "warnings", "ImagePullBackOff: pull image failed"
                ),
                Map.of(
                    "podName", "other-pod",
                    "nameSpace", "ns100002",
                    "phase", "Pending",
                    "userName", "zhaotiandi",
                    "warnings", "FailedScheduling: insufficient cpu"
                )
            )
        ));

        Map<String, Object> result = tool.execute(Map.of(
            "podName", "nginx",
            "keyword", "imagepullbackoff"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        assertEquals(1, data.get("count"));
        List<Map<String, Object>> summaries = (List<Map<String, Object>>) data.get("podWarningSummaries");
        assertEquals("Nginx-Pod-1", summaries.get(0).get("podName"));
        assertEquals("ImagePullBackOff: pull image failed", summaries.get(0).get("warning"));
    }

    @Test
    void getParameterSpecs_shouldOnlyExposeActuallySupportedParameters() {
        EventQueryTool tool = new EventQueryTool(null);
        List<String> names = tool.getParameterSpecs().stream()
            .map(spec -> spec.name())
            .toList();

        assertTrue(names.containsAll(List.of("namespace", "podName", "username", "status", "reason", "keyword")));
        assertFalse(names.contains("fieldSelector"));
        assertFalse(names.contains("labelSelector"));
        assertFalse(names.contains("since"));
        assertFalse(names.contains("involvedObjectKind"));
        assertFalse(names.contains("type"));
    }
}
