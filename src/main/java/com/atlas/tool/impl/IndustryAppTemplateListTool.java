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
 * 查询行业应用模板列表 Tool，用于让 AI 了解可部署的业务应用资产。
 *
 * <p>意图映射: {@code intentId = "industry_app_template_list"}</p>
 * <p>Agent 归属: query | 安全级别: P3</p>
 * <p>API 路径: GET /api/{orgId}/industry-app/template</p>
 */
@Component
@AtlasToolMapping(
    name = "industry_app_template_list",
    agent = "query",
    intentId = "industry_app_template_list",
    description = "查询行业应用模板列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/industry-app/template"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class IndustryAppTemplateListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public IndustryAppTemplateListTool(KubeManagerHttpClient httpClient) {
        super("industry_app_template_list", "查询行业应用模板列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return IndustryAppQuerySupport.templateListSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/industry-app/template";
            Map<String, Object> response = httpClient.get(path, IndustryAppQuerySupport.buildTemplateListQuery(params));
            return AtlasToolResult.ok("查询行业应用模板列表完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[industry_app_template_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询行业应用模板列表失败: " + e.getMessage());
        }
    }
}
