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
 * 查询 NIM 产品目录的 tag 状态 Tool。
 *
 * <p>mature kube-manager 单独提供 {@code /repository/nim/tags}，因为 NIM 镜像标签语义和普通 NGC
 * 容器目录不同。该 Tool 为后续 NIM 部署编排提供只读准备数据，不直接创建实例。</p>
 */
@Component
@AtlasToolMapping(
    name = "repository_catalog_nim_tag_list",
    agent = "query",
    intentId = "repository_catalog_nim_tag_list",
    description = "查询 NIM 产品目录的 tag 状态",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/repository/nim/tags"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class RepositoryCatalogNimTagListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RepositoryCatalogNimTagListTool(KubeManagerHttpClient httpClient) {
        super("repository_catalog_nim_tag_list", "查询 NIM 产品目录的 tag 状态");
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
                "/api/" + orgId + "/repository/nim/tags",
                RepositoryCatalogQuerySupport.buildRepositoryQuery(params)
            );
            return AtlasToolResult.ok("NIM 产品目录 tag 状态查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[repository_catalog_nim_tag_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("NIM 产品目录 tag 状态查询失败: " + e.getMessage());
        }
    }
}
