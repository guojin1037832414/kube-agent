package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
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
    description = "查询DaemonSet状态",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/dashboard/deployment"},
    operationType = AtlasToolMapping.OperationType.READ
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

    /**
     * 声明 daemonset_status 的标准列表查询参数契约。
     *
     * <p>当前后端使用 Deployment 看板数据近似承载 DaemonSet 状态查询，
     * 因此只暴露通用 page/limit/keyword 筛选，不提前承诺不存在的 DaemonSet
     * 专用字段，避免 Tool schema 与真实 API 能力脱节。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("DaemonSet 名称、工作负载名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/dashboard/deployment";
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("DaemonSet状态查询完成 (近似)", data);
        } catch (Exception e) {
            log.error("[daemonset_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("DaemonSet状态查询失败: " + e.getMessage());
        }
    }
}
