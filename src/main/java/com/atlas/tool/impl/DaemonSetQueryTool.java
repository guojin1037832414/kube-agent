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
 * DaemonSet状态查询 Tool — 接入真实 kube-manager API (使用Deployment列表近似)。
 */
@Component
@AtlasToolMapping(
    name = "daemonset_status",
    agent = "query",
    intentId = "daemonset_status",
    description = "查询DaemonSet状态"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DaemonSetQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DaemonSetQueryTool(KubeManagerHttpClient httpClient) {
        super("daemonset_status", "查询DaemonSet状态");
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
            String path = "/api/" + orgId + "/dashboard/deployment";
            Map<String, Object> response = httpClient.get(path, Map.of("current", "1", "size", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("DaemonSet状态查询完成 (近似)", data);
        } catch (Exception e) {
            log.error("[daemonset_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("DaemonSet状态查询失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
