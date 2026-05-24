package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
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
    description = "查询节点信息",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/node"},
    operationType = AtlasToolMapping.OperationType.READ
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

    /**
     * 节点详情参数契约。
     *
     * <p>当前 Tool 执行逻辑读取 {@code name} 作为查询参数。本轮先保持 canonical 与
     * 现有逻辑一致，并在描述中限定为 Node 节点名称，后续如统一重构可再迁移到 nodeName。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要查询详情的 Kubernetes Node 节点名称。这里的 name 不是 Pod、Deployment、Namespace 或用户名称；未指定时可查询节点列表。",
                false,
                List.of("nodeName", "node_name", "node", "host", "hostName", "targetName", "target_name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/node".replace("{orgId}", orgId);
            Object nameParam = params.get("name");
            Map<String, Object> query = new java.util.HashMap<>();
            query.put("page", "1");
            query.put("limit", "100");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                query.put("name", nameParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询节点信息完成", data);
        } catch (Exception e) {
            log.error("[node_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询节点信息失败: " + e.getMessage());
        }
    }
}
