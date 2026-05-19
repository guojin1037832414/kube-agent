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
 * 搜索Helm Chart Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "helm_chart_search"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/helm/repositories/charts</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_chart_search",
    agent = "deploy",
    intentId = "helm_chart_search",
    description = "搜索Helm Chart"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HelmChartSearchTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmChartSearchTool(KubeManagerHttpClient httpClient) {
        super("helm_chart_search", "搜索Helm Chart");
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
            String path = "/api/{orgId}/helm/repositories/charts".replace("{orgId}", orgId);
            Object kwParam = params.get("keyword");
            if (kwParam != null && !kwParam.toString().isBlank()) {
                path += "?keyword=" + kwParam;
            }
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("搜索Helm Chart完成", data);
        } catch (Exception e) {
            log.error("[helm_chart_search] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("搜索Helm Chart失败: " + e.getMessage());
        }
    }
}
