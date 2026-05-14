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
 * GPU配置映射查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "gpu_metrics",
    agent = "query",
    intentId = "gpu_metrics",
    description = "查询GPU配置映射"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuMetricsTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public GpuMetricsTool(KubeManagerHttpClient httpClient) {
        super("gpu_metrics", "查询GPU配置映射");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/node/all/gpu-map";
            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("GPU配置映射查询完成", data);
        } catch (Exception e) {
            log.error("[gpu_metrics] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("GPU配置映射查询失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
