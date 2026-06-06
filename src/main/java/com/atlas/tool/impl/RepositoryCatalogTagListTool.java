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
 * 查询产品/应用镜像目录的普通 tag 状态 Tool。
 *
 * <p>成熟接口要求调用方先选定一个 repository，再查询该目录下 tag 的本地镜像状态。Agent 侧必须保留
 * 这个顺序，避免把 tag 查询扩展成模糊枚举入口。</p>
 */
@Component
@AtlasToolMapping(
    name = "repository_catalog_tag_list",
    agent = "query",
    intentId = "repository_catalog_tag_list",
    description = "查询产品/应用镜像目录的普通 tag 状态",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/repository/tags"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class RepositoryCatalogTagListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RepositoryCatalogTagListTool(KubeManagerHttpClient httpClient) {
        super("repository_catalog_tag_list", "查询产品/应用镜像目录的普通 tag 状态");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("repository");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return RepositoryCatalogQuerySupport.repositoryOnlySpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get(
                "/api/" + orgId + "/repository/tags",
                RepositoryCatalogQuerySupport.buildRepositoryQuery(params)
            );
            return AtlasToolResult.ok("产品/应用镜像目录普通 tag 状态查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[repository_catalog_tag_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("产品/应用镜像目录普通 tag 状态查询失败: " + e.getMessage());
        }
    }
}
