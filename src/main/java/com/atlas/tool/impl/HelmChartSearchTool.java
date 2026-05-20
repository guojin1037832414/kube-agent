package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Helm Chart 搜索参数契约。
     *
     * <p>keyword 是模糊搜索关键字，可为空；它不是精确 Chart 名称、Release 名称或仓库名称。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "keyword",
                "Helm Chart 模糊搜索关键字，可为空；不是精确 Chart 名称、Helm Release 名称或仓库名称。",
                false,
                List.of("q", "query", "search", "searchText", "search_text", "filter")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/helm/repositories/charts".replace("{orgId}", orgId);
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");

            Object kwParam = params.get("keyword");
            if (kwParam != null && !kwParam.toString().isBlank()) {
                query.put("keyword", kwParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("搜索Helm Chart完成", data);
        } catch (Exception e) {
            log.error("[helm_chart_search] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("搜索Helm Chart失败: " + e.getMessage());
        }
    }
}
