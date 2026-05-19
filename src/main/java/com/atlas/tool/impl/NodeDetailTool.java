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
 * 查询节点信息 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "node_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/node</p>
 */
@Component
@AtlasToolMapping(
    name = "node_detail",
    agent = "query",
    intentId = "node_detail",
    description = "查询节点信息"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeDetailTool(KubeManagerHttpClient httpClient) {
        super("node_detail", "查询节点信息");
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
            String path = "/api/{orgId}/node".replace("{orgId}", orgId);
            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                path += "?name=" + nameParam;
            }
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询节点信息完成", data);
        } catch (Exception e) {
            log.error("[node_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询节点信息失败: " + e.getMessage());
        }
    }
}
