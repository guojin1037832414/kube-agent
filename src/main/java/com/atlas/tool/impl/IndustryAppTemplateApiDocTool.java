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
 * 查询行业应用模板 API 文档 Tool，帮助 AI 理解应用对外接口能力。
 */
@Component
@AtlasToolMapping(
    name = "industry_app_template_api_doc",
    agent = "query",
    intentId = "industry_app_template_api_doc",
    description = "查询行业应用模板API文档",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/template/{appId}/api-doc"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppTemplateApiDocTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppTemplateApiDocTool(KubeManagerHttpClient httpClient) {
        super("industry_app_template_api_doc", "查询行业应用模板API文档");
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
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/template/" + appId + "/api-doc";
            Map<String, Object> response = httpClient.get(path, Map.of());
            return AtlasToolResult.ok("查询行业应用模板API文档完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_template_api_doc] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用模板API文档失败: " + e.getMessage());
        }
    }
}
