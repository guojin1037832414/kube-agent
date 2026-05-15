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
 * 查询部署实例详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "deployment_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/deployment</p>
 */
@Component
@AtlasToolMapping(
    name = "deployment_detail",
    agent = "query",
    intentId = "deployment_detail",
    description = "查询部署实例详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DeploymentDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeploymentDetailTool(KubeManagerHttpClient httpClient) {
        super("deployment_detail", "查询部署实例详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/deployment".replace("{orgId}", orgId);

            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                path += "?name=" + nameParam;
            }
            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询部署实例详情完成", data);
        } catch (Exception e) {
            log.error("[deployment_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询部署实例详情失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
