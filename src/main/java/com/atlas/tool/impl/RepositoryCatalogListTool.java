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
 * 查询组织内产品/应用镜像目录 Tool。
 *
 * <p>该 Tool 对齐 mature kube-manager 的 {@code GET /api/{orgId}/repository}，服务 NGC、NV AIE、
 * NIM 等产品目录浏览与后续部署准备。它不是 {@code registry_list} 的站点级注册处配置，也不是
 * {@code image_repository} 的普通镜像仓库清单。</p>
 */
@Component
@AtlasToolMapping(
    name = "repository_catalog_list",
    agent = "query",
    intentId = "repository_catalog_list",
    description = "查询组织内产品/应用镜像目录",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/repository"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class RepositoryCatalogListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RepositoryCatalogListTool(KubeManagerHttpClient httpClient) {
        super("repository_catalog_list", "查询组织内产品/应用镜像目录");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return RepositoryCatalogQuerySupport.catalogListSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get(
                "/api/" + orgId + "/repository",
                RepositoryCatalogQuerySupport.buildCatalogQuery(params)
            );
            return AtlasToolResult.ok("组织内产品/应用镜像目录查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[repository_catalog_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("组织内产品/应用镜像目录查询失败: " + e.getMessage());
        }
    }
}
