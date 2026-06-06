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
 * 查询应用模板详情 Tool。
 *
 * <p>只读取成熟后端 {@code GET /api/{orgId}/template/{templateId}}。
 * 模板创建、更新和删除会改变线上资源定义，本批不接入。</p>
 */
@Component
@AtlasToolMapping(
    name = "template_detail",
    agent = "deploy",
    intentId = "template_detail",
    description = "查询应用模板详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/template/{templateId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class TemplateDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public TemplateDetailTool(KubeManagerHttpClient httpClient) {
        super("template_detail", "查询应用模板详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("templateId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "templateId",
            "integer",
            "应用模板 ID，来源应为 template_list 返回的数字 ID。",
            true,
            List.of("id", "template_id", "appTemplateId", "app_template_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String templateId = TemplateQuerySupport.positiveTemplateId(params, "templateId", "应用模板");
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/template/" + templateId, Map.of());
            return AtlasToolResult.ok("应用模板详情查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[template_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("应用模板详情查询失败: " + e.getMessage());
        }
    }
}
