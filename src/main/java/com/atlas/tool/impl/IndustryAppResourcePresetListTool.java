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
 * 查询行业应用模板资源预设 Tool，用于部署前容量和规格分析。
 */
@Component
@AtlasToolMapping(
    name = "industry_app_resource_preset_list",
    agent = "query",
    intentId = "industry_app_resource_preset_list",
    description = "查询行业应用模板资源预设",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/template/{appId}/resource-preset"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppResourcePresetListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppResourcePresetListTool(KubeManagerHttpClient httpClient) {
        super("industry_app_resource_preset_list", "查询行业应用模板资源预设");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("appId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return IndustryAppQuerySupport.appIdSpecs("行业应用模板 ID");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String appId = IndustryAppQuerySupport.positiveId(params, "appId");
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/template/" + appId + "/resource-preset";
            Map<String, Object> response = httpClient.get(path, Map.of());
            return AtlasToolResult.ok("查询行业应用模板资源预设完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_resource_preset_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用模板资源预设失败: " + e.getMessage());
        }
    }
}
