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
 * Deployment列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "deployment_status",
    agent = "query",
    intentId = "deployment_status",
    description = "查询Deployment列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DeploymentQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeploymentQueryTool(KubeManagerHttpClient httpClient) {
        super("deployment_status", "查询Deployment列表");
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
            String path = "/api/" + orgId + "/deployment";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("Deployment列表查询完成", data);
        } catch (Exception e) {
            log.error("[deployment_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Deployment列表查询失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
