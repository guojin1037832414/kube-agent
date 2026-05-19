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
 * Namespace列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "namespace_status",
    agent = "query",
    intentId = "namespace_status",
    description = "查询Namespace列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NamespaceQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NamespaceQueryTool(KubeManagerHttpClient httpClient) {
        super("namespace_status", "查询Namespace列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/namespace";
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("Namespace列表查询完成", data);
        } catch (Exception e) {
            log.error("[namespace_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Namespace列表查询失败: " + e.getMessage());
        }
    }
}
