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
 * 删除 Docker Compose 部署 Tool。
 *
 * <p>成熟接口为 {@code DELETE /api/{orgId}/compose/{composeId}}。
 * 后端会删除 Compose 关联的 Deployment/Service，因此必须传明确 ID 并经过人工确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "compose_deploy_delete",
    agent = "deploy",
    intentId = "compose_deploy_delete",
    description = "删除 Docker Compose 部署，会删除关联的 Deployment/Service",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/compose/{composeId}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ComposeDeployDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ComposeDeployDeleteTool(KubeManagerHttpClient httpClient) {
        super("compose_deploy_delete", "删除 Docker Compose 部署");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("composeId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("composeId", "要删除的 Compose 组合 ID；必须来自 compose_list 或详情查询。", true,
                List.of("id"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String composeId = params.get("composeId") != null ? params.get("composeId").toString().trim() : "";
        if (composeId.isBlank()) {
            return AtlasToolResult.fail("缺少要删除的 Compose 组合 ID: composeId",
                "MISSING_COMPOSE_ID",
                List.of("请先通过 compose_list 查询目标组合 ID，再执行删除"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete("/api/" + orgId + "/compose/" + composeId, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("Compose 部署删除请求已发送: " + composeId, data);
        } catch (Exception e) {
            log.error("[compose_deploy_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("删除 Compose 部署失败: " + e.getMessage());
        }
    }
}
