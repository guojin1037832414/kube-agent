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
 * 更新 Helm 仓库索引 Tool。
 *
 * <p>成熟后端将 {@code PUT /api/{orgId}/helm/repositories} 标记为 SYS_ADMIN_ONLY。
 * 该动作会刷新系统级 Helm repo 缓存，所以 Agent 侧也必须 ADMIN_ONLY。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_repo_update",
    agent = "deploy",
    intentId = "helm_repo_update",
    description = "更新 Helm 仓库索引，会修改系统级 Helm 仓库缓存",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/helm/repositories"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class HelmRepoUpdateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmRepoUpdateTool(KubeManagerHttpClient httpClient) {
        super("helm_repo_update", "更新 Helm 仓库索引");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            new ToolParameterSpec("confirmedScope", "string", "可选说明字段，用于审批时提醒这是系统级 Helm 仓库索引刷新。", false, List.of("scope"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.put("/api/" + orgId + "/helm/repositories", Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("Helm 仓库索引更新请求已发送", data);
        } catch (Exception e) {
            log.error("[helm_repo_update] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("更新 Helm 仓库索引失败: " + e.getMessage());
        }
    }
}
