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
 * 集群列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "cluster_query",
    agent = "query",
    intentId = "cluster_query",
    description = "查询集群列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-job/cluster"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ClusterQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ClusterQueryTool(KubeManagerHttpClient httpClient) {
        super("cluster_query", "查询集群列表");
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
            String path = "/api/" + orgId + "/hpc-job/cluster";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("集群列表查询完成", data);
        } catch (Exception e) {
            log.error("[cluster_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("集群列表查询失败: " + e.getMessage());
        }
    }
}
