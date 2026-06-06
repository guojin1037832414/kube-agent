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
 * 更新 Docker Compose 组合名称 Tool。
 *
 * <p>成熟后端 {@code PUT /api/{orgId}/compose/{composeId}} 当前只支持更新 composeName，
 * 不支持修改内部 Deployment 内容。因此本 Tool 只发送 composeName，避免让 Agent 误以为可热更新 YAML。</p>
 */
@Component
@AtlasToolMapping(
    name = "compose_deploy_update",
    agent = "deploy",
    intentId = "compose_deploy_update",
    description = "更新 Docker Compose 组合名称，不修改内部 Deployment 内容",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/compose/{composeId}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ComposeDeployUpdateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ComposeDeployUpdateTool(KubeManagerHttpClient httpClient) {
        super("compose_deploy_update", "更新 Docker Compose 组合名称");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("composeId", "composeName");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("composeId", "要更新的 Compose 组合 ID。", true, List.of("id")),
            ToolParameterSpec.stringParam("composeName", "新的 Compose 组合名称；后端当前只支持改名。", true,
                List.of("name", "displayName"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String composeId = requiredString(params, "composeId");
        String composeName = requiredString(params, "composeName");
        if (composeId.isBlank() || composeName.isBlank()) {
            return AtlasToolResult.fail("缺少更新 Compose 组合所需参数: composeId/composeName",
                "MISSING_COMPOSE_UPDATE_PARAMS",
                List.of("请提供 composeId 和新的 composeName；该接口不支持修改 YAML 或 Deployment 内容"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("composeName", composeName);
            Map<String, Object> response = httpClient.put("/api/" + orgId + "/compose/" + composeId, body);
            Object data = extractData(response);
            return AtlasToolResult.ok("Compose 组合改名请求已发送: " + composeId + " -> " + composeName, data);
        } catch (Exception e) {
            log.error("[compose_deploy_update] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("更新 Compose 组合失败: " + e.getMessage());
        }
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
