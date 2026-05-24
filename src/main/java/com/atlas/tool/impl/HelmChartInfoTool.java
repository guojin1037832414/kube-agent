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
 * 查询Helm Chart详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "helm_chart_info"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/helm/charts/single</p>
 * <p>备注: Helm服务可能未连接，实际调用可能返回connection_refused错误</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_chart_info",
    agent = "deploy",
    intentId = "helm_chart_info",
    description = "查询Helm Chart详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/helm/charts/single"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HelmChartInfoTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmChartInfoTool(KubeManagerHttpClient httpClient) {
        super("helm_chart_info", "查询Helm Chart详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("chart");
    }

    /**
     * Helm Chart 详情查询参数契约。
     *
     * <p>当前执行逻辑读取的 canonical 字段是 {@code chart}。这里的 chart 表示 Helm Chart
     * 名称或标识，不是 Helm Release 名称，也不是 Dashboard 图表。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "chart",
                "要查询详情的 Helm Chart 名称或标识。这里的 chart 不是 Helm Release 名称，也不是 Dashboard 图表。",
                true,
                List.of("chartName", "chart_name", "helmChart", "helm_chart")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/helm/charts/single".replace("{orgId}", orgId);

            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");

            Object chartParam = params.get("chart");
            if (chartParam != null && !chartParam.toString().isBlank()) {
                query.put("chart", chartParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询Helm Chart详情完成", data);
        } catch (Exception e) {
            log.error("[helm_chart_info] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询Helm Chart详情失败: " + e.getMessage());
        }
    }
}
