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
 * NIM 部署只读预检 Tool。
 *
 * <p>成熟前端的一键部署链路是 repository -> NIM tag -> NIM template -> formatApplication
 * -> deployment create。本 Tool 只执行前三个 GET，把可审计的部署准备数据返回给 Agent，
 * 不创建 Deployment，不轮询服务，也不暴露 API Key。</p>
 */
@Component
@AtlasToolMapping(
    name = "nim_deployment_preflight",
    agent = "deploy",
    intentId = "nim_deployment_preflight",
    description = "NIM 部署前只读预检，查询一键部署目录、NIM tag 和 NIM 模板",
    httpMethod = "GET",
    apiEndpoints = {
        "/api/{orgId}/repository",
        "/api/{orgId}/repository/nim/tags",
        "/api/{orgId}/template"
    },
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class NimDeploymentPreflightTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NimDeploymentPreflightTool(KubeManagerHttpClient httpClient) {
        super("nim_deployment_preflight", "NIM 部署前只读预检");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return NimDeploymentPreflightSupport.parameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String repository = NimDeploymentPreflightSupport.optionalRepository(params);

            Map<String, Object> catalogResponse = httpClient.get(
                "/api/" + orgId + "/repository",
                NimDeploymentPreflightSupport.buildCatalogQuery(params)
            );
            Object catalogData = extractData(catalogResponse);

            String selectedRepository = NimDeploymentPreflightSupport.selectRepository(catalogData, repository);
            Map<String, Object> tagResponse = httpClient.get(
                "/api/" + orgId + "/repository/nim/tags",
                NimDeploymentPreflightSupport.buildTagQuery(selectedRepository)
            );
            Object tagData = extractData(tagResponse);
            Map<String, Object> selectedTag = NimDeploymentPreflightSupport.chooseTag(
                tagData,
                optionalTrimmed(params.get("tag"))
            );
            String image = NimDeploymentPreflightSupport.buildImage(selectedTag);

            Map<String, Object> templateResponse = httpClient.get(
                "/api/" + orgId + "/template",
                NimDeploymentPreflightSupport.buildTemplateQuery(image)
            );
            Object templateData = extractData(templateResponse);
            Map<String, Object> selectedTemplate = NimDeploymentPreflightSupport.chooseTemplate(templateData);

            Map<String, Object> plan = NimDeploymentPreflightSupport.buildPlan(
                params,
                catalogData,
                tagData,
                selectedTag,
                image,
                templateData,
                selectedTemplate
            );
            return AtlasToolResult.ok("NIM 部署预检完成：已找到候选镜像 tag 与 NIM 模板，未执行创建", plan);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[nim_deployment_preflight] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("NIM 部署预检失败: " + e.getMessage());
        }
    }

    private String optionalTrimmed(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }
}
