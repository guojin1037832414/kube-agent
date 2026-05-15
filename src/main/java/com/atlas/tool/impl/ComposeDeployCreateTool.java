package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 创建Compose部署 Tool — 会改变后端状态的操作类接口。
 *
 * <p>⚠️ <b>安全警告</b>: 此为POST操作，会修改数据！</p>
 * <p>意图映射: {@code intentId = "compose_deploy_create"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 * <p>API路径: POST /api/{orgId}/compose</p>
 */
@Component
@AtlasToolMapping(
    name = "compose_deploy_create",
    agent = "deploy",
    intentId = "compose_deploy_create",
    description = "创建Compose部署，会修改后端状态"
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ComposeDeployCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ComposeDeployCreateTool(KubeManagerHttpClient httpClient) {
        super("compose_deploy_create", "创建Compose部署");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "yaml");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/compose";

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("name", params.get("name"));
            body.put("yaml", params.get("yaml"));

            Map<String, Object> response = httpClient.post(path, body);
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("创建Compose部署请求已发送", data);
        } catch (Exception e) {
            log.error("[compose_deploy_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("创建Compose部署失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
