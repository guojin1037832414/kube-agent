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
 * 节点指标查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "node_metrics",
    agent = "query",
    intentId = "node_metrics",
    description = "查询节点列表及资源使用率",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/node"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeMetricsTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeMetricsTool(KubeManagerHttpClient httpClient) {
        super("node_metrics", "查询节点列表及资源使用率");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/node";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("节点列表及资源使用率查询完成", data);
        } catch (Exception e) {
            log.error("[node_metrics] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("节点列表及资源使用率查询失败: " + e.getMessage());
        }
    }
}
