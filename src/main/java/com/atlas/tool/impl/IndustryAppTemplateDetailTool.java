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
 * 查询行业应用模板详情 Tool，用于部署前解释镜像、端口、资源和业务描述。
 */
@Component
@AtlasToolMapping(
    name = "industry_app_template_detail",
    agent = "query",
    intentId = "industry_app_template_detail",
    description = "查询行业应用模板详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/template/{appId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppTemplateDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppTemplateDetailTool(KubeManagerHttpClient httpClient) {
        super("industry_app_template_detail", "查询行业应用模板详情");
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
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/template/" + appId;
            Map<String, Object> response = httpClient.get(path, Map.of());
            return AtlasToolResult.ok("查询行业应用模板详情完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_template_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用模板详情失败: " + e.getMessage());
        }
    }
}
