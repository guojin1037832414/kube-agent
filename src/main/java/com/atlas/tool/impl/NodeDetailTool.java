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
 * 查询单个节点详情 Tool。
 *
 * <p>意图映射: {@code intentId = "node_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "node_detail",
    agent = "query",
    intentId = "node_detail",
    description = "查询单个节点详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeDetailTool(KubeManagerHttpClient httpClient) {
        super("node_detail", "查询单个节点详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("nodeName");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = params.get("organizationId") != null
                ? params.get("organizationId").toString()
                : "100001";
            String nodeName = params.get("nodeName").toString();

            String path = "/api/" + orgId + "/node/" + nodeName;
            Map<String, Object> response = httpClient.get(path);
            Object data = response.containsKey("result") ? response.get("result") : response;

            return AtlasToolResult.ok("节点详情查询完成", data);
        } catch (Exception e) {
            log.error("[node_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("节点详情查询失败: " + e.getMessage());
        }
    }
}
