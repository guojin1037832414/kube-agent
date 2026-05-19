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
 * 用户管理列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "user_management",
    agent = "rbac",
    intentId = "user_management",
    description = "查询用户管理列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class UserManagementTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserManagementTool(KubeManagerHttpClient httpClient) {
        super("user_management", "查询用户管理列表");
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
            String path = "/api/" + orgId + "/user";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("用户管理列表查询完成", data);
        } catch (Exception e) {
            log.error("[user_management] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户管理列表查询失败: " + e.getMessage());
        }
    }
}
