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
 * 节点查询 Tool — P1 阶段接入真实 kube-manager API。
 *
 * <p>意图映射：{@code intentId = "node_query"}，对应 "查询所有节点状态"。</p>
 * <p>API 路径：GET /api/{organizationId}/node?page=1&limit=100</p>
 */
@Component
@AtlasToolMapping(
    name = "node_query",
    agent = "query",
    intentId = "node_query",
    description = "查询 Kubernetes 集群所有节点的状态、资源使用情况"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeQueryTool(KubeManagerHttpClient httpClient) {
        super("node_query", "查询 Kubernetes 集群所有节点的状态、资源使用情况");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of(); // 无必填参数
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            // ① 从参数获取组织ID，默认 100001
            String orgId = params.get("organizationId") != null
                ? params.get("organizationId").toString()
                : "100001";

            // ② 调用 kube-manager 真实 API
            String path = "/api/" + orgId + "/node";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));

            // ③ 提取实际数据（如果响应有嵌套 result 结构）
            Object data = response.containsKey("result") ? response.get("result") : response;

            String summary = data instanceof java.util.List
                ? String.format("集群共有 %d 个节点", ((java.util.List<?>) data).size())
                : "节点查询完成";

            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[node_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("节点查询失败: " + e.getMessage());
        }
    }
}
