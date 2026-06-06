package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询 Pod 瞬时指标 Tool，用于应用实例状态、运行规模和异常态概览分析。
 */
@Component
@AtlasToolMapping(
    name = "metric_pod_instant",
    agent = "query",
    intentId = "metric_pod_instant",
    description = "查询 Pod 瞬时指标",
    httpMethod = "GET",
    apiEndpoints = {"/api/public/metric/prometheus/instant/pod"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class MetricPodInstantTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MetricPodInstantTool(KubeManagerHttpClient httpClient) {
        super("metric_pod_instant", "查询 Pod 瞬时指标");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            Map<String, Object> response = httpClient.get("/api/public/metric/prometheus/instant/pod", Map.of());
            return AtlasToolResult.ok("查询 Pod 瞬时指标完成", extractData(response));
        } catch (Exception e) {
            log.error("[metric_pod_instant] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询 Pod 瞬时指标失败: " + e.getMessage());
        }
    }
}
