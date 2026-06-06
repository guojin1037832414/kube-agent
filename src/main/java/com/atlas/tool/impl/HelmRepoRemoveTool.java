package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 移除 Helm 仓库 Tool。
 *
 * <p>成熟接口为 {@code DELETE /api/{orgId}/helm/repositories/{repoName}}，
 * 且后端标记 SYS_ADMIN_ONLY。删除仓库会影响系统级 chart 来源，必须管理员确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_repo_remove",
    agent = "deploy",
    intentId = "helm_repo_remove",
    description = "移除 Helm 仓库，会修改系统级 Helm 仓库配置",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/helm/repositories/{repoName}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class HelmRepoRemoveTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmRepoRemoveTool(KubeManagerHttpClient httpClient) {
        super("helm_repo_remove", "移除 Helm 仓库");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("repoName");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("repoName", "要移除的 Helm 仓库名称。", true, List.of("name", "repo"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String repoName = params.get("repoName") != null ? params.get("repoName").toString().trim() : "";
        if (repoName.isBlank()) {
            return AtlasToolResult.fail("缺少要移除的 Helm 仓库名称: repoName",
                "MISSING_HELM_REPO_NAME",
                List.of("请提供明确的 repoName，不能用模糊描述删除系统级仓库"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/helm/repositories/" + repoName,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Helm 仓库移除请求已发送: " + repoName, data);
        } catch (Exception e) {
            log.error("[helm_repo_remove] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("移除 Helm 仓库失败: " + e.getMessage());
        }
    }
}
