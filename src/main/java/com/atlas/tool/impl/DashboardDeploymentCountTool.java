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
 * 查询Dashboard部署统计信息 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "dashboard_deployment_count"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/dashboard/deployment/count</p>
 */
@Component
@AtlasToolMapping(
    name = "dashboard_deployment_count",
    agent = "query",
    intentId = "dashboard_deployment_count",
    description = "查询Dashboard部署统计信息"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DashboardDeploymentCountTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DashboardDeploymentCountTool(KubeManagerHttpClient httpClient) {
        super("dashboard_deployment_count", "查询Dashboard部署统计信息");
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
            String path = "/api/{orgId}/dashboard/deployment/count".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询Dashboard部署统计信息完成", data);
        } catch (Exception e) {
            log.error("[dashboard_deployment_count] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询Dashboard部署统计信息失败: " + e.getMessage());
        }
    }
}
