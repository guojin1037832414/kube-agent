package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询行业应用实例 API 调用历史 Tool，用于接口访问分析和异常定位。
 */
@Component
@AtlasToolMapping(
    name = "industry_app_instance_api_history",
    agent = "query",
    intentId = "industry_app_instance_api_history",
    description = "查询行业应用实例API调用历史",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/instance/{instanceId}/api-history"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppInstanceApiHistoryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppInstanceApiHistoryTool(KubeManagerHttpClient httpClient) {
        super("industry_app_instance_api_history", "查询行业应用实例API调用历史");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("instanceId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        List<ToolParameterSpec> specs = new ArrayList<>(IndustryAppQuerySupport.instanceIdSpecs());
        specs.addAll(IndustryAppQuerySupport.apiHistorySpecs());
        return specs;
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String instanceId = IndustryAppQuerySupport.positiveId(params, "instanceId");
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/instance/" + instanceId + "/api-history";
            Map<String, Object> response = httpClient.get(path, IndustryAppQuerySupport.buildApiHistoryQuery(params));
            return AtlasToolResult.ok("查询行业应用实例API调用历史完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_instance_api_history] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用实例API调用历史失败: " + e.getMessage());
        }
    }
}
