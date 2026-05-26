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
 * 资源看板查询 Tool — 接入真实 kube-manager API (Service暂无专用接口，使用Dashboard近似)。
 */
@Component
@AtlasToolMapping(
    name = "service_status",
    agent = "query",
    intentId = "service_status",
    description = "查询资源看板",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/dashboard/resources"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ServiceQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ServiceQueryTool(KubeManagerHttpClient httpClient) {
        super("service_status", "查询资源看板");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 service_status 的标准列表查询参数契约。
     *
     * <p>当前后端暂无 Service 专用接口，本 Tool 使用资源看板近似查询；
     * 仍然暴露 page/limit/keyword，是为了让 ReAct/Plan 在只读资源检索场景下
     * 使用统一参数模型，而不是诱导模型手写 URL query。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("Service 名称、资源名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/dashboard/resources";
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("资源看板查询完成 (近似)", data);
        } catch (Exception e) {
            log.error("[service_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("资源看板查询失败: " + e.getMessage());
        }
    }
}
