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
 * 查询域名/Ingress Tool。
 *
 * <p>意图映射: {@code intentId = "ingress_query"}</p>
 * <p>Agent归属: network | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "ingress_query",
    agent = "network",
    intentId = "ingress_query",
    description = "查询域名/Ingress",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/dashboard/deployment"},
    operationType = AtlasToolMapping.OperationType.READ
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class IngressQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IngressQueryTool(KubeManagerHttpClient httpClient) {
        super("ingress_query", "查询域名/Ingress");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            log.info("[ingress_query] 执行查询域名/Ingress");
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/dashboard/deployment";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("Ingress 查询完成", data);
        } catch (Exception e) {
            log.error("[ingress_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Ingress 查询失败: " + e.getMessage());
        }
    }
}
