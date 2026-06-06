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
 * 查询外部 Grafana 链接 Tool。
 *
 * <p>成熟 kube-manager 没有 {@code /api/{orgId}/external-link} 列表接口，而是暴露多个
 * {@code /api/external-link/grafana/...} 单项链接。历史 Tool 打到不存在的列表路径会导致
 * Agent 展示“能查外链”，真实请求却失败。本 Tool 改为聚合成熟 Grafana 入口；Kubernetes
 * Dashboard 属于后端 SYS_ADMIN_ONLY，拆到独立敏感读取 Tool。</p>
 */
@Component
@AtlasToolMapping(
    name = "external_link_list",
    agent = "query",
    intentId = "external_link_list",
    description = "查询 Grafana 外部链接列表",
    httpMethod = "GET",
    apiEndpoints = {
        "/api/external-link/grafana/cluster",
        "/api/external-link/grafana/node",
        "/api/external-link/grafana/pod-table",
        "/api/external-link/grafana/container-line",
        "/api/external-link/grafana/example",
        "/api/external-link/grafana/summary",
        "/api/external-link/grafana/pytorch-job-log"
    },
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ExternalLinkListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ExternalLinkListTool(KubeManagerHttpClient httpClient) {
        super("external_link_list", "查询外部链接列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "category",
                "可选链接类别：all、cluster、node、pod-table、container-line、example、summary、pytorch-job-log。",
                false,
                List.of("type", "linkType")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String category = params.get("category") == null ? "all" : params.get("category").toString().trim();
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : grafanaEndpoints().entrySet()) {
                if (shouldFetch(category, entry.getKey())) {
                    data.put(entry.getKey(), extractData(httpClient.get(entry.getValue())));
                }
            }
            if (data.isEmpty()) {
                return AtlasToolResult.fail("未知的外部链接类别: " + category,
                    "UNKNOWN_EXTERNAL_LINK_CATEGORY",
                    List.of("可选值: all, cluster, node, pod-table, container-line, example, summary, pytorch-job-log"));
            }
            return AtlasToolResult.ok("Grafana 外部链接查询完成", data);
        } catch (Exception e) {
            log.error("[external_link_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询 Grafana 外部链接失败: " + e.getMessage());
        }
    }

    private boolean shouldFetch(String category, String key) {
        return category == null || category.isBlank() || "all".equals(category) || key.equals(category);
    }

    private Map<String, String> grafanaEndpoints() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("cluster", "/api/external-link/grafana/cluster");
        endpoints.put("node", "/api/external-link/grafana/node");
        endpoints.put("pod-table", "/api/external-link/grafana/pod-table");
        endpoints.put("container-line", "/api/external-link/grafana/container-line");
        endpoints.put("example", "/api/external-link/grafana/example");
        endpoints.put("summary", "/api/external-link/grafana/summary");
        endpoints.put("pytorch-job-log", "/api/external-link/grafana/pytorch-job-log");
        return endpoints;
    }
}
