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
 * 集群概览/运营看板 Tool。
 *
 * <p>意图映射: {@code intentId = "cluster_overview"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "cluster_overview",
    agent = "query",
    intentId = "cluster_overview",
    description = "集群概览/运营看板",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/dashboard/resources"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ClusterOverviewTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ClusterOverviewTool(KubeManagerHttpClient httpClient) {
        super("cluster_overview", "集群概览/运营看板");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            // M5.5 多租户安全治理：orgId 必须来自可信 ThreadLocal，禁止使用 params.organizationId。
            String orgId = resolveOrganizationId(params);

            String path = "/api/" + orgId + "/dashboard/resources";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);

            return AtlasToolResult.ok("集群概览查询完成", data);
        } catch (Exception e) {
            log.error("[cluster_overview] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("集群概览查询失败: " + e.getMessage());
        }
    }
}
