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
 * 查询当前组织剩余可分配资源 Tool，用于部署前容量判断和资源余量分析。
 *
 * <p>意图映射: {@code intentId = "node_remaining_resource"}</p>
 * <p>Agent 归属: query | 安全级别: P3</p>
 * <p>API 路径: GET /api/{orgId}/node/remaining</p>
 */
@Component
@AtlasToolMapping(
    name = "node_remaining_resource",
    agent = "query",
    intentId = "node_remaining_resource",
    description = "查询当前组织剩余可分配资源",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/node/remaining"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class NodeRemainingResourceTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeRemainingResourceTool(KubeManagerHttpClient httpClient) {
        super("node_remaining_resource", "查询当前组织剩余可分配资源");
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
            String path = "/api/" + orgId + "/node/remaining";
            // 后端该接口带 @Isolation，组织边界只取可信上下文，不使用调用方伪造的 orgId。
            Map<String, Object> response = httpClient.get(path, Map.of());
            return AtlasToolResult.ok("查询当前组织剩余可分配资源完成", extractData(response));
        } catch (Exception e) {
            log.error("[node_remaining_resource] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询当前组织剩余可分配资源失败: " + e.getMessage());
        }
    }
}
