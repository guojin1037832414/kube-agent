package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 添加 Helm 仓库 Tool。
 *
 * <p>成熟后端 {@code HelmController#addRepo} 暴露 {@code POST /api/{orgId}/helm/repositories}，
 * 并标注 {@code SYS_ADMIN_ONLY}。因此本 Tool 使用 ADMIN_ONLY，且只发送 HelmRepo 的 {@code name/url}
 * 两个字段，避免把 token/orgId 等上下文混入 body。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_repo_add",
    agent = "deploy",
    intentId = "helm_repo_add",
    description = "添加 Helm 仓库，会修改后端状态",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/helm/repositories"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class HelmRepoAddTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmRepoAddTool(KubeManagerHttpClient httpClient) {
        super("helm_repo_add", "添加 Helm 仓库");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "url");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("name", "Helm 仓库名称，例如 bitnami。", true, List.of("repoName")),
            ToolParameterSpec.stringParam("url", "Helm 仓库地址，例如 https://charts.bitnami.com/bitnami。", true, List.of("repoUrl"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/helm/repositories",
                buildHelmRepoBody(params)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("添加 Helm 仓库请求已发送: " + params.get("name"), data);
        } catch (Exception e) {
            log.error("[helm_repo_add] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("添加 Helm 仓库失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildHelmRepoBody(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", params.get("name"));
        body.put("url", params.get("url"));
        return body;
    }
}
