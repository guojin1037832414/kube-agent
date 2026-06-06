package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询组织内产品/应用镜像目录分类 Tool。
 *
 * <p>分类数据会驱动 NGC、NV AIE、NIM 页面中的行业/模型标签筛选。虽然是只读配置，但属于组织内
 * 产品目录上下文，因此按敏感读取处理。</p>
 */
@Component
@AtlasToolMapping(
    name = "repository_catalog_category_list",
    agent = "query",
    intentId = "repository_catalog_category_list",
    description = "查询组织内产品/应用镜像目录分类",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/repository/category"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class RepositoryCatalogCategoryListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RepositoryCatalogCategoryListTool(KubeManagerHttpClient httpClient) {
        super("repository_catalog_category_list", "查询组织内产品/应用镜像目录分类");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get(
                "/api/" + orgId + "/repository/category",
                Map.of()
            );
            return AtlasToolResult.ok("组织内产品/应用镜像目录分类查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[repository_catalog_category_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("组织内产品/应用镜像目录分类查询失败: " + e.getMessage());
        }
    }
}
