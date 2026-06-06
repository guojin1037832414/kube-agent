package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询已部署行业应用实例列表 Tool，用于业务应用运行态分析。
 */
@Component
@AtlasToolMapping(
    name = "industry_app_instance_list",
    agent = "query",
    intentId = "industry_app_instance_list",
    description = "查询行业应用实例列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/instance"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppInstanceListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppInstanceListTool(KubeManagerHttpClient httpClient) {
        super("industry_app_instance_list", "查询行业应用实例列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return IndustryAppQuerySupport.instanceListSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/instance";
            Map<String, Object> response = httpClient.get(path, IndustryAppQuerySupport.buildInstanceListQuery(params));
            return AtlasToolResult.ok("查询行业应用实例列表完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_instance_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用实例列表失败: " + e.getMessage());
        }
    }
}
