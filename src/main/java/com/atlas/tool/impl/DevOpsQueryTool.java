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
 * DevOps工作负载查询 Tool — 接入真实 kube-manager API (暂无专用接口，使用Dashboard近似)。
 */
@Component
@AtlasToolMapping(
    name = "devops_pipeline",
    agent = "query",
    intentId = "devops_pipeline",
    description = "查询DevOps工作负载"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DevOpsQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DevOpsQueryTool(KubeManagerHttpClient httpClient) {
        super("devops_pipeline", "查询DevOps工作负载");
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
            return AtlasToolResult.ok("DevOps工作负载查询完成 (近似)", data);
        } catch (Exception e) {
            log.error("[devops_pipeline] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("DevOps工作负载查询失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
