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
 * 查询网络配置 Tool。
 *
 * <p>意图映射: {@code intentId = "network_query"}</p>
 * <p>Agent归属: network | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "network_query",
    agent = "network",
    intentId = "network_query",
    description = "查询网络配置",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/dashboard/deployment"},
    operationType = AtlasToolMapping.OperationType.READ
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NetworkQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NetworkQueryTool(KubeManagerHttpClient httpClient) {
        super("network_query", "查询网络配置");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            log.info("[network_query] 执行查询网络配置");
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/dashboard/deployment";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("网络配置查询完成", data);
        } catch (Exception e) {
            log.error("[network_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("网络配置查询失败: " + e.getMessage());
        }
    }
}
