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
 * 查询节点分配情况 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "node_allocation"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/node/organization/allocation</p>
 */
@Component
@AtlasToolMapping(
    name = "node_allocation",
    agent = "query",
    intentId = "node_allocation",
    description = "查询节点分配情况",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/node/organization/allocation"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeAllocationTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeAllocationTool(KubeManagerHttpClient httpClient) {
        super("node_allocation", "查询节点分配情况");
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
            String path = "/api/{orgId}/node/organization/allocation".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询节点分配情况完成", data);
        } catch (Exception e) {
            log.error("[node_allocation] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询节点分配情况失败: " + e.getMessage());
        }
    }
}
